package com.dfwl.fleet.security;

import java.util.HashSet;
import java.util.Set;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Syncs definitions only. Ordinary role grants are never expanded by startup. */
@Component
public final class PermissionCatalogSynchronizer implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final FunctionRegistry registry;
    private final TransactionTemplate transactions;

    public PermissionCatalogSynchronizer(JdbcTemplate jdbc, FunctionRegistry registry, TransactionTemplate transactions) {
        this.jdbc = jdbc;
        this.registry = registry;
        this.transactions = transactions;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        transactions.executeWithoutResult(status -> synchronize());
    }

    private void synchronize() {
        Set<String> existing = new HashSet<>(jdbc.queryForList("SELECT permission_code FROM sys_permission", String.class));
        for (FunctionRegistry.Function function : registry.all()) {
            if (existing.contains(function.code())) {
                jdbc.update("UPDATE sys_permission SET permission_name=?,permission_type=?,status=1 WHERE permission_code=?",
                        function.name(), function.type(), function.code());
            } else {
                jdbc.update("INSERT INTO sys_permission(permission_code,permission_name,permission_type,status) VALUES(?,?,?,1)",
                        function.code(), function.name(), function.type());
            }
        }
        for (String code : existing) {
            if (registry.find(code) != null) continue;
            jdbc.update("DELETE FROM sys_role_permission WHERE permission_id IN (SELECT id FROM sys_permission WHERE permission_code=?)", code);
            jdbc.update("DELETE FROM sys_permission WHERE permission_code=?", code);
        }
        jdbc.update("""
                INSERT INTO sys_role_permission(role_id,permission_id)
                SELECT r.id,p.id FROM sys_role r CROSS JOIN sys_permission p
                WHERE r.role_code='ADMIN' AND NOT EXISTS(
                  SELECT 1 FROM sys_role_permission rp WHERE rp.role_id=r.id AND rp.permission_id=p.id)
                """);
        Long driverRoleId = jdbc.query("SELECT id FROM sys_role WHERE role_code='DRIVER'", rs ->
                rs.next() ? rs.getLong(1) : null);
        if (driverRoleId != null && existing.isEmpty()) {
            Integer grants = jdbc.queryForObject("SELECT COUNT(*) FROM sys_role_permission WHERE role_id=?", Integer.class, driverRoleId);
            if (grants != null && grants == 0) {
                for (String code : Set.of("PAGE_MINI_ROUTES", "BUTTON_MINI_ROUTES_DEPART", "BUTTON_MINI_ROUTES_UNLOAD",
                        "PAGE_MINI_SALARY", "PAGE_MINI_EXPENSE_APPLY", "BUTTON_MINI_EXPENSE_APPLY_SUBMIT",
                        "PAGE_MINI_APPROVALS", "PAGE_MINI_TIRE_REQUEST", "BUTTON_MINI_TIRE_REQUEST_SUBMIT")) {
                    jdbc.update("""
                            INSERT INTO sys_role_permission(role_id,permission_id)
                            SELECT ?,id FROM sys_permission WHERE permission_code=?
                            """, driverRoleId, code);
                }
            }
        }
    }
}
