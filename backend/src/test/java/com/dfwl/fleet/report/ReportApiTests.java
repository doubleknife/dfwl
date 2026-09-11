package com.dfwl.fleet.report;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
        "spring.datasource.url=jdbc:h2:mem:reportdb;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-auth-test.sql"
})
@AutoConfigureMockMvc
class ReportApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TokenAuthenticationService tokenAuthenticationService;

    private String token;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM expense_energy_detail");
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
                                        departure_driver_id, departure_vehicle_id, departure_time, unload_time,
                                        effective_weight_version_id, driver_salary, created_by)
                VALUES (1, 'R001', 'K001', DATE '2026-09-10', 1, 1, 'OUTBOUND', 'A', 'B', 10.0000,
                        'COMPLETED', 1, 1, TIMESTAMP '2026-09-10 08:00:00', TIMESTAMP '2026-09-10 18:00:00',
                        1, 30.00, 1)
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
        jdbcTemplate.update("""
                INSERT INTO expense_entry (id, expense_no, expense_type, business_date, vehicle_id, driver_id,
                                           attribution_type, route_id, amount, source_type, status, created_by)
                VALUES (3, 'E003', 'ELECTRIC', DATE '2026-09-10', 1, 1, 'ROUTE', 1, 20.00, 'MANUAL', 'ACTIVE', 1)
                """);
        jdbcTemplate.update("""
                INSERT INTO expense_energy_detail (expense_id, energy_type, order_no, start_time, quantity,
                                                   auto_matched_route_id, match_status)
                VALUES (3, 'ELECTRIC', 'EN001', TIMESTAMP '2026-09-10 10:00:00', 12.345, 1, 'AUTO_MATCHED')
                """);

        token = tokenAuthenticationService.issueToken(new AuthenticatedUser(1L, "1380", 1L, "admin", "Admin", Set.of(
                "report:dashboard", "report:profit", "report:vehicle", "report:driver", "report:attendance", "report:energy")));
    }

    @Test
    void dashboardAndProfitUseCurrentEffectiveData() throws Exception {
        mockMvc.perform(get("/api/v1/reports/dashboard")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.incomeAmount").value(120.0))
                .andExpect(jsonPath("$.data.routeExpense").value(60.0))
                .andExpect(jsonPath("$.data.dailyExpense").value(5.0))
                .andExpect(jsonPath("$.data.driverSalary").value(30.0))
                .andExpect(jsonPath("$.data.totalProfit").value(25.0));

        mockMvc.perform(get("/api/v1/reports/profit")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].incomeAmount").value(120.0))
                .andExpect(jsonPath("$.data[0].routeExpense").value(60.0))
                .andExpect(jsonPath("$.data[0].profitAmount").value(30.0));
    }

    @Test
    void expenseAttendanceAndEnergyReportsReturnGroupedData() throws Exception {
        mockMvc.perform(get("/api/v1/reports/vehicle-expense")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].vehicleId").value(1))
                .andExpect(jsonPath("$.data[0].amount").value(65.0));

        mockMvc.perform(get("/api/v1/reports/driver-expense")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].driverId").value(1));

        mockMvc.perform(get("/api/v1/reports/attendance")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].routeCount").value(1))
                .andExpect(jsonPath("$.data[0].workDays").value(1));

        mockMvc.perform(get("/api/v1/reports/energy")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].quantity").value(12.345))
                .andExpect(jsonPath("$.data[0].amount").value(20.0));
    }
}
