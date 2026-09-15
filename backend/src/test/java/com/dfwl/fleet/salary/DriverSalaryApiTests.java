package com.dfwl.fleet.salary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dfwl.fleet.security.AuthenticatedUser;
import com.dfwl.fleet.security.TokenAuthenticationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
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
        seedImportFile(1);

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

    private org.springframework.test.web.servlet.ResultActions postSalary(long routeId, long driverId, String amount, String reason) throws Exception {
        return mockMvc.perform(post("/api/v1/driver-salaries")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"routeId":%d,"driverId":%d,"salaryAmount":"%s","reason":"%s"}
                        """.formatted(routeId, driverId, amount, reason)));
    }

    private long previewSalaryImport(long routeId, long driverId, String amount) throws Exception {
        Path importFile = Path.of(System.getProperty("java.io.tmpdir"), "fleet-salary-import-tests", "import-1");
        Files.createDirectories(importFile.getParent());
        Files.writeString(importFile, """
                routeId,driverId,salaryAmount
                %d,%d,%s
                """.formatted(routeId, driverId, amount), StandardCharsets.UTF_8);
        MvcResult result = mockMvc.perform(post("/api/v1/imports/preview")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"businessType":"SALARY","originalFileId":1}
                                """))
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

    private void seedImportFile(long id) {
        jdbcTemplate.update("""
                INSERT INTO file_attachment (id, owner_type, owner_id, purpose, storage_key, original_filename,
                                             content_type, file_size, file_hash, uploaded_by)
                VALUES (?, 'IMPORT', 0, 'IMPORT_FILE', ?, ?, 'text/csv', 10, ?, 1)
                """, id, "import-" + id, "import-" + id + ".csv", "hash-" + id);
    }
}
