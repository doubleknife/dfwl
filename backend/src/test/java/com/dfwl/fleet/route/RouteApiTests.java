package com.dfwl.fleet.route;

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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:routedb;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-auth-test.sql"
})
@AutoConfigureMockMvc
class RouteApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TokenAuthenticationService tokenAuthenticationService;

    private String token;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM route_weight_version");
        jdbcTemplate.update("DELETE FROM route_status_history");
        jdbcTemplate.update("DELETE FROM route_task");
        jdbcTemplate.update("DELETE FROM vehicle_trailer_current");
        jdbcTemplate.update("DELETE FROM driver_vehicle_current");
        jdbcTemplate.update("DELETE FROM trailer");
        jdbcTemplate.update("DELETE FROM vehicle");
        jdbcTemplate.update("DELETE FROM driver");
        jdbcTemplate.update("DELETE FROM product");
        jdbcTemplate.update("DELETE FROM customer");

        jdbcTemplate.update("INSERT INTO customer (id, customer_name, status) VALUES (1, '客户A', 1)");
        jdbcTemplate.update("INSERT INTO product (id, product_name, status) VALUES (1, '煤炭', 1)");
        jdbcTemplate.update("INSERT INTO driver (id, driver_type, name, phone, status) VALUES (1, 'INTERNAL', '张三', '1381', 1)");
        jdbcTemplate.update("""
                INSERT INTO vehicle (id, plate_no, insurance_complete, energy_type, max_load, load_standard_type,
                                     load_standard_percent, status)
                VALUES (1, '京A10001', 1, 'ELECTRIC', 30.000, 'PERCENT', 0.9000, 1)
                """);
        jdbcTemplate.update("INSERT INTO trailer (id, trailer_no, insurance_complete, status) VALUES (1, 'G001', 1, 1)");
        jdbcTemplate.update("INSERT INTO driver_vehicle_current (driver_id, vehicle_id, bound_at, bound_by) VALUES (1, 1, CURRENT_TIMESTAMP, 1)");
        jdbcTemplate.update("INSERT INTO vehicle_trailer_current (vehicle_id, trailer_id, bound_at, bound_by) VALUES (1, 1, CURRENT_TIMESTAMP, 1)");

        AuthenticatedUser user = new AuthenticatedUser(1L, "13800000000", 1L, "admin", "管理员", Set.of(
                "route:create", "route:publish", "route:depart", "route:unload", "route:cancel",
                "route:void", "route:reactivate", "route:weight:adjust", "route:list"));
        token = tokenAuthenticationService.issueToken(user);
    }

    @Test
    void routeCanPublishDepartAndUnloadWithFrozenSnapshot() throws Exception {
        long routeId = createRoute();

        mockMvc.perform(post("/api/v1/routes/%d/publish".formatted(routeId))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));

        mockMvc.perform(post("/api/v1/routes/%d/depart".formatted(routeId))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_TRANSIT"))
                .andExpect(jsonPath("$.data.departureDriverId").value(1))
                .andExpect(jsonPath("$.data.departureVehicleId").value(1))
                .andExpect(jsonPath("$.data.departureTrailerId").value(1));

        mockMvc.perform(post("/api/v1/routes/%d/unload".formatted(routeId))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"grossWeight":30.500,"tareWeight":10.250}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.netWeight").value(20.250));
    }

    @Test
    void inTransitRouteCannotBeCancelledAndBadWeightFails() throws Exception {
        long routeId = createRoute();
        mockMvc.perform(post("/api/v1/routes/%d/publish".formatted(routeId)).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/routes/%d/depart".formatted(routeId)).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/routes/%d/cancel".formatted(routeId))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROUTE_002"));

        mockMvc.perform(post("/api/v1/routes/%d/unload".formatted(routeId))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"grossWeight":10.000,"tareWeight":10.250}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROUTE_004"));
    }

    private long createRoute() throws Exception {
        mockMvc.perform(post("/api/v1/routes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"businessDate":"2026-09-10","customerId":1,"productId":1,"direction":"OUTBOUND",
                                 "loadingPlace":"A","unloadingPlace":"B","assignedDriverId":1,"taxUnitPrice":1.0000}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UNPUBLISHED"));
        return jdbcTemplate.queryForObject("SELECT id FROM route_task WHERE business_unique_key LIKE '1|2026-09-10%'", Long.class);
    }
}
