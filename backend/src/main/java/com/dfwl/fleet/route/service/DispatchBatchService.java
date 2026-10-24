package com.dfwl.fleet.route.service;

import com.dfwl.fleet.common.error.BusinessException;
import com.dfwl.fleet.common.error.ErrorCode;
import com.dfwl.fleet.route.dto.request.DispatchBatchRequest;
import com.dfwl.fleet.route.dto.request.DispatchConfirmRequest;
import com.dfwl.fleet.route.dto.response.DispatchBatchResponse;
import com.dfwl.fleet.route.dto.response.DispatchPreviewResponse;
import com.dfwl.fleet.route.dto.response.RouteTemplateResponse;
import com.dfwl.fleet.route.repository.RouteTemplateRepository;
import com.dfwl.fleet.route.service.TaskDriverValidationService.DriverSnapshot;
import com.dfwl.fleet.system.service.AuditWriteService;
import com.dfwl.fleet.security.TransactionalAuthorizationGuard;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Shared validation and task publication for atomic dispatch and row-based task imports. */
@Service
public class DispatchBatchService {
    private final JdbcTemplate jdbc;
    private final RouteTemplateRepository templates;
    private final TaskDriverValidationService driverValidation;
    private final AuditWriteService audit;
    private final TransactionalAuthorizationGuard authorization;
    private final Map<String, Preview> previews = new ConcurrentHashMap<>();

    public DispatchBatchService(JdbcTemplate jdbc, RouteTemplateRepository templates,
                                TaskDriverValidationService driverValidation, AuditWriteService audit,
                                TransactionalAuthorizationGuard authorization) {
        this.jdbc = jdbc;
        this.templates = templates;
        this.driverValidation = driverValidation;
        this.audit = audit;
        this.authorization = authorization;
    }

    public DispatchPreviewResponse preview(DispatchBatchRequest batch, long actor) {
        if (batch == null || batch.businessDate() == null || batch.rows() == null || batch.rows().isEmpty())
            throw new BusinessException(ErrorCode.SYS_002);
        previews.entrySet().removeIf(entry -> entry.getValue().createdAt().isBefore(Instant.now().minusSeconds(1800)));
        List<ValidatedRow> validated = validate(batch);
        String previewId = UUID.randomUUID().toString();
        previews.put(previewId, new Preview(actor, batch, List.copyOf(validated), Instant.now()));
        return new DispatchPreviewResponse(previewId, validated.stream().allMatch(row -> row.errors().isEmpty()),
                validated.stream().map(row -> new DispatchPreviewResponse.Row(row.rowNo(),
                        row.template() == null ? null : row.template().id(),
                        row.template() == null ? null : row.template().name(),
                        row.input() == null ? null : row.input().driverId(),
                        row.driver() == null ? null : row.driver().driverType(),
                        row.driver() == null ? null :
                                "OUTSOURCED".equals(row.driver().driverType()) ? row.driver().outsourcedPlate() : row.driver().vehiclePlate(),
                        row.unitPrice(), row.informationFee(), row.salary(), row.errors())).toList());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public DispatchBatchResponse confirm(DispatchConfirmRequest request, long actor) {
        authorization.require(actor, "BUTTON_DISPATCH_PUBLISH", "BUTTON_TASK_IMPORTS_SUBMIT");
        return confirmAuthorized(request, actor);
    }

    public DispatchPreviewResponse previewSingle(DispatchBatchRequest batch, long actor) {
        requireSingle(batch);
        return preview(batch, actor);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public DispatchBatchResponse confirmSingle(DispatchConfirmRequest request, long actor) {
        authorization.require(actor, "BUTTON_MINI_DISPATCH_PUBLISH");
        requireSingle(request == null ? null : request.batch());
        return confirmAuthorized(request, actor);
    }

    private void requireSingle(DispatchBatchRequest batch) {
        if (batch == null || batch.rows() == null || batch.rows().size() != 1)
            throw new BusinessException(ErrorCode.SYS_002);
    }

    private DispatchBatchResponse confirmAuthorized(DispatchConfirmRequest request, long actor) {
        if (request == null || request.batch() == null || request.requestKey() == null
                || request.requestKey().isBlank() || request.requestKey().length() > 128)
            throw new BusinessException(ErrorCode.SYS_002);
        String digest = digest(request.batch());
        String requestKey = request.requestKey().trim();
        List<StoredBatch> existing = storedBatch(requestKey);
        if (!existing.isEmpty()) {
            if (!existing.getFirst().digest().equals(digest) || existing.getFirst().actor() != actor)
                throw new BusinessException(ErrorCode.DATA_002);
            return find(existing.getFirst().id());
        }
        Preview preview = previews.get(request.previewId());
        if (preview == null || preview.actor() != actor || !preview.batch().equals(request.batch()))
            throw new BusinessException(ErrorCode.SYS_002);
        lockPublicationReferences(request.batch());
        List<ValidatedRow> rows = validate(request.batch());
        if (rows.stream().anyMatch(row -> !row.errors().isEmpty()))
            throw new BusinessException("DISPATCH_VALIDATION_FAILED", rows.stream()
                    .filter(row -> !row.errors().isEmpty())
                    .map(row -> "第" + row.rowNo() + "行：" + String.join("；", row.errors()))
                    .reduce((left, right) -> left + "；" + right).orElse("调度行校验失败"));
        if (!rows.equals(preview.validated()))
            throw new BusinessException("DISPATCH_PREVIEW_STALE", "模板、司机绑定或车辆状态已变化，请重新预览整批任务");
        long batchId;
        try { batchId = insertBatch(request, digest, actor); }
        catch (DataIntegrityViolationException duplicate) {
            List<StoredBatch> raced = jdbc.query(
                    "SELECT id, request_digest, created_by FROM dispatch_batch WHERE request_key = ? FOR UPDATE",
                    (rs, row) -> new StoredBatch(rs.getLong("id"), rs.getString("request_digest"), rs.getLong("created_by")), requestKey);
            if (raced.isEmpty() || !raced.getFirst().digest().equals(digest) || raced.getFirst().actor() != actor)
                throw new BusinessException(ErrorCode.DATA_002);
            return find(raced.getFirst().id());
        }
        for (ValidatedRow row : rows) {
            long taskId = insertTask(row, request.batch(), actor, null);
            long participantId = insertParticipant(taskId, row, actor);
            jdbc.update("UPDATE transport_task SET current_participant_id = ? WHERE id = ? AND current_participant_id IS NULL",
                    participantId, taskId);
            jdbc.update("""
                    INSERT INTO task_status_history (task_id, from_status, to_status, operator_id, reason)
                    VALUES (?, NULL, 'PUBLISHED', ?, '调度批次确认')
                    """, taskId, actor);
            jdbc.update("""
                    INSERT INTO dispatch_batch_row (batch_id, row_no, template_id, driver_id, outsourced_plate_no,
                        actual_unit_price, actual_information_fee, initial_salary, remark, result_code, task_id)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'PUBLISHED', ?)
                    """, batchId, row.rowNo(), row.template().id(), row.input().driverId(),
                    row.driver().outsourcedPlate(), row.unitPrice(), row.informationFee(), row.salary(),
                    row.input().remark(), taskId);
        }
        audit.write("DISPATCH", "DISPATCH_BATCH", batchId, "CONFIRM", null,
                Map.of("requestKey", request.requestKey(), "taskCount", rows.size()), actor, null);
        return find(batchId);
    }

    public DispatchBatchResponse find(long id) {
        List<String[]> header = jdbc.query("SELECT request_key, status FROM dispatch_batch WHERE id = ?",
                (rs, row) -> new String[]{rs.getString("request_key"), rs.getString("status")}, id);
        if (header.isEmpty()) throw new BusinessException(ErrorCode.DATA_001);
        List<DispatchBatchResponse.Row> rows = jdbc.query("""
                SELECT r.row_no, r.task_id, t.task_no FROM dispatch_batch_row r
                JOIN transport_task t ON t.id = r.task_id WHERE r.batch_id = ? ORDER BY r.row_no
                """, (rs, row) -> new DispatchBatchResponse.Row(rs.getInt("row_no"),
                rs.getLong("task_id"), rs.getString("task_no")), id);
        return new DispatchBatchResponse(id, header.getFirst()[0], header.getFirst()[1], rows);
    }

    private List<StoredBatch> storedBatch(String requestKey) {
        return jdbc.query("SELECT id, request_digest, created_by FROM dispatch_batch WHERE request_key = ?",
                (rs, row) -> new StoredBatch(rs.getLong("id"), rs.getString("request_digest"), rs.getLong("created_by")), requestKey);
    }

    /** Import preview and confirm use exactly the same driver, vehicle, template and amount rules. */
    public DispatchPreviewResponse.Row validateImportRow(java.time.LocalDate date, DispatchBatchRequest.Row input) {
        ValidatedRow row = validate(new DispatchBatchRequest(date, List.of(input))).getFirst();
        return new DispatchPreviewResponse.Row(row.rowNo(), row.template() == null ? null : row.template().id(),
                row.template() == null ? null : row.template().name(), input.driverId(),
                row.driver() == null ? null : row.driver().driverType(),
                row.driver() == null ? null : "OUTSOURCED".equals(row.driver().driverType())
                        ? row.driver().outsourcedPlate() : row.driver().vehiclePlate(),
                row.unitPrice(), row.informationFee(), row.salary(), row.errors());
    }

    /** Caller supplies a per-row transaction; the unique business key prevents repeat imports. */
    public long publishImportedRow(java.time.LocalDate date, DispatchBatchRequest.Row input,
                                   long actor, String businessKey) {
        authorization.require(actor, "BUTTON_TASK_IMPORTS_SUBMIT");
        DispatchBatchRequest batch = new DispatchBatchRequest(date, List.of(input));
        lockPublicationReferences(batch);
        ValidatedRow row = validate(batch).getFirst();
        if (!row.errors().isEmpty())
            throw new BusinessException("IMPORT_ROW_INVALID", String.join("；", row.errors()));
        long taskId = insertTask(row, batch, actor, businessKey);
        long participantId = insertParticipant(taskId, row, actor);
        jdbc.update("UPDATE transport_task SET current_participant_id = ? WHERE id = ?", participantId, taskId);
        jdbc.update("INSERT INTO task_status_history (task_id, from_status, to_status, operator_id, reason) "
                + "VALUES (?, NULL, 'PUBLISHED', ?, '固定任务导入')", taskId, actor);
        audit.write("DISPATCH", "TRANSPORT_TASK", taskId, "IMPORT_PUBLISH", null,
                Map.of("businessKey", businessKey), actor, null);
        return taskId;
    }

    private void lockPublicationReferences(DispatchBatchRequest batch) {
        batch.rows().stream().filter(java.util.Objects::nonNull)
                .map(DispatchBatchRequest.Row::templateStableCode).filter(java.util.Objects::nonNull)
                .distinct().sorted().forEach(code -> {
                    List<Long> products = jdbc.queryForList(
                            "SELECT product_id FROM route_template WHERE stable_code = ? FOR UPDATE",
                            Long.class, code.trim());
                    for (Long productId : products)
                        jdbc.queryForList("SELECT id FROM product WHERE id = ? FOR UPDATE", Long.class, productId);
                });
        batch.rows().stream().filter(java.util.Objects::nonNull)
                .map(DispatchBatchRequest.Row::driverId).filter(java.util.Objects::nonNull)
                .distinct().sorted().forEach(id -> {
                    jdbc.queryForList("SELECT id FROM driver WHERE id = ? FOR UPDATE", Long.class, id);
                    List<Long> vehicles = jdbc.queryForList(
                            "SELECT vehicle_id FROM driver_vehicle_current WHERE driver_id = ? FOR UPDATE",
                            Long.class, id);
                    for (Long vehicleId : vehicles) {
                        jdbc.queryForList("SELECT id FROM vehicle WHERE id = ? FOR UPDATE", Long.class, vehicleId);
                        List<Long> trailers = jdbc.queryForList(
                                "SELECT trailer_id FROM vehicle_trailer_current WHERE vehicle_id = ? FOR UPDATE",
                                Long.class, vehicleId);
                        for (Long trailerId : trailers)
                            jdbc.queryForList("SELECT id FROM trailer WHERE id = ? FOR UPDATE", Long.class, trailerId);
                    }
                });
    }

    private List<ValidatedRow> validate(DispatchBatchRequest batch) {
        List<ValidatedRow> result = new ArrayList<>();
        for (int i = 0; i < batch.rows().size(); i++) {
            DispatchBatchRequest.Row input = batch.rows().get(i);
            List<String> errors = new ArrayList<>();
            RouteTemplateResponse template = null;
            DriverSnapshot driver = null;
            if (input == null || input.templateStableCode() == null || input.templateStableCode().isBlank()) {
                errors.add("请选择线路模板");
            } else {
                template = templates.findEnabledByCode(input.templateStableCode().trim()).orElse(null);
                if (template == null || !templates.productEnabled(template.productId()))
                    errors.add("线路模板不存在、已停用或货物不可用");
            }
            if (input == null || input.driverId() == null) errors.add("请选择司机");
            else {
                try { driver = driverValidation.require(input.driverId(), input.outsourcedPlateNo()); }
                catch (BusinessException invalid) {
                    errors.add("OUTSOURCED_PLATE_MISSING".equals(invalid.code())
                            ? "车辆信息未补充" : "司机、车辆或车挂不满足发布条件");
                }
            }
            BigDecimal unit = input == null ? null : input.actualUnitPrice();
            BigDecimal info = input == null ? null : input.actualInformationFee();
            BigDecimal salary = input == null ? null : input.initialSalary();
            if (template != null) {
                if (unit == null) unit = template.defaultUnitPrice();
                if (info == null) info = template.defaultInformationFee();
                if (salary == null) salary = template.defaultDriverSalary();
            }
            if (!validAmount(unit) || !validAmount(info) || !validAmount(salary)) errors.add("金额必须为非负且最多两位小数");
            result.add(new ValidatedRow(i + 1, input, template, driver, unit, info, salary, List.copyOf(errors)));
        }
        return result;
    }

    private boolean validAmount(BigDecimal amount) {
        return amount != null && amount.signum() >= 0 && amount.scale() <= 2;
    }

    private long insertBatch(DispatchConfirmRequest request, String digest, long actor) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO dispatch_batch (business_date, request_key, request_digest, status, created_by, confirmed_at)
                    VALUES (?, ?, ?, 'CONFIRMED', ?, CURRENT_TIMESTAMP(3))
                    """, new String[]{"id"});
            ps.setObject(1, request.batch().businessDate());
            ps.setString(2, request.requestKey().trim());
            ps.setString(3, digest);
            ps.setLong(4, actor);
            return ps;
        }, key);
        return key.getKey().longValue();
    }

    private long insertTask(ValidatedRow row, DispatchBatchRequest batch, long actor, String importBusinessKey) {
        String taskNo = "T" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        String productName = jdbc.queryForObject("SELECT name FROM product WHERE id = ?", String.class,
                row.template().productId());
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO transport_task (task_no, template_id, template_code_snapshot, template_name_snapshot,
                        customer_name_snapshot, product_name_snapshot, loading_place_snapshot, unloading_place_snapshot,
                        actual_unit_price, actual_information_fee, business_date, status, created_by, updated_by,
                        import_business_key)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PUBLISHED', ?, ?, ?)
                    """, new String[]{"id"});
            ps.setString(1, taskNo);
            ps.setLong(2, row.template().id());
            ps.setString(3, row.template().stableCode());
            ps.setString(4, row.template().name());
            ps.setString(5, row.template().customerName());
            ps.setString(6, productName);
            ps.setString(7, row.template().loadingPlace());
            ps.setString(8, row.template().unloadingPlace());
            ps.setBigDecimal(9, row.unitPrice());
            ps.setBigDecimal(10, row.informationFee());
            ps.setObject(11, batch.businessDate());
            ps.setLong(12, actor);
            ps.setLong(13, actor);
            ps.setString(14, importBusinessKey);
            return ps;
        }, key);
        return key.getKey().longValue();
    }

    private long insertParticipant(long taskId, ValidatedRow row, long actor) {
        DriverSnapshot d = row.driver();
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO transport_participant (task_id, sequence_no, driver_id, driver_type_snapshot,
                        company_vehicle_id, vehicle_plate_snapshot, trailer_id, trailer_plate_snapshot,
                        outsourced_plate_no, source_type, status, is_current, assigned_at, salary_amount, created_by)
                    VALUES (?, 1, ?, ?, ?, ?, ?, ?, ?, 'INITIAL', 'ASSIGNED', 1, CURRENT_TIMESTAMP(3), ?, ?)
                    """, new String[]{"id"});
            ps.setLong(1, taskId);
            ps.setLong(2, row.input().driverId());
            ps.setString(3, d.driverType());
            ps.setObject(4, d.vehicleId());
            ps.setString(5, d.vehiclePlate());
            ps.setObject(6, d.trailerId());
            ps.setString(7, d.trailerPlate());
            ps.setString(8, d.outsourcedPlate());
            ps.setBigDecimal(9, row.salary());
            ps.setLong(10, actor);
            return ps;
        }, key);
        return key.getKey().longValue();
    }

    private String digest(DispatchBatchRequest batch) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(batch.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private record Preview(long actor, DispatchBatchRequest batch, List<ValidatedRow> validated, Instant createdAt) {}
    private record StoredBatch(long id, String digest, long actor) {}
    private record ValidatedRow(int rowNo, DispatchBatchRequest.Row input, RouteTemplateResponse template,
                                DriverSnapshot driver, BigDecimal unitPrice, BigDecimal informationFee,
                                BigDecimal salary, List<String> errors) {}
}
