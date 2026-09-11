package com.dfwl.fleet.expense.repository;

import com.dfwl.fleet.common.api.PageResponse;
import com.dfwl.fleet.expense.api.ExpenseAttributionHistoryResponse;
import com.dfwl.fleet.expense.api.ExpenseRequest;
import com.dfwl.fleet.expense.api.ExpenseResponse;
import com.dfwl.fleet.expense.api.ExpenseResponse.EnergyDetail;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class ExpenseRepository {

    private final JdbcTemplate jdbcTemplate;

    public ExpenseRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public PageResponse<ExpenseResponse> list(int pageNo, int pageSize) {
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM expense_entry WHERE deleted_at IS NULL", Long.class);
        List<ExpenseResponse> records = jdbcTemplate.query("""
                SELECT e.*, d.energy_type, d.station_id, d.order_no, d.start_time, d.quantity,
                       d.auto_matched_route_id, d.match_status
                FROM expense_entry e
                LEFT JOIN expense_energy_detail d ON d.expense_id = e.id
                WHERE e.deleted_at IS NULL
                ORDER BY e.id DESC
                LIMIT ? OFFSET ?
                """, this::mapExpense, pageSize, Math.max(pageNo - 1, 0) * pageSize);
        return new PageResponse<>(pageNo, pageSize, total == null ? 0 : total, records);
    }

    public Optional<ExpenseResponse> find(long id) {
        return jdbcTemplate.query("""
                SELECT e.*, d.energy_type, d.station_id, d.order_no, d.start_time, d.quantity,
                       d.auto_matched_route_id, d.match_status
                FROM expense_entry e
                LEFT JOIN expense_energy_detail d ON d.expense_id = e.id
                WHERE e.id = ? AND e.deleted_at IS NULL
                """, this::mapExpense, id).stream().findFirst();
    }

    public Optional<ExpenseResponse> findByApprovalInstanceId(long approvalInstanceId) {
        return jdbcTemplate.query("""
                SELECT e.*, d.energy_type, d.station_id, d.order_no, d.start_time, d.quantity,
                       d.auto_matched_route_id, d.match_status
                FROM expense_entry e
                LEFT JOIN expense_energy_detail d ON d.expense_id = e.id
                WHERE e.approval_instance_id = ? AND e.deleted_at IS NULL
                ORDER BY e.id
                LIMIT 1
                """, this::mapExpense, approvalInstanceId).stream().findFirst();
    }

    public long create(String expenseNo, ExpenseRequest request, String status, Long routeId, long operatorId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO expense_entry (expense_no, expense_type, business_date, vehicle_id, driver_id,
                                               attribution_type, route_id, amount, source_type, status,
                                               approval_instance_id, import_row_id, remark, created_by)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, new String[]{"id"});
            ps.setString(1, expenseNo);
            ps.setString(2, request.expenseType());
            ps.setObject(3, request.businessDate());
            ps.setLong(4, request.vehicleId());
            ps.setObject(5, request.driverId());
            ps.setString(6, request.attributionType());
            ps.setObject(7, routeId);
            ps.setBigDecimal(8, request.amount());
            ps.setString(9, request.sourceType());
            ps.setString(10, status);
            ps.setObject(11, request.approvalInstanceId());
            ps.setObject(12, request.importRowId());
            ps.setString(13, request.remark());
            ps.setLong(14, operatorId);
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    public void insertEnergyDetail(long expenseId, ExpenseRequest.EnergyDetailRequest detail,
                                   Long autoMatchedRouteId, String matchStatus) {
        jdbcTemplate.update("""
                INSERT INTO expense_energy_detail (expense_id, energy_type, station_id, order_no, start_time,
                                                   quantity, auto_matched_route_id, match_status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, expenseId, detail.energyType(), detail.stationId(), detail.orderNo(),
                Timestamp.valueOf(detail.startTime()), detail.quantity(), autoMatchedRouteId, matchStatus);
    }

    public boolean update(long id, ExpenseRequest request, Long routeId, long operatorId) {
        return jdbcTemplate.update("""
                UPDATE expense_entry
                SET expense_type = ?, business_date = ?, vehicle_id = ?, driver_id = ?, attribution_type = ?,
                    route_id = ?, amount = ?, source_type = ?, remark = ?, updated_by = ?
                WHERE id = ? AND deleted_at IS NULL
                """, request.expenseType(), request.businessDate(), request.vehicleId(), request.driverId(),
                request.attributionType(), routeId, request.amount(), request.sourceType(), request.remark(),
                operatorId, id) == 1;
    }

    public void replaceEnergyDetail(long expenseId, ExpenseRequest.EnergyDetailRequest detail,
                                    Long autoMatchedRouteId, String matchStatus) {
        jdbcTemplate.update("DELETE FROM expense_energy_detail WHERE expense_id = ?", expenseId);
        if (detail != null) {
            insertEnergyDetail(expenseId, detail, autoMatchedRouteId, matchStatus);
        }
    }

    public boolean delete(long id, long operatorId) {
        return jdbcTemplate.update("""
                UPDATE expense_entry
                SET deleted_at = CURRENT_TIMESTAMP, updated_by = ?
                WHERE id = ? AND deleted_at IS NULL
                """, operatorId, id) == 1;
    }

    public void updateAttribution(long id, String attributionType, Long routeId, long operatorId) {
        jdbcTemplate.update("""
                UPDATE expense_entry
                SET attribution_type = ?, route_id = ?, status = 'ACTIVE', updated_by = ?
                WHERE id = ? AND deleted_at IS NULL
                """, attributionType, routeId, operatorId, id);
    }

    public int markEnergyExpensesPendingForVoidedRoute(long routeId, long operatorId, String reason) {
        List<ExpenseResponse> expenses = jdbcTemplate.query("""
                SELECT e.*, d.energy_type, d.station_id, d.order_no, d.start_time, d.quantity,
                       d.auto_matched_route_id, d.match_status
                FROM expense_entry e
                JOIN expense_energy_detail d ON d.expense_id = e.id
                WHERE e.route_id = ?
                  AND e.attribution_type = 'ROUTE'
                  AND e.status = 'ACTIVE'
                  AND e.expense_type IN ('ELECTRIC', 'GAS', 'TEMP_ELECTRIC')
                  AND e.deleted_at IS NULL
                ORDER BY e.id
                """, this::mapExpense, routeId);
        for (ExpenseResponse expense : expenses) {
            jdbcTemplate.update("""
                    UPDATE expense_entry
                    SET status = 'PENDING_ATTRIBUTION', updated_by = ?
                    WHERE id = ? AND status = 'ACTIVE'
                    """, operatorId, expense.id());
            insertAttributionHistory(
                    expense.id(),
                    expense.attributionType(),
                    expense.routeId(),
                    expense.status(),
                    expense.attributionType(),
                    expense.routeId(),
                    "PENDING_ATTRIBUTION",
                    operatorId,
                    reason);
        }
        return expenses.size();
    }

    public void insertAttributionHistory(long expenseId, String beforeAttributionType, Long beforeRouteId,
                                         String beforeStatus, String afterAttributionType, Long afterRouteId,
                                         String afterStatus, long operatorId, String reason) {
        jdbcTemplate.update("""
                INSERT INTO expense_attribution_history (expense_id, before_attribution_type, before_route_id,
                                                         before_status, after_attribution_type, after_route_id,
                                                         after_status, operator_id, reason)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, expenseId, beforeAttributionType, beforeRouteId, beforeStatus,
                afterAttributionType, afterRouteId, afterStatus, operatorId, reason);
    }

    public List<ExpenseAttributionHistoryResponse> attributionHistory(long expenseId) {
        return jdbcTemplate.query("""
                SELECT id, expense_id, before_attribution_type, before_route_id, before_status,
                       after_attribution_type, after_route_id, after_status, operator_id, operation_time, reason
                FROM expense_attribution_history
                WHERE expense_id = ?
                ORDER BY id
                """, (rs, rowNum) -> new ExpenseAttributionHistoryResponse(
                rs.getLong("id"),
                rs.getLong("expense_id"),
                rs.getString("before_attribution_type"),
                readLong(rs, "before_route_id"),
                rs.getString("before_status"),
                rs.getString("after_attribution_type"),
                readLong(rs, "after_route_id"),
                rs.getString("after_status"),
                rs.getLong("operator_id"),
                rs.getTimestamp("operation_time").toLocalDateTime(),
                rs.getString("reason")), expenseId);
    }

    public boolean markReversed(long id, long operatorId) {
        return jdbcTemplate.update("""
                UPDATE expense_entry
                SET status = 'REVERSED', updated_by = ?
                WHERE id = ? AND deleted_at IS NULL AND status = 'ACTIVE'
                """, operatorId, id) == 1;
    }

    public Optional<ExpenseResponse> findReversalByOriginalId(long originalId) {
        return jdbcTemplate.query("""
                SELECT e.*, d.energy_type, d.station_id, d.order_no, d.start_time, d.quantity,
                       d.auto_matched_route_id, d.match_status
                FROM expense_entry e
                LEFT JOIN expense_energy_detail d ON d.expense_id = e.id
                WHERE e.reversal_of_id = ? AND e.status = 'REVERSAL' AND e.deleted_at IS NULL
                ORDER BY e.id
                LIMIT 1
                """, this::mapExpense, originalId).stream().findFirst();
    }

    public long createReversal(String expenseNo, ExpenseResponse original, long operatorId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO expense_entry (expense_no, expense_type, business_date, vehicle_id, driver_id,
                                               attribution_type, route_id, amount, source_type, status,
                                               approval_instance_id, reversal_of_id, remark, created_by)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'SYSTEM', 'REVERSAL', ?, ?, ?, ?)
                    """, new String[]{"id"});
            ps.setString(1, expenseNo);
            ps.setString(2, original.expenseType());
            ps.setObject(3, original.businessDate());
            ps.setLong(4, original.vehicleId());
            ps.setObject(5, original.driverId());
            ps.setString(6, original.attributionType());
            ps.setObject(7, original.routeId());
            ps.setBigDecimal(8, original.amount().negate());
            ps.setObject(9, original.approvalInstanceId());
            ps.setLong(10, original.id());
            ps.setString(11, "冲销费用 " + original.expenseNo());
            ps.setLong(12, operatorId);
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
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

    public void insertReversalLink(long originalId, long reversalId, String reason, long operatorId) {
        jdbcTemplate.update("""
                INSERT INTO expense_reversal_link (original_expense_id, reversal_expense_id, reason, operator_id)
                VALUES (?, ?, ?, ?)
                """, originalId, reversalId, reason, operatorId);
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

    public boolean vehicleExists(long vehicleId) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM vehicle WHERE id = ? AND deleted_at IS NULL", Integer.class, vehicleId);
        return count != null && count > 0;
    }

    public boolean routeExists(long routeId) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM route_task WHERE id = ? AND deleted_at IS NULL", Integer.class, routeId);
        return count != null && count > 0;
    }

    private ExpenseResponse mapExpense(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        Timestamp startTime = rs.getTimestamp("start_time");
        EnergyDetail energyDetail = startTime == null ? null : new EnergyDetail(
                rs.getString("energy_type"),
                readLong(rs, "station_id"),
                rs.getString("order_no"),
                startTime.toLocalDateTime(),
                rs.getBigDecimal("quantity"),
                readLong(rs, "auto_matched_route_id"),
                rs.getString("match_status"));
        return new ExpenseResponse(
                rs.getLong("id"),
                rs.getString("expense_no"),
                rs.getString("expense_type"),
                rs.getDate("business_date").toLocalDate(),
                rs.getLong("vehicle_id"),
                readLong(rs, "driver_id"),
                rs.getString("attribution_type"),
                readLong(rs, "route_id"),
                rs.getBigDecimal("amount"),
                rs.getString("source_type"),
                rs.getString("status"),
                readLong(rs, "approval_instance_id"),
                readLong(rs, "import_row_id"),
                readLong(rs, "reversal_of_id"),
                rs.getString("remark"),
                energyDetail,
                rs.getTimestamp("created_at").toLocalDateTime());
    }

    private Long readLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}
