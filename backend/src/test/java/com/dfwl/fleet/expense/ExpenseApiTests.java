package com.dfwl.fleet.expense;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:expensedb;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-auth-test.sql"
})
@AutoConfigureMockMvc
class ExpenseApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TokenAuthenticationService tokenAuthenticationService;

    private String token;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM expense_reversal_link");
        jdbcTemplate.update("DELETE FROM expense_energy_detail");
        jdbcTemplate.update("DELETE FROM expense_entry");
        jdbcTemplate.update("DELETE FROM route_task");
        jdbcTemplate.update("DELETE FROM vehicle");

        jdbcTemplate.update("""
                INSERT INTO vehicle (id, plate_no, insurance_complete, energy_type, max_load, load_standard_type,
                                     load_standard_percent, status)
                VALUES (1, '京A10001', 1, 'ELECTRIC', 30.000, 'PERCENT', 0.9000, 1)
                """);
        jdbcTemplate.update("""
                INSERT INTO route_task (id, route_no, business_unique_key, business_date, customer_id, product_id,
                                        direction, loading_place, unloading_place, tax_unit_price, status,
                                        departure_vehicle_id, departure_time, unload_time, created_by)
                VALUES (1, 'R001', 'K001', CURRENT_DATE, 1, 1, 'OUTBOUND', 'A', 'B', 1.0000,
                        'COMPLETED', 1, TIMESTAMP '2026-09-10 08:00:00', TIMESTAMP '2026-09-10 18:00:00', 1)
                """);
        token = tokenAuthenticationService.issueToken(new AuthenticatedUser(1L, "1380", 1L, "admin", "管理员", Set.of(
                "expense:add", "expense:edit", "expense:reversal", "expense:attribution:edit", "expense:list", "expense:approval:view")));
    }

    @Test
    void energyExpenseAutoMatchesRouteByVehicleAndTime() throws Exception {
        mockMvc.perform(post("/api/v1/expenses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expenseType":"ELECTRIC","businessDate":"2026-09-10","vehicleId":1,
                                 "attributionType":"ROUTE","amount":120.50,"sourceType":"MANUAL",
                                 "energyDetail":{"energyType":"ELECTRIC","orderNo":"E001",
                                 "startTime":"2026-09-10T10:00:00","quantity":30.123}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.routeId").value(1))
                .andExpect(jsonPath("$.data.energyDetail.autoMatchedRouteId").value(1))
                .andExpect(jsonPath("$.data.energyDetail.matchStatus").value("AUTO_MATCHED"));
    }

    @Test
    void approvalExpenseCannotEditAndCanBeReversed() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO expense_entry (id, expense_no, expense_type, business_date, vehicle_id, attribution_type,
                                           route_id, amount, source_type, status, approval_instance_id, created_by)
                VALUES (1, 'E001', 'REPAIR', CURRENT_DATE, 1, 'DAILY', NULL, 100.00, 'APPROVAL', 'ACTIVE', 10, 1)
                """);

        mockMvc.perform(put("/api/v1/expenses/1")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expenseType":"REPAIR","businessDate":"2026-09-10","vehicleId":1,
                                 "attributionType":"DAILY","amount":101.00,"sourceType":"MANUAL"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EXPENSE_004"));

        mockMvc.perform(post("/api/v1/expenses/1/reversal")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"审批费用录入错误"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REVERSAL"))
                .andExpect(jsonPath("$.data.reversalOfId").value(1))
                .andExpect(jsonPath("$.data.amount").value(-100.00));
    }
}
