package com.dfwl.fleet.route;

import static com.dfwl.fleet.security.TestFunctionGrants.grants;

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

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:v3-task-api;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
        "spring.datasource.password=", "spring.flyway.enabled=false", "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-auth-test.sql"
})
@AutoConfigureMockMvc
class RouteApiTests {
    @Autowired MockMvc mvc;
    @Autowired TokenAuthenticationService tokens;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void accounts() {
        long adminRole = jdbc.queryForObject("SELECT id FROM sys_role WHERE role_code='ADMIN'", Long.class);
        long driverRole = jdbc.queryForObject("SELECT id FROM sys_role WHERE role_code='DRIVER'", Long.class);
        jdbc.update("MERGE INTO sys_user(id,phone,password_hash,role_id,status) KEY(id) VALUES(1,'13900000001','test-only',?,1)", adminRole);
        jdbc.update("MERGE INTO sys_user(id,phone,password_hash,role_id,status) KEY(id) VALUES(2,'13900000002','test-only',?,1)", driverRole);
    }

    @Test
    void transportTasksAreTheOnlyCurrentManagementResource() throws Exception {
        String token = token("ADMIN", grants("PAGE_ROUTES"));
        mvc.perform(get("/api/v1/transport-tasks").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(get("/api/v1/routes").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/routes").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void managementTaskListIsNotGrantedByDriverRoleOrMissingPermission() throws Exception {
        mvc.perform(get("/api/v1/transport-tasks")
                        .header("Authorization", "Bearer " + token("ADMIN", Set.of())))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/transport-tasks")
                        .header("Authorization", "Bearer " + token("DRIVER", grants("PAGE_ROUTES"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void remainingRetiredV2ModulesHaveNoReachableHttpEntry() throws Exception {
        String token = token("ADMIN", grants("PAGE_ROUTES", "PAGE_EXPENSE_LEDGER", "BUTTON_APPROVALS_TODO_APPROVE", "PAGE_TIRES"));
        for (String path : new String[] {
                "/api/v1/expenses",
                "/api/v1/reports/profit",
                "/api/v1/imports"
        }) {
            mvc.perform(get(path).header("Authorization", "Bearer " + token))
                    .andExpect(status().isNotFound());
        }
        mvc.perform(get("/api/v1/approval-templates")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/settlements/2026-09/versions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void v3FinanceEndpointsEnforcePermissionAndDenyDriverRole() throws Exception {
        for (String path : new String[] {
                "/api/v1/expense-ledger", "/api/v1/finance/tasks",
                "/api/v1/finance/templates", "/api/v1/finance/company?month=2026-10",
                "/api/v1/settlements/2026-10/versions"
        }) {
            mvc.perform(get(path).header("Authorization", "Bearer " + token("ADMIN", Set.of())))
                    .andExpect(status().isForbidden());
            mvc.perform(get(path).header("Authorization", "Bearer " + token("DRIVER", grants(
                            "PAGE_EXPENSE_LEDGER", "PAGE_FINANCE_TASKS", "PAGE_FINANCE_TEMPLATES",
                            "PAGE_FINANCE_COMPANY", "PAGE_SETTLEMENTS"))))
                    .andExpect(status().isForbidden());
        }
    }

    private String token(String role, Set<String> permissions) {
        boolean driver = "DRIVER".equals(role);
        long roleId = jdbc.queryForObject("SELECT id FROM sys_role WHERE role_code=?", Long.class, role);
        return tokens.issueToken(new AuthenticatedUser(driver ? 2L : 1L,
                driver ? "13900000002" : "13900000001", roleId, role, role, permissions));
    }
}
