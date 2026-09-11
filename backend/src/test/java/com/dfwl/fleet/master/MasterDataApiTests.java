package com.dfwl.fleet.master;

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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:masterdb;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-auth-test.sql"
})
@AutoConfigureMockMvc
class MasterDataApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TokenAuthenticationService tokenAuthenticationService;

    private String token;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM route_task");
        jdbcTemplate.update("DELETE FROM vehicle_trailer_history");
        jdbcTemplate.update("DELETE FROM vehicle_trailer_current");
        jdbcTemplate.update("DELETE FROM driver_vehicle_history");
        jdbcTemplate.update("DELETE FROM driver_vehicle_current");
        jdbcTemplate.update("DELETE FROM trailer");
        jdbcTemplate.update("DELETE FROM vehicle");
        jdbcTemplate.update("DELETE FROM driver");

        AuthenticatedUser user = new AuthenticatedUser(1L, "13800000000", 1L, "admin", "管理员", Set.of(
                "driver:list", "driver:add", "driver:bindVehicle", "driver:unbindVehicle",
                "vehicle:list", "vehicle:add", "vehicle:view", "vehicle:bindingHistory",
                "trailer:add", "trailer:bindVehicle", "trailer:unbindVehicle"));
        token = tokenAuthenticationService.issueToken(user);
    }

    @Test
    void createsDriverVehicleAndBindingHistory() throws Exception {
        long driverId = createDriver("张三", "13810000000");
        long vehicleId = createVehicle("京A10001");

        mockMvc.perform(post("/api/v1/drivers/%d/bind-vehicle".formatted(driverId))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"vehicleId":%d}
                                """.formatted(vehicleId)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/drivers/%d".formatted(driverId))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentVehicleId").value(vehicleId));

        mockMvc.perform(get("/api/v1/vehicles/%d/binding-history".formatted(vehicleId))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].subjectId").value(vehicleId))
                .andExpect(jsonPath("$.data[0].targetId").value(driverId));
    }

    @Test
    void inTransitRouteBlocksDriverVehicleUnbind() throws Exception {
        long driverId = createDriver("李四", "13810000001");
        long vehicleId = createVehicle("京A10002");
        mockMvc.perform(post("/api/v1/drivers/%d/bind-vehicle".formatted(driverId))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"vehicleId":%d}
                                """.formatted(vehicleId)))
                .andExpect(status().isOk());

        jdbcTemplate.update("""
                INSERT INTO route_task (route_no, business_unique_key, business_date, customer_id, product_id, direction,
                                        loading_place, unloading_place, tax_unit_price, assigned_driver_id,
                                        departure_vehicle_id, status, created_by)
                VALUES ('R001', 'K001', CURRENT_DATE, 1, 1, 'OUT', 'A', 'B', 1.0000, ?, ?, 'IN_TRANSIT', 1)
                """, driverId, vehicleId);

        mockMvc.perform(post("/api/v1/drivers/%d/unbind-vehicle".formatted(driverId))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"换班"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BIND_003"));
    }

    private long createDriver(String name, String phone) throws Exception {
        mockMvc.perform(post("/api/v1/drivers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"driverType":"INTERNAL","name":"%s","phone":"%s","status":1}
                                """.formatted(name, phone)))
                .andExpect(status().isOk());
        return jdbcTemplate.queryForObject("SELECT id FROM driver WHERE phone = ?", Long.class, phone);
    }

    private long createVehicle(String plateNo) throws Exception {
        mockMvc.perform(post("/api/v1/vehicles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"plateNo":"%s","insuranceComplete":true,"energyType":"ELECTRIC","maxLoad":30.000,
                                 "loadStandardType":"PERCENT","loadStandardPercent":0.9000,"status":1}
                                """.formatted(plateNo)))
                .andExpect(status().isOk());
        return jdbcTemplate.queryForObject("SELECT id FROM vehicle WHERE plate_no = ?", Long.class, plateNo);
    }
}
