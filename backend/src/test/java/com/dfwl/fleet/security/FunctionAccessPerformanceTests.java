package com.dfwl.fleet.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.dfwl.fleet.auth.repository.UserAccountRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:function-performance;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
        "spring.datasource.password=", "spring.flyway.enabled=false", "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-auth-test.sql"
})
class FunctionAccessPerformanceTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired UserAccountRepository accounts;
    @Autowired TokenAuthenticationService tokens;
    @Autowired FunctionAccessGuard guard;
    @Autowired FunctionRegistry registry;
    @Autowired PermissionCatalogSynchronizer catalog;

    @Test
    void startupCompilesAllProtectedApiBindingsAgainstOneRegistry() {
        assertThat(guard.apiBindings()).hasSizeGreaterThan(140);
        assertThat(guard.apiBindings()).allSatisfy(binding -> {
            assertThat(binding.method()).isNotBlank();
            assertThat(binding.path()).startsWith("/api/v1/");
            assertThat(binding.self() || !binding.anyOf().isEmpty()).isTrue();
            assertThat(binding.anyOf()).allSatisfy(code -> assertThat(registry.find(code)).isNotNull());
        });
        assertThat(guard.apiBindings()).filteredOn(binding -> "GET".equals(binding.method())
                && "/api/v1/products".equals(binding.path()))
                .singleElement().satisfies(binding -> assertThat(binding.anyOf())
                        .containsExactlyInAnyOrder("PAGE_PRODUCTS", "PAGE_TEMPLATES"));
        assertThat(guard.apiBindings()).filteredOn(binding -> "GET".equals(binding.method())
                && "/api/v1/drivers/binding-candidates".equals(binding.path()))
                .singleElement().satisfies(binding -> assertThat(binding.anyOf()).containsExactly("PAGE_BINDING"));
        assertThat(registry.codes()).noneMatch(code -> code.contains(":"));
    }

    @Test
    void openApiListsTheSameFunctionsAsCompiledProtectedRoutes() throws Exception {
        Map<String, String> documented = new HashMap<>();
        String path = null;
        String operation = null;
        var pathPattern = Pattern.compile("^  (/[^:]+):\\s*$");
        var methodPattern = Pattern.compile("^    (get|post|put|patch|delete):\\s*$");
        for (String line : Files.readAllLines(Path.of("../docs/openapi.yaml"))) {
            var pathMatch = pathPattern.matcher(line);
            if (pathMatch.matches()) { path = pathMatch.group(1); operation = null; continue; }
            var methodMatch = methodPattern.matcher(line);
            if (path != null && methodMatch.matches()) {
                operation = methodMatch.group(1).toUpperCase() + " /api/v1" + path;
                documented.put(operation, "UNBOUND");
                continue;
            }
            if (operation == null) continue;
            if (line.startsWith("      x-self-access: true")) documented.put(operation, "SELF");
            else if (line.startsWith("      x-function-any-of: [")) {
                String values = line.substring(line.indexOf('[') + 1, line.indexOf(']'));
                documented.put(operation, String.join(",", java.util.Arrays.stream(values.split(","))
                        .map(String::trim).sorted().toList()));
            }
        }
        documented.remove("POST /api/v1/auth/login");
        Map<String, String> compiled = new HashMap<>();
        for (var binding : guard.apiBindings()) {
            compiled.put(binding.method() + " " + binding.path(),
                    binding.self() ? "SELF" : String.join(",", binding.anyOf()));
        }
        var missing = new java.util.HashSet<>(compiled.keySet());
        missing.removeAll(documented.keySet());
        var extra = new java.util.HashSet<>(documented.keySet());
        extra.removeAll(compiled.keySet());
        assertThat(missing).as("OpenAPI missing runtime routes").isEmpty();
        assertThat(extra).as("OpenAPI lists routes absent from runtime").isEmpty();
        for (String key : compiled.keySet())
            assertThat(documented.get(key)).as(key).isEqualTo(compiled.get(key));
    }

    @Test
    void measuresPermissionSnapshotHitAndSafeMissReload() {
        Long roleId = jdbc.queryForObject("SELECT id FROM sys_role WHERE role_code='ADMIN'", Long.class);
        jdbc.update("INSERT INTO sys_user(phone,password_hash,role_id,status) VALUES('13888880001','HASH',?,1)", roleId);
        Long userId = jdbc.queryForObject("SELECT id FROM sys_user WHERE phone='13888880001'", Long.class);
        AuthenticatedUser snapshot = loaded(userId);
        assertThat(snapshot.permissions()).contains("PAGE_PRODUCTS", "BUTTON_PRODUCTS_MANAGE");
        String token = tokens.issueToken(snapshot);
        for (int i = 0; i < 30; i++) tokens.authenticate(token);
        long hitStart = System.nanoTime();
        for (int i = 0; i < 300; i++) assertThat(tokens.authenticate(token)).isPresent();
        long hitNs = (System.nanoTime() - hitStart) / 300;

        tokens.revokeRoleTokens(roleId);
        assertThat(tokens.authenticate(token)).isEmpty();
        jdbc.update("DELETE FROM sys_role_permission WHERE role_id=? AND permission_id IN "
                + "(SELECT id FROM sys_permission WHERE permission_code IN ('PAGE_PRODUCTS','BUTTON_PRODUCTS_MANAGE'))", roleId);
        long missStart = System.nanoTime();
        AuthenticatedUser reloaded = null;
        for (int i = 0; i < 100; i++) reloaded = loaded(userId);
        long missNs = (System.nanoTime() - missStart) / 100;
        assertThat(reloaded.permissions()).doesNotContain("PAGE_PRODUCTS", "BUTTON_PRODUCTS_MANAGE");
        String freshToken = tokens.issueToken(reloaded);
        assertThat(tokens.authenticate(freshToken)).isPresent();
        assertThat(tokens.authenticate(token)).isEmpty();
        System.out.printf("PERMISSION_CACHE_PERF hit_avg_ns=%d miss_reload_ns=%d%n", hitNs, missNs);
    }

    @Test
    void grantChangeKeepsSessionsAndRollbackDoesNotEvictThem() {
        Long roleId = jdbc.queryForObject("SELECT id FROM sys_role WHERE role_code='ADMIN'", Long.class);
        jdbc.update("INSERT INTO sys_user(phone,password_hash,role_id,status) VALUES('13888880002','HASH',?,1)", roleId);
        Long userId = jdbc.queryForObject("SELECT id FROM sys_user WHERE phone='13888880002'", Long.class);
        AuthenticatedUser snapshot = loaded(userId);
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            tokens.invalidateRolePermissions(roleId);
            String tokenIssuedDuringTransaction = tokens.issueToken(snapshot);
            assertThat(tokens.authenticate(tokenIssuedDuringTransaction)).isPresent();
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(synchronization -> synchronization.afterCommit());
            assertThat(tokens.authenticate(tokenIssuedDuringTransaction)).isPresent();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    @Test
    void startupSyncDoesNotRegrantRevokedDriverFunctions() throws Exception {
        Long driverRoleId = jdbc.queryForObject("SELECT id FROM sys_role WHERE role_code='DRIVER'", Long.class);
        jdbc.update("DELETE FROM sys_role_permission WHERE role_id=?", driverRoleId);
        catalog.run(null);
        Integer grants = jdbc.queryForObject("SELECT COUNT(*) FROM sys_role_permission WHERE role_id=?",
                Integer.class, driverRoleId);
        assertThat(grants).isZero();
    }

    private AuthenticatedUser loaded(long userId) {
        var account = accounts.findById(userId).orElseThrow();
        return new AuthenticatedUser(account.id(), account.phone(), account.roleId(),
                account.roleCode(), account.roleName(), Set.copyOf(account.permissions()));
    }
}
