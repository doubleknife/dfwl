package com.dfwl.fleet.route.repository;

import com.dfwl.fleet.common.error.BusinessException;
import com.dfwl.fleet.common.error.ErrorCode;
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
public class RouteParticipantRepository {
    private final JdbcTemplate jdbc;

    public RouteParticipantRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Participant> list(long routeId) {
        return jdbc.query("SELECT * FROM route_participant WHERE route_id = ? ORDER BY sequence_no", this::map, routeId);
    }

    public List<Participant> listByDriver(long driverId) {
        return jdbc.query("SELECT * FROM route_participant WHERE driver_id = ? ORDER BY route_id, sequence_no",
                this::map, driverId);
    }

    public List<Participant> listByCompanyVehicle(long vehicleId) {
        return jdbc.query("SELECT * FROM route_participant WHERE company_vehicle_id = ? ORDER BY route_id, sequence_no",
                this::map, vehicleId);
    }

    public Optional<Participant> current(long routeId) {
        return jdbc.query("""
                SELECT p.* FROM route_task r
                JOIN route_participant p ON p.id = r.current_participant_id AND p.route_id = r.id
                WHERE r.id = ? AND r.deleted_at IS NULL
                """, this::map, routeId).stream().findFirst();
    }

    public long createInitial(long routeId, long driverId, String driverType, Long vehicleId,
                              String outsourcedPlate, BigDecimal salaryAmount, long operatorId) {
        if (salaryAmount != null && salaryAmount.signum() < 0) throw new BusinessException(ErrorCode.SYS_002);
        KeyHolder key = new GeneratedKeyHolder();
        LocalDateTime now = LocalDateTime.now();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO route_participant
                      (route_id, sequence_no, driver_id, driver_type_snapshot, company_vehicle_id,
                       outsourced_plate_no, salary_amount, source_type, status, assigned_at, created_by)
                    VALUES (?, 1, ?, ?, ?, ?, ?, 'INITIAL', 'ASSIGNED', ?, ?)
                    """, new String[]{"id"});
            ps.setLong(1, routeId);
            ps.setLong(2, driverId);
            ps.setString(3, driverType);
            ps.setObject(4, vehicleId);
            ps.setString(5, outsourcedPlate);
            ps.setBigDecimal(6, salaryAmount);
            ps.setTimestamp(7, Timestamp.valueOf(now));
            ps.setLong(8, operatorId);
            return ps;
        }, key);
        long participantId = key.getKey().longValue();
        if (jdbc.update("UPDATE route_task SET current_participant_id = ? WHERE id = ? AND current_participant_id IS NULL",
                participantId, routeId) != 1) {
            throw new BusinessException(ErrorCode.ROUTE_002);
        }
        return participantId;
    }

    public void departed(long routeId, long driverId, Long vehicleId, long operatorId, LocalDateTime when) {
        Participant current = current(routeId).orElseThrow(() -> new BusinessException(ErrorCode.ROUTE_003));
        if (current.driverId() != driverId || !"ASSIGNED".equals(current.status())) {
            throw new BusinessException(ErrorCode.ROUTE_003);
        }
        if (jdbc.update("""
                UPDATE route_participant SET status = 'IN_TRANSIT', company_vehicle_id = ?,
                    departed_at = ?, updated_by = ?, version = version + 1
                WHERE id = ? AND status = 'ASSIGNED'
                """, vehicleId, Timestamp.valueOf(when), operatorId, current.id()) != 1) {
            throw new BusinessException(ErrorCode.ROUTE_002);
        }
    }

    public void completed(long routeId, long operatorId, LocalDateTime when) {
        Participant current = current(routeId).orElseThrow(() -> new BusinessException(ErrorCode.ROUTE_003));
        if (jdbc.update("""
                UPDATE route_participant SET status = 'COMPLETED', ended_at = ?, updated_by = ?, version = version + 1
                WHERE id = ? AND status = 'IN_TRANSIT'
                """, Timestamp.valueOf(when), operatorId, current.id()) != 1) {
            throw new BusinessException(ErrorCode.ROUTE_002);
        }
    }

    public void cancelInitial(long routeId, long operatorId, String reason) {
        Optional<Participant> current = current(routeId);
        if (current.isEmpty()) return;
        Participant p = current.get();
        if (p.sequenceNo() != 1 || !"ASSIGNED".equals(p.status())) {
            throw new BusinessException(ErrorCode.ROUTE_002);
        }
        if (jdbc.update("""
                UPDATE route_participant SET status = 'CANCELLED', ended_at = CURRENT_TIMESTAMP(3),
                    end_reason = ?, updated_by = ?, version = version + 1 WHERE id = ? AND status = 'ASSIGNED'
                """, reason, operatorId, p.id()) != 1) {
            throw new BusinessException(ErrorCode.ROUTE_002);
        }
        jdbc.update("UPDATE route_task SET current_participant_id = NULL WHERE id = ? AND current_participant_id = ?",
                routeId, p.id());
    }

    /** The V1 salary entry is projected onto the initial participant in the same transaction. */
    public void updateInitialSalary(long routeId, BigDecimal amount, long operatorId) {
        if (amount == null || amount.signum() < 0) throw new BusinessException(ErrorCode.SYS_002);
        Participant p = current(routeId).orElseThrow(() -> new BusinessException(ErrorCode.ROUTE_003));
        if (p.sequenceNo() != 1 || !"INITIAL".equals(p.sourceType()) || "CANCELLED".equals(p.status())) {
            throw new BusinessException(ErrorCode.ROUTE_002);
        }
        if (p.salaryAmount() != null && p.salaryAmount().compareTo(amount) == 0) return;
        if (jdbc.update("""
                UPDATE route_participant SET salary_amount = ?, updated_by = ?, version = version + 1
                WHERE id = ? AND version = ?
                """, amount, operatorId, p.id(), p.version()) != 1) {
            throw new BusinessException(ErrorCode.ROUTE_002);
        }
        jdbc.update("""
                INSERT INTO route_participant_salary_history
                    (participant_id, before_amount, after_amount, reason, operator_id)
                VALUES (?, ?, ?, ?, ?)
                """, p.id(), p.salaryAmount(), amount,
                p.salaryAmount() == null ? "初始工资录入" : "工资调整", operatorId);
    }

    private Participant map(java.sql.ResultSet rs, int ignored) throws java.sql.SQLException {
        return new Participant(rs.getLong("id"), rs.getLong("route_id"), rs.getInt("sequence_no"),
                rs.getLong("driver_id"), rs.getString("driver_type_snapshot"),
                nullableLong(rs, "company_vehicle_id"), rs.getString("outsourced_plate_no"),
                rs.getString("source_type"), rs.getString("status"), rs.getBigDecimal("salary_amount"),
                rs.getInt("historical_data_complete") == 1, rs.getInt("version"));
    }

    private Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    public record Participant(long id, long routeId, int sequenceNo, long driverId,
                              String driverTypeSnapshot, Long companyVehicleId, String outsourcedPlateNo,
                              String sourceType, String status, BigDecimal salaryAmount,
                              boolean historicalDataComplete, int version) {}
}
