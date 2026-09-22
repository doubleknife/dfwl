package com.dfwl.fleet.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dfwl.fleet.security.PermissionCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:default-admin-seed-db;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-auth-test.sql"
})
@AutoConfigureMockMvc
class DefaultAdminSeedIntegrationTests {

    private static final String DEFAULT_PHONE = "13900000001";
    private static final String DEFAULT_PASSWORD = "Dfwl@Fleet2026!";
    private static final String CHANGED_PASSWORD = "changed-test-password";
    private static final String SEED_MARKER = "INSERT IGNORE INTO sys_role";

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void baselineSeedCreatesCompleteUsableAdminAndIsIdempotent() throws Exception {
        String migrationSeed = readSeed(new ClassPathResource("db/migration/V2__default_admin_seed.sql"));
        String databaseSeed = readSeed(new FileSystemResource("../database/database.sql"));
        assertThat(normalize(databaseSeed)).isEqualTo(normalize(migrationSeed));

        executeSeed(migrationSeed);
        assertSeedState();

        String originalHash = passwordHash();
        assertThat(originalHash).isNotEqualTo(DEFAULT_PASSWORD);
        assertThat(passwordEncoder.matches(DEFAULT_PASSWORD, originalHash)).isTrue();

        executeSeed(migrationSeed);
        assertSeedState();
        assertThat(passwordHash()).isEqualTo(originalHash);

        String token = login(DEFAULT_PASSWORD);
        assertAdminAccess(token);

        mockMvc.perform(put("/api/v1/auth/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ChangePasswordPayload(DEFAULT_PASSWORD, CHANGED_PASSWORD))))
                .andExpect(status().isOk());

        String changedHash = passwordHash();
        assertThat(changedHash).isNotEqualTo(originalHash);
        assertThat(passwordEncoder.matches(CHANGED_PASSWORD, changedHash)).isTrue();

        executeSeed(migrationSeed);
        assertSeedState();
        assertThat(passwordHash()).isEqualTo(changedHash);
        assertAdminAccess(login(CHANGED_PASSWORD));
    }

    private void assertSeedState() {
        Set<String> expectedCodes = Arrays.stream(PermissionCode.values())
                .map(PermissionCode::code)
                .collect(Collectors.toSet());
        Set<String> actualCodes = Set.copyOf(jdbcTemplate.queryForList(
                "SELECT permission_code FROM sys_permission", String.class));
        assertThat(actualCodes).isEqualTo(expectedCodes);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM sys_role
                WHERE role_code = 'ADMIN' AND is_system_fixed = 1 AND status = 1
                """, Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM sys_role_permission rp
                JOIN sys_role r ON r.id = rp.role_id
                WHERE r.role_code = 'ADMIN'
                """, Integer.class)).isEqualTo(PermissionCode.values().length);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM sys_user u
                JOIN sys_role r ON r.id = u.role_id
                WHERE u.phone = ? AND u.status = 1 AND r.role_code = 'ADMIN'
                """, Integer.class, DEFAULT_PHONE)).isEqualTo(1);
    }

    private void assertAdminAccess(String token) throws Exception {
        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role.code").value("ADMIN"))
                .andExpect(jsonPath("$.data.permissions.length()").value(PermissionCode.values().length));
        mockMvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/roles").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/permissions").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/audit-logs").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private String login(String password) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginPayload(DEFAULT_PHONE, password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        return json.path("data").path("accessToken").asText();
    }

    private String passwordHash() {
        return jdbcTemplate.queryForObject(
                "SELECT password_hash FROM sys_user WHERE phone = ?", String.class, DEFAULT_PHONE);
    }

    private void executeSeed(String seed) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new EncodedResource(
                    new ByteArrayResource(seed.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8));
        }
    }

    private String readSeed(org.springframework.core.io.Resource resource) throws Exception {
        String sql = resource.getContentAsString(StandardCharsets.UTF_8);
        int marker = sql.indexOf(SEED_MARKER);
        assertThat(marker).isGreaterThanOrEqualTo(0);
        return sql.substring(marker);
    }

    private String normalize(String sql) {
        return sql.replace("\r\n", "\n").trim();
    }

    private record LoginPayload(String phone, String password) {
    }

    private record ChangePasswordPayload(String oldPassword, String newPassword) {
    }
}
