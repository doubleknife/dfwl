package com.dfwl.fleet.security;

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

    public void denyAllDrivers(AuthenticatedUser user) {
        if (isDriverUser(user)) {
            throw new AccessDeniedException("driver is not allowed");
        }
    }

    private Long readLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}
