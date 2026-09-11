package com.dfwl.fleet.route.repository;

import com.dfwl.fleet.common.api.PageResponse;
import com.dfwl.fleet.route.api.RouteRequest;
import com.dfwl.fleet.route.api.RouteResponse;
import com.dfwl.fleet.route.api.RouteWeightVersionResponse;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class RouteRepository {

    private final JdbcTemplate jdbcTemplate;

    public RouteRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public PageResponse<RouteResponse> list(int pageNo, int pageSize) {
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM route_task WHERE deleted_at IS NULL", Long.class);
        List<RouteResponse> records = jdbcTemplate.query("""
                SELECT r.*, w.gross_weight, w.tare_weight, w.net_weight,
                       w.load_standard_threshold, w.load_standard_met
                FROM route_task r
                LEFT JOIN route_weight_version w ON w.id = r.effective_weight_version_id
                WHERE r.deleted_at IS NULL
                ORDER BY r.id DESC
                LIMIT ? OFFSET ?
                """, this::mapRoute, pageSize, Math.max(pageNo - 1, 0) * pageSize);
        return new PageResponse<>(pageNo, pageSize, total == null ? 0 : total, records);
    }

    public PageResponse<RouteResponse> listForDriver(long driverId, int pageNo, int pageSize) {
        Long total = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM route_task
                WHERE deleted_at IS NULL
                  AND (assigned_driver_id = ? OR departure_driver_id = ?)
                """, Long.class, driverId, driverId);
        List<RouteResponse> records = jdbcTemplate.query("""
                SELECT r.*, w.gross_weight, w.tare_weight, w.net_weight,
                       w.load_standard_threshold, w.load_standard_met
                FROM route_task r
                LEFT JOIN route_weight_version w ON w.id = r.effective_weight_version_id
                WHERE r.deleted_at IS NULL
                  AND (r.assigned_driver_id = ? OR r.departure_driver_id = ?)
                ORDER BY r.id DESC
                LIMIT ? OFFSET ?
                """, this::mapRoute, driverId, driverId, pageSize, Math.max(pageNo - 1, 0) * pageSize);
        return new PageResponse<>(pageNo, pageSize, total == null ? 0 : total, records);
    }

    public Optional<RouteResponse> find(long id) {
        return jdbcTemplate.query("""
                SELECT r.*, w.gross_weight, w.tare_weight, w.net_weight,
                       w.load_standard_threshold, w.load_standard_met
                FROM route_task r
                LEFT JOIN route_weight_version w ON w.id = r.effective_weight_version_id
                WHERE r.id = ? AND r.deleted_at IS NULL
                """, this::mapRoute, id).stream().findFirst();
    }

    public long create(String routeNo, String businessKey, RouteRequest request, long operatorId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO route_task (route_no, external_route_no, trip_sequence, business_unique_key,
                                            business_date, customer_id, product_id, direction, loading_place,
                                            unloading_place, tax_unit_price, assigned_driver_id, created_by)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, new String[]{"id"});
            ps.setString(1, routeNo);
            ps.setString(2, request.externalRouteNo());
            ps.setString(3, request.tripSequence());
            ps.setString(4, businessKey);
            ps.setObject(5, request.businessDate());
            ps.setLong(6, request.customerId());
            ps.setLong(7, request.productId());
            ps.setString(8, request.direction());
            ps.setString(9, request.loadingPlace());
            ps.setString(10, request.unloadingPlace());
            ps.setBigDecimal(11, request.taxUnitPrice());
            if (request.assignedDriverId() == null) {
                ps.setObject(12, null);
            } else {
                ps.setLong(12, request.assignedDriverId());
            }
            ps.setLong(13, operatorId);
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    public boolean updateBeforeDeparture(long id, RouteRequest request, String businessKey, long operatorId) {
        return jdbcTemplate.update("""
                UPDATE route_task
                SET external_route_no = ?, trip_sequence = ?, business_unique_key = ?, business_date = ?,
                    customer_id = ?, product_id = ?, direction = ?, loading_place = ?, unloading_place = ?,
                    tax_unit_price = ?, assigned_driver_id = ?, updated_by = ?, version = version + 1
                WHERE id = ? AND deleted_at IS NULL AND status IN ('UNPUBLISHED', 'PUBLISHED')
                """, request.externalRouteNo(), request.tripSequence(), businessKey, request.businessDate(),
                request.customerId(), request.productId(), request.direction(), request.loadingPlace(),
                request.unloadingPlace(), request.taxUnitPrice(), request.assignedDriverId(), operatorId, id) == 1;
    }

    public boolean duplicateBusinessKeyExists(String businessKey, Long excludeRouteId) {
        String sql = excludeRouteId == null
                ? "SELECT COUNT(*) FROM route_task WHERE business_unique_key = ? AND deleted_at IS NULL AND status <> 'CANCELLED'"
                : "SELECT COUNT(*) FROM route_task WHERE business_unique_key = ? AND id <> ? AND deleted_at IS NULL AND status <> 'CANCELLED'";
        Integer count = excludeRouteId == null
                ? jdbcTemplate.queryForObject(sql, Integer.class, businessKey)
                : jdbcTemplate.queryForObject(sql, Integer.class, businessKey, excludeRouteId);
        return count != null && count > 0;
    }

    public boolean existsActiveCustomer(long customerId) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM customer WHERE id = ? AND status = 1", Integer.class, customerId);
        return count != null && count > 0;
    }

    public boolean existsActiveProduct(long productId) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM product WHERE id = ? AND status = 1", Integer.class, productId);
        return count != null && count > 0;
    }

    public Optional<RouteBindingSnapshot> findBindingSnapshot(long driverId) {
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
                """, (rs, rowNum) -> new RouteBindingSnapshot(
                rs.getLong("driver_id"),
                rs.getInt("driver_status"),
                readLong(rs, "vehicle_id"),
                readInteger(rs, "vehicle_status"),
                readInteger(rs, "insurance_complete"),
                readLong(rs, "trailer_id"),
                readInteger(rs, "trailer_status"),
                readInteger(rs, "trailer_insurance_complete")), driverId).stream().findFirst();
    }

    public void lockVehicle(long vehicleId) {
        jdbcTemplate.queryForList("SELECT id FROM vehicle WHERE id = ? FOR UPDATE", Long.class, vehicleId);
    }

    public void lockRoute(long routeId) {
        jdbcTemplate.queryForList("SELECT id FROM route_task WHERE id = ? FOR UPDATE", Long.class, routeId);
    }

    public boolean updateStatus(long id, String fromStatus, String toStatus, String operationType, long operatorId, String reason) {
        int updated = jdbcTemplate.update("""
                UPDATE route_task
                SET status = ?, updated_by = ?, version = version + 1
                WHERE id = ? AND status = ? AND deleted_at IS NULL
                """, toStatus, operatorId, id, fromStatus);
        if (updated != 1) {
            return false;
        }
        insertStatusHistory(id, fromStatus, toStatus, operationType, operatorId, reason);
        return true;
    }

    public boolean publish(long id, String fromStatus, long operatorId) {
        return updateStatus(id, fromStatus, "PUBLISHED", "PUBLISH", operatorId, null);
    }

    public boolean depart(long id, String fromStatus, RouteBindingSnapshot snapshot, long operatorId, LocalDateTime now) {
        int updated = jdbcTemplate.update("""
                UPDATE route_task
                SET status = 'IN_TRANSIT', departure_driver_id = ?, departure_vehicle_id = ?,
                    departure_trailer_id = ?, departure_time = ?, updated_by = ?, version = version + 1
                WHERE id = ?
                  AND status = 'PUBLISHED'
                  AND deleted_at IS NULL
                  AND NOT EXISTS (
                    SELECT 1 FROM (
                      SELECT id FROM route_task
                      WHERE id <> ?
                        AND status = 'IN_TRANSIT'
                        AND departure_vehicle_id = ?
                        AND deleted_at IS NULL
                    ) busy_vehicle
                  )
                """, snapshot.driverId(), snapshot.vehicleId(), snapshot.trailerId(), Timestamp.valueOf(now),
                operatorId, id, id, snapshot.vehicleId());
        if (updated != 1) {
            return false;
        }
        insertStatusHistory(id, fromStatus, "IN_TRANSIT", "DEPART", operatorId, null);
        return true;
    }

    public long insertWeightVersion(long routeId, int versionNo, BigDecimal grossWeight,
                                    BigDecimal tareWeight, BigDecimal netWeight,
                                    BigDecimal loadStandardThreshold, boolean loadStandardMet,
                                    String sourceType, long operatorId, String reason, List<Long> attachmentIds) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO route_weight_version (route_id, version_no, gross_weight, tare_weight, net_weight,
                                                      load_standard_threshold, load_standard_met, source_type,
                                                      operator_id, reason, attachment_ids)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, new String[]{"id"});
            ps.setLong(1, routeId);
            ps.setInt(2, versionNo);
            ps.setBigDecimal(3, grossWeight);
            ps.setBigDecimal(4, tareWeight);
            ps.setBigDecimal(5, netWeight);
            ps.setBigDecimal(6, loadStandardThreshold);
            ps.setInt(7, loadStandardMet ? 1 : 0);
            ps.setString(8, sourceType);
            ps.setLong(9, operatorId);
            ps.setString(10, reason);
            ps.setString(11, joinAttachmentIds(attachmentIds));
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    public List<RouteWeightVersionResponse> weightVersions(long routeId) {
        return jdbcTemplate.query("""
                SELECT id, route_id, version_no, gross_weight, tare_weight, net_weight,
                       load_standard_threshold, load_standard_met, source_type, operator_id,
                       operation_time, reason, attachment_ids
                FROM route_weight_version
                WHERE route_id = ?
                ORDER BY version_no ASC
                """, (rs, rowNum) -> new RouteWeightVersionResponse(
                rs.getLong("id"),
                rs.getLong("route_id"),
                rs.getInt("version_no"),
                rs.getBigDecimal("gross_weight"),
                rs.getBigDecimal("tare_weight"),
                rs.getBigDecimal("net_weight"),
                rs.getBigDecimal("load_standard_threshold"),
                readBoolean(rs, "load_standard_met"),
                rs.getString("source_type"),
                rs.getLong("operator_id"),
                rs.getTimestamp("operation_time").toLocalDateTime(),
                rs.getString("reason"),
                splitAttachmentIds(rs.getString("attachment_ids"))), routeId);
    }

    public int nextWeightVersionNo(long routeId) {
        Integer max = jdbcTemplate.queryForObject("SELECT MAX(version_no) FROM route_weight_version WHERE route_id = ?", Integer.class, routeId);
        return max == null ? 1 : max + 1;
    }

    public boolean unload(long routeId, String fromStatus, long weightVersionId, long operatorId, LocalDateTime now) {
        int updated = jdbcTemplate.update("""
                UPDATE route_task
                SET status = 'COMPLETED', unload_time = ?, effective_weight_version_id = ?,
                    updated_by = ?, version = version + 1
                WHERE id = ? AND status = 'IN_TRANSIT' AND deleted_at IS NULL
                """, Timestamp.valueOf(now), weightVersionId, operatorId, routeId);
        if (updated != 1) {
            return false;
        }
        insertStatusHistory(routeId, fromStatus, "COMPLETED", "UNLOAD", operatorId, null);
        return true;
    }

    public void updateEffectiveWeight(long routeId, long weightVersionId, long operatorId) {
        jdbcTemplate.update("""
                UPDATE route_task
                SET effective_weight_version_id = ?, updated_by = ?, version = version + 1
                WHERE id = ?
                """, weightVersionId, operatorId, routeId);
    }

    public Optional<LoadStandardConfig> loadStandardForVehicle(long vehicleId) {
        return jdbcTemplate.query("""
                SELECT max_load, load_standard_type, load_standard_percent, load_standard_min_load
                FROM vehicle
                WHERE id = ? AND deleted_at IS NULL
                """, (rs, rowNum) -> new LoadStandardConfig(
                rs.getBigDecimal("max_load"),
                rs.getString("load_standard_type"),
                rs.getBigDecimal("load_standard_percent"),
                rs.getBigDecimal("load_standard_min_load")), vehicleId).stream().findFirst();
    }

    public boolean weightAdjustAttachmentsBelongToRoute(long routeId, List<Long> attachmentIds) {
        if (attachmentIds == null || attachmentIds.isEmpty()) {
            return true;
        }
        String placeholders = attachmentIds.stream().map(ignored -> "?").collect(Collectors.joining(","));
        List<Object> args = new java.util.ArrayList<>();
        args.add(routeId);
        args.addAll(attachmentIds);
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM file_attachment
                WHERE owner_type = 'ROUTE'
                  AND owner_id = ?
                  AND purpose = 'WEIGHT_ADJUST'
                  AND id IN (%s)
                """.formatted(placeholders), Integer.class, args.toArray());
        return count != null && count == attachmentIds.size();
    }

    public boolean logicDelete(long routeId, long operatorId) {
        return jdbcTemplate.update("""
                UPDATE route_task
                SET deleted_at = CURRENT_TIMESTAMP, updated_by = ?, version = version + 1
                WHERE id = ? AND deleted_at IS NULL AND status IN ('UNPUBLISHED', 'PUBLISHED', 'CANCELLED')
                """, operatorId, routeId) == 1;
    }

    private void insertStatusHistory(long routeId, String fromStatus, String toStatus, String operationType,
                                     long operatorId, String reason) {
        jdbcTemplate.update("""
                INSERT INTO route_status_history (route_id, from_status, to_status, operation_type, operator_id, reason)
                VALUES (?, ?, ?, ?, ?, ?)
                """, routeId, fromStatus, toStatus, operationType, operatorId, reason);
    }

    private RouteResponse mapRoute(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new RouteResponse(
                rs.getLong("id"),
                rs.getString("route_no"),
                rs.getString("external_route_no"),
                rs.getString("trip_sequence"),
                rs.getDate("business_date").toLocalDate(),
                rs.getLong("customer_id"),
                rs.getLong("product_id"),
                rs.getString("direction"),
                rs.getString("loading_place"),
                rs.getString("unloading_place"),
                rs.getBigDecimal("tax_unit_price"),
                readLong(rs, "assigned_driver_id"),
                rs.getString("status"),
                readLong(rs, "departure_driver_id"),
                readLong(rs, "departure_vehicle_id"),
                readLong(rs, "departure_trailer_id"),
                rs.getTimestamp("departure_time") == null ? null : rs.getTimestamp("departure_time").toLocalDateTime(),
                rs.getTimestamp("unload_time") == null ? null : rs.getTimestamp("unload_time").toLocalDateTime(),
                rs.getBigDecimal("gross_weight"),
                rs.getBigDecimal("tare_weight"),
                rs.getBigDecimal("net_weight"),
                rs.getBigDecimal("load_standard_threshold"),
                readBoolean(rs, "load_standard_met"));
    }

    private Long readLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private Integer readInteger(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private Boolean readBoolean(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value == 1;
    }

    private String joinAttachmentIds(List<Long> attachmentIds) {
        if (attachmentIds == null || attachmentIds.isEmpty()) {
            return null;
        }
        return attachmentIds.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private List<Long> splitAttachmentIds(String value) {
        if (value == null || value.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(value.split(","))
                .filter(part -> !part.isBlank())
                .map(Long::valueOf)
                .toList();
    }

    public record RouteBindingSnapshot(
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

    public record LoadStandardConfig(
            BigDecimal maxLoad,
            String loadStandardType,
            BigDecimal loadStandardPercent,
            BigDecimal loadStandardMinLoad
    ) {
    }
}
