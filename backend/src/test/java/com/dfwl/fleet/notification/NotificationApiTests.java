package com.dfwl.fleet.notification;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dfwl.fleet.security.AuthenticatedUser;
import com.dfwl.fleet.security.TokenAuthenticationService;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:notificationdb;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-auth-test.sql"
})
@AutoConfigureMockMvc
class NotificationApiTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired NotificationService service;
    @Autowired TokenAuthenticationService tokens;
    @Autowired PlatformTransactionManager transactionManager;
    private String userAToken;
    private String userBToken;

    @BeforeEach
    void reset() {
        jdbc.update("DELETE FROM in_app_notification");
        userAToken = tokens.issueToken(new AuthenticatedUser(801L, "13900000801", 1L, "DRIVER", "司机", Set.of()));
        userBToken = tokens.issueToken(new AuthenticatedUser(802L, "13900000802", 1L, "DRIVER", "司机", Set.of()));
    }

    @Test
    void notificationIsIdempotentAndNeverExposesAnotherUsersRecords() throws Exception {
        service.toUser(801, "route:assigned:1", "ROUTE_ASSIGNED", "新线路", "请查看", "ROUTE", 1);
        service.toUser(801, "route:assigned:1", "ROUTE_ASSIGNED", "新线路", "请查看", "ROUTE", 1);
        service.toUser(802, "route:assigned:1", "ROUTE_ASSIGNED", "新线路", "请查看", "ROUTE", 1);
        mvc.perform(get("/api/v1/notifications/unread-count").header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.unreadCount").value(1));
        mvc.perform(get("/api/v1/notifications").header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].title").value("新线路"));
        Long otherId = jdbc.queryForObject("SELECT id FROM in_app_notification WHERE user_id = 802", Long.class);
        mvc.perform(post("/api/v1/notifications/{id}/read", otherId).header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/notifications/unread-count").header("Authorization", "Bearer " + userBToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.unreadCount").value(1));
        mvc.perform(post("/api/v1/notifications/read-all").header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.unreadCount").value(0));
    }

    @Test
    void rolledBackBusinessTransactionCannotEmitNotification() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            service.toUser(801, "approval:approved:99", "APPROVAL_APPROVED", "通过", "完成", "APPROVAL", 99);
            status.setRollbackOnly();
        });
        mvc.perform(get("/api/v1/notifications/unread-count").header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.unreadCount").value(0));
    }

    @Test
    void pendingApprovalOnlyNotifiesAnActiveNonDriverWithProcessingPermission() {
        jdbc.update("INSERT INTO sys_role (id, role_code, role_name, status) VALUES (801, 'DRIVER', '司机', 1), (802, 'ADMIN_TEST', '测试审批人', 1)");
        jdbc.update("INSERT INTO sys_user (id, phone, password_hash, role_id, status) VALUES (801, '13900000801', 'hash', 801, 1), (802, '13900000802', 'hash', 802, 1)");
        jdbc.update("INSERT INTO sys_permission (id, permission_code, permission_name, permission_type, status) VALUES (801, 'approval:process', '审批处理', 'API', 1)");
        jdbc.update("INSERT INTO sys_role_permission (role_id, permission_id) VALUES (801, 801), (802, 801)");
        service.toEligibleApprover(801, "approval:todo:1", "待办", "请处理", 99);
        service.toEligibleApprover(802, "approval:todo:1", "待办", "请处理", 99);
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM in_app_notification WHERE type = 'APPROVAL_TODO'", Integer.class);
        org.assertj.core.api.Assertions.assertThat(count).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "SELECT user_id FROM in_app_notification WHERE type = 'APPROVAL_TODO'", Long.class)).isEqualTo(802L);
    }
}
