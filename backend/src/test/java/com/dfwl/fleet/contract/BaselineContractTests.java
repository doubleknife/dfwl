package com.dfwl.fleet.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.dfwl.fleet.route.domain.TransportTaskStatus;
import com.dfwl.fleet.security.PermissionCode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class BaselineContractTests {

    private static final Path REPO_ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void taskStatusesMatchBaseline() throws IOException {
        String openapi = read("docs/openapi.yaml");

        assertThat(enumNames(TransportTaskStatus.class))
                .containsExactly("UNPUBLISHED", "PUBLISHED", "IN_TRANSIT", "TRANSFER_PENDING", "COMPLETED", "CANCELLED");
        assertThat(openapi).contains("enum: [UNPUBLISHED, PUBLISHED, IN_TRANSIT, TRANSFER_PENDING, COMPLETED, CANCELLED]");
        assertThat(openapi).doesNotContain("VOIDED", "/routes/{id}/void", "/routes/{id}/reactivate");
    }

    @Test
    void onlyLaterStageContractsAreAbsentUntilRebuilt() throws IOException {
        String openapi = read("docs/openapi.yaml");
        assertThat(openapi).contains("/approvals:", "/expense-applications/daily-submit:", "targetFlowNodeId");
        assertThat(openapi).contains("/expense-ledger:", "/finance/tasks:", "/settlements/{month}/versions:");
        assertThat(openapi).doesNotContain("\n  /expenses:", "\n  /tires:",
                "/reports/profit:", "/imports:", "targetNodeOrder");
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
        Set<String> codes = Arrays.stream(PermissionCode.values())
                .map(PermissionCode::code)
                .collect(Collectors.toSet());

        assertThat(codes).hasSize(PermissionCode.values().length);
        for (String code : codes) {
            if (code.startsWith("tire:") || code.startsWith("import:")) {
                assertThat(openapi).doesNotContain("- " + code);
            } else {
                assertThat(openapi).contains("- " + code);
            }
        }
        assertThat(codes).doesNotContain("route:add", "route:adjustWeight", "task:void", "task:reactivate");
    }

    @Test
    void sqlUsesSingleFinalTaskBaseline() throws IOException {
        String sql = read("database/database.sql");
        String migration = read("backend/src/main/resources/db/migration/V1__baseline_schema.sql");

        assertThat(sql).doesNotContain("ENUM(");
        assertThat(sql).contains("CREATE TABLE route_template", "CREATE TABLE transport_task",
                "CREATE TABLE transport_participant", "CREATE TABLE participant_salary_history");
        assertThat(sql).doesNotContain("CREATE TABLE route_task", "CREATE TABLE driver_salary_entry",
                "CREATE TABLE customer", "pre_void_status");
        assertThat(sql).contains("DECIMAL(18,2)");
        assertThat(sql).contains("DECIMAL(18,3)");
        assertThat(sql).contains("DECIMAL(8,4)");
        assertThat(migration).doesNotContain("CREATE DATABASE", "USE fleet_ops");
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

