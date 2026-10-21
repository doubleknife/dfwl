package com.dfwl.fleet.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dfwl.fleet.route.domain.TransportTaskStatus;
import com.dfwl.fleet.security.FunctionRegistry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class BaselineContractTests {

    private static final Path REPO_ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void taskStatusesMatchBaseline() throws IOException {
        String openapi = read("docs/openapi.yaml");

        assertThat(enumNames(TransportTaskStatus.class))
                .containsExactly("PUBLISHED", "IN_TRANSIT", "TRANSFER_PENDING", "COMPLETED", "CANCELLED");
        assertThat(openapi).contains("enum: [PUBLISHED, IN_TRANSIT, TRANSFER_PENDING, COMPLETED, CANCELLED]");
        assertThat(openapi).doesNotContain("UNPUBLISHED");
        assertThatThrownBy(() -> TransportTaskStatus.valueOf("UNPUBLISHED"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(openapi).doesNotContain("VOIDED", "/routes/{id}/void", "/routes/{id}/reactivate");
    }

    @Test
    void onlyLaterStageContractsAreAbsentUntilRebuilt() throws IOException {
        String openapi = read("docs/openapi.yaml");
        assertThat(openapi).contains("/approvals:", "/expense-applications/daily-preview:", "targetFlowNodeId");
        assertThat(openapi).doesNotContain("/expense-applications/daily-submit:");
        assertThat(openapi).contains("/expense-ledger:", "/finance/tasks:", "/settlements/{month}/versions:");
        assertThat(openapi).doesNotContain("\n  /expenses:",
                "/reports/profit:", "\n  /imports:", "targetNodeOrder");
        assertThat(openapi).contains("/charging/imports:", "zeroCount");
    }

    @Test
    void coreResourcesHaveSingleWriteEntry() throws IOException {
        String openapi = read("docs/openapi.yaml");
        assertThat(openapi).contains("/route-templates:", "/transport-tasks:");
        assertThat(openapi).doesNotContain("/routes:", "/customers:", "/driver-salaries/{id}/adjust:");
    }

    @Test
    void permissionRegistryMatchesOpenApi() throws IOException {
        String openapi = read("docs/openapi.yaml");
        Object parsedOpenApi = new Yaml().load(openapi);
        assertThat(parsedOpenApi).isNotNull();
        FunctionRegistry registry = new FunctionRegistry();
        Set<String> codes = registry.codes();
        assertThat(codes).hasSize(registry.all().size());
        assertThat(codes).contains("PAGE_PRODUCTS", "BUTTON_PRODUCTS_MANAGE");
        assertThat(codes).noneMatch(code -> code.contains(":"));
        String functionSchema = openapi.substring(openapi.indexOf("    FunctionCode:"));
        var matcher = Pattern.compile("(?m)^        - ([A-Z_]+)$").matcher(functionSchema);
        java.util.HashSet<String> documented = new java.util.HashSet<>();
        while (matcher.find()) documented.add(matcher.group(1));
        assertThat(documented).containsExactlyInAnyOrderElementsOf(codes);
    }

    @Test
    void sqlUsesSingleFinalTaskBaseline() throws IOException {
        String sql = read("database/database.sql");
        String migration = read("backend/src/main/resources/db/migration/V1__baseline_schema.sql");

        assertThat(sql).contains("status VARCHAR(32) NOT NULL DEFAULT 'PUBLISHED'",
                "CHECK (status IN ('PUBLISHED', 'IN_TRANSIT', 'TRANSFER_PENDING', 'COMPLETED', 'CANCELLED'))")
                .doesNotContain("UNPUBLISHED");
        assertThat(migration).contains("CHECK (status IN ('PUBLISHED', 'IN_TRANSIT', 'TRANSFER_PENDING', 'COMPLETED', 'CANCELLED'))")
                .doesNotContain("UNPUBLISHED");

        assertThat(sql).doesNotContain("ENUM(");
        assertThat(sql).contains("CREATE TABLE route_template", "CREATE TABLE transport_task",
                "CREATE TABLE transport_participant", "CREATE TABLE participant_salary_history");
        assertThat(sql).doesNotContain("CREATE TABLE route_task", "CREATE TABLE driver_salary_entry",
                "CREATE TABLE customer", "pre_void_status");
        assertThat(sql).contains("DECIMAL(18,2)");
        assertThat(sql).contains("DECIMAL(18,3)");
        assertThat(sql).contains("DECIMAL(8,4)");
        assertThat(migration).doesNotContain("CREATE DATABASE", "USE fleet_ops");
        Set<String> productionTables = tableNames(sql);
        assertThat(productionTables).hasSize(55).containsExactlyInAnyOrderElementsOf(tableNames(migration));
        assertThat(productionTables).containsExactlyInAnyOrderElementsOf(
                tableNames(read("backend/src/test/resources/schema-auth-test.sql")));
    }

    private static Set<String> tableNames(String sql) {
        var matcher = Pattern.compile("(?i)CREATE TABLE\\s+([a-z_]+)").matcher(sql);
        java.util.HashSet<String> tables = new java.util.HashSet<>();
        while (matcher.find()) tables.add(matcher.group(1));
        return tables;
    }

    private static <E extends Enum<E>> String[] enumNames(Class<E> enumType) {
        return Arrays.stream(enumType.getEnumConstants())
                .map(Enum::name)
                .toArray(String[]::new);
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(REPO_ROOT.resolve(relativePath));
    }
}

