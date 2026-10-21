package com.dfwl.fleet.salary.repository;

import com.dfwl.fleet.common.api.PageResponse;
import com.dfwl.fleet.salary.dto.response.ParticipantSalaryHistoryResponse;
import com.dfwl.fleet.salary.dto.response.ParticipantSalaryResponse;
import com.dfwl.fleet.salary.dto.response.DriverSalaryMonthlySummaryResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Participant is the only writable salary fact; all salary views are projections. */
@Repository
public class DriverSalaryRepository {
    private final JdbcTemplate jdbc;

    public DriverSalaryRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public PageResponse<ParticipantSalaryResponse> list(Long driverId, String month, int pageNo, int pageSize) {
        StringBuilder where = new StringBuilder(" WHERE t.deleted_at IS NULL AND t.status = 'COMPLETED'"
                + " AND p.status <> 'CANCELLED'");
        List<Object> args = new ArrayList<>();
        if (driverId != null) { where.append(" AND p.driver_id = ?"); args.add(driverId); }
        if (month != null && !month.isBlank()) {
            YearMonth period = YearMonth.parse(month);
            where.append(" AND t.business_date >= ? AND t.business_date < ?");
            args.add(period.atDay(1)); args.add(period.plusMonths(1).atDay(1));
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM transport_participant p"
                + " JOIN transport_task t ON t.id = p.task_id" + where, Long.class, args.toArray());
        List<Object> paged = new ArrayList<>(args);
        paged.add(pageSize);
        paged.add(Math.max(pageNo - 1, 0) * pageSize);
        List<ParticipantSalaryResponse> rows = jdbc.query("""
                SELECT p.id AS participant_id, p.sequence_no, p.driver_id, d.name AS driver_name,
                       p.driver_type_snapshot, p.salary_amount, p.source_type, p.updated_at,
                       t.id AS task_id, t.task_no, t.business_date
                FROM transport_participant p
                JOIN transport_task t ON t.id = p.task_id
                JOIN driver d ON d.id = p.driver_id
                """ + where + " ORDER BY t.business_date DESC, p.id DESC LIMIT ? OFFSET ?",
                this::mapSalary, paged.toArray());
        return new PageResponse<>(pageNo, pageSize, total == null ? 0 : total, rows);
    }

    public DriverSalaryMonthlySummaryResponse monthlySummary(long driverId, String month) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(p.salary_amount), 0) AS total_amount, COUNT(*) AS entry_count
                FROM transport_participant p JOIN transport_task t ON t.id = p.task_id
                WHERE p.driver_id = ? AND p.driver_type_snapshot = 'INTERNAL'
                  AND p.status <> 'CANCELLED' AND t.status = 'COMPLETED'
                  AND t.deleted_at IS NULL AND t.business_date >= ? AND t.business_date < ?
                """, (rs, row) -> new DriverSalaryMonthlySummaryResponse(month,
                rs.getBigDecimal("total_amount"), rs.getLong("entry_count")), driverId,
                YearMonth.parse(month).atDay(1), YearMonth.parse(month).plusMonths(1).atDay(1));
    }

    public List<ParticipantSalaryHistoryResponse> history(long participantId) {
        return jdbc.query("""
                SELECT h.*, u.phone AS operator_phone
                FROM participant_salary_history h
                LEFT JOIN sys_user u ON u.id = h.operator_id
                WHERE h.participant_id = ? ORDER BY h.operated_at, h.id
                """, (rs, row) -> new ParticipantSalaryHistoryResponse(
                rs.getLong("id"), rs.getLong("participant_id"), rs.getBigDecimal("before_amount"),
                rs.getBigDecimal("after_amount"), rs.getString("reason"), rs.getLong("operator_id"),
                rs.getTimestamp("operated_at").toLocalDateTime(), rs.getString("operator_phone")), participantId);
    }

    private ParticipantSalaryResponse mapSalary(ResultSet rs, int row) throws SQLException {
        return new ParticipantSalaryResponse(rs.getLong("participant_id"), rs.getLong("task_id"),
                rs.getString("task_no"), rs.getInt("sequence_no"), rs.getLong("driver_id"),
                rs.getString("driver_name"), rs.getString("driver_type_snapshot"),
                rs.getDate("business_date").toLocalDate(), rs.getBigDecimal("salary_amount"),
                rs.getString("source_type"), rs.getTimestamp("updated_at").toLocalDateTime());
    }
}
