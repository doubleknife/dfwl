package com.dfwl.fleet.route.service;

import com.dfwl.fleet.attachment.service.AttachmentService;
import com.dfwl.fleet.common.api.PageResponse;
import com.dfwl.fleet.common.error.BusinessException;
import com.dfwl.fleet.common.error.ErrorCode;
import com.dfwl.fleet.route.dto.request.DispatchBatchRequest;
import com.dfwl.fleet.route.dto.response.DispatchPreviewResponse;
import com.dfwl.fleet.security.AuthenticatedUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/** Fixed V3 task workbook. Every row delegates publication to DispatchBatchService. */
@Service
public class TaskImportService {
    public static final String VERSION = "TASK_V3_1";
    private static final List<String> HEADERS = List.of("业务日期", "线路模板编号", "司机手机号", "外协车牌",
            "实际含税单价", "实际信息费", "实际司机工资", "备注");
    private final JdbcTemplate jdbc;
    private final DispatchBatchService dispatch;
    private final AttachmentService attachments;
    private final ObjectMapper mapper;
    private final TransactionTemplate rowTransaction;
    private final TransactionTemplate setupTransaction;

    public TaskImportService(JdbcTemplate jdbc, DispatchBatchService dispatch, AttachmentService attachments,
                             ObjectMapper mapper, PlatformTransactionManager transactions) {
        this.jdbc = jdbc;
        this.dispatch = dispatch;
        this.attachments = attachments;
        this.mapper = mapper;
        this.rowTransaction = new TransactionTemplate(transactions);
        this.rowTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.setupTransaction = new TransactionTemplate(transactions);
    }

    public byte[] template() {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet(VERSION);
            var header = sheet.createRow(0);
            var style = workbook.createCellStyle();
            var font = workbook.createFont();
            font.setBold(true);
            style.setFont(font);
            for (int i = 0; i < HEADERS.size(); i++) {
                var cell = header.createCell(i);
                cell.setCellValue(HEADERS.get(i));
                cell.setCellStyle(style);
                sheet.setColumnWidth(i, (i == 7 ? 32 : 20) * 256);
            }
            var phoneStyle = workbook.createCellStyle();
            phoneStyle.setDataFormat(workbook.createDataFormat().getFormat("@"));
            sheet.setDefaultColumnStyle(2, phoneStyle);
            sheet.createFreezePane(0, 1);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) { throw new IllegalStateException(ex); }
    }

    public ImportDetail preview(MultipartFile file, String requestKey, AuthenticatedUser user) {
        List<RawRow> parsed = parse(readImportFile(file, requestKey));
        Set<String> seen = new HashSet<>();
        List<ImportRowView> rows = new ArrayList<>();
        for (RawRow raw : parsed) {
            Evaluated evaluated = evaluate(raw, seen);
            String message = String.join("；", evaluated.errors());
            rows.add(new ImportRowView(raw.rowNo(), raw.businessDate(), raw.templateStableCode(),
                    raw.driverPhone(), raw.outsourcedPlateNo(), raw.actualUnitPrice(), raw.actualInformationFee(),
                    raw.initialSalary(), raw.remark(), evaluated.errors().isEmpty() ? "VALID" : "INVALID",
                    message.isEmpty() ? null : message, null, serialize(raw)));
        }
        return new ImportDetail(0, VERSION, "PREVIEWED", user.id(), java.time.LocalDateTime.now(), null, rows);
    }

    public ImportDetail confirm(MultipartFile file, String requestKey, AuthenticatedUser user) {
        byte[] bytes = readImportFile(file, requestKey);
        List<RawRow> parsed = parse(bytes);
        String hash = digest(bytes);
        long id = setupTransaction.execute(status -> {
            List<Long> existing = jdbc.queryForList("""
                    SELECT i.id FROM import_task i JOIN file_attachment a ON a.id = i.source_attachment_id
                    WHERE i.business_type = 'TASK' AND i.request_key = ? AND i.created_by = ? AND a.file_hash = ?
                    """, Long.class, requestKey.trim(), user.id(), hash);
            if (!existing.isEmpty()) return existing.getFirst();
            Integer keyInUse = jdbc.queryForObject("SELECT COUNT(*) FROM import_task WHERE business_type = 'TASK' AND request_key = ?",
                    Integer.class, requestKey.trim());
            if (keyInUse != null && keyInUse > 0) throw new BusinessException(ErrorCode.DATA_002);
            long attachmentId = attachments.upload("IMPORT", 0, "IMPORT_FILE", file, user).id();
            KeyHolder keys = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                PreparedStatement ps = connection.prepareStatement("""
                        INSERT INTO import_task (business_type, template_version, source_attachment_id,
                            request_key, status, created_by) VALUES ('TASK', ?, ?, ?, 'PREVIEWED', ?)
                        """, new String[]{"id"});
                ps.setString(1, VERSION);
                ps.setLong(2, attachmentId);
                ps.setString(3, requestKey.trim());
                ps.setLong(4, user.id());
                return ps;
            }, keys);
            long newId = keys.getKey().longValue();
            attachments.bindImportFile(attachmentId, newId, user.id());
            Set<String> seen = new HashSet<>();
            for (RawRow raw : parsed) {
                Evaluated evaluated = evaluate(raw, seen);
                String message = String.join("；", evaluated.errors());
                jdbc.update("""
                        INSERT INTO import_row (import_task_id, row_no, raw_json, validation_status,
                            result_code, result_message) VALUES (?, ?, ?, ?, ?, ?)
                        """, newId, raw.rowNo(), serialize(raw), evaluated.errors().isEmpty() ? "VALID" : "INVALID",
                        evaluated.errors().isEmpty() ? null : "VALIDATION_FAILED", message.isEmpty() ? null : message);
            }
            return newId;
        });
        return confirm(id, user);
    }

    private byte[] readImportFile(MultipartFile file, String requestKey) {
        if (requestKey == null || requestKey.isBlank() || requestKey.length() > 128
                || file == null || file.isEmpty() || file.getSize() > 5L * 1024 * 1024
                || file.getOriginalFilename() == null
                || !file.getOriginalFilename().toLowerCase(java.util.Locale.ROOT).endsWith(".xlsx"))
            throw new BusinessException("TASK_IMPORT_FILE_INVALID", "请选择不超过 5 MB 的系统任务 Excel 文件");
        try { return file.getBytes(); }
        catch (IOException ex) { throw new BusinessException("TASK_IMPORT_FILE_INVALID", "任务文件读取失败"); }
    }

    public ImportDetail confirm(long id, AuthenticatedUser user) {
        ImportDetail current = detail(id);
        if (current.createdBy() != user.id()) throw new BusinessException(ErrorCode.AUTH_003);
        if (Set.of("COMPLETED", "COMPLETED_WITH_ERRORS").contains(current.status())) return current;
        int claimed = jdbc.update("""
                UPDATE import_task SET status = 'CONFIRMING' WHERE id = ? AND created_by = ? AND status = 'PREVIEWED'
                """, id, user.id());
        if (claimed != 1) throw new BusinessException("TASK_IMPORT_BUSY", "该导入正在确认，请稍后刷新结果");
        boolean failed = false;
        Set<String> seen = new HashSet<>();
        for (ImportRowView item : current.rows()) {
            RawRow raw = deserialize(item.rawJson());
            try {
                rowTransaction.executeWithoutResult(status -> {
                    Evaluated checked = evaluate(raw, seen);
                    if (!checked.errors().isEmpty())
                        throw new BusinessException("TASK_IMPORT_ROW_INVALID", String.join("；", checked.errors()));
                    long taskId = dispatch.publishImportedRow(checked.date(), checked.input(), user.id(), checked.businessKey());
                    jdbc.update("""
                            UPDATE import_row SET validation_status = 'PUBLISHED', result_code = 'PUBLISHED',
                                result_message = '发布成功', target_id = ? WHERE import_task_id = ? AND row_no = ?
                            """, taskId, id, raw.rowNo());
                });
            } catch (BusinessException | DataIntegrityViolationException ex) {
                failed = true;
                String message = ex instanceof DataIntegrityViolationException ? "重复业务行或业务数据已变化" : ex.getMessage();
                jdbc.update("""
                        UPDATE import_row SET validation_status = 'FAILED', result_code = 'VALIDATION_FAILED',
                            result_message = ? WHERE import_task_id = ? AND row_no = ?
                        """, message, id, raw.rowNo());
            }
        }
        jdbc.update("UPDATE import_task SET status = ?, confirmed_at = CURRENT_TIMESTAMP(3) WHERE id = ?",
                failed ? "COMPLETED_WITH_ERRORS" : "COMPLETED", id);
        return detail(id);
    }

    public PageResponse<ImportSummary> history(int pageNo, int pageSize) {
        if (pageNo < 1 || pageSize < 1 || pageSize > 100) throw new BusinessException(ErrorCode.SYS_002);
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM import_task WHERE business_type = 'TASK' AND confirmed_at IS NOT NULL", Long.class);
        List<ImportSummary> records = jdbc.query("""
                SELECT id, template_version, status, created_by, created_at, confirmed_at
                FROM import_task WHERE business_type = 'TASK' AND confirmed_at IS NOT NULL ORDER BY id DESC LIMIT ? OFFSET ?
                """, (rs, row) -> new ImportSummary(rs.getLong("id"), rs.getString("template_version"),
                rs.getString("status"), rs.getLong("created_by"), rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getTimestamp("confirmed_at") == null ? null : rs.getTimestamp("confirmed_at").toLocalDateTime()),
                pageSize, (pageNo - 1) * pageSize);
        return new PageResponse<>(pageNo, pageSize, count == null ? 0 : count, records);
    }

    public ImportDetail detail(long id) {
        List<ImportDetail> headers = jdbc.query("""
                SELECT id, template_version, status, created_by, created_at, confirmed_at
                FROM import_task WHERE id = ? AND business_type = 'TASK'
                """, (rs, row) -> new ImportDetail(rs.getLong("id"), rs.getString("template_version"),
                rs.getString("status"), rs.getLong("created_by"), rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getTimestamp("confirmed_at") == null ? null : rs.getTimestamp("confirmed_at").toLocalDateTime(),
                List.of()), id);
        if (headers.isEmpty()) throw new BusinessException(ErrorCode.DATA_001);
        List<ImportRowView> rows = jdbc.query("""
                SELECT r.row_no, r.raw_json, r.validation_status, r.result_message, t.task_no
                FROM import_row r LEFT JOIN transport_task t ON t.id = r.target_id
                WHERE r.import_task_id = ? ORDER BY r.row_no
                """, (rs, row) -> {
            RawRow raw = deserialize(rs.getString("raw_json"));
            return new ImportRowView(rs.getInt("row_no"), raw.businessDate(), raw.templateStableCode(),
                    raw.driverPhone(), raw.outsourcedPlateNo(), raw.actualUnitPrice(), raw.actualInformationFee(),
                    raw.initialSalary(), raw.remark(), rs.getString("validation_status"),
                    rs.getString("result_message"), rs.getString("task_no"), rs.getString("raw_json"));
        }, id);
        ImportDetail header = headers.getFirst();
        return new ImportDetail(header.id(), header.templateVersion(), header.status(), header.createdBy(),
                header.createdAt(), header.confirmedAt(), rows);
    }

    private List<RawRow> parse(byte[] bytes) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            if (workbook.getNumberOfSheets() != 1 || !VERSION.equals(workbook.getSheetAt(0).getSheetName()))
                throw new BusinessException("TASK_IMPORT_TEMPLATE_INVALID", "请使用系统下载的固定任务模板");
            var sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(0);
            DataFormatter format = new DataFormatter(java.util.Locale.CHINA);
            if (header == null || header.getLastCellNum() != HEADERS.size())
                throw new BusinessException("TASK_IMPORT_TEMPLATE_INVALID", "任务模板表头不符，请重新下载模板");
            for (int i = 0; i < HEADERS.size(); i++)
                if (!HEADERS.get(i).equals(text(header.getCell(i), format)))
                    throw new BusinessException("TASK_IMPORT_TEMPLATE_INVALID", "任务模板表头不符，请重新下载模板");
            List<RawRow> rows = new ArrayList<>();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || blank(row, format)) continue;
                if (rows.size() >= 1000) throw new BusinessException("TASK_IMPORT_TOO_LARGE", "单次最多导入 1000 行");
                for (int col = HEADERS.size(); col < row.getLastCellNum(); col++)
                    if (!text(row.getCell(col), format).isBlank())
                        throw new BusinessException("TASK_IMPORT_TEMPLATE_INVALID", "第" + (i + 1) + "行含模板以外的列");
                for (int col = 0; col < HEADERS.size(); col++)
                    if (row.getCell(col) != null && row.getCell(col).getCellType() == CellType.FORMULA)
                        throw new BusinessException("TASK_IMPORT_FORMULA", "第" + (i + 1) + "行含公式，请粘贴为值后重试");
                String date = text(row.getCell(0), format);
                Cell dateCell = row.getCell(0);
                if (dateCell != null && dateCell.getCellType() == CellType.NUMERIC
                        && DateUtil.isCellDateFormatted(dateCell))
                    date = dateCell.getLocalDateTimeCellValue().toLocalDate().toString();
                rows.add(new RawRow(i + 1, date, text(row.getCell(1), format), text(row.getCell(2), format),
                        text(row.getCell(3), format), text(row.getCell(4), format), text(row.getCell(5), format),
                        text(row.getCell(6), format), text(row.getCell(7), format)));
            }
            if (rows.isEmpty()) throw new BusinessException("TASK_IMPORT_EMPTY", "文件中没有可导入的任务行");
            return rows;
        } catch (BusinessException ex) { throw ex; }
        catch (IOException | RuntimeException ex) {
            throw new BusinessException("TASK_IMPORT_FILE_INVALID", "文件无法按系统任务 Excel 模板读取");
        }
    }

    private boolean blank(Row row, DataFormatter formatter) {
        for (int i = 0; i < HEADERS.size(); i++) if (!text(row.getCell(i), formatter).isBlank()) return false;
        return true;
    }

    private String text(Cell cell, DataFormatter formatter) {
        return cell == null ? "" : formatter.formatCellValue(cell).trim();
    }

    private Evaluated evaluate(RawRow raw, Set<String> seen) {
        List<String> errors = new ArrayList<>();
        LocalDate date = null;
        try {
            String value = raw.businessDate();
            date = value.matches("[0-9]{8}")
                    ? LocalDate.parse(value, DateTimeFormatter.BASIC_ISO_DATE)
                    : LocalDate.parse(value);
        } catch (RuntimeException ex) { errors.add("业务日期须为 yyyy-MM-dd 或 yyyyMMdd"); }
        Long driverId = null;
        if (raw.driverPhone() == null || !raw.driverPhone().matches("1[0-9]{10}")) errors.add("司机手机号格式不正确");
        else {
            List<Long> drivers = jdbc.queryForList("""
                    SELECT id FROM driver WHERE phone = ? AND status = 1 AND deleted_at IS NULL
                    """, Long.class, raw.driverPhone());
            if (drivers.size() != 1) errors.add("找不到有效司机，请先建立司机档案");
            else driverId = drivers.getFirst();
        }
        BigDecimal unit = amount(raw.actualUnitPrice(), "实际含税单价", errors);
        BigDecimal info = amount(raw.actualInformationFee(), "实际信息费", errors);
        BigDecimal salary = amount(raw.initialSalary(), "实际司机工资", errors);
        DispatchBatchRequest.Row input = new DispatchBatchRequest.Row(raw.templateStableCode(), driverId,
                raw.outsourcedPlateNo(), unit, info, salary, raw.remark());
        String key = null;
        if (date != null) {
            DispatchPreviewResponse.Row shared = dispatch.validateImportRow(date, input);
            errors.addAll(shared.errors());
            if (errors.isEmpty()) {
                key = digest(String.join("|", date.toString(), raw.templateStableCode(), raw.driverPhone(),
                        raw.outsourcedPlateNo() == null ? "" : raw.outsourcedPlateNo(),
                        money(shared.actualUnitPrice()), money(shared.actualInformationFee()), money(shared.initialSalary()))
                        .getBytes(StandardCharsets.UTF_8));
                if (!seen.add(key)) errors.add("文件中存在重复业务行");
                Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM transport_task WHERE import_business_key = ?",
                        Integer.class, key);
                if (exists != null && exists > 0) errors.add("该业务行已经导入，请勿重复提交");
            }
        }
        return new Evaluated(date, input, key, List.copyOf(errors));
    }

    private BigDecimal amount(String text, String label, List<String> errors) {
        if (text == null || text.isBlank()) return null;
        try { return new BigDecimal(text); }
        catch (NumberFormatException ex) { errors.add(label + "不是有效金额"); return null; }
    }

    private String money(BigDecimal value) { return value.setScale(2).toPlainString(); }

    private String serialize(RawRow raw) {
        try { return mapper.writeValueAsString(raw); }
        catch (JsonProcessingException ex) { throw new IllegalStateException(ex); }
    }

    private RawRow deserialize(String json) {
        try { return mapper.readValue(json, RawRow.class); }
        catch (JsonProcessingException ex) { throw new IllegalStateException(ex); }
    }

    private String digest(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }

    private record RawRow(int rowNo, String businessDate, String templateStableCode, String driverPhone,
                          String outsourcedPlateNo, String actualUnitPrice, String actualInformationFee,
                          String initialSalary, String remark) {}
    private record Evaluated(LocalDate date, DispatchBatchRequest.Row input, String businessKey, List<String> errors) {}
    public record ImportSummary(long id, String templateVersion, String status, long createdBy,
                                java.time.LocalDateTime createdAt, java.time.LocalDateTime confirmedAt) {}
    public record ImportRowView(int rowNo, String businessDate, String templateStableCode, String driverPhone,
                                String outsourcedPlateNo, String actualUnitPrice, String actualInformationFee,
                                String initialSalary, String remark, String status, String message, String taskNo,
                                @com.fasterxml.jackson.annotation.JsonIgnore String rawJson) {}
    public record ImportDetail(long id, String templateVersion, String status, long createdBy,
                               java.time.LocalDateTime createdAt, java.time.LocalDateTime confirmedAt,
                               List<ImportRowView> rows) {}
}
