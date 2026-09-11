package com.dfwl.fleet.security;

import com.dfwl.fleet.common.domain.RouteStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {

    private static final String DRIVER_ROLE = "DRIVER";

    private final JdbcTemplate jdbcTemplate;

    public CurrentUserService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean isDriverUser(AuthenticatedUser user) {
        return user != null && user.roleCode() != null && DRIVER_ROLE.equalsIgnoreCase(user.roleCode());
    }

    public CurrentDriverContext requireDriver(AuthenticatedUser user) {
        if (!isDriverUser(user)) {
            throw new AccessDeniedException("driver role required");
        }
        return findDriver(user).orElseThrow(() -> new AccessDeniedException("driver binding required"));
    }

    public Optional<CurrentDriverContext> findDriver(AuthenticatedUser user) {
        if (user == null) {
            return Optional.empty();
        }
        List<CurrentDriverContext> byUserId = jdbcTemplate.query("""
                SELECT d.id, d.driver_type, dvc.vehicle_id AS current_vehicle_id
                FROM driver d
                LEFT JOIN driver_vehicle_current dvc ON dvc.driver_id = d.id
                WHERE d.user_id = ? AND d.deleted_at IS NULL AND d.status = 1
                """, (rs, rowNum) -> new CurrentDriverContext(
                rs.getLong("id"),
                rs.getString("driver_type"),
                readLong(rs, "current_vehicle_id")), user.id());
        if (!byUserId.isEmpty()) {
            return Optional.of(byUserId.get(0));
        }
        return jdbcTemplate.query("""
                SELECT d.id, d.driver_type, dvc.vehicle_id AS current_vehicle_id
                FROM driver d
                LEFT JOIN driver_vehicle_current dvc ON dvc.driver_id = d.id
                WHERE d.phone = ? AND d.deleted_at IS NULL AND d.status = 1
                """, (rs, rowNum) -> new CurrentDriverContext(
                rs.getLong("id"),
                rs.getString("driver_type"),
                readLong(rs, "current_vehicle_id")), user.phone()).stream().findFirst();
    }

    public void denyOutsourcedDriver(AuthenticatedUser user) {
        if (isDriverUser(user) && requireDriver(user).outsourced()) {
            throw new AccessDeniedException("outsourced driver is not allowed");
        }
    }

    public void denyAllDrivers(AuthenticatedUser user) {
        if (isDriverUser(user)) {
            throw new AccessDeniedException("driver is not allowed");
        }
    }

    public void ensureDriverCanViewRoute(AuthenticatedUser user, long routeId) {
        if (!isDriverUser(user)) {
            return;
        }
        CurrentDriverContext driver = requireDriver(user);
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM route_task
                WHERE id = ? AND deleted_at IS NULL
                  AND (
                    assigned_driver_id = ?
                    OR departure_driver_id = ?
                  )
                """, Integer.class, routeId, driver.driverId(), driver.driverId());
        if (count == null || count == 0) {
            throw new AccessDeniedException("route is outside current driver scope");
        }
    }

    public void ensureDriverCanDepart(AuthenticatedUser user, long routeId) {
        if (!isDriverUser(user)) {
            return;
        }
        CurrentDriverContext driver = requireDriver(user);
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM route_task
                WHERE id = ? AND deleted_at IS NULL
                  AND status = ?
                  AND assigned_driver_id = ?
                """, Integer.class, routeId, RouteStatus.PUBLISHED.name(), driver.driverId());
        if (count == null || count == 0) {
            throw new AccessDeniedException("route is outside current driver departure scope");
        }
    }

    public void ensureDriverCanUnload(AuthenticatedUser user, long routeId) {
        if (!isDriverUser(user)) {
            return;
        }
        CurrentDriverContext driver = requireDriver(user);
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM route_task
                WHERE id = ? AND deleted_at IS NULL
                  AND status = ?
                  AND departure_driver_id = ?
                """, Integer.class, routeId, RouteStatus.IN_TRANSIT.name(), driver.driverId());
        if (count == null || count == 0) {
            throw new AccessDeniedException("route is outside current driver unload scope");
        }
    }

    public void ensureDriverCanViewVehicle(AuthenticatedUser user, long vehicleId) {
        if (!isDriverUser(user)) {
            return;
        }
        CurrentDriverContext driver = requireDriver(user);
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM vehicle v
                WHERE v.id = ? AND v.deleted_at IS NULL
                  AND (
                    EXISTS (
                      SELECT 1 FROM driver_vehicle_current dvc
                      WHERE dvc.driver_id = ? AND dvc.vehicle_id = v.id
                    )
                    OR EXISTS (
                      SELECT 1 FROM route_task r
                      WHERE r.departure_driver_id = ? AND r.departure_vehicle_id = v.id AND r.deleted_at IS NULL
                    )
                  )
                """, Integer.class, vehicleId, driver.driverId(), driver.driverId());
        if (count == null || count == 0) {
            throw new AccessDeniedException("vehicle is outside current driver scope");
        }
    }

    public void ensureDriverCanUseTireRequest(AuthenticatedUser user, long requestDriverId, long vehicleId) {
        if (!isDriverUser(user)) {
            return;
        }
        CurrentDriverContext driver = requireDriver(user);
        if (driver.outsourced()) {
            throw new AccessDeniedException("outsourced driver cannot request tires");
        }
        if (driver.driverId() != requestDriverId || driver.currentVehicleId() == null || driver.currentVehicleId() != vehicleId) {
            throw new AccessDeniedException("tire request is outside current driver scope");
        }
    }

    private Long readLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}
