package com.dfwl.fleet.system;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DefaultAdminSeedIntegrationTests {
    private static final Path ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void firstReleaseBaselineSeedsRolesAndPermissionsWithoutAccountsOrPasswords() throws Exception {
        String sql = Files.readString(ROOT.resolve("database/database.sql"));
        String flyway = Files.readString(ROOT.resolve(
                "backend/src/main/resources/db/migration/V1__baseline_schema.sql"));

        assertThat(sql).contains("INSERT IGNORE INTO sys_role");
        assertThat(sql).doesNotContain("INSERT IGNORE INTO sys_permission");
        assertThat(sql).doesNotContain("INSERT INTO sys_user", "INSERT IGNORE INTO sys_user",
                "password_hash VALUES", "default-admin-seed");
        assertThat(flyway).contains("INSERT IGNORE INTO sys_role");
        assertThat(flyway).doesNotContain("INSERT IGNORE INTO sys_permission");
        assertThat(flyway).doesNotContain("INSERT INTO sys_user", "INSERT IGNORE INTO sys_user");
    }
}
