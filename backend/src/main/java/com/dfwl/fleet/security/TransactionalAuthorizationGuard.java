package com.dfwl.fleet.security;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Rechecks critical writes after the service transaction begins. The role lock orders writes with grant changes. */
@Component
public final class TransactionalAuthorizationGuard {
    private final JdbcTemplate jdbc;
    private final FunctionRegistry registry;

    public TransactionalAuthorizationGuard(JdbcTemplate jdbc, FunctionRegistry registry) {
        this.jdbc = jdbc;
        this.registry = registry;
    }

    public void require(long actorId, String... anyOf) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        // Direct internal service invocations have no bearer request. HTTP writes are checked by FunctionAccessGuard.
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser current)) return;
        if (current.id() != actorId || !TransactionSynchronizationManager.isActualTransactionActive())
            throw new AccessDeniedException("当前操作身份或事务无效");

        var state = jdbc.query("""
                SELECT u.role_id,u.status AS user_status,r.status AS role_status
                FROM sys_user u JOIN sys_role r ON r.id=u.role_id
                WHERE u.id=? FOR UPDATE
                """, (rs, row) -> new long[]{rs.getLong("role_id"), rs.getInt("user_status"),
                rs.getInt("role_status")}, actorId).stream().findFirst().orElse(null);
        if (state == null || state[1] != 1 || state[2] != 1)
            throw new AccessDeniedException("当前账号已停用");
        Set<String> needed = new HashSet<>();
        for (String code : anyOf) {
            FunctionRegistry.Function function = registry.find(code);
            if (function == null || !"BUTTON".equals(function.type()))
                throw new AccessDeniedException("操作未注册");
            needed.add(code);
            needed.add(function.parentCode());
        }
        String placeholders = String.join(",", needed.stream().map(ignored -> "?").toList());
        Object[] args = new Object[needed.size() + 1];
        args[0] = state[0];
        int index = 1;
        for (String code : needed) args[index++] = code;
        Set<String> granted = new HashSet<>(jdbc.queryForList("""
                SELECT p.permission_code FROM sys_role_permission rp
                JOIN sys_permission p ON p.id=rp.permission_id
                WHERE rp.role_id=? AND p.status=1 AND p.permission_code IN (%s)
                """.formatted(placeholders), String.class, args));
        if (Arrays.stream(anyOf).noneMatch(code -> granted.contains(code)
                && granted.contains(registry.find(code).parentCode())))
            throw new AccessDeniedException("当前账号没有此操作权限");
    }
}
