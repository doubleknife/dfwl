package com.dfwl.fleet.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dfwl.fleet.common.web.RequestIdHolder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:authdb;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-auth-test.sql"
})
@AutoConfigureMockMvc
class AuthApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM sys_role_permission");
        jdbcTemplate.update("DELETE FROM sys_permission");
        jdbcTemplate.update("DELETE FROM sys_user");
        jdbcTemplate.update("DELETE FROM sys_role");

        jdbcTemplate.update("INSERT INTO sys_role (id, role_code, role_name) VALUES (1, 'admin', '管理员')");
        jdbcTemplate.update("INSERT INTO sys_role (id, role_code, role_name) VALUES (2, 'viewer', '查看员')");
        jdbcTemplate.update("""
                INSERT INTO sys_permission (id, permission_code, permission_name, permission_type)
                VALUES (1, 'user:manage', '用户管理', 'API')
                """);
        jdbcTemplate.update("INSERT INTO sys_role_permission (role_id, permission_id) VALUES (1, 1)");
        jdbcTemplate.update("""
                INSERT INTO sys_user (id, phone, password_hash, role_id, status)
                VALUES (1, '13800000000', ?, 1, 1)
                """, passwordEncoder.encode("oldPass"));
        jdbcTemplate.update("""
                INSERT INTO sys_user (id, phone, password_hash, role_id, status)
                VALUES (2, '13900000000', ?, 2, 1)
                """, passwordEncoder.encode("viewerPass"));
        jdbcTemplate.update("""
                INSERT INTO sys_user (id, phone, password_hash, role_id, status)
                VALUES (3, '13700000000', ?, 2, 0)
                """, passwordEncoder.encode("disabledPass"));
    }

    @Test
    void loginMeAndLogoutUseDatabaseBackedToken() throws Exception {
        String token = login("13800000000", "oldPass");

        mockMvc.perform(get("/api/v1/me")
                        .header(RequestIdHolder.HEADER, "req-me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.phone").value("13800000000"))
                .andExpect(jsonPath("$.data.role.code").value("admin"))
                .andExpect(jsonPath("$.data.permissions[0]").value("user:manage"));

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SYS_004"));
    }

    @Test
    void disabledUserCannotLogin() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"13700000000","password":"disabledPass"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AUTH_002"));
    }

    @Test
    void userManagePermissionIsEnforcedForAdminActions() throws Exception {
        String viewerToken = login("13900000000", "viewerPass");
        mockMvc.perform(post("/api/v1/users/2/reset-password")
                        .header("Authorization", "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"newPassword":"newPass"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_003"));

        String adminToken = login("13800000000", "oldPass");
        mockMvc.perform(post("/api/v1/users/2/reset-password")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"newPassword":"newPass"}
                                """))
                .andExpect(status().isOk());

        assertThat(login("13900000000", "newPass")).isNotBlank();
    }

    @Test
    void changingPasswordRevokesExistingToken() throws Exception {
        String token = login("13800000000", "oldPass");

        mockMvc.perform(put("/api/v1/auth/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"oldPassword":"oldPass","newPassword":"freshPass"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
        assertThat(login("13800000000", "freshPass")).isNotBlank();
    }

    private String login(String phone, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"%s","password":"%s"}
                                """.formatted(phone, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        return json.path("data").path("accessToken").asText();
    }
}
