package com.dfwl.fleet.salary.repository;

import com.dfwl.fleet.common.api.PageResponse;
import com.dfwl.fleet.salary.api.DriverSalaryHistoryResponse;
import com.dfwl.fleet.salary.api.DriverSalaryResponse;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class DriverSalaryRepository {

    private final JdbcTemplate jdbcTemplate;

    public DriverSalaryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<RouteSalaryContext> findRouteContext(long routeId) {
        return jdbcTemplate.query("""
                SELECT r.id AS route_id, r.route_no, r.business_date,
                       COALESCE(r.departure_driver_id, r.assigned_driver_id) AS driver_id,
                       d.name AS driver_name
                FROM route_task r
                LEFT JOIN driver d ON d.id = COALESCE(r.departure_driver_id, r.assigned_driver_id)
                WHERE r.id = ? AND r.deleted_at IS NULL
                """, (rs, rowNum) -> new RouteSalaryContext(
                rs.getLong("route_id"),
                rs.getString("route_no"),
                rs.getDate("business_date").toLocalDate(),
                readLong(rs, "driver_id"),
                rs.getString("driver_name")), routeId).stream().findFirst();
    }

    public Optional<SalaryEntry> findEntryByRouteForUpdate(long routeId) {
        return jdbcTemplate.query("""
                SELECT * FROM driver_salary_entry WHERE route_id = ? FOR UPDATE
                """, (rs, rowNum) -> new SalaryEntry(
                rs.getLong("id"),
                rs.getLong("route_id"),
                rs.getLong("driver_id"),
                rs.getString("business_month"),
                rs.getDate("business_date").toLocalDate(),
                rs.getBigDecimal("salary_amount"),
                rs.getString("source_type")), routeId).stream().findFirst();
    }

    public boolean salaryExistsForRoute(long routeId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM driver_salary_entry WHERE route_id = ?
                """, Integer.class, routeId);
        return count != null && count > 0;
    }

    public Optional<Long> importTaskIdForRow(long rowId) {
        return jdbcTemplate.query("""
                SELECT import_task_id FROM import_row WHERE id = ?
                """, (rs, rowNum) -> rs.getLong("import_task_id"), rowId).stream().findFirst();
    }

    public long insertEntry(long routeId, long driverId, String businessMonth, LocalDate businessDate,
                            BigDecimal amount, String sourceType, Long importTaskId, Long importRowId, long operatorId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO driver_salary_entry (route_id, driver_id, business_month, business_date, salary_amount,
                                                     source_type, import_task_id, import_row_id, created_by)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, new String[]{"id"});
            ps.setLong(1, routeId);
            ps.setLong(2, driverId);
            ps.setString(3, businessMonth);
            ps.setDate(4, Date.valueOf(businessDate));
            ps.setBigDecimal(5, amount);
            ps.setString(6, sourceType);
            ps.setObject(7, importTaskId);
            ps.setObject(8, importRowId);
            ps.setLong(9, operatorId);
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    public void updateEntry(long entryId, long driverId, String businessMonth, LocalDate businessDate,
                            BigDecimal amount, String sourceType, Long importTaskId, Long importRowId, long operatorId) {
        jdbcTemplate.update("""
                UPDATE driver_salary_entry
                SET driver_id = ?, business_month = ?, business_date = ?, salary_amount = ?, source_type = ?,
                    import_task_id = ?, import_row_id = ?, updated_by = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, driverId, businessMonth, Date.valueOf(businessDate), amount, sourceType,
                importTaskId, importRowId, operatorId, entryId);
    }

    public void updateRouteSalary(long routeId, BigDecimal amount, String sourceType, long operatorId) {
        jdbcTemplate.update("""
                UPDATE route_task
                SET driver_salary = ?, salary_source = ?, updated_by = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND deleted_at IS NULL
                """, amount, sourceType, operatorId, routeId);
    }

    public void insertHistory(Long entryId, long routeId, long driverId, BigDecimal beforeAmount, BigDecimal afterAmount,
                              String beforeSourceType, String afterSourceType, long operatorId, String reason,
                              Long importTaskId, Long importRowId) {
        jdbcTemplate.update("""
                INSERT INTO driver_salary_history (salary_entry_id, route_id, driver_id, before_amount, after_amount,
                                                   before_source_type, after_source_type, operator_id, reason,
                                                   import_task_id, import_row_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, entryId, routeId, driverId, beforeAmount, afterAmount, beforeSourceType, afterSourceType,
                operatorId, reason, importTaskId, importRowId);
    }

    public Optional<DriverSalaryResponse> findByRoute(long routeId) {
        return queryResponses("""
                WHERE e.route_id = ?
                """, routeId).stream().findFirst();
    }

    public Optional<DriverSalaryResponse> findById(long id) {
        return queryResponses("""
                WHERE e.id = ?
                """, id).stream().findFirst();
    }

    public PageResponse<DriverSalaryResponse> list(Long driverId, String businessMonth, int pageNo, int pageSize) {
        StringBuilder where = new StringBuilder("WHERE 1 = 1");
        List<Object> args = new java.util.ArrayList<>();
        if (driverId != null) {
            where.append(" AND e.driver_id = ?");
            args.add(driverId);
        }
        if (businessMonth != null && !businessMonth.isBlank()) {
            where.append(" AND e.business_month = ?");
            args.add(businessMonth);
        }
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM driver_salary_entry e " + where, Long.class, args.toArray());
        args.add(pageSize);
        args.add(Math.max(pageNo - 1, 0) * pageSize);
        List<DriverSalaryResponse> records = jdbcTemplate.query(baseSelect() + " " + where + " ORDER BY e.business_date DESC, e.id DESC LIMIT ? OFFSET ?",
                this::mapResponse, args.toArray());
        return new PageResponse<>(pageNo, pageSize, total == null ? 0 : total, records);
    }

    public List<DriverSalaryHistoryResponse> history(long routeId) {
        return jdbcTemplate.query("""
                SELECT *
                FROM driver_salary_history
                WHERE route_id = ?
                ORDER BY operation_time, id
                """, (rs, rowNum) -> new DriverSalaryHistoryResponse(
                rs.getLong("id"),
                readLong(rs, "salary_entry_id"),
                rs.getLong("route_id"),
                rs.getLong("driver_id"),
                rs.getBigDecimal("before_amount"),
                rs.getBigDecimal("after_amount"),
                rs.getString("before_source_type"),
                rs.getString("after_source_type"),
                rs.getLong("operator_id"),
                rs.getTimestamp("operation_time").toLocalDateTime(),
                rs.getString("reason"),
                readLong(rs, "import_task_id"),
                readLong(rs, "import_row_id")), routeId);
    }

    private List<DriverSalaryResponse> queryResponses(String where, Object... args) {
        return jdbcTemplate.query(baseSelect() + " " + where, this::mapResponse, args);
    }

    private String baseSelect() {
        return """
                SELECT e.id, e.route_id, r.route_no, e.driver_id, d.name AS driver_name, e.business_month,
                       e.business_date, e.salary_amount, e.source_type, e.import_task_id, e.import_row_id,
                       e.created_by, e.created_at, e.updated_by, e.updated_at
                FROM driver_salary_entry e
                JOIN route_task r ON r.id = e.route_id
                JOIN driver d ON d.id = e.driver_id
                """;
    }

    private DriverSalaryResponse mapResponse(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new DriverSalaryResponse(
                rs.getLong("id"),
                rs.getLong("route_id"),
                rs.getString("route_no"),
                rs.getLong("driver_id"),
                rs.getString("driver_name"),
                rs.getString("business_month"),
                rs.getDate("business_date").toLocalDate(),
                rs.getBigDecimal("salary_amount"),
                rs.getString("source_type"),
                readLong(rs, "import_task_id"),
                readLong(rs, "import_row_id"),
                rs.getLong("created_by"),
                rs.getTimestamp("created_at").toLocalDateTime(),
                readLong(rs, "updated_by"),
                rs.getTimestamp("updated_at").toLocalDateTime());
    }

    private Long readLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    public record RouteSalaryContext(long routeId, String routeNo, LocalDate businessDate, Long driverId, String driverName) {
    }

    public record SalaryEntry(long id, long routeId, long driverId, String businessMonth, LocalDate businessDate,
                              BigDecimal amount, String sourceType) {
    }
}
