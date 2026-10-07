package com.dfwl.fleet.route;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dfwl.fleet.security.AuthenticatedUser;
import com.dfwl.fleet.security.TokenAuthenticationService;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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

    @Test
    void transportTasksAreTheOnlyCurrentManagementResource() throws Exception {
        String token = token("ADMIN", Set.of("task:view"));
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
                        .header("Authorization", "Bearer " + token("DRIVER", Set.of("task:view"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void remainingRetiredV2ModulesHaveNoReachableHttpEntry() throws Exception {
        String token = token("ADMIN", Set.of("task:view", "expense:list", "approval:process", "tire:view"));
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
            mvc.perform(get(path).header("Authorization", "Bearer " + token("DRIVER", Set.of(
                            "expense:ledger:view", "report:task:view", "report:template:view",
                            "report:company:view", "settlement:view"))))
                    .andExpect(status().isForbidden());
        }
    }

    private String token(String role, Set<String> permissions) {
        return tokens.issueToken(new AuthenticatedUser(1L, "13900000001", 1L, role, role, permissions));
    }
}
