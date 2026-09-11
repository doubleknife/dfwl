package com.dfwl.fleet.auth.repository;

import com.dfwl.fleet.auth.domain.UserAccount;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserAccountRepository {

    private final JdbcTemplate jdbcTemplate;

    public UserAccountRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<UserAccount> findByPhone(String phone) {
        return findOne("""
                SELECT u.id, u.phone, u.password_hash, u.role_id, u.status, u.last_login_at,
                       r.role_code, r.role_name
                FROM sys_user u
                JOIN sys_role r ON r.id = u.role_id
                WHERE u.phone = ?
                """, phone);
    }

    public Optional<UserAccount> findById(long id) {
        return findOne("""
                SELECT u.id, u.phone, u.password_hash, u.role_id, u.status, u.last_login_at,
                       r.role_code, r.role_name
                FROM sys_user u
                JOIN sys_role r ON r.id = u.role_id
                WHERE u.id = ?
                """, id);
    }

    public void updateLastLoginAt(long id, LocalDateTime loginAt) {
        jdbcTemplate.update("UPDATE sys_user SET last_login_at = ?, version = version + 1 WHERE id = ?",
                Timestamp.valueOf(loginAt), id);
    }

    public boolean updatePassword(long id, String passwordHash) {
        return jdbcTemplate.update("""
                UPDATE sys_user
                SET password_hash = ?, version = version + 1
                WHERE id = ?
                """, passwordHash, id) == 1;
    }

    public boolean updateStatus(long id, int status) {
        return jdbcTemplate.update("""
                UPDATE sys_user
                SET status = ?, version = version + 1
                WHERE id = ?
                """, status, id) == 1;
    }

    public Optional<DriverIdentity> findDriverIdentity(long userId, String phone) {
        return jdbcTemplate.query("""
                SELECT d.id AS driver_id, d.driver_type, d.name AS driver_name,
                       dvc.vehicle_id AS current_vehicle_id, v.plate_no AS current_plate_no
                FROM driver d
                LEFT JOIN driver_vehicle_current dvc ON dvc.driver_id = d.id
                LEFT JOIN vehicle v ON v.id = dvc.vehicle_id AND v.deleted_at IS NULL
                WHERE d.deleted_at IS NULL AND d.status = 1
                  AND (d.user_id = ? OR (d.user_id IS NULL AND d.phone = ?))
                ORDER BY CASE WHEN d.user_id = ? THEN 0 ELSE 1 END
                LIMIT 1
                """, (rs, rowNum) -> new DriverIdentity(
                rs.getLong("driver_id"),
                rs.getString("driver_type"),
                rs.getString("driver_name"),
                readLong(rs, "current_vehicle_id"),
                rs.getString("current_plate_no")), userId, phone, userId).stream().findFirst();
    }

    private Optional<UserAccount> findOne(String sql, Object argument) {
        List<UserAccount> users = jdbcTemplate.query(sql, this::mapUser, argument);
        return users.stream().findFirst();
    }

    private UserAccount mapUser(ResultSet rs, int rowNum) throws SQLException {
        long userId = rs.getLong("id");
        return new UserAccount(
                userId,
                rs.getString("phone"),
                rs.getString("password_hash"),
                rs.getLong("role_id"),
                rs.getString("role_code"),
                rs.getString("role_name"),
                rs.getInt("status"),
                readLocalDateTime(rs, "last_login_at"),
                findPermissionCodes(userId));
    }

    private Set<String> findPermissionCodes(long userId) {
        List<String> codes = jdbcTemplate.queryForList("""
                SELECT p.permission_code
                FROM sys_user u
                JOIN sys_role_permission rp ON rp.role_id = u.role_id
                JOIN sys_permission p ON p.id = rp.permission_id
                WHERE u.id = ? AND p.status = 1
                ORDER BY p.permission_code
                """, String.class, userId);
        return new LinkedHashSet<>(codes);
    }

    private LocalDateTime readLocalDateTime(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private Long readLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    public record DriverIdentity(Long driverId, String driverType, String driverName, Long currentVehicleId, String currentPlateNo) {
    }
}
