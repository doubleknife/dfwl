package com.dfwl.fleet.expense;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dfwl.fleet.security.AuthenticatedUser;
import com.dfwl.fleet.security.TokenAuthenticationService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:energyattrdb;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-auth-test.sql"
})
@AutoConfigureMockMvc
class EnergyAttributionTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TokenAuthenticationService tokenAuthenticationService;

    private String token;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("ALTER TABLE expense_attribution_history ALTER COLUMN reason VARCHAR(500)");
        List.of(
                "settlement_diff", "settlement_detail", "settlement_version", "settlement_month",
                "expense_attribution_history", "expense_energy_detail", "expense_entry",
                "route_weight_version", "route_status_history", "route_task",
                "driver_vehicle_current", "vehicle", "driver", "product", "customer"
        ).forEach(table -> jdbcTemplate.update("DELETE FROM " + table));

        jdbcTemplate.update("INSERT INTO customer (id, customer_name, status) VALUES (1, '客户A', 1)");
        jdbcTemplate.update("INSERT INTO product (id, product_name, status) VALUES (1, '煤炭', 1)");
        jdbcTemplate.update("INSERT INTO driver (id, name, phone, driver_type, status) VALUES (1, 'Driver A', '1381', 'INTERNAL', 1)");
        jdbcTemplate.update("""
                INSERT INTO vehicle (id, plate_no, insurance_complete, energy_type, max_load, load_standard_type,
                                     load_standard_percent, status)
                VALUES (1, '京A10001', 1, 'ELECTRIC', 30.000, 'PERCENT', 0.9000, 1),
                       (2, '京A10002', 1, 'ELECTRIC', 30.000, 'PERCENT', 0.9000, 1)
                """);
        jdbcTemplate.update("INSERT INTO driver_vehicle_current (driver_id, vehicle_id, bound_at, bound_by) VALUES (1, 1, CURRENT_TIMESTAMP, 1)");
        jdbcTemplate.update("""
                INSERT INTO route_task (id, route_no, business_unique_key, business_date, customer_id, product_id,
                                        direction, loading_place, unloading_place, tax_unit_price,
                                        assigned_driver_id, status, departure_driver_id, departure_vehicle_id,
                                        departure_time, unload_time, effective_weight_version_id, created_by)
                VALUES (1, 'R1', 'r1', '2026-09-10', 1, 1, 'OUTBOUND', 'A', 'B', 10.0000,
                        1, 'IN_TRANSIT', 1, 1, TIMESTAMP '2026-09-10 08:00:00', TIMESTAMP '2026-09-10 12:00:00', NULL, 1),
                       (2, 'R2', 'r2', '2026-09-10', 1, 1, 'OUTBOUND', 'A', 'C', 10.0000,
                        1, 'COMPLETED', 1, 1, TIMESTAMP '2026-09-10 13:00:00', TIMESTAMP '2026-09-10 15:00:00', 2, 1),
                       (3, 'R3', 'r3', '2026-09-10', 1, 1, 'OUTBOUND', 'A', 'D', 10.0000,
                        1, 'COMPLETED', 1, 1, TIMESTAMP '2026-09-10 16:00:00', TIMESTAMP '2026-09-10 18:00:00', 3, 1)
                """);
        jdbcTemplate.update("""
                INSERT INTO route_weight_version (id, route_id, version_no, gross_weight, tare_weight, net_weight,
                                                  source_type, operator_id)
                VALUES (2, 2, 1, 30.000, 10.000, 20.000, 'UNLOAD', 1),
                       (3, 3, 1, 30.000, 10.000, 20.000, 'UNLOAD', 1)
                """);
        token = tokenAuthenticationService.issueToken(new AuthenticatedUser(
                1L, "finance", 1L, "FINANCE", "财务", Set.of(
                "expense:add", "expense:list", "expense:attribution:edit",
                "route:void", "route:reactivate", "report:dashboard", "settlement:generate", "settlement:view")));
    }

    @Test
    void energyExpenseAutoMatchesRouteByVehicleAndOrderTime() throws Exception {
        long expenseId = createEnergyExpense("E-AUTO", "ELECTRIC", "2026-09-10T09:00:00");

        assertThat(jdbcTemplate.queryForObject("SELECT attribution_type FROM expense_entry WHERE id = ?", String.class, expenseId))
                .isEqualTo("ROUTE");
        assertThat(jdbcTemplate.queryForObject("SELECT route_id FROM expense_entry WHERE id = ?", Long.class, expenseId))
                .isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("SELECT auto_matched_route_id FROM expense_energy_detail WHERE expense_id = ?", Long.class, expenseId))
                .isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("SELECT match_status FROM expense_energy_detail WHERE expense_id = ?", String.class, expenseId))
                .isEqualTo("AUTO_MATCHED");
    }

    @Test
    void routeVoidMovesMatchedEnergyExpensesToPendingAttribution() throws Exception {
        long expenseId = createEnergyExpense("E-VOID", "GAS", "2026-09-10T09:00:00");

        mockMvc.perform(post("/api/v1/routes/1/void")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"异常作废\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("VOIDED"));

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM expense_entry WHERE id = ?", String.class, expenseId))
                .isEqualTo("PENDING_ATTRIBUTION");
        assertThat(jdbcTemplate.queryForObject("SELECT attribution_type FROM expense_entry WHERE id = ?", String.class, expenseId))
                .isEqualTo("ROUTE");
        assertThat(jdbcTemplate.queryForObject("SELECT route_id FROM expense_entry WHERE id = ?", Long.class, expenseId))
                .isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM expense_attribution_history WHERE expense_id = ?", Integer.class, expenseId))
                .isEqualTo(1);
    }

    @Test
    void financeCanRebindPendingEnergyExpenseToAnotherRoute() throws Exception {
        long expenseId = createPendingEnergyExpense();

        mockMvc.perform(put("/api/v1/expenses/%d/attribution".formatted(expenseId))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"attributionType\":\"ROUTE\",\"routeId\":2,\"reason\":\"改挂正确线路\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.routeId").value(2));

        mockMvc.perform(get("/api/v1/expenses/%d/attribution-history".formatted(expenseId))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].beforeStatus").value("PENDING_ATTRIBUTION"))
                .andExpect(jsonPath("$.data[0].afterRouteId").value(2))
                .andExpect(jsonPath("$.data[0].afterStatus").value("ACTIVE"));
    }

    @Test
    void financeCanConfirmPendingEnergyExpenseAsDailyAndClearsRoute() throws Exception {
        long expenseId = createPendingEnergyExpense();

        mockMvc.perform(put("/api/v1/expenses/%d/attribution".formatted(expenseId))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"attributionType\":\"DAILY\",\"reason\":\"确认为日常\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.attributionType").value("DAILY"))
                .andExpect(jsonPath("$.data.routeId").doesNotExist());

        assertThat(jdbcTemplate.queryForObject("SELECT route_id FROM expense_entry WHERE id = ?", Long.class, expenseId))
                .isNull();
    }

    @Test
    void nonEnergyExpenseIsNotChangedWhenRouteVoided() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO expense_entry (id, expense_no, expense_type, business_date, vehicle_id, attribution_type,
                                           route_id, amount, source_type, status, created_by)
                VALUES (100, 'R-REPAIR', 'REPAIR', '2026-09-10', 1, 'ROUTE', 1, 66.00, 'MANUAL', 'ACTIVE', 1)
                """);

        mockMvc.perform(post("/api/v1/routes/1/void")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"异常作废\"}"))
                .andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM expense_entry WHERE id = 100", String.class))
                .isEqualTo("ACTIVE");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM expense_attribution_history WHERE expense_id = 100", Integer.class))
                .isZero();
    }

    @Test
    void reactivatingRouteDoesNotRestorePendingEnergyAttribution() throws Exception {
        long expenseId = createEnergyExpense("E-REACT", "ELECTRIC", "2026-09-10T09:00:00");
        mockMvc.perform(post("/api/v1/routes/1/void")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"异常作废\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/routes/1/reactivate").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM expense_entry WHERE id = ?", String.class, expenseId))
                .isEqualTo("PENDING_ATTRIBUTION");
        assertThat(jdbcTemplate.queryForObject("SELECT route_id FROM expense_entry WHERE id = ?", Long.class, expenseId))
                .isEqualTo(1L);
    }

    @Test
    void pendingAttributionExpenseDoesNotEnterRealtimeProfitOrCompanyExpense() throws Exception {
        createPendingEnergyExpense();

        mockMvc.perform(get("/api/v1/reports/dashboard").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.routeExpense").value(0))
                .andExpect(jsonPath("$.data.dailyExpense").value(0))
                .andExpect(jsonPath("$.data.totalProfit").value(400.0));
    }

    @Test
    void generatedSettlementSnapshotDoesNotChangeAfterAttributionAdjustment() throws Exception {
        long expenseId = createActiveRouteEnergyExpenseOnCompletedRoute();

        mockMvc.perform(post("/api/v1/settlements/2026-09/versions").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.routeExpense").value(30.0));
        Long versionId = jdbcTemplate.queryForObject("SELECT id FROM settlement_version", Long.class);

        mockMvc.perform(put("/api/v1/expenses/%d/attribution".formatted(expenseId))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"attributionType\":\"DAILY\",\"reason\":\"确认为日常\"}"))
                .andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForObject("SELECT route_expense FROM settlement_version WHERE id = ?", BigDecimal.class, versionId))
                .isEqualByComparingTo("30.00");
    }

    @Test
    void voidTransactionRollsBackExpensePendingUpdateWhenHistoryWriteFails() throws Exception {
        long expenseId = createEnergyExpense("E-ROLLBACK", "ELECTRIC", "2026-09-10T09:00:00");
        jdbcTemplate.execute("ALTER TABLE expense_attribution_history ALTER COLUMN reason VARCHAR(1)");

        mockMvc.perform(post("/api/v1/routes/1/void")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"超过长度\"}"))
                .andExpect(status().is5xxServerError());

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM route_task WHERE id = 1", String.class))
                .isEqualTo("IN_TRANSIT");
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM expense_entry WHERE id = ?", String.class, expenseId))
                .isEqualTo("ACTIVE");
    }

    private long createEnergyExpense(String no, String type, String startTime) throws Exception {
        mockMvc.perform(post("/api/v1/expenses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expenseType":"%s","businessDate":"2026-09-10","vehicleId":1,
                                 "attributionType":"DAILY","amount":50.00,"sourceType":"MANUAL",
                                 "energyDetail":{"energyType":"%s","stationId":9,"orderNo":"%s",
                                  "startTime":"%s","quantity":12.345}}
                                """.formatted(type, type, no, startTime)))
                .andExpect(status().isOk());
        return jdbcTemplate.queryForObject("SELECT id FROM expense_entry WHERE expense_no IS NOT NULL ORDER BY id DESC LIMIT 1", Long.class);
    }

    private long createPendingEnergyExpense() {
        jdbcTemplate.update("""
                INSERT INTO expense_entry (id, expense_no, expense_type, business_date, vehicle_id, attribution_type,
                                           route_id, amount, source_type, status, created_by)
                VALUES (200, 'E-PENDING', 'ELECTRIC', '2026-09-10', 1, 'ROUTE', 1, 50.00, 'MANUAL', 'PENDING_ATTRIBUTION', 1)
                """);
        jdbcTemplate.update("""
                INSERT INTO expense_energy_detail (expense_id, energy_type, station_id, order_no, start_time,
                                                   quantity, auto_matched_route_id, match_status)
                VALUES (200, 'ELECTRIC', 9, 'PENDING', TIMESTAMP '2026-09-10 09:00:00', 12.345, 1, 'AUTO_MATCHED')
                """);
        return 200L;
    }

    private long createActiveRouteEnergyExpenseOnCompletedRoute() {
        jdbcTemplate.update("""
                INSERT INTO expense_entry (id, expense_no, expense_type, business_date, vehicle_id, attribution_type,
                                           route_id, amount, source_type, status, created_by)
                VALUES (300, 'E-COMP', 'ELECTRIC', '2026-09-10', 1, 'ROUTE', 2, 30.00, 'MANUAL', 'ACTIVE', 1)
                """);
        jdbcTemplate.update("""
                INSERT INTO expense_energy_detail (expense_id, energy_type, station_id, order_no, start_time,
                                                   quantity, auto_matched_route_id, match_status)
                VALUES (300, 'ELECTRIC', 9, 'COMP', TIMESTAMP '2026-09-10 14:00:00', 10.000, 2, 'AUTO_MATCHED')
                """);
        return 300L;
    }
}
