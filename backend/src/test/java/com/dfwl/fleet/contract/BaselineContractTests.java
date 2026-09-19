package com.dfwl.fleet.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.dfwl.fleet.approval.domain.ApprovalFlowStatus;
import com.dfwl.fleet.expense.domain.ExpenseStatus;
import com.dfwl.fleet.expense.domain.ExpenseType;
import com.dfwl.fleet.route.domain.RouteStatus;
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
    void routeStatusesMatchBaseline() throws IOException {
        String openapi = read("docs/openapi.yaml");

        assertThat(enumNames(RouteStatus.class))
                .containsExactly("UNPUBLISHED", "PUBLISHED", "IN_TRANSIT", "COMPLETED", "VOIDED", "CANCELLED");
        assertThat(openapi).contains("enum: [UNPUBLISHED, PUBLISHED, IN_TRANSIT, COMPLETED, VOIDED, CANCELLED]");
        assertThat(openapi).doesNotContain("DRAFT, PUBLISHED");
    }

    @Test
    void approvalDraftOnlyAppliesToApprovalFlow() throws IOException {
        String openapi = read("docs/openapi.yaml");

        assertThat(enumNames(ApprovalFlowStatus.class)).containsExactly("DRAFT", "ACTIVE", "MAINTENANCE");
        assertThat(openapi).contains("enum: [DRAFT, ACTIVE, MAINTENANCE]");
    }

    @Test
    void expenseEnumsMatchBaseline() throws IOException {
        String openapi = read("docs/openapi.yaml");

        assertThat(enumNames(ExpenseType.class)).containsExactly(
                "PENALTY",
                "CARRYING",
                "REPAIR",
                "ELECTRIC",
                "GAS",
                "TEMP_ELECTRIC",
                "WATER",
                "TOLL",
                "CAR_WASH",
                "INFORMATION",
                "DRIVER_SALARY",
                "OTHER");
        assertThat(enumNames(ExpenseStatus.class)).containsExactly(
                "ACTIVE", "PENDING_ATTRIBUTION", "REVERSED", "REVERSAL");
        assertThat(openapi).contains("PENALTY, CARRYING, REPAIR, ELECTRIC, GAS, TEMP_ELECTRIC");
        assertThat(openapi).contains("enum: [ACTIVE, PENDING_ATTRIBUTION, REVERSED, REVERSAL]");
    }

    @Test
    void permissionRegistryMatchesOpenApi() throws IOException {
        String openapi = read("docs/openapi.yaml");
        Set<String> codes = Arrays.stream(PermissionCode.values())
                .map(PermissionCode::code)
                .collect(Collectors.toSet());

        assertThat(codes).hasSize(PermissionCode.values().length);
        for (String code : codes) {
            assertThat(openapi).contains("- " + code);
        }
        assertThat(openapi)
                .doesNotContain("route:add")
                .doesNotContain("route:adjustWeight")
                .doesNotContain("expense:confirmAttribution")
                .doesNotContain("expense:reverse")
                .doesNotContain("import:confirm")
                .doesNotContain("import:downloadError");
    }

    @Test
    void sqlUsesVarcharEnumsAndNonUniqueRouteBusinessKey() throws IOException {
        String sql = read("database/database.sql");
        String migration = read("backend/src/main/resources/db/migration/V1__baseline_schema.sql");

        assertThat(sql).doesNotContain("ENUM(");
        assertThat(sql).doesNotContain("business_unique_key VARCHAR(255) NOT NULL UNIQUE");
        assertThat(sql).contains("KEY idx_route_business_unique_key (business_unique_key)");
        assertThat(sql).contains("DECIMAL(18,2)");
        assertThat(sql).contains("DECIMAL(18,3)");
        assertThat(sql).contains("DECIMAL(18,4)");
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

