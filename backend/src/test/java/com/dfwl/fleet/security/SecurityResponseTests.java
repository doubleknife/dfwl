package com.dfwl.fleet.security;

import static com.dfwl.fleet.security.TestFunctionGrants.grants;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dfwl.fleet.common.web.RequestIdHolder;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:securitydb;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-auth-test.sql"
})
@AutoConfigureMockMvc
class SecurityResponseTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TokenAuthenticationService tokenAuthenticationService;

    @Test
    void unauthenticatedRequestsUseUnifiedResponse() throws Exception {
        mockMvc.perform(get("/api/v1/system/ping").header(RequestIdHolder.HEADER, "req-unauth"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(RequestIdHolder.HEADER, "req-unauth"))
                .andExpect(jsonPath("$.code").value("SYS_004"))
                .andExpect(jsonPath("$.requestId").value("req-unauth"));
    }

    @Test
    void authenticatedTokenCanReachPingEndpoint() throws Exception {
        AuthenticatedUser user = new AuthenticatedUser(1L, "13800000000", 1L, "admin", "管理员", Set.of());
        when(tokenAuthenticationService.authenticate("test-token"))
                .thenReturn(Optional.of(UsernamePasswordAuthenticationToken.authenticated(user, "test-token", Set.of())));

        MvcResult result = mockMvc.perform(get("/api/v1/system/ping")
                        .header(RequestIdHolder.HEADER, "req-ok")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.requestId").value("req-ok"))
                .andReturn();

        assertThat(result.getResponse().getHeader(RequestIdHolder.HEADER)).isEqualTo("req-ok");
    }

    @Test
    void chargingOriginalFileRequiresViewPermission() throws Exception {
        AuthenticatedUser user = new AuthenticatedUser(2L, "13800000001", 2L, "viewer", "只读用户", Set.of());
        when(tokenAuthenticationService.authenticate("no-charging-view"))
                .thenReturn(Optional.of(UsernamePasswordAuthenticationToken.authenticated(user,
                        "no-charging-view", Set.of())));

        mockMvc.perform(get("/api/v1/charging/imports/1/original-file")
                        .header("Authorization", "Bearer no-charging-view"))
                .andExpect(status().isForbidden());
    }

    @Test
    void pendingAttributionSummaryRequiresEditPermissionRatherThanLedgerView() throws Exception {
        AuthenticatedUser viewer = new AuthenticatedUser(4L, "13800000004", 4L, "viewer", "只读用户",
                grants("PAGE_EXPENSE_LEDGER"));
        when(tokenAuthenticationService.authenticate("ledger-viewer"))
                .thenReturn(Optional.of(UsernamePasswordAuthenticationToken.authenticated(viewer,
                        "ledger-viewer", Set.of(new SimpleGrantedAuthority("PAGE_EXPENSE_LEDGER")))));
        mockMvc.perform(get("/api/v1/expense-ledger/pending-attribution/summary")
                        .header("Authorization", "Bearer ledger-viewer"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/expense-ledger/pending-attribution")
                        .header("Authorization", "Bearer ledger-viewer"))
                .andExpect(status().isForbidden());

        AuthenticatedUser editor = new AuthenticatedUser(5L, "13800000005", 5L, "editor", "归属人员",
                grants("BUTTON_EXPENSE_UNASSIGNED_ATTRIBUTE"));
        when(tokenAuthenticationService.authenticate("attribution-editor"))
                .thenReturn(Optional.of(UsernamePasswordAuthenticationToken.authenticated(editor,
                        "attribution-editor", Set.of(new SimpleGrantedAuthority("BUTTON_EXPENSE_UNASSIGNED_ATTRIBUTE")))));
        mockMvc.perform(get("/api/v1/expense-ledger/pending-attribution/summary")
                        .header("Authorization", "Bearer attribution-editor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.entry_count").isNumber());
    }

    @Test
    void vehicleDeletionIsNotAnAvailableOperation() throws Exception {
        AuthenticatedUser user = new AuthenticatedUser(3L, "13800000002", 3L, "admin", "管理员", Set.of());
        when(tokenAuthenticationService.authenticate("vehicle-admin"))
                .thenReturn(Optional.of(UsernamePasswordAuthenticationToken.authenticated(user,
                        "vehicle-admin", Set.of())));

        mockMvc.perform(delete("/api/v1/vehicles/1")
                        .header("Authorization", "Bearer vehicle-admin"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.message").value("此操作不可用"));
    }
}
