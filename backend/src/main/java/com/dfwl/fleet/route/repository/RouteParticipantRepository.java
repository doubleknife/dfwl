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

    public String currentPlateNo(long routeId) {
        return jdbc.query("""
                SELECT CASE WHEN p.driver_type_snapshot = 'OUTSOURCED'
                            THEN p.outsourced_plate_no ELSE v.plate_no END AS plate_no
                FROM route_task r
                JOIN route_participant p ON p.id = r.current_participant_id
                LEFT JOIN vehicle v ON v.id = p.company_vehicle_id
                WHERE r.id = ? AND r.deleted_at IS NULL
                """, (rs, row) -> rs.getString("plate_no"), routeId).stream()
                .filter(java.util.Objects::nonNull).findFirst().orElse(null);
    }

    public List<ParticipantDetail> details(long routeId, boolean includeSalary) {
        return jdbc.query("""
                SELECT p.*, r.current_participant_id, d.name AS driver_name
                FROM route_participant p
                JOIN route_task r ON r.id = p.route_id
                LEFT JOIN driver d ON d.id = p.driver_id
                WHERE p.route_id = ? AND r.deleted_at IS NULL
                ORDER BY p.sequence_no
                """, (rs, row) -> new ParticipantDetail(
                rs.getLong("id"), rs.getInt("sequence_no"), rs.getLong("driver_id"),
                rs.getString("driver_name"), rs.getString("driver_type_snapshot"),
                nullableLong(rs, "company_vehicle_id"), rs.getString("outsourced_plate_no"),
                rs.getString("source_type"), rs.getString("status"),
                rs.getTimestamp("assigned_at") == null ? null : rs.getTimestamp("assigned_at").toLocalDateTime(),
                rs.getTimestamp("departed_at") == null ? null : rs.getTimestamp("departed_at").toLocalDateTime(),
                rs.getTimestamp("ended_at") == null ? null : rs.getTimestamp("ended_at").toLocalDateTime(),
                rs.getString("end_reason"), rs.getString("transfer_reason"),
                rs.getLong("current_participant_id") == rs.getLong("id"),
                includeSalary ? rs.getBigDecimal("salary_amount") : null,
                rs.getInt("version")), routeId);
    }

    public List<SalaryHistory> salaryHistory(long participantId) {
        return jdbc.query("""
                SELECT before_amount, after_amount, reason, operator_id, operation_time
                FROM route_participant_salary_history WHERE participant_id = ? ORDER BY id
                """, (rs, row) -> new SalaryHistory(rs.getBigDecimal("before_amount"),
                rs.getBigDecimal("after_amount"), rs.getString("reason"),
                rs.getLong("operator_id"), rs.getTimestamp("operation_time").toLocalDateTime()), participantId);
    }

    public BigDecimal effectiveSalary(long routeId) {
        List<Participant> all = list(routeId);
        BigDecimal sum = BigDecimal.ZERO;
        for (Participant p : all) {
            if ("CANCELLED".equals(p.status()) || "LEGACY_ENDED".equals(p.status())) continue;
            if (p.salaryAmount() == null) throw new BusinessException(ErrorCode.ROUTE_002);
            sum = sum.add(p.salaryAmount());
        }
        return sum;
    }

    public void adjustSalary(Participant p, BigDecimal amount, String reason, long operatorId) {
        if (amount == null || amount.signum() < 0 || reason == null || reason.isBlank())
            throw new BusinessException(ErrorCode.SYS_002);
        if (p.salaryAmount() != null && p.salaryAmount().compareTo(amount) == 0) return;
        if (jdbc.update("""
                UPDATE route_participant SET salary_amount = ?, updated_by = ?, version = version + 1
                WHERE id = ? AND version = ?
                """, amount, operatorId, p.id(), p.version()) != 1)
            throw new BusinessException(ErrorCode.ROUTE_002);
        jdbc.update("""
                INSERT INTO route_participant_salary_history
                    (participant_id, before_amount, after_amount, reason, operator_id)
                VALUES (?, ?, ?, ?, ?)
                """, p.id(), p.salaryAmount(), amount, reason.trim(), operatorId);
    }

    public long transfer(long routeId, Participant previous, long driverId, String driverType,
                         Long vehicleId, String plate, BigDecimal salary, String reason, long operatorId) {
        if (jdbc.update("""
                UPDATE route_participant SET status = 'TRANSFERRED', ended_at = CURRENT_TIMESTAMP(3),
                    end_reason = ?, transfer_reason = ?, updated_by = ?, version = version + 1
                WHERE id = ? AND route_id = ? AND version = ? AND status IN ('ASSIGNED', 'IN_TRANSIT')
                """, reason, reason, operatorId, previous.id(), routeId, previous.version()) != 1)
            throw new BusinessException(ErrorCode.ROUTE_002);
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO route_participant
                    (route_id, sequence_no, driver_id, driver_type_snapshot, company_vehicle_id,
                     outsourced_plate_no, source_type, status, assigned_at, transfer_reason,
                     salary_amount, created_by)
                    VALUES (?, ?, ?, ?, ?, ?, 'TRANSFER', 'ASSIGNED', CURRENT_TIMESTAMP(3), ?, ?, ?)
                    """, new String[]{"id"});
            ps.setLong(1, routeId);
            ps.setInt(2, previous.sequenceNo() + 1);
            ps.setLong(3, driverId);
            ps.setString(4, driverType);
            ps.setObject(5, vehicleId);
            ps.setString(6, plate);
            ps.setString(7, reason);
            ps.setBigDecimal(8, salary);
            ps.setLong(9, operatorId);
            return ps;
        }, key);
        return key.getKey().longValue();
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
        if (p.sequenceNo() != 1 || !"INITIAL".equals(p.sourceType()) || !"ASSIGNED".equals(p.status())) {
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

    public record ParticipantDetail(long id, int sequenceNo, long driverId, String driverName,
                                    String driverType, Long companyVehicleId, String outsourcedPlateNo,
                                    String source, String status, LocalDateTime assignedAt,
                                    LocalDateTime departedAt, LocalDateTime endedAt,
                                    String endReason, String transferReason, boolean current,
                                    BigDecimal salaryAmount, int version) {}

    public record SalaryHistory(BigDecimal beforeAmount, BigDecimal afterAmount, String reason,
                                long operatorId, LocalDateTime operationTime) {}
}
