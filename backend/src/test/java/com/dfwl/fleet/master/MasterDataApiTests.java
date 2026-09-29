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
        jdbcTemplate.update("DELETE FROM departure_event");
        jdbcTemplate.update("DELETE FROM transport_participant");
        jdbcTemplate.update("DELETE FROM transport_task");
        jdbcTemplate.update("DELETE FROM route_template");
        jdbcTemplate.update("DELETE FROM product");
        jdbcTemplate.update("DELETE FROM vehicle_trailer_history");
        jdbcTemplate.update("DELETE FROM vehicle_trailer_current");
        jdbcTemplate.update("DELETE FROM driver_vehicle_history");
        jdbcTemplate.update("DELETE FROM driver_vehicle_current");
        jdbcTemplate.update("DELETE FROM trailer");
        jdbcTemplate.update("DELETE FROM vehicle");
        jdbcTemplate.update("DELETE FROM driver");
        jdbcTemplate.update("DELETE FROM sys_user");
        jdbcTemplate.update("DELETE FROM sys_role");
        jdbcTemplate.update("INSERT INTO sys_role (id, role_code, role_name, status) VALUES (2, 'DRIVER', '司机', 1)");

        AuthenticatedUser user = new AuthenticatedUser(1L, "13800000000", 1L, "admin", "管理员", Set.of(
                "driver:list", "driver:add", "driver:bindVehicle", "driver:unbindVehicle",
                "vehicle:list", "vehicle:add", "vehicle:view", "vehicle:bindingHistory",
                "trailer:list", "trailer:add", "trailer:bindVehicle", "trailer:unbindVehicle"));
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
                .andExpect(jsonPath("$.data.currentVehicleId").value(vehicleId))
                .andExpect(jsonPath("$.data.currentVehiclePlateNo").value("京A10001"));

        mockMvc.perform(get("/api/v1/drivers?pageNo=1&pageSize=20")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].currentVehiclePlateNo").value("京A10001"));

        mockMvc.perform(get("/api/v1/vehicles/%d/binding-history".formatted(vehicleId))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].subjectId").value(vehicleId))
                .andExpect(jsonPath("$.data[0].targetId").value(driverId))
                .andExpect(jsonPath("$.data[0].subjectName").value("京A10001"))
                .andExpect(jsonPath("$.data[0].targetName").value("张三"));

        mockMvc.perform(get("/api/v1/vehicles/%d".formatted(vehicleId))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentDriverName").value("张三"));

        mockMvc.perform(get("/api/v1/drivers/%d/binding-history".formatted(driverId))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].subjectId").value(vehicleId))
                .andExpect(jsonPath("$.data[0].targetId").value(driverId))
                .andExpect(jsonPath("$.data[0].subjectName").value("京A10001"))
                .andExpect(jsonPath("$.data[0].targetName").value("张三"));
    }

    @Test
    void readsVehicleTrailerBindingHistoryWithoutChangingBinding() throws Exception {
        long vehicleId = createVehicle("京A10003");
        mockMvc.perform(post("/api/v1/trailers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"trailerNo":"T10003","plateNo":"京B10003","insuranceComplete":true,"status":1}
                                """))
                .andExpect(status().isOk());
        long trailerId = jdbcTemplate.queryForObject("SELECT id FROM trailer WHERE plate_no = '京B10003'", Long.class);
        mockMvc.perform(post("/api/v1/trailers/%d/bind-vehicle".formatted(trailerId))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"vehicleId":%d}
                                """.formatted(vehicleId)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/vehicles/%d/trailer-binding-history".formatted(vehicleId))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].subjectId").value(vehicleId))
                .andExpect(jsonPath("$.data[0].targetId").value(trailerId))
                .andExpect(jsonPath("$.data[0].subjectName").value("京A10003"))
                .andExpect(jsonPath("$.data[0].targetName").value("京B10003"));

        mockMvc.perform(get("/api/v1/vehicles/%d".formatted(vehicleId))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentTrailerPlateNo").value("京B10003"));

        mockMvc.perform(get("/api/v1/trailers?pageNo=1&pageSize=20")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].currentVehiclePlateNo").value("京A10003"));
    }

    @Test
    void inTransitTaskBlocksDriverVehicleUnbind() throws Exception {
        long driverId = createDriver("李四", "13810000001");
        long vehicleId = createVehicle("京A10002");
        mockMvc.perform(post("/api/v1/drivers/%d/bind-vehicle".formatted(driverId))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"vehicleId":%d}
                                """.formatted(vehicleId)))
                .andExpect(status().isOk());

        jdbcTemplate.update("INSERT INTO product (id, name, enabled) VALUES (1, '砂石', 1)");
        jdbcTemplate.update("""
                INSERT INTO route_template (id, stable_code, name, customer_name, product_id,
                    loading_place, unloading_place, default_unit_price, default_information_fee,
                    default_driver_salary, created_by, updated_by)
                VALUES (1, 'T-001', '验收线路', '客户', 1, 'A', 'B', 1.00, 0.00, 0.00, 1, 1)
                """);
        jdbcTemplate.update("""
                INSERT INTO transport_task (id, task_no, template_id, business_date,
                    template_code_snapshot, template_name_snapshot, customer_name_snapshot,
                    product_name_snapshot, loading_place_snapshot, unloading_place_snapshot,
                    actual_unit_price, actual_information_fee, status, created_by)
                VALUES (1, 'TASK-001', 1, CURRENT_DATE, 'T-001', '验收线路', '客户',
                    '砂石', 'A', 'B', 1.00, 0.00, 'IN_TRANSIT', 1)
                """);
        jdbcTemplate.update("""
                INSERT INTO transport_participant (task_id, sequence_no, driver_id, driver_type_snapshot,
                    company_vehicle_id, vehicle_plate_snapshot, source_type, status, salary_amount, is_current)
                VALUES (1, 1, ?, 'INTERNAL', ?, '京A10002', 'INITIAL', 'IN_TRANSIT', 0.00, 1)
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
                                {"driverType":"INTERNAL","name":"%s","phone":"%s","status":1,
                                 "initialPassword":"local-test-only"}
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
