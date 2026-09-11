package com.dfwl.fleet.settlement;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dfwl.fleet.security.AuthenticatedUser;
import com.dfwl.fleet.security.TokenAuthenticationService;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:settlementdb;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-auth-test.sql"
})
@AutoConfigureMockMvc
class SettlementApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TokenAuthenticationService tokenAuthenticationService;

    private String token;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM settlement_diff");
        jdbcTemplate.update("DELETE FROM settlement_detail");
        jdbcTemplate.update("DELETE FROM settlement_version");
        jdbcTemplate.update("DELETE FROM settlement_month");
        jdbcTemplate.update("DELETE FROM expense_entry");
        jdbcTemplate.update("DELETE FROM route_weight_version");
        jdbcTemplate.update("DELETE FROM route_task");
        jdbcTemplate.update("DELETE FROM vehicle");
        jdbcTemplate.update("DELETE FROM driver");

        jdbcTemplate.update("""
                INSERT INTO driver (id, name, phone, driver_type, status)
                VALUES (1, 'Driver A', '13800000001', 'INTERNAL', 1)
                """);
        jdbcTemplate.update("""
                INSERT INTO vehicle (id, plate_no, insurance_complete, energy_type, max_load, load_standard_type,
                                     load_standard_percent, status)
                VALUES (1, 'A10001', 1, 'ELECTRIC', 30.000, 'PERCENT', 0.9000, 1)
                """);
        jdbcTemplate.update("""
                INSERT INTO route_task (id, route_no, business_unique_key, business_date, customer_id, product_id,
                                        direction, loading_place, unloading_place, tax_unit_price, status,
                                        departure_driver_id, departure_vehicle_id, effective_weight_version_id,
                                        driver_salary, created_by)
                VALUES (1, 'R001', 'K001', DATE '2026-09-10', 1, 1, 'OUTBOUND', 'A', 'B', 10.0000,
                        'COMPLETED', 1, 1, 1, 30.00, 1)
                """);
        jdbcTemplate.update("""
                INSERT INTO route_weight_version (id, route_id, version_no, gross_weight, tare_weight, net_weight,
                                                  source_type, operator_id)
                VALUES (1, 1, 1, 20.000, 8.000, 12.000, 'UNLOAD', 1)
                """);
        jdbcTemplate.update("""
                INSERT INTO expense_entry (id, expense_no, expense_type, business_date, vehicle_id, driver_id,
                                           attribution_type, route_id, amount, source_type, status, created_by)
                VALUES (1, 'E001', 'REPAIR', DATE '2026-09-10', 1, 1, 'ROUTE', 1, 40.00, 'MANUAL', 'ACTIVE', 1)
                """);
        jdbcTemplate.update("""
                INSERT INTO expense_entry (id, expense_no, expense_type, business_date, vehicle_id, driver_id,
                                           attribution_type, route_id, amount, source_type, status, created_by)
                VALUES (2, 'E002', 'CAR_WASH', DATE '2026-09-10', 1, 1, 'DAILY', NULL, 5.00, 'MANUAL', 'ACTIVE', 1)
                """);

        token = tokenAuthenticationService.issueToken(new AuthenticatedUser(1L, "1380", 1L, "admin", "Admin", Set.of(
                "settlement:view", "settlement:generate", "settlement:diff:view")));
    }

    @Test
    void generatedSettlementVersionsAreImmutableAndDiffExplainsChanges() throws Exception {
        mockMvc.perform(post("/api/v1/settlements/2026-09/versions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.versionNo").value(1))
                .andExpect(jsonPath("$.data.totalIncome").value(120.0))
                .andExpect(jsonPath("$.data.totalProfit").value(45.0));

        jdbcTemplate.update("UPDATE route_weight_version SET net_weight = 13.000 WHERE id = 1");

        mockMvc.perform(post("/api/v1/settlements/2026-09/versions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.versionNo").value(2))
                .andExpect(jsonPath("$.data.totalIncome").value(130.0))
                .andExpect(jsonPath("$.data.totalProfit").value(55.0));

        mockMvc.perform(get("/api/v1/settlements/2026-09/versions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].versionNo").value(2))
                .andExpect(jsonPath("$.data[1].versionNo").value(1))
                .andExpect(jsonPath("$.data[1].totalProfit").value(45.0));

        mockMvc.perform(get("/api/v1/settlements/versions/2/diff")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].changeType").value("MODIFY"))
                .andExpect(jsonPath("$.data[0].incomeDelta").value(10.0))
                .andExpect(jsonPath("$.data[0].profitDelta").value(10.0));

        mockMvc.perform(get("/api/v1/settlements/versions/1/details")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].factType").value("DAILY_EXPENSE"))
                .andExpect(jsonPath("$.data[0].profitAmount").value(-5.0))
                .andExpect(jsonPath("$.data[1].factType").value("ROUTE"))
                .andExpect(jsonPath("$.data[1].profitAmount").value(50.0));
    }
}
