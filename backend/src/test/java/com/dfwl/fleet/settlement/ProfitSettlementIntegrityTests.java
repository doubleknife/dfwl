package com.dfwl.fleet.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dfwl.fleet.security.AuthenticatedUser;
import com.dfwl.fleet.security.TokenAuthenticationService;
import java.math.BigDecimal;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:profitsettlementdb;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-auth-test.sql"
})
@AutoConfigureMockMvc
class ProfitSettlementIntegrityTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TokenAuthenticationService tokenAuthenticationService;

    private String token;

    @BeforeEach
    void setUp() {
        for (String table : new String[]{
                "settlement_diff", "settlement_detail", "settlement_version", "settlement_month",
                "expense_attribution_history", "expense_reversal_link", "expense_energy_detail", "expense_entry",
                "route_weight_version", "route_status_history", "route_task",
                "vehicle", "driver", "product", "customer"}) {
            jdbcTemplate.update("DELETE FROM " + table);
        }
        jdbcTemplate.update("INSERT INTO customer (id, customer_name, status) VALUES (1, '客户A', 1)");
        jdbcTemplate.update("INSERT INTO product (id, product_name, status) VALUES (1, '煤炭', 1)");
        jdbcTemplate.update("INSERT INTO driver (id, name, phone, driver_type, status) VALUES (1, '司机A', '1381', 'INTERNAL', 1), (2, '司机B', '1382', 'INTERNAL', 1)");
        jdbcTemplate.update("""
                INSERT INTO vehicle (id, plate_no, insurance_complete, energy_type, max_load, load_standard_type,
                                     load_standard_percent, status)
                VALUES (1, 'A10001', 1, 'ELECTRIC', 30.000, 'PERCENT', 0.9000, 1),
                       (2, 'A10002', 1, 'ELECTRIC', 30.000, 'PERCENT', 0.9000, 1)
                """);
        jdbcTemplate.update("""
                INSERT INTO route_task (id, route_no, business_unique_key, business_date, customer_id, product_id,
                                        direction, loading_place, unloading_place, mileage_km, tax_unit_price, status,
                                        departure_driver_id, departure_vehicle_id, departure_time, unload_time,
                                        effective_weight_version_id, driver_salary, created_by, updated_by)
                VALUES (1, 'R001', 'K001', DATE '2026-09-10', 1, 1, 'OUTBOUND', 'A', 'B', 100.00, 10.0000,
                        'COMPLETED', 1, 1, TIMESTAMP '2026-09-10 08:00:00', TIMESTAMP '2026-09-10 18:00:00', 1, 30.00, 1, 1),
                       (2, 'R002', 'K002', DATE '2026-09-11', 1, 1, 'OUTBOUND', 'A', 'C', 80.00, 10.0000,
                        'COMPLETED', 2, 2, TIMESTAMP '2026-09-11 08:00:00', TIMESTAMP '2026-09-11 16:00:00', 2, NULL, 1, 1),
                       (3, 'R003', 'K003', DATE '2026-08-20', 1, 1, 'OUTBOUND', 'X', 'Y', 70.00, 20.0000,
                        'COMPLETED', 1, 1, TIMESTAMP '2026-08-20 08:00:00', TIMESTAMP '2026-08-20 16:00:00', 3, 10.00, 1, 1)
                """);
        jdbcTemplate.update("""
                INSERT INTO route_weight_version (id, route_id, version_no, gross_weight, tare_weight, net_weight,
                                                  source_type, operator_id, operation_time, reason)
                VALUES (1, 1, 1, 20.000, 8.000, 12.000, 'UNLOAD', 1, TIMESTAMP '2026-09-10 18:00:00', NULL),
                       (2, 2, 1, 18.000, 8.000, 10.000, 'UNLOAD', 1, TIMESTAMP '2026-09-11 16:00:00', NULL),
                       (3, 3, 1, 11.000, 6.000, 5.000, 'UNLOAD', 1, TIMESTAMP '2026-08-20 16:00:00', NULL)
                """);
        jdbcTemplate.update("""
                INSERT INTO expense_entry (id, expense_no, expense_type, business_date, vehicle_id, driver_id,
                                           attribution_type, route_id, amount, source_type, status, created_by, updated_by)
                VALUES (1, 'E-REPAIR', 'REPAIR', DATE '2026-09-10', 1, 1, 'ROUTE', 1, 40.00, 'MANUAL', 'ACTIVE', 7, 7),
                       (2, 'E-WASH', 'CAR_WASH', DATE '2026-09-10', 1, 1, 'DAILY', NULL, 5.00, 'MANUAL', 'ACTIVE', 7, 7),
                       (3, 'E-ELEC', 'ELECTRIC', DATE '2026-09-10', 1, 1, 'ROUTE', 1, 20.00, 'MANUAL', 'ACTIVE', 7, 7),
                       (4, 'E-WATER', 'WATER', DATE '2026-09-11', 2, 2, 'ROUTE', 2, 7.00, 'MANUAL', 'ACTIVE', 7, 7),
                       (5, 'E-PENDING', 'PENALTY', DATE '2026-09-10', 1, 1, 'ROUTE', 1, 10.00, 'MANUAL', 'PENDING_ATTRIBUTION', 7, 7),
                       (6, 'E-OLD', 'REPAIR', DATE '2026-09-10', 1, 1, 'ROUTE', 1, 50.00, 'MANUAL', 'REVERSED', 7, 7),
                       (7, 'E-RV', 'REPAIR', DATE '2026-09-10', 1, 1, 'ROUTE', 1, -50.00, 'SYSTEM', 'REVERSAL', 7, 7)
                """);
        jdbcTemplate.update("""
                INSERT INTO expense_energy_detail (expense_id, energy_type, order_no, start_time, quantity,
                                                   auto_matched_route_id, match_status)
                VALUES (3, 'ELECTRIC', 'EN001', TIMESTAMP '2026-09-10 10:00:00', 12.345, 1, 'AUTO_MATCHED')
                """);
        jdbcTemplate.update("""
                INSERT INTO expense_reversal_link (original_expense_id, reversal_expense_id, reason, operator_id, created_at)
                VALUES (6, 7, '录入错误冲销', 9, TIMESTAMP '2026-09-12 09:00:00')
                """);
        token = tokenAuthenticationService.issueToken(new AuthenticatedUser(10L, "admin", 1L, "admin", "Admin", Set.of(
                "report:dashboard", "report:profit", "report:vehicle", "report:driver", "report:attendance", "report:energy",
                "settlement:view", "settlement:generate", "settlement:diff:view")));
    }

    @Test
    void actualWeightChangeUpdatesRouteIncome() throws Exception {
        jdbcTemplate.update("UPDATE route_weight_version SET net_weight = 13.000 WHERE id = 1");
        mockMvc.perform(get("/api/v1/reports/profit").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.routeId==1)].incomeAmount").value(contains(130.0)));
    }

    @Test
    void routeExpenseOnlyAffectsItsRouteProfit() throws Exception {
        mockMvc.perform(get("/api/v1/reports/profit").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.routeId==1)].routeExpense").value(contains(60.0)))
                .andExpect(jsonPath("$.data[?(@.routeId==2)].routeExpense").value(contains(7.0)));
    }

    @Test
    void dailyExpenseAffectsCompanyProfitButNotSingleRouteProfit() throws Exception {
        mockMvc.perform(get("/api/v1/reports/dashboard").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dailyExpense").value(5.0))
                .andExpect(jsonPath("$.data.totalProfit").value(208.0));
        mockMvc.perform(get("/api/v1/reports/profit").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.routeId==1)].profitAmount").value(contains(30.0)));
    }

    @Test
    void pendingAttributionDoesNotEnterCurrentProfit() throws Exception {
        mockMvc.perform(get("/api/v1/reports/profit").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.routeId==1)].penaltyAmount").value(contains(0)));
    }

    @Test
    void reversedAndReversalDoNotDoubleDeductExpense() throws Exception {
        mockMvc.perform(get("/api/v1/reports/profit").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.routeId==1)].repairAmount").value(contains(40.0)));
    }

    @Test
    void historicalSupplementUsesBusinessDateMonth() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO expense_entry (id, expense_no, expense_type, business_date, vehicle_id, attribution_type,
                                           route_id, amount, source_type, status, created_by)
                VALUES (20, 'AUG-DAILY', 'OTHER', DATE '2026-08-15', 1, 'DAILY', NULL, 8.00, 'MANUAL', 'ACTIVE', 8)
                """);
        generate("2026-08").andExpect(jsonPath("$.data.dailyExpense").value(8.0));
        generate("2026-09").andExpect(jsonPath("$.data.dailyExpense").value(5.0));
    }

    @Test
    void vehicleExpenseReportClassifiesExpenseTypes() throws Exception {
        mockMvc.perform(get("/api/v1/reports/vehicle-expense").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.vehicleId==1)].repairAmount").value(contains(40.0)))
                .andExpect(jsonPath("$.data[?(@.vehicleId==1)].energyAmount").value(contains(20.0)))
                .andExpect(jsonPath("$.data[?(@.vehicleId==1)].carWashAmount").value(contains(5.0)));
    }

    @Test
    void driverExpenseReportIncludesRoutesMileageExpensesAndSalary() throws Exception {
        mockMvc.perform(get("/api/v1/reports/driver-expense").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.driverId==1)].routeCount").value(contains(2)))
                .andExpect(jsonPath("$.data[?(@.driverId==1)].mileageKm").value(contains(170.0)))
                .andExpect(jsonPath("$.data[?(@.driverId==1)].driverSalary").value(contains(40.0)));
    }

    @Test
    void attendanceUsesDriverDateDispatchFacts() throws Exception {
        mockMvc.perform(get("/api/v1/reports/attendance").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.driverId==1 && @.workDate=='2026-09-10')].worked").value(contains(1)));
    }

    @Test
    void energyReportAggregatesQuantityAndAmount() throws Exception {
        mockMvc.perform(get("/api/v1/reports/energy").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].quantity").value(12.345))
                .andExpect(jsonPath("$.data[0].amount").value(20.0));
    }

    @Test
    void v1SnapshotDoesNotChangeAfterHistoricalWeightAdjustment() throws Exception {
        long v1 = generatedVersionId("2026-09");
        adjustRouteOneWeight();
        assertThat(jdbcTemplate.queryForObject("SELECT total_income FROM settlement_version WHERE id = ?", BigDecimal.class, v1))
                .isEqualByComparingTo("220.00");
    }

    @Test
    void v2ContainsWeightAdjustDiff() throws Exception {
        generatedVersionId("2026-09");
        adjustRouteOneWeight();
        long v2 = generatedVersionId("2026-09");
        assertThat(diffType(v2, "WEIGHT_ADJUST")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT income_delta FROM settlement_diff WHERE settlement_version_id = ? AND change_type = 'WEIGHT_ADJUST'", BigDecimal.class, v2))
                .isEqualByComparingTo("10.00");
    }

    @Test
    void routeExpenseReassignmentKeepsCompanyProfitButChangesRouteDiffs() throws Exception {
        long v1 = generatedVersionId("2026-09");
        BigDecimal oldProfit = jdbcTemplate.queryForObject("SELECT total_profit FROM settlement_version WHERE id = ?", BigDecimal.class, v1);
        jdbcTemplate.update("UPDATE expense_entry SET route_id = 2, updated_by = 8 WHERE id = 1");
        jdbcTemplate.update("""
                INSERT INTO expense_attribution_history (expense_id, before_attribution_type, before_route_id, before_status,
                                                         after_attribution_type, after_route_id, after_status, operator_id, reason)
                VALUES (1, 'ROUTE', 1, 'ACTIVE', 'ROUTE', 2, 'ACTIVE', 8, '改挂线路')
                """);
        long v2 = generatedVersionId("2026-09");
        assertThat(jdbcTemplate.queryForObject("SELECT total_profit FROM settlement_version WHERE id = ?", BigDecimal.class, v2))
                .isEqualByComparingTo(oldProfit);
        assertThat(diffType(v2, "REASSIGN")).isGreaterThanOrEqualTo(1);
    }

    @Test
    void reversalAppearsInNewVersionDiff() throws Exception {
        jdbcTemplate.update("DELETE FROM expense_reversal_link");
        jdbcTemplate.update("DELETE FROM expense_entry WHERE id IN (6, 7)");
        generatedVersionId("2026-09");
        jdbcTemplate.update("""
                INSERT INTO expense_entry (id, expense_no, expense_type, business_date, vehicle_id, driver_id,
                                           attribution_type, route_id, amount, source_type, status, created_by, updated_by)
                VALUES (6, 'E-OLD', 'REPAIR', DATE '2026-09-10', 1, 1, 'ROUTE', 1, 50.00, 'MANUAL', 'REVERSED', 7, 9),
                       (7, 'E-RV', 'REPAIR', DATE '2026-09-10', 1, 1, 'ROUTE', 1, -50.00, 'SYSTEM', 'REVERSAL', 9, 9)
                """);
        jdbcTemplate.update("""
                INSERT INTO expense_reversal_link (original_expense_id, reversal_expense_id, reason, operator_id, created_at)
                VALUES (6, 7, '录入错误冲销', 9, TIMESTAMP '2026-09-12 09:00:00')
                """);
        long v2 = generatedVersionId("2026-09");
        assertThat(diffType(v2, "REVERSAL")).isGreaterThanOrEqualTo(1);
    }

    @Test
    void settlementDiffSeparatesBusinessOperatorFromVersionGenerator() throws Exception {
        generatedVersionId("2026-09");
        adjustRouteOneWeight();
        long v2 = generatedVersionId("2026-09");
        assertThat(jdbcTemplate.queryForObject("SELECT generated_by FROM settlement_version WHERE id = ?", Long.class, v2)).isEqualTo(10L);
        assertThat(jdbcTemplate.queryForObject("SELECT business_operator_id FROM settlement_diff WHERE settlement_version_id = ? AND change_type = 'WEIGHT_ADJUST'", Long.class, v2)).isEqualTo(8L);
    }

    @Test
    void missingSalaryMarksCostIncomplete() throws Exception {
        mockMvc.perform(get("/api/v1/reports/dashboard").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.salaryComplete").value(false))
                .andExpect(jsonPath("$.data.salaryMissing").value(true));
        mockMvc.perform(get("/api/v1/reports/profit").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.routeId==2)].salaryComplete").value(contains(0)));
    }

    private void adjustRouteOneWeight() {
        jdbcTemplate.update("""
                INSERT INTO route_weight_version (id, route_id, version_no, gross_weight, tare_weight, net_weight,
                                                  source_type, operator_id, operation_time, reason)
                VALUES (10, 1, 2, 21.000, 8.000, 13.000, 'ADJUSTMENT', 8, TIMESTAMP '2026-09-13 09:00:00', '复核重量')
                """);
        jdbcTemplate.update("UPDATE route_task SET effective_weight_version_id = 10, updated_by = 8 WHERE id = 1");
    }

    private org.springframework.test.web.servlet.ResultActions generate(String yearMonth) throws Exception {
        return mockMvc.perform(post("/api/v1/settlements/%s/versions".formatted(yearMonth))
                .header("Authorization", "Bearer " + token)).andExpect(status().isOk());
    }

    private long generatedVersionId(String yearMonth) throws Exception {
        generate(yearMonth);
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM settlement_version", Long.class);
    }

    private int diffType(long versionId, String type) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM settlement_diff WHERE settlement_version_id = ? AND change_type = ?", Integer.class, versionId, type);
    }
}
