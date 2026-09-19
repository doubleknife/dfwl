package com.dfwl.fleet.salary;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

import com.dfwl.fleet.common.error.BusinessException;
import com.dfwl.fleet.report.repository.ReportRepository;
import com.dfwl.fleet.route.repository.RouteRepository;
import com.dfwl.fleet.route.service.RouteSalaryService;
import com.dfwl.fleet.salary.dto.request.DriverSalaryRequest;
import com.dfwl.fleet.salary.repository.DriverSalaryRepository;
import com.dfwl.fleet.salary.service.DriverSalaryService;
import java.math.BigDecimal;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.stubbing.Answer;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;


import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dfwl.fleet.security.AuthenticatedUser;
import com.dfwl.fleet.security.TokenAuthenticationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:salarydb;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-auth-test.sql",
        "fleet.storage.local-root=${java.io.tmpdir}/fleet-salary-import-tests"
})
@AutoConfigureMockMvc
class DriverSalaryApiTests {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private TokenAuthenticationService tokenAuthenticationService;
    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;
    private String driverAToken;
    private String driverBToken;
    private String outsourcedToken;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM settlement_diff");
        jdbcTemplate.update("DELETE FROM settlement_detail");
        jdbcTemplate.update("DELETE FROM settlement_version");
        jdbcTemplate.update("DELETE FROM settlement_month");
        jdbcTemplate.update("DELETE FROM driver_salary_history");
        jdbcTemplate.update("DELETE FROM driver_salary_entry");
        jdbcTemplate.update("DELETE FROM import_row");
        jdbcTemplate.update("DELETE FROM import_task");
        jdbcTemplate.update("DELETE FROM file_attachment");
        jdbcTemplate.update("DELETE FROM route_weight_version");
        jdbcTemplate.update("DELETE FROM route_status_history");
        jdbcTemplate.update("DELETE FROM route_task");
        jdbcTemplate.update("DELETE FROM driver_vehicle_current");
        jdbcTemplate.update("DELETE FROM vehicle");
        jdbcTemplate.update("DELETE FROM driver");
        jdbcTemplate.update("DELETE FROM product");
        jdbcTemplate.update("DELETE FROM customer");

        jdbcTemplate.update("INSERT INTO customer (id, customer_name, status) VALUES (1, '客户A', 1)");
        jdbcTemplate.update("INSERT INTO product (id, product_name, status) VALUES (1, '砂石', 1)");
        jdbcTemplate.update("""
                INSERT INTO driver (id, user_id, driver_type, name, phone, status)
                VALUES (1, 101, 'INTERNAL', 'Driver A', '13800000001', 1),
                       (2, 102, 'INTERNAL', 'Driver B', '13800000002', 1),
                       (3, 103, 'OUTSOURCED', 'Driver C', '13800000003', 1)
                """);
        jdbcTemplate.update("""
                INSERT INTO vehicle (id, plate_no, insurance_complete, energy_type, max_load, load_standard_type,
                                     load_standard_percent, status)
                VALUES (1, '京A10001', 1, 'ELECTRIC', 30.000, 'PERCENT', 0.9000, 1),
                       (2, '京A10002', 1, 'GAS', 30.000, 'PERCENT', 0.9000, 1),
                       (3, '京A10003', 1, 'ELECTRIC', 30.000, 'PERCENT', 0.9000, 1)
                """);
        jdbcTemplate.update("INSERT INTO driver_vehicle_current (driver_id, vehicle_id, bound_at, bound_by) VALUES (1, 1, CURRENT_TIMESTAMP, 1)");
        jdbcTemplate.update("INSERT INTO driver_vehicle_current (driver_id, vehicle_id, bound_at, bound_by) VALUES (2, 2, CURRENT_TIMESTAMP, 1)");
        jdbcTemplate.update("INSERT INTO driver_vehicle_current (driver_id, vehicle_id, bound_at, bound_by) VALUES (3, 3, CURRENT_TIMESTAMP, 1)");
        seedCompletedRoute(1, 1, "2026-09-10", null);
        seedCompletedRoute(2, 2, "2026-09-11", null);
        seedCompletedRoute(3, 1, "2026-08-31", null);

        adminToken = tokenAuthenticationService.issueToken(new AuthenticatedUser(1L, "admin", 1L, "ADMIN", "管理员", Set.of(
                "salary:manage", "salary:view", "import:preview", "import:commit",
                "report:profit", "settlement:view", "settlement:generate", "settlement:diff:view")));
        driverAToken = tokenAuthenticationService.issueToken(new AuthenticatedUser(101L, "13800000001", 2L, "DRIVER", "司机", Set.of("salary:mine")));
        driverBToken = tokenAuthenticationService.issueToken(new AuthenticatedUser(102L, "13800000002", 2L, "DRIVER", "司机", Set.of("salary:mine")));
        outsourcedToken = tokenAuthenticationService.issueToken(new AuthenticatedUser(103L, "13800000003", 2L, "DRIVER", "司机", Set.of("salary:mine")));
    }

    @Test
    void manualSalaryEntryWritesSourceAndRouteFinalSalary() throws Exception {
        postSalary(1, 1, "120.50", "人工录入")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.routeId").value(1))
                .andExpect(jsonPath("$.data.driverId").value(1))
                .andExpect(jsonPath("$.data.salaryAmount").value(120.50))
                .andExpect(jsonPath("$.data.sourceType").value("MANUAL"));

        assertThat(jdbcTemplate.queryForObject("SELECT driver_salary FROM route_task WHERE id = 1", String.class))
                .isEqualTo("120.50");
        var entry = salaryRepository.findByRoute(1).orElseThrow();
        assertThat(entry.salaryAmount()).isEqualByComparingTo("120.50");
        assertThat(entry.sourceType()).isEqualTo("MANUAL");
        var history = salaryService.history(1).get(0);
        assertThat(history.beforeAmount()).isNull();
        assertThat(history.afterAmount()).isEqualByComparingTo("120.50");
        assertThat(history.afterSourceType()).isEqualTo("MANUAL");
        assertThat(history.operatorId()).isEqualTo(1);
        assertThat(history.reason()).isEqualTo("人工录入");
        assertThat(jdbcTemplate.queryForObject("SELECT salary_source FROM route_task WHERE id = 1", String.class)).isEqualTo("MANUAL");
        assertThat(jdbcTemplate.queryForObject("SELECT updated_by FROM route_task WHERE id = 1", Long.class)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("SELECT updated_at FROM route_task WHERE id = 1", java.sql.Timestamp.class)).isNotNull();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM driver_salary_history WHERE route_id = 1", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void salaryImportCommitsBusinessRecord() throws Exception {
        long taskId = previewSalaryImport(1, 1, "88.00");

        mockMvc.perform(post("/api/v1/imports/%d/commit".formatted(taskId)).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.successCount").value(1))
                .andExpect(jsonPath("$.data.rows[0].businessType").value("SALARY"));

        assertThat(jdbcTemplate.queryForObject("SELECT salary_amount FROM driver_salary_entry WHERE route_id = 1", String.class))
                .isEqualTo("88.00");
        assertThat(jdbcTemplate.queryForObject("SELECT source_type FROM driver_salary_entry WHERE route_id = 1", String.class))
                .isEqualTo("IMPORT");
        assertThat(jdbcTemplate.queryForObject("SELECT driver_salary FROM route_task WHERE id = 1", BigDecimal.class)).isEqualByComparingTo("88.00");
        assertThat(jdbcTemplate.queryForObject("SELECT salary_source FROM route_task WHERE id = 1", String.class)).isEqualTo("IMPORT");
        var history = salaryService.history(1).get(0);
        assertThat(history.afterSourceType()).isEqualTo("IMPORT");
        assertThat(history.afterAmount()).isEqualByComparingTo("88.00");
        assertThat(history.importTaskId()).isEqualTo(taskId);
        assertThat(history.importRowId()).isNotNull();
    }

    @Test
    void duplicateSalaryImportFails() throws Exception {
        long firstTask = previewSalaryImport(1, 1, "88.00");
        mockMvc.perform(post("/api/v1/imports/%d/commit".formatted(firstTask)).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        long secondTask = previewSalaryImport(1, 1, "99.00");
        mockMvc.perform(post("/api/v1/imports/%d/commit".formatted(secondTask)).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.failureCount").value(1))
                .andExpect(jsonPath("$.data.rows[0].finalErrorCode").value("IMPORT_DUPLICATE_EXISTING"));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM driver_salary_entry WHERE route_id = 1", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void oneRouteKeepsOneEffectiveSalary() throws Exception {
        postSalary(1, 1, "100.00", "首次").andExpect(status().isOk());
        postSalary(1, 1, "130.00", "调整").andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM driver_salary_entry WHERE route_id = 1", Integer.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT driver_salary FROM route_task WHERE id = 1", String.class))
                .isEqualTo("130.00");
    }

    @Test
    void salaryModificationKeepsHistory() throws Exception {
        postSalary(1, 1, "100.00", "首次").andExpect(status().isOk());
        postSalary(1, 1, "130.00", "调整原因").andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/driver-salaries/routes/1/history").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[1].beforeAmount").value(100.00))
                .andExpect(jsonPath("$.data[1].afterAmount").value(130.00))
                .andExpect(jsonPath("$.data[1].reason").value("调整原因"));
    }

    @Test
    void salaryMonthUsesRouteBusinessDate() throws Exception {
        postSalary(3, 1, "77.00", "补录八月").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.businessMonth").value("2026-08"));
    }

    @Test
    void salaryChangeRecalculatesRealtimeProfit() throws Exception {
        postSalary(1, 1, "100.00", "首次").andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/reports/profit").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.routeId==1)].profitAmount").value(hasItem(300.0)));

        postSalary(1, 1, "130.00", "调薪").andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/reports/profit").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.routeId==1)].profitAmount").value(hasItem(270.0)));
    }

    @Test
    void oldSettlementVersionDoesNotChangeAfterSalaryUpdate() throws Exception {
        seedSalaryEntry(1, 1, "2026-09", "2026-09-10", "100.00");
        long v1 = generateSettlement("2026-09");
        String before = detailJson(v1, 1);

        postSalary(1, 1, "150.00", "历史工资调整").andExpect(status().isOk());
        assertThat(detailJson(v1, 1)).isEqualTo(before);
    }

    @Test
    void newSettlementVersionContainsSalaryDiff() throws Exception {
        seedSalaryEntry(1, 1, "2026-09", "2026-09-10", "100.00");
        generateSettlement("2026-09");
        postSalary(1, 1, "150.00", "历史工资调整").andExpect(status().isOk());
        long v2 = generateSettlement("2026-09");
        assertThat(objectMapper.readTree(detailJson(v2, 1)).path("driverSalary").decimalValue()).isEqualByComparingTo("150.00");

        mockMvc.perform(get("/api/v1/settlements/versions/%d/diff".formatted(v2)).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.sourceId==1 && @.changeType=='MODIFY')].businessOperatorId").value(1))
                .andExpect(jsonPath("$.data[?(@.sourceId==1 && @.changeType=='MODIFY')].businessReason").value("历史工资调整"));
    }

    @Test
    void internalDriverCanViewOnlyOwnSalary() throws Exception {
        seedSalaryEntry(1, 1, "2026-09", "2026-09-10", "100.00");
        seedSalaryEntry(2, 2, "2026-09", "2026-09-11", "200.00");

        mockMvc.perform(get("/api/v1/driver-salaries/me").header("Authorization", "Bearer " + driverAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].routeId").value(1));
    }

    @Test
    void outsourcedDriverSalarySlipIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/driver-salaries/me").header("Authorization", "Bearer " + outsourcedToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_003"));
    }

    @Test
    void driverCannotViewAnotherDriversSalary() throws Exception {
        seedSalaryEntry(2, 2, "2026-09", "2026-09-11", "200.00");

        mockMvc.perform(get("/api/v1/driver-salaries/drivers/2").header("Authorization", "Bearer " + driverAToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_003"));

        mockMvc.perform(get("/api/v1/driver-salaries/drivers/2").header("Authorization", "Bearer " + driverBToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Autowired private DriverSalaryService salaryService;
    @Autowired private RouteSalaryService routeSalaryService;
    @Autowired private ReportRepository reportRepository;
    @Autowired private PlatformTransactionManager transactionManager;
    @SpyBean private DriverSalaryRepository salaryRepository;
    @SpyBean private RouteRepository routeRepository;

    @ParameterizedTest
    @CsvSource({"false,ENTRY", "true,ENTRY", "false,HISTORY", "true,HISTORY", "false,ROUTE", "true,ROUTE"})
    void salaryWritesRemainAtomicAfterAnyWriteFails(boolean adjustment, String failingStep) {
        if (adjustment) salaryService.upsertManual(salaryRequest("100.00"), 1);
        var entries = jdbcTemplate.queryForList("SELECT * FROM driver_salary_entry ORDER BY id");
        var histories = jdbcTemplate.queryForList("SELECT * FROM driver_salary_history ORDER BY id");
        var routes = jdbcTemplate.queryForList("SELECT * FROM route_task ORDER BY id");
        Answer<Object> failAfterWrite = invocation -> {
            invocation.callRealMethod();
            throw new DataAccessResourceFailureException("injected " + failingStep);
        };
        switch (failingStep) {
            case "ENTRY" -> {
                if (adjustment) {
                    doAnswer(failAfterWrite).when(salaryRepository).updateEntry(anyLong(), anyLong(), anyString(),
                            any(), any(), anyString(), any(), any(), anyLong());
                } else {
                    doAnswer(failAfterWrite).when(salaryRepository).insertEntry(anyLong(), anyLong(), anyString(),
                            any(), any(), anyString(), any(), any(), anyLong());
                }
            }
            case "HISTORY" -> doAnswer(failAfterWrite).when(salaryRepository).insertHistory(any(), anyLong(), anyLong(),
                    any(), any(), any(), anyString(), anyLong(), any(), any(), any());
            case "ROUTE" -> doAnswer(failAfterWrite).when(routeRepository)
                    .updateRouteSalary(anyLong(), any(), anyString(), anyLong());
            default -> throw new IllegalArgumentException(failingStep);
        }
        assertThatThrownBy(() -> salaryService.upsertManual(salaryRequest("150.00"), 2))
                .isInstanceOf(DataAccessResourceFailureException.class).hasMessage("injected " + failingStep);
        assertThat(jdbcTemplate.queryForList("SELECT * FROM driver_salary_entry ORDER BY id")).isEqualTo(entries);
        assertThat(jdbcTemplate.queryForList("SELECT * FROM driver_salary_history ORDER BY id")).isEqualTo(histories);
        assertThat(jdbcTemplate.queryForList("SELECT * FROM route_task ORDER BY id")).isEqualTo(routes);
    }

    @Test
    void laterHistoryFailureRollsBackAlreadySynchronizedRouteInSameTransaction() {
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).execute(status -> {
            var salary = salaryService.upsertManual(salaryRequest("120.00"), 1);
            assertThat(jdbcTemplate.queryForObject("SELECT driver_salary FROM route_task WHERE id = 1", BigDecimal.class))
                    .isEqualByComparingTo("120.00");
            // Force a real NOT NULL history failure after the route has already been synchronized.
            salaryRepository.insertHistory(salary.id(), 1, 1, new BigDecimal("120.00"), null,
                    "MANUAL", "MANUAL", 1, "late history failure", null, null);
            return null;
        })).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM driver_salary_entry", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM driver_salary_history", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT driver_salary FROM route_task WHERE id = 1", BigDecimal.class)).isNull();
        assertThat(jdbcTemplate.queryForObject("SELECT salary_source FROM route_task WHERE id = 1", String.class)).isNull();
        assertThat(jdbcTemplate.queryForObject("SELECT updated_by FROM route_task WHERE id = 1", Long.class)).isNull();
    }

    @Test
    void concurrentAdjustmentsKeepEntryHistoryAndRouteInOneSerializedOrder() throws Exception {
        salaryService.upsertManual(salaryRequest("100.00"), 1);
        var barrier = new CyclicBarrier(2);
        doAnswer(invocation -> {
            barrier.await(10, TimeUnit.SECONDS);
            return invocation.callRealMethod();
        }).when(salaryRepository).findEntryByRouteForUpdate(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> salaryService.upsertManual(salaryRequest("130.00"), 2));
            var second = executor.submit(() -> salaryService.upsertManual(salaryRequest("170.00"), 3));
            assertThat(first.get(20, TimeUnit.SECONDS).salaryAmount()).isEqualByComparingTo("130.00");
            assertThat(second.get(20, TimeUnit.SECONDS).salaryAmount()).isEqualByComparingTo("170.00");
        }
        var history = salaryService.history(1);
        assertThat(history).hasSize(3);
        assertThat(history.get(1).beforeAmount()).isEqualByComparingTo("100.00");
        assertThat(history.get(2).beforeAmount()).isEqualByComparingTo(history.get(1).afterAmount());
        BigDecimal finalSalary = history.get(2).afterAmount();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM driver_salary_entry WHERE route_id = 1", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT salary_amount FROM driver_salary_entry WHERE route_id = 1", BigDecimal.class))
                .isEqualByComparingTo(finalSalary);
        assertThat(jdbcTemplate.queryForObject("SELECT driver_salary FROM route_task WHERE id = 1", BigDecimal.class))
                .isEqualByComparingTo(finalSalary);
        assertThat(jdbcTemplate.queryForObject("SELECT updated_by FROM route_task WHERE id = 1", Long.class))
                .isEqualTo(history.get(2).operatorId());
        assertThat(jdbcTemplate.queryForObject("SELECT salary_source FROM route_task WHERE id = 1", String.class)).isEqualTo("MANUAL");
    }

    @Test
    void missingAndDeletedRouteSynchronizationRetainsOriginalNoOpSemantics() {
        jdbcTemplate.update("UPDATE route_task SET deleted_at = CURRENT_TIMESTAMP WHERE id = 1");
        var routes = jdbcTemplate.queryForList("SELECT * FROM route_task ORDER BY id");
        routeSalaryService.updateSalary(Long.MAX_VALUE, new BigDecimal("10.00"), "MANUAL", 1);
        routeSalaryService.updateSalary(1, new BigDecimal("10.00"), "MANUAL", 1);
        assertThat(jdbcTemplate.queryForList("SELECT * FROM route_task ORDER BY id")).isEqualTo(routes);
        for (long id : new long[]{1L, Long.MAX_VALUE}) {
            assertThatThrownBy(() -> salaryService.upsertManual(new DriverSalaryRequest(id, 1L, new BigDecimal("10.00"), "missing"), 1))
                    .isInstanceOf(BusinessException.class).extracting("code").isEqualTo("ROUTE_001");
        }
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM driver_salary_entry", Integer.class)).isZero();
    }

    @Test
    void companyProfitTracksSalaryAdjustmentsWithoutChangingIncomeOrOtherExpenses() {
        salaryService.upsertManual(salaryRequest("100.00"), 1);
        var before = reportRepository.dashboard();
        salaryService.upsertManual(salaryRequest("130.00"), 2);
        var after = reportRepository.dashboard();
        assertThat((BigDecimal) after.get("companyTotalProfit"))
                .isEqualByComparingTo(((BigDecimal) before.get("companyTotalProfit")).subtract(new BigDecimal("30.00")));
        assertThat(after.get("incomeAmount")).isEqualTo(before.get("incomeAmount"));
        assertThat(after.get("routeExpense")).isEqualTo(before.get("routeExpense"));
        assertThat(after.get("dailyExpense")).isEqualTo(before.get("dailyExpense"));
    }

    private DriverSalaryRequest salaryRequest(String amount) {
        return new DriverSalaryRequest(1L, 1L, new BigDecimal(amount), "工资测试");
    }

    private org.springframework.test.web.servlet.ResultActions postSalary(long routeId, long driverId, String amount, String reason) throws Exception {
        return mockMvc.perform(post("/api/v1/driver-salaries")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"routeId":%d,"driverId":%d,"salaryAmount":"%s","reason":"%s"}
                        """.formatted(routeId, driverId, amount, reason)));
    }

    private long previewSalaryImport(long routeId, long driverId, String amount) throws Exception {
        byte[] csv = """
                routeId,driverId,salaryAmount
                %d,%d,%s
                """.formatted(routeId, driverId, amount).getBytes(StandardCharsets.UTF_8);
        MvcResult upload = mockMvc.perform(multipart("/api/v1/attachments")
                        .file(new MockMultipartFile("file", "salary.csv", "text/csv", csv))
                        .param("ownerType", "IMPORT").param("ownerId", "0").param("purpose", "IMPORT_FILE")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andReturn();
        long fileId = objectMapper.readTree(upload.getResponse().getContentAsByteArray()).path("data").path("id").asLong();
        MvcResult result = mockMvc.perform(post("/api/v1/imports/preview")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"businessType":"SALARY","originalFileId":%d}
                                """.formatted(fileId)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        return response.path("data").path("id").asLong();
    }

    private long generateSettlement(String yearMonth) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/settlements/%s/versions".formatted(yearMonth))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray()).path("data").path("id").asLong();
    }

    private String detailJson(long versionId, long routeId) {
        return jdbcTemplate.queryForObject("""
                SELECT snapshot_json FROM settlement_detail
                WHERE settlement_version_id = ? AND fact_type = 'ROUTE' AND source_id = ?
                """, String.class, versionId, routeId);
    }

    private void seedCompletedRoute(long routeId, long driverId, String businessDate, String salary) {
        jdbcTemplate.update("""
                INSERT INTO route_task (id, route_no, business_unique_key, business_date, customer_id, product_id,
                                        direction, loading_place, unloading_place, tax_unit_price, status,
                                        assigned_driver_id, departure_driver_id, departure_vehicle_id, departure_time,
                                        unload_time, effective_weight_version_id, driver_salary, salary_source, created_by)
                VALUES (?, ?, ?, ?, 1, 1, 'OUTBOUND', 'A', 'B', 20.0000, 'COMPLETED',
                        ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?, ?, ?, 1)
                """, routeId, "R" + routeId, "r" + routeId, businessDate, driverId, driverId, driverId,
                routeId, salary == null ? null : new java.math.BigDecimal(salary), salary == null ? null : "MANUAL");
        jdbcTemplate.update("""
                INSERT INTO route_weight_version (id, route_id, version_no, gross_weight, tare_weight, net_weight,
                                                  load_standard_threshold, load_standard_met, source_type, operator_id)
                VALUES (?, ?, 1, 30.000, 10.000, 20.000, 27.000, 0, 'UNLOAD', 101)
                """, routeId, routeId);
    }

    private void seedSalaryEntry(long routeId, long driverId, String month, String businessDate, String amount) {
        jdbcTemplate.update("""
                INSERT INTO driver_salary_entry (route_id, driver_id, business_month, business_date, salary_amount,
                                                 source_type, created_by)
                VALUES (?, ?, ?, ?, ?, 'MANUAL', 1)
                """, routeId, driverId, month, businessDate, new java.math.BigDecimal(amount));
        jdbcTemplate.update("UPDATE route_task SET driver_salary = ?, salary_source = 'MANUAL' WHERE id = ?",
                new java.math.BigDecimal(amount), routeId);
    }

}
