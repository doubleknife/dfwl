package com.dfwl.fleet.system.repository;

import com.dfwl.fleet.common.api.PageResponse;
import com.dfwl.fleet.system.api.AuditLogResponse;
import com.dfwl.fleet.system.api.PermissionResponse;
import com.dfwl.fleet.system.api.RoleRequest;
import com.dfwl.fleet.system.api.RoleResponse;
import com.dfwl.fleet.system.api.UserRequest;
import com.dfwl.fleet.system.api.UserResponse;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class SystemManagementRepository {

    private final JdbcTemplate jdbcTemplate;

    public SystemManagementRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public PageResponse<UserResponse> listUsers(int pageNo, int pageSize) {
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_user", Long.class);
        List<UserResponse> records = jdbcTemplate.query("""
                SELECT u.*, r.role_code, r.role_name
                FROM sys_user u
                JOIN sys_role r ON r.id = u.role_id
                ORDER BY u.id DESC
                LIMIT ? OFFSET ?
                """, (rs, rowNum) -> new UserResponse(
                rs.getLong("id"),
                rs.getString("phone"),
                rs.getLong("role_id"),
                rs.getString("role_code"),
                rs.getString("role_name"),
                rs.getInt("status"),
                rs.getTimestamp("last_login_at") == null ? null : rs.getTimestamp("last_login_at").toLocalDateTime(),
                readLong(rs, "created_by"),
                rs.getTimestamp("created_at").toLocalDateTime(),
                readLong(rs, "updated_by"),
                rs.getTimestamp("updated_at").toLocalDateTime()), pageSize, Math.max(pageNo - 1, 0) * pageSize);
        return new PageResponse<>(pageNo, pageSize, total == null ? 0 : total, records);
    }

    public Optional<UserResponse> findUser(long id) {
        return jdbcTemplate.query("""
                SELECT u.*, r.role_code, r.role_name
                FROM sys_user u
                JOIN sys_role r ON r.id = u.role_id
                WHERE u.id = ?
                """, (rs, rowNum) -> new UserResponse(
                rs.getLong("id"),
                rs.getString("phone"),
                rs.getLong("role_id"),
                rs.getString("role_code"),
                rs.getString("role_name"),
                rs.getInt("status"),
                rs.getTimestamp("last_login_at") == null ? null : rs.getTimestamp("last_login_at").toLocalDateTime(),
                readLong(rs, "created_by"),
                rs.getTimestamp("created_at").toLocalDateTime(),
                readLong(rs, "updated_by"),
                rs.getTimestamp("updated_at").toLocalDateTime()), id).stream().findFirst();
    }

    public boolean roleExists(long roleId) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_role WHERE id = ?", Integer.class, roleId);
        return count != null && count > 0;
    }

    public long createUser(UserRequest request, String passwordHash, long operatorId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO sys_user (phone, password_hash, role_id, status, created_by)
                    VALUES (?, ?, ?, ?, ?)
                    """, new String[]{"id"});
            ps.setString(1, request.phone());
            ps.setString(2, passwordHash);
            ps.setLong(3, request.roleId());
            ps.setInt(4, request.status());
            ps.setLong(5, operatorId);
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    public int updateUser(long id, UserRequest request, long operatorId) {
        return jdbcTemplate.update("""
                UPDATE sys_user
                SET phone = ?, role_id = ?, status = ?, updated_by = ?, version = version + 1, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, request.phone(), request.roleId(), request.status(), operatorId, id);
    }

    public PageResponse<RoleResponse> listRoles(int pageNo, int pageSize) {
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_role", Long.class);
        List<RoleResponse> records = jdbcTemplate.query("""
                SELECT *
                FROM sys_role
                ORDER BY id DESC
                LIMIT ? OFFSET ?
                """, (rs, rowNum) -> new RoleResponse(
                rs.getLong("id"),
                rs.getString("role_code"),
                rs.getString("role_name"),
                rs.getInt("is_system_fixed") == 1,
                rs.getInt("status"),
                List.of()), pageSize, Math.max(pageNo - 1, 0) * pageSize);
        return new PageResponse<>(pageNo, pageSize, total == null ? 0 : total, records);
    }

    public Optional<RoleResponse> findRole(long id) {
        return jdbcTemplate.query("""
                SELECT *
                FROM sys_role
                WHERE id = ?
                """, (rs, rowNum) -> new RoleResponse(
                rs.getLong("id"),
                rs.getString("role_code"),
                rs.getString("role_name"),
                rs.getInt("is_system_fixed") == 1,
                rs.getInt("status"),
                rolePermissions(rs.getLong("id"))), id).stream().findFirst();
    }

    public long createRole(RoleRequest request) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO sys_role (role_code, role_name, status)
                    VALUES (?, ?, ?)
                    """, new String[]{"id"});
            ps.setString(1, request.roleCode());
            ps.setString(2, request.roleName());
            ps.setInt(3, request.status());
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    public int updateRole(long id, RoleRequest request) {
        return jdbcTemplate.update("""
                UPDATE sys_role
                SET role_code = ?, role_name = ?, status = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, request.roleCode(), request.roleName(), request.status(), id);
    }

    public int updateRoleStatus(long id, int status) {
        return jdbcTemplate.update("UPDATE sys_role SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", status, id);
    }

    public List<PermissionResponse> listPermissions(String permissionType) {
        if (permissionType == null || permissionType.isBlank()) {
            return jdbcTemplate.query("""
                    SELECT *
                    FROM sys_permission
                    ORDER BY permission_type, permission_code
                    """, this::mapPermission);
        }
        return jdbcTemplate.query("""
                SELECT *
                FROM sys_permission
                WHERE permission_type = ?
                ORDER BY permission_code
                """, this::mapPermission, permissionType);
    }

    public List<PermissionResponse> rolePermissions(long roleId) {
        return jdbcTemplate.query("""
                SELECT p.*
                FROM sys_permission p
                JOIN sys_role_permission rp ON rp.permission_id = p.id
                WHERE rp.role_id = ?
                ORDER BY p.permission_type, p.permission_code
                """, this::mapPermission, roleId);
    }

    public List<PermissionResponse> findPermissionsByCodes(List<String> permissionCodes) {
        if (permissionCodes == null || permissionCodes.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", permissionCodes.stream().map(code -> "?").toList());
        return jdbcTemplate.query("""
                SELECT *
                FROM sys_permission
                WHERE permission_code IN (%s)
                """.formatted(placeholders), this::mapPermission, permissionCodes.toArray());
    }

    public void replaceRolePermissions(long roleId, List<Long> permissionIds) {
        jdbcTemplate.update("DELETE FROM sys_role_permission WHERE role_id = ?", roleId);
        for (Long permissionId : permissionIds) {
            jdbcTemplate.update("INSERT INTO sys_role_permission (role_id, permission_id) VALUES (?, ?)", roleId, permissionId);
        }
    }

    public PageResponse<AuditLogResponse> listAuditLogs(String module, String businessType, Long operatorId,
                                                       String startTime, String endTime, int pageNo, int pageSize) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (module != null && !module.isBlank()) {
            where.append(" AND module = ?");
            args.add(module);
        }
        if (businessType != null && !businessType.isBlank()) {
            where.append(" AND business_type = ?");
            args.add(businessType);
        }
        if (operatorId != null) {
            where.append(" AND operator_id = ?");
            args.add(operatorId);
        }
        if (startTime != null && !startTime.isBlank()) {
            where.append(" AND operation_time >= ?");
            args.add(startTime);
        }
        if (endTime != null && !endTime.isBlank()) {
            where.append(" AND operation_time <= ?");
            args.add(endTime);
        }
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_log" + where, Long.class, args.toArray());
        List<Object> queryArgs = new ArrayList<>(args);
        queryArgs.add(pageSize);
        queryArgs.add(Math.max(pageNo - 1, 0) * pageSize);
        List<AuditLogResponse> records = jdbcTemplate.query("""
                SELECT *
                FROM audit_log
                %s
                ORDER BY operation_time DESC, id DESC
                LIMIT ? OFFSET ?
                """.formatted(where), (rs, rowNum) -> new AuditLogResponse(
                rs.getLong("id"),
                rs.getString("module"),
                rs.getString("business_type"),
                readLong(rs, "business_id"),
                rs.getString("operation_type"),
                rs.getString("before_json"),
                rs.getString("after_json"),
                rs.getLong("operator_id"),
                rs.getTimestamp("operation_time").toLocalDateTime(),
                rs.getString("reason"),
                rs.getString("ip"),
                rs.getString("terminal")), queryArgs.toArray());
        return new PageResponse<>(pageNo, pageSize, total == null ? 0 : total, records);
    }

    private PermissionResponse mapPermission(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new PermissionResponse(
                rs.getLong("id"),
                rs.getString("permission_code"),
                rs.getString("permission_name"),
                rs.getString("permission_type"),
                rs.getInt("status"));
    }

    private Long readLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}
