package com.dfwl.fleet.route.repository;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/** Persistence and reference snapshots for the existing historical route import entry point. */
@Repository
public class RouteImportRepository {
    private final JdbcTemplate jdbcTemplate;
    public RouteImportRepository(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    public Optional<Long> findRouteByBusinessKey(String key) {
        return jdbcTemplate.query("""
                SELECT id FROM route_task
                WHERE business_unique_key = ? AND deleted_at IS NULL AND status <> 'CANCELLED'
                ORDER BY id LIMIT 1
                """, (rs, rowNum) -> rs.getLong("id"), key).stream().findFirst();
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

    public boolean customerExists(long id) {
        return exists("customer", id, "status = 1");
    }

    public boolean productExists(long id) {
        return exists("product", id, "status = 1");
    }

    public boolean vehicleExists(long id) {
        return exists("vehicle", id, "status = 1 AND deleted_at IS NULL");
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
}
