package com.dfwl.fleet.imports;

import com.dfwl.fleet.imports.spi.ImportBusinessHandler;
import com.dfwl.fleet.imports.spi.ImportBusinessType;
import com.dfwl.fleet.report.repository.ReportRepository;
import org.springframework.boot.test.mock.mockito.SpyBean;
import com.dfwl.fleet.salary.imports.SalaryImportHandler;
import com.dfwl.fleet.route.repository.RouteRepository;
import com.dfwl.fleet.salary.repository.DriverSalaryRepository;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.dao.DataAccessResourceFailureException;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

import static org.assertj.core.api.Assertions.assertThat;
import com.dfwl.fleet.imports.dto.request.ImportPreviewRequest;
import com.dfwl.fleet.imports.repository.ImportRepository;
import com.dfwl.fleet.imports.service.ImportRowCommitService;
import com.dfwl.fleet.imports.service.ImportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:salarymigration;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.flyway.enabled=false", "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-auth-test.sql"
})
class SalaryImportMigrationTests {
    @Autowired private JdbcTemplate jdbc;
    @SpyBean private ImportRepository repository;
    @Autowired private ImportRowCommitService rows;
    @Autowired private ImportService imports;
    @Autowired private ObjectMapper mapper;

    @BeforeEach
    void setup() {
        for (String table : List.of("driver_salary_history", "driver_salary_entry", "import_row", "import_task",
                "route_task", "driver")) jdbc.update("DELETE FROM " + table);
        jdbc.update("INSERT INTO driver (id, name, phone, driver_type, status) VALUES (1, 'A', '13800000001', 'INTERNAL', 1), (2, 'B', '13800000002', 'INTERNAL', 1)");
        for (long id : new long[]{10, 20}) jdbc.update("""
                INSERT INTO route_task (id, route_no, business_unique_key, business_date, customer_id, product_id,
                                        direction, loading_place, unloading_place, tax_unit_price, status, assigned_driver_id, created_by)
                VALUES (?, ?, ?, '2026-09-18', 1, 1, 'OUTBOUND', 'A', 'B', 1, 'COMPLETED', 1, 1)
                """, id, "R" + id, "KEY" + id);
    }

    @ParameterizedTest
    @ValueSource(strings = {"valid", "alias", "zero", "negative", "routeMissing", "routeBad", "driverMissing", "driverBad",
            "amountMissing", "amountBad", "routeAbsent", "driverMismatch", "explicitRouteAbsent", "explicitRouteMissing",
            "businessDuplicate", "explicitBusinessDuplicate", "malformedExplicitDuplicate", "historyDuplicate", "batchDuplicate",
            "duplicateBeforeInvalidAmount"})
    void salaryBehaviorCharacterization(String scenario) throws Exception {
        Map<String, Object> raw = new HashMap<>(Map.of("routeId", 10, "driverId", 1, "salaryAmount", "88.50"));
        String explicit = null;
        String previewKey = "SALARY|10", commitKey = "SALARY|10";
        String code = null, message = null;
        boolean existing = false, previewSuccess = true;
        switch (scenario) {
            case "alias" -> { raw.remove("salaryAmount"); raw.put("amount", "88.50"); }
            case "zero", "negative" -> { raw.put("salaryAmount", scenario.equals("zero") ? "0" : "-1"); code = "IMPORT_FORMAT_ERROR"; message = "工资金额必须大于0"; }
            case "routeMissing", "explicitRouteMissing" -> { raw.remove("routeId"); code = "IMPORT_FORMAT_ERROR"; message = "routeId不能为空"; previewKey = null; commitKey = null; if (scenario.startsWith("explicit")) explicit = "CUSTOM"; }
            case "routeBad" -> { raw.put("routeId", "bad"); code = "IMPORT_FORMAT_ERROR"; message = "routeId格式错误"; previewKey = null; commitKey = null; }
            case "driverMissing" -> { raw.remove("driverId"); code = "IMPORT_FORMAT_ERROR"; message = "driverId不能为空"; previewKey = null; commitKey = null; }
            case "driverBad" -> { raw.put("driverId", "bad"); code = "IMPORT_FORMAT_ERROR"; message = "driverId格式错误"; previewKey = null; commitKey = null; }
            case "amountMissing" -> { raw.remove("salaryAmount"); code = "IMPORT_FORMAT_ERROR"; message = "amount不能为空"; previewKey = null; commitKey = null; }
            case "amountBad" -> { raw.put("salaryAmount", "bad"); code = "IMPORT_FORMAT_ERROR"; message = "salaryAmount格式错误"; previewKey = null; commitKey = null; }
            case "routeAbsent", "explicitRouteAbsent" -> { raw.put("routeId", 99); code = "ROUTE_001"; message = "线路不存在"; previewKey = "SALARY|99"; commitKey = "SALARY|99"; if (scenario.startsWith("explicit")) { explicit = " CUSTOM "; commitKey = "CUSTOM"; } }
            case "driverMismatch" -> { raw.put("driverId", 2); code = "ROUTE_003"; message = "线路司机不匹配"; }
            case "businessDuplicate", "explicitBusinessDuplicate", "malformedExplicitDuplicate", "duplicateBeforeInvalidAmount" -> {
                existing = true; code = "IMPORT_DUPLICATE_EXISTING"; message = "业务数据已存在";
                if (scenario.equals("explicitBusinessDuplicate")) { explicit = " CUSTOM "; previewKey = "CUSTOM"; commitKey = "CUSTOM"; }
                if (scenario.equals("malformedExplicitDuplicate")) { explicit = "SALARY|bad"; previewKey = explicit; commitKey = explicit; }
                if (scenario.equals("duplicateBeforeInvalidAmount")) raw.put("salaryAmount", "bad");
            }
            case "historyDuplicate" -> { code = "IMPORT_DUPLICATE_EXISTING"; message = "业务数据已存在"; long old = task(); repository.insertRow(old, 1, "{}", "{}", "SUCCESS", null, null, "SALARY", "SALARY|10"); long row = repository.findRows(old).get(0).id(); repository.markRowFinal(row, "SUCCESS", null, null, "SALARY", null, "SALARY|10"); }
            case "batchDuplicate" -> { code = "IMPORT_DUPLICATE_IN_BATCH"; message = "导入批次内重复"; }
            default -> { }
        }
        if (existing) jdbc.update("""
                INSERT INTO driver_salary_entry (route_id, driver_id, business_month, business_date, salary_amount, source_type, created_by)
                VALUES (10, 1, '2026-09', '2026-09-18', 100, 'MANUAL', 1)
                """);
        previewSuccess = code == null || scenario.equals("explicitBusinessDuplicate") || scenario.equals("malformedExplicitDuplicate");
        var batch = new HashSet<String>();
        if (scenario.equals("batchDuplicate")) batch.add("SALARY|10");
        var preview = rows.preparePreview("SALARY", new ImportPreviewRequest.ImportRowRequest(1, raw, explicit), batch);
        assertThat(preview.validation().success()).isEqualTo(previewSuccess);
        assertThat(preview.businessUniqueKey()).isEqualTo(previewKey);
        assertThat(preview.validation().errorCode()).isEqualTo(previewSuccess ? null : code);
        assertThat(preview.validation().errorMessage()).isEqualTo(previewSuccess ? null : message);
        // Preserve the supplied key here to characterize commit independently of preview persistence.
        long task = task();
        repository.insertRow(task, 1, mapper.writeValueAsString(raw), null, "SUCCESS", null, null, "SALARY", explicit);
        var input = repository.findRows(task).get(0);
        var commitBatch = new HashSet<String>();
        if (scenario.equals("batchDuplicate")) commitBatch.add("SALARY|10");
        rows.commitRow("SALARY", input, commitBatch, 7);
        var committed = repository.findRows(task).get(0);
        assertThat(committed.finalStatus()).isEqualTo(code == null ? "SUCCESS" : "FAILURE");
        assertThat(committed.finalErrorCode()).isEqualTo(code);
        assertThat(committed.finalErrorMessage()).isEqualTo(message);
        assertThat(committed.businessUniqueKey()).isEqualTo(commitKey);
        if (code == null) {
            long id = jdbc.queryForObject("SELECT id FROM driver_salary_entry WHERE route_id = 10", Long.class);
            assertThat(committed.businessId()).isEqualTo(id);
            assertThat(jdbc.queryForObject("SELECT salary_amount FROM driver_salary_entry WHERE id = ?", BigDecimal.class, id)).isEqualByComparingTo("88.50");
            assertThat(jdbc.queryForObject("SELECT import_task_id FROM driver_salary_entry WHERE id = ?", Long.class, id)).isEqualTo(task);
            assertThat(jdbc.queryForObject("SELECT import_row_id FROM driver_salary_entry WHERE id = ?", Long.class, id)).isEqualTo(input.id());
            assertThat(jdbc.queryForObject("SELECT salary_entry_id FROM driver_salary_history", Long.class)).isEqualTo(id);
            assertThat(jdbc.queryForObject("SELECT after_amount FROM driver_salary_history", BigDecimal.class)).isEqualByComparingTo("88.50");
            assertThat(jdbc.queryForObject("SELECT reason FROM driver_salary_history", String.class)).isEqualTo("工资导入");
            assertThat(jdbc.queryForObject("SELECT driver_salary FROM route_task WHERE id = 10", BigDecimal.class)).isEqualByComparingTo("88.50");
            assertThat(jdbc.queryForObject("SELECT salary_source FROM route_task WHERE id = 10", String.class)).isEqualTo("IMPORT");
        } else {
            assertThat(committed.businessId()).isNull();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM driver_salary_entry", Integer.class)).isEqualTo(existing ? 1 : 0);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM driver_salary_history", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT driver_salary FROM route_task WHERE id = 10", BigDecimal.class)).isNull();
        }
    }

    @Autowired private List<ImportBusinessHandler> handlers;
    @Autowired private ReportRepository reports;
    @SpyBean
    private SalaryImportHandler salaryHandler;
    @SpyBean
    private RouteRepository routeRepository;
    @SpyBean
    private DriverSalaryRepository salaryRepository;

    @Test
    void discoversSalaryHandlerWithoutConcreteImportsAndRejectsBadRegistrations() {
        assertThat(handlers).hasSize(4);
        assertThat(handlers.stream().map(ImportBusinessHandler::businessType)).containsExactlyInAnyOrder(ImportBusinessType.SALARY, ImportBusinessType.TIRE, ImportBusinessType.EXPENSE, ImportBusinessType.ROUTE);
        rows.preparePreview("SALARY", new ImportPreviewRequest.ImportRowRequest(1,
                Map.of("routeId", 10, "driverId", 1, "salaryAmount", "10"), null), new HashSet<>());
        verify(salaryHandler).resolveBusinessKey(any());
        verify(salaryHandler).validateDuplicate(any());
        verify(salaryHandler).validate(any());
        clearInvocations(salaryHandler);
        for (String type : List.of("ROUTE", "TIRE", "EXPENSE")) {
            rows.preparePreview(type, new ImportPreviewRequest.ImportRowRequest(1, Map.of(), null), new HashSet<>());
        }
        verifyNoInteractions(salaryHandler);
        assertThatThrownBy(() -> new ImportRowCommitService(repository, mapper, List.of()))
                .isInstanceOf(IllegalStateException.class).hasMessage("Missing import handler: SALARY");
        assertThatThrownBy(() -> new ImportRowCommitService(repository, mapper,
                List.of(salaryHandler, salaryHandler)))
                .isInstanceOf(IllegalStateException.class).hasMessage("Duplicate import handler: SALARY");
    }

    @ParameterizedTest
    @ValueSource(strings = {"SALARY", "DRIVER", "DELETED"})
    void commitRevalidatesChangesAfterSuccessfulPreview(String change) throws Exception {
        long task = preparedTask(10);
        switch (change) {
            case "SALARY" -> jdbc.update("""
                    INSERT INTO driver_salary_entry (route_id, driver_id, business_month, business_date, salary_amount, source_type, created_by)
                    VALUES (10, 1, '2026-09', '2026-09-18', 100, 'MANUAL', 1)
                    """);
            case "DRIVER" -> jdbc.update("UPDATE route_task SET assigned_driver_id = 2 WHERE id = 10");
            case "DELETED" -> jdbc.update("UPDATE route_task SET deleted_at = CURRENT_TIMESTAMP WHERE id = 10");
            default -> throw new IllegalArgumentException(change);
        }
        var result = imports.commit(task, 7);
        assertThat(result.failureCount()).isEqualTo(1);
        assertThat(result.rows().get(0).finalErrorCode()).isEqualTo(switch (change) {
            case "SALARY" -> "IMPORT_DUPLICATE_EXISTING";
            case "DRIVER" -> "ROUTE_003";
            default -> "ROUTE_001";
        });
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM driver_salary_history", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT driver_salary FROM route_task WHERE id = 10", BigDecimal.class)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ENTRY", "HISTORY", "ROUTE", "FINAL_ROW"})
    void failedSalaryRowRollsBackAllWritesAndOtherRowsStillCommit(String stage) throws Exception {
        long task = preparedTask(10, 20);
        long firstRowId = repository.findRows(task).get(0).id();
        Answer<Object> failure = invocation -> {
            invocation.callRealMethod();
            throw new DataAccessResourceFailureException("injected " + stage);
        };
        switch (stage) {
            case "FINAL_ROW" -> doAnswer(failure).when(repository).markRowFinal(
                    eq(firstRowId), eq("SUCCESS"),
                    any(), any(), eq("SALARY"), any(), any());
            case "ENTRY" -> doAnswer(failure).when(salaryRepository).insertEntry(
                    eq(10L), anyLong(), anyString(),
                    any(), any(), anyString(),
                    any(), any(), anyLong());
            case "HISTORY" -> doAnswer(failure).when(salaryRepository).insertHistory(
                    any(), eq(10L), anyLong(),
                    any(), any(), any(),
                    anyString(), anyLong(), any(),
                    any(), any());
            case "ROUTE" -> doAnswer(failure).when(routeRepository).updateRouteSalary(
                    eq(10L), any(), anyString(),
                    anyLong());
            default -> throw new IllegalArgumentException(stage);
        }
        var committed = imports.commit(task, 7);
        assertThat(committed.successCount()).isEqualTo(1);
        assertThat(committed.failureCount()).isEqualTo(1);
        assertThat(committed.rows().get(0).finalStatus()).isEqualTo("FAILURE");
        assertThat(committed.rows().get(0).finalErrorCode()).isEqualTo("IMPORT_DB_ERROR");
        assertThat(committed.rows().get(0).finalErrorMessage()).isEqualTo("数据库写入失败");
        assertThat(committed.rows().get(0).businessId()).isNull();
        assertThat(committed.rows().get(1).finalStatus()).isEqualTo("SUCCESS");
        assertThat(jdbc.queryForList("SELECT route_id FROM driver_salary_entry", Long.class)).containsExactly(20L);
        assertThat(jdbc.queryForList("SELECT route_id FROM driver_salary_history", Long.class)).containsExactly(20L);
        assertThat(jdbc.queryForObject("SELECT driver_salary FROM route_task WHERE id = 10", BigDecimal.class)).isNull();
        assertThat(jdbc.queryForObject("SELECT salary_source FROM route_task WHERE id = 10", String.class)).isNull();
        assertThat(jdbc.queryForObject("SELECT driver_salary FROM route_task WHERE id = 20", BigDecimal.class)).isEqualByComparingTo("88.50");
        assertThat(reports.driverSalary()).isEqualByComparingTo("88.50");
        var before = repository.findRows(task);
        imports.commit(task, 7);
        assertThat(repository.findRows(task)).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM driver_salary_history", Integer.class)).isEqualTo(1);
    }

    private long preparedTask(long... routes) throws Exception {
        long task = task();
        var keys = new HashSet<String>();
        int number = 0;
        for (long route : routes) {
            Map<String, Object> raw = Map.of("routeId", route, "driverId", 1, "salaryAmount", "88.50");
            var preview = rows.preparePreview("SALARY", new ImportPreviewRequest.ImportRowRequest(++number, raw, null), keys);
            assertThat(preview.validation().success()).isTrue();
            repository.insertRow(task, number, mapper.writeValueAsString(raw), mapper.writeValueAsString(raw),
                    "SUCCESS", null, null, "SALARY", preview.businessUniqueKey());
        }
        return task;
    }

    private long task() { return repository.createTask(UUID.randomUUID().toString(), "SALARY", null, 1L, 1, 0, 0, 7); }
}
