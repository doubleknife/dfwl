package com.dfwl.fleet.imports.repository;

import com.dfwl.fleet.common.api.PageResponse;
import com.dfwl.fleet.imports.api.ImportRowResponse;
import com.dfwl.fleet.imports.api.ImportTaskResponse;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class ImportRepository {

    private final JdbcTemplate jdbcTemplate;

    public ImportRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long createTask(String batchNo, String businessType, Long templateId, Long originalFileId,
                           int totalCount, int successCount, int failureCount, long operatorId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO import_task (batch_no, business_type, template_id, original_file_id, total_count,
                                             success_count, unpublished_count, failure_count, status, uploaded_by)
                    VALUES (?, ?, ?, ?, ?, ?, 0, ?, 'PREVIEWED', ?)
                    """, new String[]{"id"});
            ps.setString(1, batchNo);
            ps.setString(2, businessType);
            ps.setObject(3, templateId);
            ps.setLong(4, originalFileId);
            ps.setInt(5, totalCount);
            ps.setInt(6, successCount);
            ps.setInt(7, failureCount);
            ps.setLong(8, operatorId);
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    public void insertRow(long taskId, int rowNo, String rawJson, String normalizedJson, String previewStatus,
                          String errorCode, String errorMessage, String businessType, String businessUniqueKey) {
        jdbcTemplate.update("""
                INSERT INTO import_row (import_task_id, row_no, raw_data_json, normalized_data_json, preview_status,
                                        preview_error_code, preview_error_message, business_type, business_unique_key)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, taskId, rowNo, rawJson, normalizedJson, previewStatus, errorCode, errorMessage,
                businessType, businessUniqueKey);
    }

    public Optional<ImportTaskResponse> findTask(long id, boolean includeRows) {
        return jdbcTemplate.query("""
                SELECT * FROM import_task WHERE id = ?
                """, (rs, rowNum) -> new ImportTaskResponse(
                rs.getLong("id"),
                rs.getString("batch_no"),
                rs.getString("business_type"),
                readLong(rs, "template_id"),
                rs.getLong("original_file_id"),
                rs.getInt("total_count"),
                rs.getInt("success_count"),
                rs.getInt("unpublished_count"),
                rs.getInt("failure_count"),
                rs.getString("status"),
                rs.getLong("uploaded_by"),
                rs.getTimestamp("uploaded_at").toLocalDateTime(),
                rs.getTimestamp("completed_at") == null ? null : rs.getTimestamp("completed_at").toLocalDateTime(),
                includeRows ? findRows(rs.getLong("id")) : List.of()), id).stream().findFirst();
    }

    public boolean importFileExists(long attachmentId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM file_attachment
                WHERE id = ? AND purpose = 'IMPORT_FILE'
                """, Integer.class, attachmentId);
        return count != null && count > 0;
    }

    public Optional<ImportTemplateRecord> findTemplate(long templateId, String businessType) {
        return jdbcTemplate.query("""
                SELECT id, template_name, business_type, status
                FROM import_template
                WHERE id = ? AND business_type = ? AND status = 1
                """, (rs, rowNum) -> new ImportTemplateRecord(
                rs.getLong("id"),
                rs.getString("template_name"),
                rs.getString("business_type"),
                rs.getInt("status")), templateId, businessType).stream().findFirst();
    }

    public List<FieldMappingRecord> fieldMappings(long templateId) {
        return jdbcTemplate.query("""
                SELECT source_column, target_field, required_flag
                FROM import_field_mapping
                WHERE template_id = ?
                ORDER BY id
                """, (rs, rowNum) -> new FieldMappingRecord(
                rs.getString("source_column"),
                rs.getString("target_field"),
                rs.getInt("required_flag") == 1), templateId);
    }

    public void linkImportFile(long attachmentId, long taskId) {
        jdbcTemplate.update("""
                UPDATE file_attachment
                SET owner_type = 'IMPORT', owner_id = ?
                WHERE id = ? AND purpose = 'IMPORT_FILE'
                """, taskId, attachmentId);
    }

    public PageResponse<ImportTaskResponse> listTasks(int pageNo, int pageSize) {
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM import_task", Long.class);
        List<ImportTaskResponse> records = jdbcTemplate.query("""
                SELECT * FROM import_task ORDER BY id DESC LIMIT ? OFFSET ?
                """, (rs, rowNum) -> new ImportTaskResponse(
                rs.getLong("id"),
                rs.getString("batch_no"),
                rs.getString("business_type"),
                readLong(rs, "template_id"),
                rs.getLong("original_file_id"),
                rs.getInt("total_count"),
                rs.getInt("success_count"),
                rs.getInt("unpublished_count"),
                rs.getInt("failure_count"),
                rs.getString("status"),
                rs.getLong("uploaded_by"),
                rs.getTimestamp("uploaded_at").toLocalDateTime(),
                rs.getTimestamp("completed_at") == null ? null : rs.getTimestamp("completed_at").toLocalDateTime(),
                List.of()), pageSize, Math.max(pageNo - 1, 0) * pageSize);
        return new PageResponse<>(pageNo, pageSize, total == null ? 0 : total, records);
    }

    public List<ImportRowResponse> findRows(long taskId) {
        return jdbcTemplate.query("""
                SELECT * FROM import_row WHERE import_task_id = ? ORDER BY row_no
                """, (rs, rowNum) -> new ImportRowResponse(
                rs.getLong("id"),
                rs.getInt("row_no"),
                rs.getString("raw_data_json"),
                rs.getString("normalized_data_json"),
                rs.getString("preview_status"),
                rs.getString("preview_error_code"),
                rs.getString("preview_error_message"),
                rs.getString("final_status"),
                rs.getString("final_error_code"),
                rs.getString("final_error_message"),
                rs.getString("business_type"),
                readLong(rs, "business_id"),
                rs.getString("business_unique_key")), taskId);
    }

    public boolean committedKeyExists(String businessType, String key) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM import_row
                WHERE business_type = ? AND business_unique_key = ? AND final_status IN ('SUCCESS', 'UNPUBLISHED')
                """, Integer.class, businessType, key);
        return count != null && count > 0;
    }

    public boolean beginCommit(long taskId) {
        return jdbcTemplate.update("""
                UPDATE import_task
                SET status = 'COMMITTING'
                WHERE id = ? AND status = 'PREVIEWED'
                """, taskId) == 1;
    }

    public void markCommitted(long taskId, int successCount, int unpublishedCount, int failureCount) {
        jdbcTemplate.update("""
                UPDATE import_task
                SET success_count = ?, unpublished_count = ?, failure_count = ?, status = 'COMMITTED', completed_at = ?
                WHERE id = ? AND status IN ('PREVIEWED', 'COMMITTING')
                """, successCount, unpublishedCount, failureCount, Timestamp.valueOf(LocalDateTime.now()), taskId);
    }

    public void updatePreviewCounts(long taskId, int successCount, int failureCount) {
        jdbcTemplate.update("""
                UPDATE import_task
                SET success_count = ?, failure_count = ?, status = 'PREVIEWED'
                WHERE id = ?
                """, successCount, failureCount, taskId);
    }

    public void markRowFinal(long rowId, String status, String errorCode, String errorMessage) {
        jdbcTemplate.update("""
                UPDATE import_row
                SET final_status = ?, final_error_code = ?, final_error_message = ?
                WHERE id = ?
                """, status, errorCode, errorMessage, rowId);
    }

    public void markRowFinal(long rowId, String status, String errorCode, String errorMessage,
                             String businessType, Long businessId, String businessUniqueKey) {
        jdbcTemplate.update("""
                UPDATE import_row
                SET final_status = ?, final_error_code = ?, final_error_message = ?,
                    business_type = ?, business_id = ?, business_unique_key = ?
                WHERE id = ?
                """, status, errorCode, errorMessage, businessType, businessId, businessUniqueKey, rowId);
    }

    public Optional<Long> findRouteByBusinessKey(String key) {
        return jdbcTemplate.query("""
                SELECT id FROM route_task
                WHERE business_unique_key = ? AND deleted_at IS NULL AND status <> 'CANCELLED'
                ORDER BY id LIMIT 1
                """, (rs, rowNum) -> rs.getLong("id"), key).stream().findFirst();
    }

    public Optional<Long> findTireByNo(String tireNo) {
        return jdbcTemplate.query("""
                SELECT id FROM tire WHERE tire_no = ? AND deleted_at IS NULL
                """, (rs, rowNum) -> rs.getLong("id"), tireNo).stream().findFirst();
    }

    public Optional<Long> findImportedBusiness(String businessType, String businessUniqueKey) {
        return jdbcTemplate.query("""
                SELECT business_id
                FROM import_row
                WHERE business_type = ? AND business_unique_key = ?
                  AND final_status IN ('SUCCESS', 'UNPUBLISHED')
                  AND business_id IS NOT NULL
                ORDER BY id LIMIT 1
                """, (rs, rowNum) -> rs.getLong("business_id"), businessType, businessUniqueKey).stream().findFirst();
    }

    public long insertRoute(ImportedRoute route, long operatorId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO route_task (route_no, external_route_no, trip_sequence, business_unique_key,
                                            business_date, customer_id, product_id, direction, loading_place,
                                            unloading_place, carrying_fee, mileage_km, tax_unit_price, info_fee,
                                            assigned_driver_id, import_vehicle_id, driver_salary, salary_source,
                                            status, created_by)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, new String[]{"id"});
            ps.setString(1, route.routeNo());
            ps.setString(2, route.externalRouteNo());
            ps.setString(3, route.tripSequence());
            ps.setString(4, route.businessUniqueKey());
            ps.setObject(5, route.businessDate());
            ps.setLong(6, route.customerId());
            ps.setLong(7, route.productId());
            ps.setString(8, route.direction());
            ps.setString(9, route.loadingPlace());
            ps.setString(10, route.unloadingPlace());
            ps.setBigDecimal(11, route.carryingFee());
            ps.setBigDecimal(12, route.mileageKm());
            ps.setBigDecimal(13, route.taxUnitPrice());
            ps.setBigDecimal(14, route.infoFee());
            ps.setObject(15, route.assignedDriverId());
            ps.setObject(16, route.importVehicleId());
            ps.setBigDecimal(17, route.driverSalary());
            ps.setString(18, route.salarySource());
            ps.setString(19, route.status());
            ps.setLong(20, operatorId);
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    public void insertRouteStatusHistory(long routeId, String toStatus, long operatorId) {
        jdbcTemplate.update("""
                INSERT INTO route_status_history (route_id, from_status, to_status, operation_type, operator_id, reason)
                VALUES (?, NULL, ?, 'IMPORT', ?, NULL)
                """, routeId, toStatus, operatorId);
    }

    public long insertTire(ImportedTire tire) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO tire (tire_no, barcode, arrival_time, description, status, data_source,
                                      install_time, vehicle_id, driver_id)
                    VALUES (?, ?, ?, ?, ?, 'IMPORT', ?, ?, ?)
                    """, new String[]{"id"});
            ps.setString(1, tire.tireNo());
            ps.setString(2, tire.barcode());
            ps.setTimestamp(3, tire.arrivalTime() == null ? null : Timestamp.valueOf(tire.arrivalTime()));
            ps.setString(4, tire.description());
            ps.setString(5, tire.status());
            ps.setTimestamp(6, tire.installTime() == null ? null : Timestamp.valueOf(tire.installTime()));
            ps.setObject(7, tire.vehicleId());
            ps.setObject(8, tire.driverId());
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    public void insertTireClaim(long tireId, long driverId, long vehicleId, LocalDateTime installTime) {
        jdbcTemplate.update("""
                INSERT INTO tire_claim (tire_id, driver_id, vehicle_id, install_time, source_type)
                VALUES (?, ?, ?, ?, 'IMPORT')
                """, tireId, driverId, vehicleId, Timestamp.valueOf(installTime));
    }

    public long insertExpense(ImportedExpense expense, long operatorId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO expense_entry (expense_no, expense_type, business_date, vehicle_id, driver_id,
                                               attribution_type, route_id, amount, source_type, status,
                                               import_row_id, remark, created_by)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'IMPORT', 'ACTIVE', ?, ?, ?)
                    """, new String[]{"id"});
            ps.setString(1, expense.expenseNo());
            ps.setString(2, expense.expenseType());
            ps.setObject(3, expense.businessDate());
            ps.setLong(4, expense.vehicleId());
            ps.setObject(5, expense.driverId());
            ps.setString(6, expense.attributionType());
            ps.setObject(7, expense.routeId());
            ps.setBigDecimal(8, expense.amount());
            ps.setLong(9, expense.importRowId());
            ps.setString(10, expense.remark());
            ps.setLong(11, operatorId);
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    public void insertEnergyDetail(long expenseId, String energyType, Long stationId, String orderNo,
                                   LocalDateTime startTime, BigDecimal quantity, Long routeId, String matchStatus) {
        jdbcTemplate.update("""
                INSERT INTO expense_energy_detail (expense_id, energy_type, station_id, order_no, start_time,
                                                   quantity, auto_matched_route_id, match_status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, expenseId, energyType, stationId, orderNo, Timestamp.valueOf(startTime), quantity, routeId, matchStatus);
    }

    public void insertPenaltyDetail(long expenseId, String detail, BigDecimal deductPoints, String penaltyNo, Long driverId) {
        jdbcTemplate.update("""
                INSERT INTO expense_penalty_detail (expense_id, detail, deduct_points, penalty_no, driver_id)
                VALUES (?, ?, ?, ?, ?)
                """, expenseId, detail, deductPoints, penaltyNo, driverId);
    }

    public void insertRepairDetail(long expenseId, Long trailerId, String detail, String receiptNo,
                                   String invoiceNo, String repairShop) {
        jdbcTemplate.update("""
                INSERT INTO expense_repair_detail (expense_id, trailer_id, detail, receipt_no, invoice_no, repair_shop)
                VALUES (?, ?, ?, ?, ?, ?)
                """, expenseId, trailerId, detail, receiptNo, invoiceNo, repairShop);
    }

    public void insertTollDetail(long expenseId, Long trailerId, String detail, String receiptNo) {
        jdbcTemplate.update("""
                INSERT INTO expense_toll_detail (expense_id, trailer_id, detail, receipt_no)
                VALUES (?, ?, ?, ?)
                """, expenseId, trailerId, detail, receiptNo);
    }

    public boolean customerExists(long id) {
        return exists("customer", id, "status = 1");
    }

    public boolean productExists(long id) {
        return exists("product", id, "status = 1");
    }

    public boolean driverExists(long id) {
        return exists("driver", id, "status = 1 AND deleted_at IS NULL");
    }

    public boolean vehicleExists(long id) {
        return exists("vehicle", id, "status = 1 AND deleted_at IS NULL");
    }

    public boolean routeExists(long id) {
        return exists("route_task", id, "deleted_at IS NULL");
    }

    public Optional<RouteSalarySnapshot> routeSalary(long id) {
        return jdbcTemplate.query("""
                SELECT id, COALESCE(departure_driver_id, assigned_driver_id) AS driver_id, business_date
                FROM route_task
                WHERE id = ? AND deleted_at IS NULL
                """, (rs, rowNum) -> new RouteSalarySnapshot(
                rs.getLong("id"),
                readLong(rs, "driver_id"),
                rs.getDate("business_date").toLocalDate()), id).stream().findFirst();
    }

    public boolean salaryExistsForRoute(long routeId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM driver_salary_entry WHERE route_id = ?
                """, Integer.class, routeId);
        return count != null && count > 0;
    }

    public Optional<BindingSnapshot> driverBinding(long driverId) {
        return jdbcTemplate.query("""
                SELECT d.id AS driver_id, d.status AS driver_status,
                       v.id AS vehicle_id, v.status AS vehicle_status, v.insurance_complete,
                       t.id AS trailer_id, t.status AS trailer_status, t.insurance_complete AS trailer_insurance_complete
                FROM driver d
                LEFT JOIN driver_vehicle_current dvc ON dvc.driver_id = d.id
                LEFT JOIN vehicle v ON v.id = dvc.vehicle_id AND v.deleted_at IS NULL
                LEFT JOIN vehicle_trailer_current vtc ON vtc.vehicle_id = v.id
                LEFT JOIN trailer t ON t.id = vtc.trailer_id AND t.deleted_at IS NULL
                WHERE d.id = ? AND d.deleted_at IS NULL
                """, (rs, rowNum) -> new BindingSnapshot(
                rs.getLong("driver_id"),
                rs.getInt("driver_status"),
                readLong(rs, "vehicle_id"),
                readInteger(rs, "vehicle_status"),
                readInteger(rs, "insurance_complete"),
                readLong(rs, "trailer_id"),
                readInteger(rs, "trailer_status"),
                readInteger(rs, "trailer_insurance_complete")), driverId).stream().findFirst();
    }

    public Optional<Long> matchRoute(long vehicleId, LocalDateTime startTime) {
        return jdbcTemplate.query("""
                SELECT id
                FROM route_task
                WHERE departure_vehicle_id = ?
                  AND departure_time IS NOT NULL
                  AND unload_time IS NOT NULL
                  AND departure_time <= ?
                  AND unload_time >= ?
                  AND status IN ('IN_TRANSIT', 'COMPLETED')
                  AND deleted_at IS NULL
                ORDER BY departure_time DESC
                LIMIT 1
                """, (rs, rowNum) -> rs.getLong("id"),
                vehicleId, Timestamp.valueOf(startTime), Timestamp.valueOf(startTime)).stream().findFirst();
    }

    private boolean exists(String tableName, long id, String extraCondition) {
        String sql = "SELECT COUNT(*) FROM " + tableName + " WHERE id = ?" +
                (extraCondition == null ? "" : " AND " + extraCondition);
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, id);
        return count != null && count > 0;
    }

    private Integer readInteger(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private Long readLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    public record ImportedRoute(
            String routeNo,
            String externalRouteNo,
            String tripSequence,
            String businessUniqueKey,
            LocalDate businessDate,
            long customerId,
            long productId,
            String direction,
            String loadingPlace,
            String unloadingPlace,
            BigDecimal carryingFee,
            BigDecimal mileageKm,
            BigDecimal taxUnitPrice,
            BigDecimal infoFee,
            Long assignedDriverId,
            Long importVehicleId,
            BigDecimal driverSalary,
            String salarySource,
            String status
    ) {
    }

    public record ImportedTire(
            String tireNo,
            String barcode,
            LocalDateTime arrivalTime,
            String description,
            String status,
            LocalDateTime installTime,
            Long vehicleId,
            Long driverId
    ) {
    }

    public record ImportedExpense(
            String expenseNo,
            String expenseType,
            LocalDate businessDate,
            long vehicleId,
            Long driverId,
            String attributionType,
            Long routeId,
            BigDecimal amount,
            long importRowId,
            String remark
    ) {
    }

    public record BindingSnapshot(
            long driverId,
            int driverStatus,
            Long vehicleId,
            Integer vehicleStatus,
            Integer vehicleInsuranceComplete,
            Long trailerId,
            Integer trailerStatus,
            Integer trailerInsuranceComplete
    ) {
    }

    public record RouteSalarySnapshot(long routeId, Long driverId, LocalDate businessDate) {
    }

    public record ImportTemplateRecord(long id, String templateName, String businessType, int status) {
    }

    public record FieldMappingRecord(String sourceColumn, String targetField, boolean required) {
    }
}
