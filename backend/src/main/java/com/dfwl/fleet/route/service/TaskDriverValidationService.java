package com.dfwl.fleet.route.service;

import com.dfwl.fleet.common.error.BusinessException;
import com.dfwl.fleet.common.error.ErrorCode;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Driver type is always loaded from the server's driver record. */
@Service
public class TaskDriverValidationService {
    private final JdbcTemplate jdbc;

    public TaskDriverValidationService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public DriverSnapshot require(long driverId, String outsourcedPlate) {
        // Serialize allocation and departure with administrator status changes.
        jdbc.queryForList("SELECT id FROM driver WHERE id=? AND deleted_at IS NULL FOR UPDATE", Long.class, driverId);
        List<DriverSnapshot> found = jdbc.query("""
                SELECT d.driver_type, d.status AS driver_status, v.id AS vehicle_id,
                    v.plate_no AS vehicle_plate, v.status AS vehicle_status, v.insurance_complete AS vehicle_insurance,
                    t.id AS trailer_id, t.plate_no AS trailer_plate, t.status AS trailer_status,
                    t.insurance_complete AS trailer_insurance
                FROM driver d
                LEFT JOIN driver_vehicle_current b ON b.driver_id = d.id
                LEFT JOIN vehicle v ON v.id = b.vehicle_id AND v.deleted_at IS NULL
                LEFT JOIN vehicle_trailer_current vt ON vt.vehicle_id = v.id
                LEFT JOIN trailer t ON t.id = vt.trailer_id AND t.deleted_at IS NULL
                WHERE d.id = ? AND d.deleted_at IS NULL
                """, (rs, row) -> {
            String type = rs.getString("driver_type");
            Long vehicleId = optionalLong(rs, "vehicle_id");
            Long trailerId = optionalLong(rs, "trailer_id");
            boolean valid = rs.getInt("driver_status") == 1;
            if ("INTERNAL".equals(type)) {
                valid &= vehicleId != null && rs.getInt("vehicle_status") == 1
                        && rs.getInt("vehicle_insurance") == 1;
                if (trailerId != null) valid &= rs.getInt("trailer_status") == 1
                        && rs.getInt("trailer_insurance") == 1;
            } else if ("OUTSOURCED".equals(type)) {
                valid &= outsourcedPlate != null && !outsourcedPlate.isBlank() && vehicleId == null;
            } else valid = false;
            return new DriverSnapshot(driverId, type, vehicleId, rs.getString("vehicle_plate"), trailerId,
                    rs.getString("trailer_plate"),
                    "OUTSOURCED".equals(type) && outsourcedPlate != null ? outsourcedPlate.trim() : null, valid);
        }, driverId);
        if (found.size() == 1 && "OUTSOURCED".equals(found.getFirst().driverType())
                && (outsourcedPlate == null || outsourcedPlate.isBlank()))
            throw new BusinessException("OUTSOURCED_PLATE_MISSING", "车辆信息未补充");
        if (found.size() != 1 || !found.getFirst().valid()) throw new BusinessException(ErrorCode.ROUTE_003);
        return found.getFirst();
    }

    public void lockAvailableCompanyVehicle(long vehicleId, long taskId) {
        jdbc.queryForList("SELECT id FROM vehicle WHERE id = ? FOR UPDATE", Long.class, vehicleId);
        Integer inUse = jdbc.queryForObject("""
                SELECT COUNT(*) FROM transport_task t
                JOIN transport_participant p ON p.id = t.current_participant_id
                WHERE t.id <> ? AND t.status IN ('IN_TRANSIT', 'TRANSFER_PENDING')
                  AND p.company_vehicle_id = ? AND p.is_current = 1
                """, Integer.class, taskId, vehicleId);
        if (inUse != null && inUse > 0) throw new BusinessException(ErrorCode.ROUTE_003);
    }

    private Long optionalLong(java.sql.ResultSet rs, String name) throws java.sql.SQLException {
        long value = rs.getLong(name);
        return rs.wasNull() ? null : value;
    }

    public record DriverSnapshot(long driverId, String driverType, Long vehicleId, String vehiclePlate,
                                 Long trailerId, String trailerPlate, String outsourcedPlate, boolean valid) {}
}
