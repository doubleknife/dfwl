package com.dfwl.fleet.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.Connection;
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
        "spring.datasource.url=jdbc:h2:mem:driverscopedb;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-auth-test.sql"
})
@AutoConfigureMockMvc
class DriverScopeApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TokenAuthenticationService tokenAuthenticationService;

    private String driverAToken;
    private String driverBToken;
    private String outsourcedToken;
    private String financeToken;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM tire_claim");
        jdbcTemplate.update("DELETE FROM tire_request_item");
        jdbcTemplate.update("DELETE FROM tire_request");
        jdbcTemplate.update("DELETE FROM tire");
        jdbcTemplate.update("DELETE FROM expense_entry");
        jdbcTemplate.update("DELETE FROM route_weight_version");
        jdbcTemplate.update("DELETE FROM route_status_history");
        jdbcTemplate.update("DELETE FROM route_task");
        jdbcTemplate.update("DELETE FROM vehicle_trailer_current");
        jdbcTemplate.update("DELETE FROM driver_vehicle_current");
        jdbcTemplate.update("DELETE FROM vehicle");
        jdbcTemplate.update("DELETE FROM driver");
        jdbcTemplate.update("DELETE FROM product");
        jdbcTemplate.update("DELETE FROM customer");
        jdbcTemplate.update("DELETE FROM sys_user");

        jdbcTemplate.update("INSERT INTO customer (id, customer_name, status) VALUES (1, '客户A', 1)");
        jdbcTemplate.update("INSERT INTO product (id, product_name, status) VALUES (1, '煤炭', 1)");
        jdbcTemplate.update("""
                INSERT INTO driver (id, user_id, driver_type, name, phone, status)
                VALUES (1, 101, 'INTERNAL', 'Driver A', '13800000001', 1),
                       (2, 102, 'INTERNAL', 'Driver B', '13800000002', 1),
                       (3, 103, 'OUTSOURCED', 'Out Driver', '13800000003', 1)
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
        jdbcTemplate.update("""
                INSERT INTO route_task (id, route_no, business_unique_key, business_date, customer_id, product_id,
                                        direction, loading_place, unloading_place, tax_unit_price,
                                        assigned_driver_id, status, departure_driver_id, departure_vehicle_id,
                                        departure_time, created_by)
                VALUES (1, 'R1', 'r1', '2026-09-10', 1, 1, 'OUTBOUND', 'A', 'B', 9.0000, 1, 'PUBLISHED', NULL, NULL, NULL, 1),
                       (2, 'R2', 'r2', '2026-09-10', 1, 1, 'OUTBOUND', 'A', 'C', 8.0000, 2, 'PUBLISHED', NULL, NULL, NULL, 1),
                       (3, 'R3', 'r3', '2026-09-11', 1, 1, 'RETURN', 'C', 'A', 7.0000, 1, 'IN_TRANSIT', 1, 1, CURRENT_TIMESTAMP, 1)
                """);
        jdbcTemplate.update("INSERT INTO tire (id, tire_no, barcode, status, data_source) VALUES (1, 'TIRE-001', 'B001', 'IN_STOCK', 'MANUAL')");
        jdbcTemplate.update("INSERT INTO expense_entry (id, expense_no, expense_type, business_date, vehicle_id, attribution_type, amount, source_type, status, created_by) VALUES (1, 'E1', 'REPAIR', '2026-09-10', 1, 'DAILY', 10.00, 'MANUAL', 'ACTIVE', 1)");
        jdbcTemplate.update("INSERT INTO sys_user (id, phone, password_hash, role_id, status) VALUES (1, 'finance', 'x', 1, 1)");

        Set<String> driverPermissions = Set.of(
                "route:list", "route:depart", "route:unload", "vehicle:list", "vehicle:view",
                "driver:list", "tire:request", "expense:list", "expense:add", "approval:create",
                "approval:process", "report:driver", "report:profit");
        driverAToken = tokenAuthenticationService.issueToken(new AuthenticatedUser(
                101L, "13800000001", 2L, "DRIVER", "司机", driverPermissions));
        driverBToken = tokenAuthenticationService.issueToken(new AuthenticatedUser(
                102L, "13800000002", 2L, "DRIVER", "司机", driverPermissions));
        outsourcedToken = tokenAuthenticationService.issueToken(new AuthenticatedUser(
                103L, "13800000003", 2L, "DRIVER", "司机", driverPermissions));
        financeToken = tokenAuthenticationService.issueToken(new AuthenticatedUser(
                1L, "finance", 1L, "FINANCE", "财务", Set.of("route:list", "expense:list", "report:profit")));
    }

    @Test
    void driverACanOnlyListOwnRoutes() throws Exception {
        mockMvc.perform(get("/api/v1/routes").header("Authorization", "Bearer " + driverAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.records[?(@.id==1)]").exists())
                .andExpect(jsonPath("$.data.records[?(@.id==3)]").exists())
                .andExpect(jsonPath("$.data.records[?(@.id==2)]").doesNotExist());
    }

    @Test
    void driverACannotGuessRouteBDetail() throws Exception {
        mockMvc.perform(get("/api/v1/routes/2").header("Authorization", "Bearer " + driverAToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_003"));
    }

    @Test
    void driverACannotDepartRouteB() throws Exception {
        mockMvc.perform(post("/api/v1/routes/2/depart").header("Authorization", "Bearer " + driverAToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_003"));
    }

    @Test
    void driverACannotUnloadRouteB() throws Exception {
        jdbcTemplate.update("""
                UPDATE route_task
                SET status = 'IN_TRANSIT', departure_driver_id = 2, departure_vehicle_id = 2, departure_time = CURRENT_TIMESTAMP
                WHERE id = 2
                """);
        mockMvc.perform(post("/api/v1/routes/2/unload")
                        .header("Authorization", "Bearer " + driverAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"grossWeight\":30.000,\"tareWeight\":10.000}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_003"));
    }

    @Test
    void departedRouteUsesSnapshotDriverAfterBindingChanges() throws Exception {
        jdbcTemplate.update("DELETE FROM driver_vehicle_current WHERE driver_id IN (1, 2)");
        jdbcTemplate.update("INSERT INTO driver_vehicle_current (driver_id, vehicle_id, bound_at, bound_by) VALUES (2, 1, CURRENT_TIMESTAMP, 1)");

        mockMvc.perform(post("/api/v1/routes/3/unload")
                        .header("Authorization", "Bearer " + driverBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"grossWeight\":30.000,\"tareWeight\":10.000}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/routes/3/unload")
                        .header("Authorization", "Bearer " + driverAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"grossWeight\":30.000,\"tareWeight\":10.000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }

    @Test
    void driverRouteDetailDoesNotExposeSensitiveBusinessFields() throws Exception {
        String body = mockMvc.perform(get("/api/v1/routes/1").header("Authorization", "Bearer " + driverAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("taxUnitPrice", "driverSalary", "profit", "income", "expense", "attribution");
    }

    @Test
    void outsourcedDriverCannotCreateTireRequest() throws Exception {
        mockMvc.perform(post("/api/v1/tire-requests")
                        .header("Authorization", "Bearer " + outsourcedToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"driverId":3,"vehicleId":3,
                                 "items":[{"tireId":1,"confirmedTireNo":"TIRE-001"}]}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_003"));
    }

    @Test
    void outsourcedDriverCannotAccessPayrollLikeReports() throws Exception {
        mockMvc.perform(get("/api/v1/reports/driver-expense").header("Authorization", "Bearer " + outsourcedToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_003"));
    }

    @Test
    void outsourcedDriverWithMisconfiguredExpenseApprovalPermissionsStillDenied() throws Exception {
        mockMvc.perform(get("/api/v1/expenses").header("Authorization", "Bearer " + outsourcedToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/approvals")
                        .header("Authorization", "Bearer " + outsourcedToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"approvalType":"EXPENSE","businessType":"EXPENSE",
                                 "businessSnapshot":{"amount":"1.00"}}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void internalDriverCanCreateTireRequestForOwnCurrentVehicle() throws Exception {
        mockMvc.perform(post("/api/v1/tire-requests")
                        .header("Authorization", "Bearer " + driverAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"driverId":1,"vehicleId":1,
                                 "items":[{"tireId":1,"confirmedTireNo":"TIRE-001"}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.driverId").value(1));
    }

    @Test
    void financeRoleUsesPermissionScopeAndIsNotLimitedToDriverRoutes() throws Exception {
        mockMvc.perform(get("/api/v1/routes").header("Authorization", "Bearer " + financeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3));

        mockMvc.perform(get("/api/v1/reports/profit").header("Authorization", "Bearer " + financeToken))
                .andExpect(status().isOk());
    }

    @Test
    void userModelStillAllowsOnlyOneRole() throws Exception {
        try (Connection connection = jdbcTemplate.getDataSource().getConnection()) {
            assertThat(connection.getMetaData().getTables(null, null, "sys_user_role", null).next()).isFalse();
        }
        Long roleId = jdbcTemplate.queryForObject("SELECT role_id FROM sys_user WHERE id = 1", Long.class);
        assertThat(roleId).isEqualTo(1L);
    }
}
