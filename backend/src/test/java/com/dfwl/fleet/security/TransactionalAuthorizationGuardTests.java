package com.dfwl.fleet.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:transaction-auth;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
        "spring.datasource.password=", "spring.flyway.enabled=false", "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-auth-test.sql"
})
class TransactionalAuthorizationGuardTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired TransactionalAuthorizationGuard guard;

    @BeforeEach
    void grants() {
        jdbc.update("DELETE FROM sys_role_permission");
        jdbc.update("DELETE FROM sys_permission");
        jdbc.update("DELETE FROM auth_session");
        jdbc.update("DELETE FROM sys_user");
        long role = jdbc.queryForObject("SELECT id FROM sys_role WHERE role_code='ADMIN'", Long.class);
        jdbc.update("INSERT INTO sys_user(id,phone,password_hash,role_id,status) VALUES(9001,'13990009001','test-only',?,1)", role);
        jdbc.update("INSERT INTO sys_permission(id,permission_code,permission_name,permission_type) VALUES(1,'PAGE_SETTLEMENTS','月结','PAGE')");
        jdbc.update("INSERT INTO sys_permission(id,permission_code,permission_name,permission_type) VALUES(2,'BUTTON_SETTLEMENTS_GENERATE','生成版本','BUTTON')");
        jdbc.update("INSERT INTO sys_role_permission(role_id,permission_id) VALUES(?,1),(?,2)", role, role);
        AuthenticatedUser stale = new AuthenticatedUser(9001L, "13990009001", role, "ADMIN", "管理员",
                Set.of("PAGE_SETTLEMENTS", "BUTTON_SETTLEMENTS_GENERATE"));
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(stale, null, java.util.List.of()));
    }

    @AfterEach
    void clearSecurityContext() { SecurityContextHolder.clearContext(); }

    @Test
    void transactionRejectsRevokedButtonEvenWhenRequestPrincipalStillHasIt() {
        TransactionTemplate template = new TransactionTemplate(transactions);
        template.execute(status -> { guard.require(9001, "BUTTON_SETTLEMENTS_GENERATE"); return null; });
        jdbc.update("DELETE FROM sys_role_permission WHERE permission_id=2");
        assertThatThrownBy(() -> template.execute(status -> {
            guard.require(9001, "BUTTON_SETTLEMENTS_GENERATE"); return null;
        })).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void concurrentRevocationWinsBeforeAWaitingCriticalWrite() throws Exception {
        long role = jdbc.queryForObject("SELECT id FROM sys_role WHERE role_code='ADMIN'", Long.class);
        CountDownLatch revokedInTransaction = new CountDownLatch(1);
        CountDownLatch allowCommit = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var revoker = pool.submit(() -> new TransactionTemplate(transactions).execute(status -> {
                jdbc.update("DELETE FROM sys_role_permission WHERE role_id=? AND permission_id=2", role);
                jdbc.update("UPDATE sys_role SET permission_version=permission_version+1 WHERE id=?", role);
                revokedInTransaction.countDown();
                try { if (!allowCommit.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("timeout"); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException(ex); }
                return null;
            }));
            assertThat(revokedInTransaction.await(5, TimeUnit.SECONDS)).isTrue();
            var writer = pool.submit(() -> {
                AuthenticatedUser stale = new AuthenticatedUser(9001L, "13990009001", role, "ADMIN", "管理员",
                        Set.of("PAGE_SETTLEMENTS", "BUTTON_SETTLEMENTS_GENERATE"));
                SecurityContextHolder.getContext().setAuthentication(
                        UsernamePasswordAuthenticationToken.authenticated(stale, null, java.util.List.of()));
                try {
                    new TransactionTemplate(transactions).execute(status -> {
                        guard.require(9001, "BUTTON_SETTLEMENTS_GENERATE"); return null;
                    });
                    return true;
                } catch (AccessDeniedException denied) { return false; }
                finally { SecurityContextHolder.clearContext(); }
            });
            assertThat(writer.isDone()).isFalse();
            allowCommit.countDown();
            revoker.get(5, TimeUnit.SECONDS);
            assertThat(writer.get(5, TimeUnit.SECONDS)).isFalse();
        } finally {
            allowCommit.countDown();
            pool.shutdownNow();
        }
    }
}
