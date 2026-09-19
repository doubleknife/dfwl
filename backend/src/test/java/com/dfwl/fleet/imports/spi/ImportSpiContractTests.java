package com.dfwl.fleet.imports.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** Test-only discovery/protocol examples; no production dispatcher or transaction is installed. */
class ImportSpiContractTests {

    @ParameterizedTest
    @EnumSource(ImportBusinessType.class)
    void selectsExactlyOneHandlerByStableType(ImportBusinessType type) {
        var handler = new FakeHandler(type);
        assertThat(select(List.of(handler), " " + type.name().toLowerCase(java.util.Locale.ROOT) + " "))
                .isSameAs(handler);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"UNKNOWN", " "})
    void unknownTypesNeverFallBackToAnotherHandler(String type) {
        assertThat(ImportBusinessType.parse(type)).isEmpty();
        assertThatThrownBy(() -> select(List.of(new FakeHandler(ImportBusinessType.ROUTE)), type))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Unsupported import type");
    }

    @Test
    void missingAndDuplicateRegistrationsAreExplicitErrors() {
        assertThatThrownBy(() -> select(List.of(), "SALARY"))
                .isInstanceOf(IllegalStateException.class).hasMessage("No handler: SALARY");
        assertThatThrownBy(() -> select(List.of(new FakeHandler(ImportBusinessType.SALARY),
                        new FakeHandler(ImportBusinessType.SALARY)), "SALARY"))
                .isInstanceOf(IllegalStateException.class).hasMessage("Duplicate handler: SALARY");
    }

    @Test
    void validationCarriesResolvedKeyAndStableFailureDetails() {
        var handler = new FakeHandler(ImportBusinessType.TIRE);
        var row = context(null, " T-001 ");
        var resolved = handler.resolveBusinessKey(row);
        assertThat(resolved.success()).isTrue();
        assertThat(resolved.businessUniqueKey()).isEqualTo("T-001");
        assertThat(resolved.errorCode()).isNull();
        assertThat(resolved.errorMessage()).isNull();
        var missing = handler.resolveBusinessKey(context(null, null));
        assertThat(missing.success()).isFalse();
        assertThat(missing.businessUniqueKey()).isNull();
        assertThat(missing.errorCode()).isEqualTo("IMPORT_KEY_MISSING");
        assertThat(missing.errorMessage()).isEqualTo("业务唯一键不能为空");
    }

    @ParameterizedTest
    @EnumSource(ImportCommitResult.Status.class)
    void commitPreservesAllThreeStatusesAndTheirMetadata(ImportCommitResult.Status status) {
        var handler = new FakeHandler(ImportBusinessType.ROUTE);
        handler.outcome = status;
        var result = handler.commit(context(42L, "KEY"));
        assertThat(result.finalStatus()).isEqualTo(status);
        assertThat(result.businessUniqueKey()).isEqualTo("KEY");
        if (status == ImportCommitResult.Status.FAILURE) {
            assertThat(result.businessId()).isNull();
            assertThat(result.errorCode()).isEqualTo("ROUTE_003");
            assertThat(result.errorMessage()).isEqualTo("司机车辆校验失败");
        } else {
            assertThat(result.businessId()).isEqualTo(99L);
            assertThat(result.errorCode()).isNull();
            assertThat(result.errorMessage()).isNull();
        }
    }

    @Test
    void commitRevalidatesChangedBusinessStateInsteadOfTrustingPreview() {
        var handler = new FakeHandler(ImportBusinessType.SALARY);
        assertThat(handler.validate(context(null, "SALARY|1")).success()).isTrue();
        handler.valid = false;
        var result = handler.commit(context(7L, "SALARY|1"));
        assertThat(result.finalStatus()).isEqualTo(ImportCommitResult.Status.FAILURE);
        assertThat(result.errorCode()).isEqualTo("ROUTE_003");
        assertThat(handler.writes).isZero();
    }

    @Test
    void duplicateAndStructureValidationCanRunSeparately() {
        var handler = new FakeHandler(ImportBusinessType.TIRE);
        handler.duplicate = true;
        handler.valid = false;
        var duplicate = handler.validateDuplicate(context(8L, "T-1"));
        assertThat(duplicate.success()).isFalse();
        assertThat(duplicate.errorCode()).isEqualTo("IMPORT_DUPLICATE_EXISTING");
        assertThat(duplicate.businessUniqueKey()).isEqualTo("T-1");
        assertThat(handler.validations).isZero();
    }

    @Test
    void infrastructureFailureEscapesInsteadOfBecomingBusinessResult() {
        var handler = new FakeHandler(ImportBusinessType.EXPENSE);
        var failure = new IllegalStateException("simulated database failure");
        handler.infrastructureFailure = failure;
        assertThatThrownBy(() -> handler.commit(context(1L, "EXPENSE_NO|1"))).isSameAs(failure);
    }

    @Test
    void rowContextPreservesNullableNestedDataAndProvenanceWithoutApiTypes() {
        Map<String, Object> data = new HashMap<>();
        data.put("optional", null);
        data.put("amount", new BigDecimal("12.34"));
        data.put("energyDetail", Map.of("orderNo", "E-1"));
        var context = new ImportRowContext(27L, data, "ENERGY|E-1", 9);
        data.put("amount", BigDecimal.ZERO);
        assertThat(context.importRowId()).isEqualTo(27L);
        assertThat(context.operatorId()).isEqualTo(9L);
        assertThat(context.rowData()).containsEntry("optional", null).containsEntry("amount", new BigDecimal("12.34"));
        assertThat(context.rowData().get("energyDetail")).isEqualTo(Map.of("orderNo", "E-1"));
        assertThatThrownBy(() -> context.rowData().put("amount", BigDecimal.ZERO))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(context(null, "preview").importRowId()).isNull();
    }

    @Test
    void spiSourceHasOnlyJdkImportsAndNoConcreteDomainReferences() throws IOException {
        var imports = Pattern.compile("(?m)^import\s+(?:static\s+)?([^;]+);");
        Path source = Path.of("src/main/java/com/dfwl/fleet/imports/spi");
        try (var files = Files.list(source)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String text = Files.readString(file);
                var matcher = imports.matcher(text);
                while (matcher.find()) assertThat(matcher.group(1)).startsWith("java.");
                for (String forbidden : List.of("imports.api", "imports.repository", "route", "tire", "expense",
                        "salary", "attachment", "security")) {
                    assertThat(text).doesNotContain("com.dfwl.fleet." + forbidden + ".");
                }
            }
        }
    }

    private static ImportRowContext context(Long rowId, String key) {
        return new ImportRowContext(rowId, Map.of(), key, 1L);
    }

    // Mirrors future list discovery without connecting it to the current Spring application.
    private static ImportBusinessHandler select(List<ImportBusinessHandler> handlers, String value) {
        var type = ImportBusinessType.parse(value).orElseThrow(() -> new IllegalArgumentException("Unsupported import type"));
        var registry = new EnumMap<ImportBusinessType, ImportBusinessHandler>(ImportBusinessType.class);
        for (var handler : handlers) {
            if (registry.putIfAbsent(handler.businessType(), handler) != null) {
                throw new IllegalStateException("Duplicate handler: " + handler.businessType());
            }
        }
        var selected = registry.get(type);
        if (selected == null) throw new IllegalStateException("No handler: " + type);
        return selected;
    }

    private static final class FakeHandler implements ImportBusinessHandler {
        private final ImportBusinessType type;
        private boolean valid = true;
        private boolean duplicate;
        private int writes;
        private int validations;
        private RuntimeException infrastructureFailure;
        private ImportCommitResult.Status outcome = ImportCommitResult.Status.SUCCESS;

        private FakeHandler(ImportBusinessType type) { this.type = type; }
        public ImportBusinessType businessType() { return type; }

        public ImportValidationResult resolveBusinessKey(ImportRowContext row) {
            String key = row.businessUniqueKey();
            return key == null || key.isBlank()
                    ? new ImportValidationResult(false, null, "IMPORT_KEY_MISSING", "业务唯一键不能为空")
                    : new ImportValidationResult(true, key.trim(), null, null);
        }

        public ImportValidationResult validateDuplicate(ImportRowContext row) {
            return new ImportValidationResult(!duplicate, row.businessUniqueKey(),
                    duplicate ? "IMPORT_DUPLICATE_EXISTING" : null, duplicate ? "业务数据已存在" : null);
        }

        public ImportValidationResult validate(ImportRowContext row) {
            validations++;
            return new ImportValidationResult(valid, row.businessUniqueKey(),
                    valid ? null : "ROUTE_003", valid ? null : "司机车辆校验失败");
        }

        public ImportCommitResult commit(ImportRowContext row) {
            var check = validateDuplicate(row);
            if (check.success()) check = validate(row);
            if (!check.success()) return new ImportCommitResult(ImportCommitResult.Status.FAILURE, null,
                    check.businessUniqueKey(), check.errorCode(), check.errorMessage());
            if (infrastructureFailure != null) throw infrastructureFailure;
            if (outcome == ImportCommitResult.Status.FAILURE) return new ImportCommitResult(outcome, null,
                    row.businessUniqueKey(), "ROUTE_003", "司机车辆校验失败");
            writes++;
            return new ImportCommitResult(outcome, 99L, row.businessUniqueKey(), null, null);
        }
    }
}
