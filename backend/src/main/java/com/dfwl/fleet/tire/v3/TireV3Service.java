package com.dfwl.fleet.tire.v3;

import com.dfwl.fleet.approval.v3.ApprovalWorkflowService;
import com.dfwl.fleet.attachment.service.AttachmentService;
import com.dfwl.fleet.common.api.PageResponse;
import com.dfwl.fleet.common.error.BusinessException;
import com.dfwl.fleet.common.error.ErrorCode;
import com.dfwl.fleet.security.AuthenticatedUser;
import com.dfwl.fleet.security.CurrentDriverContext;
import com.dfwl.fleet.security.CurrentUserService;
import com.dfwl.fleet.system.service.AuditWriteService;
import com.dfwl.fleet.tire.dto.request.OcrConfirmRequest;
import com.dfwl.fleet.tire.dto.response.OcrRecordResponse;
import com.dfwl.fleet.tire.service.TireService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/** The sole V3 tire inventory, import and driver-request write path. */
@Service
public class TireV3Service {
    private static final List<String> HEADERS = List.of("到货时间", "物料描述", "条码", "胎号");
    private static final DateTimeFormatter ARRIVAL = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Pattern SEPARATED_DATE = Pattern.compile("^(\\d{4})([-/.])(\\d{1,2})\\2(\\d{1,2})(?:[ T]+(\\d{1,2}):(\\d{2})(?::(\\d{2}))?)?$");
    private static final Pattern CHINESE_DATE = Pattern.compile("^(\\d{4})年(\\d{1,2})月(\\d{1,2})日(?:\\s*(\\d{1,2}):(\\d{2})(?::(\\d{2}))?)?$");
    private static final Pattern COMPACT_DATE = Pattern.compile("^(\\d{4})(\\d{2})(\\d{2})(?:[ T]+(\\d{1,2}):(\\d{2})(?::(\\d{2}))?)?$");
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final CurrentUserService users;
    private final AuditWriteService audit;
    private final AttachmentService attachments;
    private final ApprovalWorkflowService approvals;
    private final TireService ocrService;
    private final TransactionTemplate transactions;

    public TireV3Service(JdbcTemplate jdbc, ObjectMapper mapper, CurrentUserService users,
            AuditWriteService audit, AttachmentService attachments, ApprovalWorkflowService approvals,
            TireService ocrService,
            org.springframework.transaction.PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc; this.mapper = mapper; this.users = users; this.audit = audit;
        this.attachments = attachments; this.approvals = approvals; this.ocrService = ocrService;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public record TireEdit(String tireNo, String barcode, String materialDescription,
                           LocalDateTime arrivedAt, Integer expectedVersion, String reason) {}
    public record TireRequestItem(Long ocrRecordId, String confirmedTireNo) {}
    public record TireRequestInput(Long templateId, List<TireRequestItem> items, String requestKey) {}
    public record TireResubmission(List<TireRequestItem> items, Integer expectedVersion) {}
    public record ImportRow(int rowNo, String arrivedAt, String materialDescription, String barcode, String tireNo) {}

    public PageResponse<Map<String, Object>> inventory(int pageNo, int pageSize, String tireNo, String status) {
        if (pageNo < 1 || pageSize < 1 || pageSize > 100) throw invalid("分页参数不正确");
        if (status != null && !status.isBlank() && !Set.of("AVAILABLE", "CLAIMED").contains(status))
            throw invalid("轮胎状态不正确");
        String where = " WHERE t.deleted_at IS NULL" +
                (tireNo == null || tireNo.isBlank() ? "" : " AND t.tire_no LIKE ?") +
                (status == null || status.isBlank() ? "" : " AND t.status=?");
        List<Object> params = new ArrayList<>();
        if (tireNo != null && !tireNo.isBlank()) params.add("%" + tireNo.trim() + "%");
        if (status != null && !status.isBlank()) params.add(status);
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM tire t" + where, Long.class, params.toArray());
        params.add(pageSize); params.add((pageNo - 1) * pageSize);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT t.id, t.tire_no tireNo, t.barcode, t.material_description materialDescription,
                       t.arrived_at arrivedAt, t.status, t.version, t.created_at createdAt,
                       d.name applicantName, v.plate_no currentVehiclePlate
                FROM tire t
                LEFT JOIN tire_request r ON r.tire_id=t.id AND r.status='APPROVED'
                LEFT JOIN driver d ON d.id=r.applicant_driver_id
                LEFT JOIN vehicle v ON v.id=r.vehicle_id
                %s ORDER BY t.id DESC LIMIT ? OFFSET ?
                """.formatted(where), params.toArray());
        return new PageResponse<>(pageNo, pageSize, count == null ? 0 : count, rows);
    }

    public Map<String, Object> detail(long id) {
        Map<String, Object> tire = one("""
                SELECT t.id,t.tire_no tireNo,t.barcode,t.material_description materialDescription,
                       t.arrived_at arrivedAt,t.status,t.version,t.created_at createdAt,
                       d.name applicantName,v.plate_no currentVehiclePlate
                FROM tire t
                LEFT JOIN tire_request r ON r.tire_id=t.id AND r.status='APPROVED'
                LEFT JOIN driver d ON d.id=r.applicant_driver_id
                LEFT JOIN vehicle v ON v.id=r.vehicle_id
                WHERE t.id=? AND t.deleted_at IS NULL
                """, id);
        if (tire == null) throw new BusinessException(ErrorCode.DATA_001);
        tire.put("history", jdbc.queryForList("""
                SELECT h.field_name fieldName,h.before_value beforeValue,h.after_value afterValue,
                       h.reason,h.operated_at operatedAt,u.phone operatorPhone
                FROM tire_history h LEFT JOIN sys_user u ON u.id=h.operator_id
                WHERE h.tire_id=? ORDER BY h.id DESC
                """, id));
        tire.put("approvalChain", jdbc.queryForList("""
                SELECT chain_entry.approvalNo,chain_entry.versionNo,chain_entry.eventType,chain_entry.nodeName,
                       chain_entry.operatorPhone,chain_entry.operatorRole,chain_entry.operatedAt,chain_entry.comment,
                       chain_entry.targetNodeName
                FROM (
                  SELECT a.approval_no approvalNo,s.version_no versionNo,
                         CASE WHEN s.version_no=1 THEN 'SUBMIT' ELSE 'RESUBMIT' END eventType,
                         NULL nodeName,u.phone operatorPhone,role.role_name operatorRole,
                         s.submitted_at operatedAt,NULL comment,NULL targetNodeName,
                         0 eventOrder,s.id eventId
                  FROM approval_submission_version s
                  JOIN approval_instance a ON a.id=s.instance_id
                  JOIN sys_user u ON u.id=s.submitted_by
                  JOIN sys_role role ON role.id=u.role_id
                  WHERE EXISTS (SELECT 1 FROM tire_request tr
                                WHERE tr.tire_id=? AND tr.approval_instance_id=a.id)
                  UNION ALL
                  SELECT a.approval_no,sv.version_no,x.action_type,n.node_name,
                         u.phone,role.role_name,x.operated_at,x.comment,
                         x.target_step_name_snapshot,1,x.id
                  FROM approval_action x
                  JOIN approval_task k ON k.id=x.task_id
                  JOIN approval_submission_version sv ON sv.id=k.submission_version_id
                  JOIN approval_instance a ON a.id=k.instance_id
                  JOIN approval_flow_node n ON n.id=k.node_id
                  JOIN sys_user u ON u.id=x.operator_id
                  JOIN sys_role role ON role.id=u.role_id
                  WHERE x.action_type<>'RESUBMIT'
                    AND EXISTS (SELECT 1 FROM tire_request tr
                                WHERE tr.tire_id=? AND tr.approval_instance_id=a.id)
                  UNION ALL
                  SELECT a.approval_no,sv.version_no,'PENDING',n.node_name,
                         NULL,NULL,k.created_at,NULL,NULL,2,k.id
                  FROM approval_task k
                  JOIN approval_submission_version sv ON sv.id=k.submission_version_id
                  JOIN approval_instance a ON a.id=k.instance_id
                  JOIN approval_flow_node n ON n.id=k.node_id
                  WHERE k.status='PENDING' AND k.invalidated=0
                    AND EXISTS (SELECT 1 FROM tire_request tr
                                WHERE tr.tire_id=? AND tr.approval_instance_id=a.id)
                ) chain_entry
                ORDER BY chain_entry.operatedAt,chain_entry.eventOrder,chain_entry.eventId
                """, id, id, id));
        return tire;
    }

    public List<Map<String, Object>> history(long id) {
        return (List<Map<String, Object>>) detail(id).get("history");
    }

    public Map<String, Object> requestDetail(long id, AuthenticatedUser user) {
        Map<String, Object> request = one("""
                SELECT r.id,r.batch_key batchKey,r.confirmed_tire_no tireNo,r.status,r.created_at requestedAt,
                       r.ocr_record_id ocrRecordId,r.photo_attachment_id photoAttachmentId,
                       d.name driverName,v.plate_no vehiclePlate,o.recognized_text recognizedText,
                       o.confirmed_text confirmedText,r.applicant_driver_id applicantDriverId
                FROM tire_request r JOIN driver d ON d.id=r.applicant_driver_id
                JOIN vehicle v ON v.id=r.vehicle_id JOIN ocr_record o ON o.id=r.ocr_record_id
                WHERE r.id=?
                """, id);
        if (request == null) throw new BusinessException(ErrorCode.DATA_001);
        if (users.isDriverUser(user)) {
            if (users.requireDriver(user).driverId() != ((Number) request.get("applicantDriverId")).longValue())
                throw new AccessDeniedException("只能查看本人的换胎申请");
        } else if (!user.permissions().contains("PAGE_APPROVALS_TODO")
                && !user.permissions().contains("PAGE_APPROVALS_HISTORY")) {
            throw new AccessDeniedException("无权查看换胎申请");
        }
        request.remove("applicantDriverId");
        request.put("items", jdbc.queryForList("""
                SELECT r.id,r.confirmed_tire_no tireNo,r.status,r.photo_attachment_id photoAttachmentId,
                       o.recognized_text recognizedText,o.confirmed_text confirmedText
                FROM tire_request r JOIN ocr_record o ON o.id=r.ocr_record_id
                WHERE r.batch_key=? ORDER BY r.id
                """, request.remove("batchKey")));
        return request;
    }

    @Transactional
    public Map<String, Object> edit(long id, TireEdit input, AuthenticatedUser user) {
        users.denyAllDrivers(user);
        if (input == null || input.expectedVersion() == null || blank(input.reason()))
            throw invalid("请填写修改原因并刷新轮胎资料");
        Map<String, Object> before = one("SELECT * FROM tire WHERE id=? AND deleted_at IS NULL FOR UPDATE", id);
        if (before == null) throw new BusinessException(ErrorCode.DATA_001);
        if (((Number) before.get("version")).intValue() != input.expectedVersion())
            throw invalid("轮胎资料已由他人修改，请刷新后重试");
        if (!"AVAILABLE".equals(before.get("status")))
            throw invalid("已申领轮胎不能修改入库身份信息");
        String tireNo = required(input.tireNo(), "胎号", 128);
        String material = required(input.materialDescription(), "物料描述", 1000);
        String barcode = blank(input.barcode()) ? null : input.barcode().trim();
        if (barcode != null && barcode.length() > 128) throw invalid("条码过长");
        if (input.arrivedAt() == null) throw invalid("请选择到货时间");
        Map<String, Object> after = Map.of("tire_no", tireNo, "barcode", barcode == null ? "" : barcode,
                "material_description", material, "arrived_at", input.arrivedAt());
        Map<String, Object> changes = new LinkedHashMap<>();
        for (String field : after.keySet()) {
            String old = "arrived_at".equals(field) && before.get(field) instanceof Timestamp timestamp
                    ? timestamp.toLocalDateTime().toString()
                    : before.get(field) == null ? "" : before.get(field).toString();
            String newer = "arrived_at".equals(field) ? input.arrivedAt().toString() : after.get(field).toString();
            if (!old.equals(newer)) changes.put(field, newer);
        }
        if (changes.isEmpty()) return detail(id);
        try {
            jdbc.update("""
                    UPDATE tire SET tire_no=?,barcode=?,material_description=?,arrived_at=?,
                        version=version+1,updated_by=? WHERE id=? AND version=? AND status='AVAILABLE'
                    """, tireNo, barcode, material, input.arrivedAt(), user.id(), id, input.expectedVersion());
        } catch (DuplicateKeyException ex) { throw invalid("胎号或条码已存在，请核对后重试"); }
        for (var entry : changes.entrySet()) {
            jdbc.update("""
                    INSERT INTO tire_history(tire_id,field_name,before_value,after_value,reason,operator_id)
                    VALUES(?,?,?,?,?,?)
                    """, id, entry.getKey(), String.valueOf(before.get(entry.getKey())),
                    String.valueOf(entry.getValue()), input.reason().trim(), user.id());
        }
        audit.write("TIRE", "TIRE", id, "EDIT", before, changes, user.id(), input.reason().trim());
        return detail(id);
    }

    public byte[] template() {
        try (var workbook = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("轮胎入库");
            var header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.size(); i++) {
                header.createCell(i).setCellValue(HEADERS.get(i));
                sheet.setColumnWidth(i, (i == 1 ? 38 : 23) * 256);
            }
            sheet.createFreezePane(0, 1);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) { throw new IllegalStateException(ex); }
    }

    public Map<String, Object> preview(MultipartFile file, AuthenticatedUser user) {
        users.denyAllDrivers(user);
        List<ImportRow> rows = parseImportFile(file);
        Set<String> seen = new HashSet<>();
        Set<String> seenBarcodes = new HashSet<>();
        List<Map<String, Object>> resultRows = new ArrayList<>();
        for (ImportRow row : rows) {
            String message = validate(row, seen, seenBarcodes);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("rowNo", row.rowNo());
            result.put("arrivedAt", row.arrivedAt());
            result.put("materialDescription", row.materialDescription());
            result.put("barcode", row.barcode());
            result.put("tireNo", row.tireNo());
            result.put("status", message == null ? "VALID" : "INVALID");
            result.put("message", message);
            resultRows.add(result);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", 0L);
        result.put("status", "PREVIEWED");
        result.put("rows", resultRows);
        return result;
    }

    public Map<String, Object> confirm(MultipartFile file, String requestKey, AuthenticatedUser user) {
        users.denyAllDrivers(user);
        if (requestKey == null || requestKey.isBlank() || requestKey.length() > 128)
            throw invalid("导入请求标识不正确");
        List<ImportRow> rows = parseImportFile(file);
        String hash = fileHash(file);
        long id = transactions.execute(tx -> {
            Map<String, Object> existing = one("""
                    SELECT i.id, i.created_by createdBy, a.file_hash fileHash FROM import_task i
                    JOIN file_attachment a ON a.id=i.source_attachment_id
                    WHERE i.business_type='TIRE' AND i.request_key=?
                    """, requestKey.trim());
            if (existing != null) {
                if (((Number) existing.get("createdBy")).longValue() != user.id()
                        || !hash.equals(existing.get("fileHash"))) throw invalid("导入请求已用于其他文件");
                return ((Number) existing.get("id")).longValue();
            }
            long attachmentId = attachments.upload("IMPORT", 0, "IMPORT_FILE", file, user).id();
            long newId = insert("""
                    INSERT INTO import_task(business_type,template_version,source_attachment_id,request_key,status,created_by)
                    VALUES('TIRE','V3-FOUR-FIELDS',?,?,'PREVIEWED',?)
                    """, attachmentId, requestKey.trim(), user.id());
            attachments.bindImportFile(attachmentId, newId, user.id());
            Set<String> seen = new HashSet<>();
            Set<String> seenBarcodes = new HashSet<>();
            for (ImportRow row : rows) {
                String message = validate(row, seen, seenBarcodes);
                jdbc.update("""
                        INSERT INTO import_row(import_task_id,row_no,raw_json,validation_status,result_code,result_message)
                        VALUES(?,?,?,?,?,?)
                        """, newId, row.rowNo(), json(row), message == null ? "VALID" : "INVALID",
                        message == null ? null : "VALIDATION_FAILED", message);
            }
            return newId;
        });
        return confirm(id, user);
    }

    private List<ImportRow> parseImportFile(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > 5L * 1024 * 1024
                || file.getOriginalFilename() == null || !file.getOriginalFilename().toLowerCase().endsWith(".xlsx"))
            throw invalid("请选择不超过 5 MB 的系统轮胎 Excel 文件");
        try { return parse(file.getBytes()); }
        catch (IOException ex) { throw invalid("轮胎 Excel 文件读取失败"); }
    }

    private String fileHash(MultipartFile file) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(file.getBytes())); }
        catch (IOException | NoSuchAlgorithmException ex) { throw invalid("轮胎 Excel 文件读取失败"); }
    }

    public Map<String, Object> confirm(long id, AuthenticatedUser user) {
        users.denyAllDrivers(user);
        Map<String, Object> current = importDetail(id);
        if (((Number) current.get("createdBy")).longValue() != user.id()) throw new AccessDeniedException("只能确认本人上传的文件");
        if (String.valueOf(current.get("status")).startsWith("COMPLETED")) return current;
        if (jdbc.update("UPDATE import_task SET status='CONFIRMING' WHERE id=? AND status='PREVIEWED'", id) != 1)
            throw invalid("导入正在确认，请稍后刷新");
        boolean errors = false;
        Set<String> seen = new HashSet<>();
        Set<String> seenBarcodes = new HashSet<>();
        for (Object value : (List<?>) current.get("rows")) {
            @SuppressWarnings("unchecked") Map<String, Object> item = (Map<String, Object>) value;
            ImportRow row = new ImportRow(((Number) item.get("rowNo")).intValue(),
                    String.valueOf(item.get("arrivedAt")), String.valueOf(item.get("materialDescription")),
                    String.valueOf(item.get("barcode")), String.valueOf(item.get("tireNo")));
            try {
                String message = validate(row, seen, seenBarcodes);
                if (message != null) throw invalid(message);
                transactions.executeWithoutResult(tx -> {
                    long tireId;
                    try {
                        tireId = insert("""
                                INSERT INTO tire(tire_no,barcode,material_description,arrived_at,status,created_by)
                                VALUES(?,?,?,?,'AVAILABLE',?)
                                """, row.tireNo().trim(), blank(row.barcode()) ? null : row.barcode().trim(), row.materialDescription().trim(),
                                LocalDateTime.parse(row.arrivedAt(), ARRIVAL), user.id());
                    } catch (DuplicateKeyException ex) { throw invalid("胎号或条码已存在，请勿重复入库"); }
                    audit.write("TIRE", "TIRE", tireId, "IMPORT", null,
                            Map.of("tireNo", row.tireNo().trim(), "status", "AVAILABLE"), user.id(), null);
                    jdbc.update("""
                            UPDATE import_row SET validation_status='IMPORTED',result_code='IMPORTED',
                                result_message='入库成功',target_id=? WHERE import_task_id=? AND row_no=?
                            """, tireId, id, row.rowNo());
                });
            } catch (RuntimeException ex) {
                errors = true;
                jdbc.update("""
                        UPDATE import_row SET validation_status='FAILED',result_code='VALIDATION_FAILED',
                            result_message=? WHERE import_task_id=? AND row_no=?
                        """, ex.getMessage(), id, row.rowNo());
            }
        }
        jdbc.update("UPDATE import_task SET status=?,confirmed_at=CURRENT_TIMESTAMP(3) WHERE id=?",
                errors ? "COMPLETED_WITH_ERRORS" : "COMPLETED", id);
        return importDetail(id);
    }

    public PageResponse<Map<String, Object>> importHistory(int pageNo, int pageSize) {
        if (pageNo < 1 || pageSize < 1 || pageSize > 100) throw invalid("分页参数不正确");
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM import_task WHERE business_type='TIRE' AND confirmed_at IS NOT NULL", Long.class);
        var rows = jdbc.queryForList("""
                SELECT id,status,created_at createdAt,confirmed_at confirmedAt FROM import_task
                WHERE business_type='TIRE' AND confirmed_at IS NOT NULL ORDER BY id DESC LIMIT ? OFFSET ?
                """, pageSize, (pageNo - 1) * pageSize);
        return new PageResponse<>(pageNo, pageSize, total == null ? 0 : total, rows);
    }

    public Map<String, Object> importDetail(long id) {
        Map<String, Object> task = one("""
                SELECT id,status,created_by createdBy,created_at createdAt,confirmed_at confirmedAt
                FROM import_task WHERE id=? AND business_type='TIRE'
                """, id);
        if (task == null) throw new BusinessException(ErrorCode.DATA_001);
        var rows = jdbc.queryForList("""
                SELECT row_no rowNo,raw_json rawJson,validation_status status,result_message message
                FROM import_row WHERE import_task_id=? ORDER BY row_no
                """, id);
        for (var row : rows) {
            try { row.putAll(mapper.readValue(String.valueOf(row.remove("rawJson")),
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {})); }
            catch (JsonProcessingException ex) { throw new IllegalStateException(ex); }
        }
        task.put("rows", rows);
        return task;
    }

    private Map<String, Object> requestTarget(String tireNo) {
        String number = required(tireNo, "胎号", 128);
        Map<String, Object> target = one("""
                SELECT id,tire_no tireNo,status FROM tire WHERE tire_no=? AND deleted_at IS NULL
                """, number);
        requireRequestable(target);
        return target;
    }

    private void requireRequestable(Map<String, Object> tire) {
        if (tire == null) throw invalid("胎号不存在，请核对后重试");
        if ("CLAIMED".equals(tire.get("status"))) throw invalid("该轮胎已被申领，不能重复申请");
        if (!"AVAILABLE".equals(tire.get("status"))) throw invalid("该轮胎当前状态不可申领");
        String requestStatus = activeRequestStatus(((Number) tire.get("id")).longValue());
        if ("APPROVED".equals(requestStatus)) throw invalid("该轮胎已被申领，不能重复申请");
        if ("PENDING_APPROVAL".equals(requestStatus))
            throw invalid("该轮胎已有待审批的申领申请，不能重复申请");
    }

    public Map<String, Object> requestTarget(String tireNo, AuthenticatedUser user) {
        CurrentDriverContext driver = users.requireDriver(user);
        if (driver.outsourced() || driver.currentVehicleId() == null)
            throw new AccessDeniedException("仅有当前公司车辆的自有司机可以申请换胎");
        return requestTarget(tireNo);
    }

    @Transactional
    public OcrRecordResponse confirmOcr(long id, OcrConfirmRequest input, AuthenticatedUser user) {
        Map<String, Object> record = one("""
                SELECT a.uploaded_by FROM ocr_record o JOIN file_attachment a ON a.id=o.attachment_id
                WHERE o.id=? AND a.purpose='TIRE_OCR'
                """, id);
        if (record == null || ((Number) record.get("uploaded_by")).longValue() != user.id())
            throw new AccessDeniedException("只能确认本人上传的轮胎照片");
        return ocrService.confirm(id, input, user.id());
    }

    @Transactional
    public Map<String, Object> submitRequest(TireRequestInput input, AuthenticatedUser user) {
        if (input == null || input.templateId() == null || blank(input.requestKey())
                || input.items() == null || input.items().isEmpty() || input.items().size() > 20)
            throw invalid("请选择审批流程并确认 1 至 20 张轮胎照片");
        CurrentDriverContext driver = users.requireDriver(user);
        if (driver.outsourced() || driver.currentVehicleId() == null)
            throw new AccessDeniedException("仅有当前公司车辆的自有司机可以申请换胎");
        Map<String, Object> previous = one("""
                SELECT id FROM approval_instance WHERE applicant_user_id=? AND request_key=?
                """, user.id(), input.requestKey());
        if (previous != null) return approvals.detail(((Number) previous.get("id")).longValue(), user);
        Long activeTireTemplate = jdbc.queryForObject("""
                SELECT COUNT(*) FROM approval_template t
                JOIN approval_flow f ON f.template_id=t.id AND f.status='ACTIVE'
                WHERE t.id=? AND t.enabled=1 AND t.business_type='TIRE_REPLACEMENT'
                """, Long.class, input.templateId());
        if (activeTireTemplate == null || activeTireTemplate != 1)
            throw invalid("请选择当前有效的换胎审批流程");
        List<Map<String, Object>> rows = inspectRequestItems(input.items(), user, true);
        for (Map<String, Object> row : rows)
            if (!Boolean.TRUE.equals(row.get("valid"))) throw invalid("第 " + ((Number) row.get("index")).intValue()
                    + " 条胎号 " + row.get("tireNo") + "：" + row.get("message"));
        for (Map<String, Object> row : rows) {
            Long returned = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM tire_request r JOIN approval_instance a ON a.id=r.approval_instance_id
                    WHERE r.tire_id=? AND r.status='RETURNED_TO_APPLICANT'
                      AND a.status='RETURNED_TO_APPLICANT' AND a.applicant_user_id=?
                    """, Long.class, row.get("tireId"), user.id());
            if (returned != null && returned > 0)
                throw invalid("胎号 " + row.get("tireNo") + " 已有被打回申请，请在原申请中修改并重新提交");
        }
        String batchKey = UUID.randomUUID().toString();
        long requestId = 0;
        for (Map<String, Object> row : rows) {
            long id = insert("""
                    INSERT INTO tire_request(batch_key,applicant_driver_id,vehicle_id,confirmed_tire_no,
                        ocr_record_id,photo_attachment_id,tire_id,status) VALUES(?,?,?,?,?,?,?,'DRAFT')
                    """, batchKey, driver.driverId(), driver.currentVehicleId(), row.get("tireNo"),
                    row.get("ocrRecordId"), row.get("attachmentId"), row.get("tireId"));
            if (requestId == 0) requestId = id;
            jdbc.update("UPDATE ocr_record SET confirmed_text=? WHERE id=?", row.get("tireNo"), row.get("ocrRecordId"));
            audit.write("TIRE", "TIRE_REQUEST", id, "CREATE", null,
                    Map.of("tireNo", row.get("tireNo"), "driverId", driver.driverId()), user.id(), null);
        }
        return approvals.create(new ApprovalWorkflowService.Application(input.templateId(), null,
                input.requestKey(), null, null, requestId, null), user);
    }

    public Map<String, Object> validateRequest(TireRequestInput input, AuthenticatedUser user) {
        CurrentDriverContext driver = users.requireDriver(user);
        if (driver.outsourced() || driver.currentVehicleId() == null)
            throw new AccessDeniedException("仅有当前公司车辆的自有司机可以申请换胎");
        if (input == null || input.items() == null || input.items().isEmpty() || input.items().size() > 20)
            throw invalid("请上传 1 至 20 张轮胎照片");
        List<Map<String, Object>> rows = inspectRequestItems(input.items(), user, false);
        return Map.of("rows", rows, "allValid", rows.stream().allMatch(row -> Boolean.TRUE.equals(row.get("valid"))));
    }

    @Transactional
    public Map<String, Object> resubmitRequest(long approvalId, TireResubmission input, AuthenticatedUser user) {
        CurrentDriverContext driver = users.requireDriver(user);
        if (driver.outsourced() || driver.currentVehicleId() == null)
            throw new AccessDeniedException("仅有当前公司车辆的自有司机可以重新提交换胎申请");
        if (input == null || input.expectedVersion() == null || input.items() == null
                || input.items().isEmpty() || input.items().size() > 20)
            throw invalid("请确认 1 至 20 张轮胎照片并刷新审批状态");
        List<Map<String, Object>> rows = inspectRequestItems(input.items(), user, true);
        for (Map<String, Object> row : rows)
            if (!Boolean.TRUE.equals(row.get("valid"))) throw invalid("第 " + row.get("index")
                    + " 条胎号 " + row.get("tireNo") + "：" + row.get("message"));
        for (Map<String, Object> row : rows) {
            Long used = jdbc.queryForObject("SELECT COUNT(*) FROM approval_submission_attachment WHERE attachment_id=?",
                    Long.class, row.get("attachmentId"));
            if (used != null && used > 0)
                throw invalid("第 " + row.get("index") + " 条照片已用于原申请，请重新上传照片后提交");
        }
        return approvals.resubmitTire(approvalId, input.expectedVersion(), driver, rows, user);
    }

    private List<Map<String, Object>> inspectRequestItems(List<TireRequestItem> items,
            AuthenticatedUser user, boolean lock) {
        List<Map<String, Object>> rows = new ArrayList<>();
        Set<String> tireNumbers = new HashSet<>();
        Set<Long> ocrIds = new HashSet<>();
        for (int i = 0; i < items.size(); i++) {
            TireRequestItem item = items.get(i);
            String tireNo = item == null || item.confirmedTireNo() == null ? ""
                    : item.confirmedTireNo().trim().toUpperCase();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("index", i + 1); row.put("tireNo", tireNo); row.put("valid", false);
            rows.add(row);
            if (item == null || item.ocrRecordId() == null || tireNo.isEmpty() || tireNo.length() > 128) {
                row.put("message", "请上传照片并填写完整胎号"); continue;
            }
            if (!ocrIds.add(item.ocrRecordId())) { row.put("message", "同一张照片不能重复使用"); continue; }
            if (!tireNumbers.add(tireNo)) { row.put("message", "列表中胎号重复"); continue; }
            Map<String, Object> ocr = one("""
                    SELECT o.id,o.attachment_id,a.uploaded_by,a.purpose FROM ocr_record o
                    JOIN file_attachment a ON a.id=o.attachment_id WHERE o.id=?
                    """, item.ocrRecordId());
            if (ocr == null || ((Number) ocr.get("uploaded_by")).longValue() != user.id()
                    || !"TIRE_OCR".equals(ocr.get("purpose"))) {
                row.put("message", "照片不存在或不属于当前用户"); continue;
            }
            Map<String, Object> tire = one("SELECT id,status FROM tire WHERE tire_no=? AND deleted_at IS NULL"
                    + (lock ? " FOR UPDATE" : ""), tireNo);
            if (tire == null) { row.put("message", "胎号不存在"); continue; }
            if ("CLAIMED".equals(tire.get("status"))) { row.put("message", "该轮胎已被申领"); continue; }
            if (!"AVAILABLE".equals(tire.get("status"))) { row.put("message", "该轮胎当前状态不可申领"); continue; }
            String status = activeRequestStatus(((Number) tire.get("id")).longValue());
            if ("APPROVED".equals(status)) { row.put("message", "该轮胎已被申领"); continue; }
            if ("PENDING_APPROVAL".equals(status)) { row.put("message", "该轮胎已有待审批申请"); continue; }
            row.put("valid", true); row.put("message", "库存校验通过");
            row.put("ocrRecordId", item.ocrRecordId()); row.put("attachmentId", ocr.get("attachment_id"));
            row.put("tireId", tire.get("id"));
        }
        return rows;
    }

    private String activeRequestStatus(long tireId) {
        // Read the current committed request after locking the tire, even when an
        // earlier lookup established this transaction's REPEATABLE READ snapshot.
        List<String> statuses = jdbc.queryForList("""
                SELECT status FROM tire_request
                WHERE tire_id=? AND status IN ('PENDING_APPROVAL','APPROVED')
                ORDER BY CASE WHEN status='APPROVED' THEN 0 ELSE 1 END
                LIMIT 1 FOR UPDATE
                """, String.class, tireId);
        return statuses.isEmpty() ? null : statuses.getFirst();
    }

    private List<ImportRow> parse(byte[] content) {
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            if (book.getNumberOfSheets() != 1 || !"轮胎入库".equals(book.getSheetName(0)))
                throw invalid("请使用系统下载的轮胎固定模板");
            var sheet = book.getSheetAt(0);
            var header = sheet.getRow(0);
            DataFormatter format = new DataFormatter(java.util.Locale.CHINA);
            if (header == null || header.getLastCellNum() != 4) throw invalid("轮胎模板表头不正确");
            for (int i = 0; i < 4; i++)
                if (!HEADERS.get(i).equals(text(header.getCell(i), format)))
                    throw invalid("轮胎模板表头不正确，请重新下载");
            List<ImportRow> rows = new ArrayList<>();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                for (Cell cell : row) if (cell.getCellType() == CellType.FORMULA || cell.getColumnIndex() > 3)
                    throw invalid("第" + (i + 1) + "行含公式或模板外字段");
                String date = normalizeArrival(row.getCell(0), format, book.isDate1904());
                ImportRow item = new ImportRow(i + 1, date, text(row.getCell(1), format),
                        text(row.getCell(2), format), text(row.getCell(3), format));
                if (List.of(item.arrivedAt(), item.materialDescription(), item.barcode(), item.tireNo())
                        .stream().allMatch(String::isBlank)) continue;
                if (rows.size() >= 1000) throw invalid("一次最多导入 1000 行");
                rows.add(item);
            }
            if (rows.isEmpty()) throw invalid("Excel 中没有轮胎数据");
            return rows;
        } catch (BusinessException ex) { throw ex; }
        catch (IOException | RuntimeException ex) { throw invalid("轮胎文件无法读取，请使用系统固定模板"); }
    }

    private String normalizeArrival(Cell cell, DataFormatter format, boolean date1904) {
        String original = text(cell, format);
        if (cell != null && cell.getCellType() == CellType.NUMERIC) {
            double serial = cell.getNumericCellValue();
            if (Double.isFinite(serial) && serial > 0 && DateUtil.isValidExcelDate(serial)) {
                try {
                    LocalDateTime date = DateUtil.getLocalDateTime(serial, date1904);
                    if (date != null && date.getYear() >= 1000 && date.getYear() <= 9999)
                        return date.truncatedTo(ChronoUnit.MINUTES).format(ARRIVAL);
                } catch (RuntimeException ignored) { /* Preserve the source value for a row-level error. */ }
            }
            return original;
        }
        Matcher match = SEPARATED_DATE.matcher(original);
        int offset = 1;
        if (!match.matches()) { match = CHINESE_DATE.matcher(original); offset = 0; }
        if (!match.matches()) { match = COMPACT_DATE.matcher(original); offset = 0; }
        if (!match.matches()) return original;
        try {
            int year = Integer.parseInt(match.group(1));
            if (year < 1000 || year > 9999) return original;
            LocalDateTime date = LocalDateTime.of(year, Integer.parseInt(match.group(2 + offset)),
                    Integer.parseInt(match.group(3 + offset)),
                    match.group(4 + offset) == null ? 0 : Integer.parseInt(match.group(4 + offset)),
                    match.group(5 + offset) == null ? 0 : Integer.parseInt(match.group(5 + offset)),
                    match.group(6 + offset) == null ? 0 : Integer.parseInt(match.group(6 + offset)));
            return date.truncatedTo(ChronoUnit.MINUTES).format(ARRIVAL);
        } catch (RuntimeException ignored) { return original; }
    }

    private String validate(ImportRow row, Set<String> seen, Set<String> seenBarcodes) {
        List<String> errors = new ArrayList<>();
        try { LocalDateTime.parse(row.arrivedAt(), ARRIVAL); }
        catch (RuntimeException ex) { errors.add("到货时间须填写完整年月日，可使用 Excel 日期或常见日期格式"); }
        if (blank(row.materialDescription()) || row.materialDescription().length() > 1000) errors.add("请填写物料描述");
        if (row.barcode().length() > 128) errors.add("条码不能超过 128 字");
        else if (!blank(row.barcode())) {
            String barcode = row.barcode().trim();
            if (!seenBarcodes.add(barcode)) errors.add("文件内条码重复");
            Long barcodeExists = jdbc.queryForObject("SELECT COUNT(*) FROM tire WHERE barcode=?", Long.class, barcode);
            if (barcodeExists != null && barcodeExists > 0) errors.add("条码已入库");
        }
        if (blank(row.tireNo()) || row.tireNo().length() > 128) errors.add("请填写胎号");
        else {
            String no = row.tireNo().trim();
            if (!seen.add(no)) errors.add("文件内胎号重复");
            Long exists = jdbc.queryForObject("SELECT COUNT(*) FROM tire WHERE tire_no=?", Long.class, no);
            if (exists != null && exists > 0) errors.add("胎号已入库");
        }
        return errors.isEmpty() ? null : String.join("；", errors);
    }

    private String text(Cell cell, DataFormatter format) { return cell == null ? "" : format.formatCellValue(cell).trim(); }
    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw new IllegalStateException(ex); }
    }
    private Map<String, Object> one(String sql, Object... args) {
        return jdbc.queryForList(sql, args).stream().findFirst().orElse(null);
    }
    private long insert(String sql, Object... args) {
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, new String[] {"id"});
            for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
            return statement;
        }, key);
        return key.getKey().longValue();
    }
    private String required(String value, String name, int max) {
        if (blank(value) || value.trim().length() > max) throw invalid("请填写有效的" + name);
        return value.trim();
    }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private BusinessException invalid(String message) { return new BusinessException(ErrorCode.SYS_002.name(), message); }
}
