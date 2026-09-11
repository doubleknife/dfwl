package com.dfwl.fleet.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
}
