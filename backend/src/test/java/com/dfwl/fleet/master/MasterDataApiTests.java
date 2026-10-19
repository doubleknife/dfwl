package com.dfwl.fleet.master;

import static com.dfwl.fleet.security.TestFunctionGrants.grants;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dfwl.fleet.security.AuthenticatedUser;
import com.dfwl.fleet.security.TokenAuthenticationService;
import com.dfwl.fleet.auth.service.AuthService;
import com.dfwl.fleet.auth.dto.request.LoginRequest;
import com.dfwl.fleet.common.error.BusinessException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    @Autowired private AuthService authService;

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
        jdbcTemplate.update("DELETE FROM auth_session");
        jdbcTemplate.update("DELETE FROM sys_user");
        jdbcTemplate.update("DELETE FROM sys_role");
        jdbcTemplate.update("""
                INSERT INTO sys_role (id, role_code, role_name, status) VALUES
                  (1, 'ADMIN', '管理员', 1),
                  (2, 'DRIVER', '司机', 1),
                  (3, 'VIEWER', '只读', 1)
                """);
        jdbcTemplate.update("INSERT INTO sys_user(id,phone,password_hash,role_id,status) VALUES(1,'13800000000','test-only',1,1)");
        jdbcTemplate.update("INSERT INTO sys_user(id,phone,password_hash,role_id,status) VALUES(3,'13800000003','test-only',3,1)");

        AuthenticatedUser user = new AuthenticatedUser(1L, "13800000000", 1L, "admin", "管理员", grants(
                "PAGE_DRIVERS", "BUTTON_DRIVERS_ADD", "BUTTON_DRIVERS_EDIT", "BUTTON_BINDING_BIND_DRIVER", "BUTTON_BINDING_UNBIND_DRIVER",
                "PAGE_VEHICLES", "BUTTON_VEHICLES_ADD", "BUTTON_VEHICLES_EDIT", "PAGE_VEHICLES", "PAGE_BINDING",
                "PAGE_TRAILERS", "BUTTON_TRAILERS_ADD", "BUTTON_BINDING_BIND_TRAILER", "BUTTON_BINDING_UNBIND_TRAILER"));
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
    void driverListFiltersAcrossTheWholeServerSidePage() throws Exception {
        createDriver("张三", "13810000000");
        createDriver("李四", "13810000001");
        mockMvc.perform(get("/api/v1/drivers?pageNo=1&pageSize=1&keyword=李四&driverType=INTERNAL&status=1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].name").value("李四"));
        mockMvc.perform(get("/api/v1/drivers?pageNo=1&pageSize=1&keyword=13810000000&driverType=OUTSOURCED")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
        mockMvc.perform(get("/api/v1/drivers?pageNo=1&pageSize=20&status=3")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
    }

    @Test
    void driverEditUsesExistingPutAndRequiresEditPermission() throws Exception {
        long driverId = createDriver("张三", "13810000000");
        String update = """
                {"driverType":"INTERNAL","name":"张三更新","phone":"13810000000","drivingLicenseNo":"DL-13810000000","idCardNo":"%s","status":0}
                """.formatted(testCard("13810000000"));
        mockMvc.perform(put("/api/v1/drivers/%d".formatted(driverId))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("张三更新"))
                .andExpect(jsonPath("$.data.status").value(0));
        AuthenticatedUser viewer = new AuthenticatedUser(3L, "13800000003", 3L, "viewer", "只读", grants("PAGE_DRIVERS"));
        String viewerToken = tokenAuthenticationService.issueToken(viewer);
        mockMvc.perform(put("/api/v1/drivers/%d".formatted(driverId))
                        .header("Authorization", "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isForbidden());
    }

    @Test
    void disabledDriverCannotLoginOrReuseAnIssuedToken() throws Exception {
        long driverId = createDriver("司机登录", "13810000000");
        String issued = authService.login(new LoginRequest("13810000000", "local-test-only")).accessToken();
        String unusedUntilReenabled = authService.login(new LoginRequest("13810000000", "local-test-only")).accessToken();
        assertThat(tokenAuthenticationService.authenticate(issued)).isPresent();
        mockMvc.perform(put("/api/v1/drivers/%d".formatted(driverId))
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"driverType":"INTERNAL","name":"司机登录","phone":"13810000000",
                     "drivingLicenseNo":"DL-13810000000","idCardNo":"%s","status":0}
                    """.formatted(testCard("13810000000")))).andExpect(status().isOk());
        assertThat(tokenAuthenticationService.authenticate(issued)).isEmpty();
        assertThatThrownBy(() -> authService.login(new LoginRequest("13810000000", "local-test-only")))
                .isInstanceOf(BusinessException.class);
        mockMvc.perform(put("/api/v1/drivers/%d".formatted(driverId))
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"driverType":"INTERNAL","name":"司机登录","phone":"13810000000",
                     "drivingLicenseNo":"DL-13810000000","idCardNo":"%s","status":1}
                    """.formatted(testCard("13810000000")))).andExpect(status().isOk());
        assertThat(tokenAuthenticationService.authenticate(issued)).isEmpty();
        assertThat(tokenAuthenticationService.authenticate(unusedUntilReenabled)).isEmpty();
        assertThat(authService.login(new LoginRequest("13810000000", "local-test-only")).accessToken()).isNotBlank();
    }

    @Test
    void driverCertificateValidationDerivesBirthAndRejectsDuplicates() throws Exception {
        long first = createDriver("证件测试", "13810000901");
        mockMvc.perform(get("/api/v1/drivers/%d".formatted(first))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.idCardNo").value(testCard("13810000901")))
                .andExpect(jsonPath("$.data.birthDate").value("1990-01-01"))
                .andExpect(jsonPath("$.data.age").isNumber());
        mockMvc.perform(post("/api/v1/drivers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"driverType":"INTERNAL","name":"重复身份证","phone":"13810000902",
                                 "drivingLicenseNo":"DL-NEW","idCardNo":"%s","status":1}
                                """.formatted(testCard("13810000901"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("身份证号已被其他司机使用"));
        mockMvc.perform(post("/api/v1/drivers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"driverType":"INTERNAL","name":"重复驾驶证","phone":"13810000903",
                                 "drivingLicenseNo":"DL-13810000901","idCardNo":"%s","status":1}
                                """.formatted(testCard("13810000903"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("驾驶证号已被其他司机使用"));
        long second = createDriver("另一司机", "13810000905");
        mockMvc.perform(put("/api/v1/drivers/%d".formatted(second))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"driverType":"INTERNAL","name":"另一司机","phone":"13810000905",
                                 "drivingLicenseNo":"DL-13810000905","idCardNo":"%s","status":1}
                                """.formatted(testCard("13810000901"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("身份证号已被其他司机使用"));
        mockMvc.perform(put("/api/v1/drivers/%d".formatted(second))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"driverType":"INTERNAL","name":"另一司机","phone":"13810000905",
                                 "drivingLicenseNo":"DL-13810000901","idCardNo":"%s","status":1}
                                """.formatted(testCard("13810000905"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("驾驶证号已被其他司机使用"));
        for (String invalid : new String[]{"110105199002300012", "110105199001010010", "123"}) {
            mockMvc.perform(post("/api/v1/drivers")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"driverType":"INTERNAL","name":"非法证件","phone":"13810000904",
                                     "drivingLicenseNo":"DL-INVALID","idCardNo":"%s","status":1}
                                    """.formatted(invalid)))
                    .andExpect(status().isConflict());
        }
    }

    @Test
    void vehicleLicenseIsRequiredTrimmedAndUnique() throws Exception {
        createVehicle("京A10901");
        long second = createVehicle("京A10904");
        mockMvc.perform(post("/api/v1/vehicles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"plateNo":"京A10902","vehicleLicenseNo":" VL-京A10901 ","insuranceComplete":true,
                                 "energyType":"ELECTRIC","maxLoad":30,"loadStandardType":"PERCENT",
                                 "loadStandardPercent":0.9,"status":1}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("行驶证号已被其他车辆使用"));
        mockMvc.perform(put("/api/v1/vehicles/%d".formatted(second))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"plateNo":"京A10904","vehicleLicenseNo":"VL-京A10901","insuranceComplete":true,
                                 "energyType":"ELECTRIC","maxLoad":30,"loadStandardType":"PERCENT",
                                 "loadStandardPercent":0.9,"status":1}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("行驶证号已被其他车辆使用"));
        mockMvc.perform(post("/api/v1/vehicles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"plateNo":"京A10903","insuranceComplete":true,"energyType":"ELECTRIC",
                                 "maxLoad":30,"loadStandardType":"PERCENT","loadStandardPercent":0.9,"status":1}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void outsourcedDriverCannotBindCompanyVehicle() throws Exception {
        long vehicleId = createVehicle("京A10006");
        jdbcTemplate.update("INSERT INTO driver (driver_type,name,phone,id_card_no,driving_license_no,status) VALUES ('OUTSOURCED','外协司机','13810000006','110105199001010061','TEST-DL-006',1)");
        long driverId = jdbcTemplate.queryForObject("SELECT id FROM driver WHERE phone='13810000006'", Long.class);
        mockMvc.perform(post("/api/v1/drivers/%d/bind-vehicle".formatted(driverId))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vehicleId\":" + vehicleId + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AUTH_003"));
        mockMvc.perform(post("/api/v1/drivers/%d/unbind-vehicle".formatted(driverId))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AUTH_003"));
    }

    @Test
    void vehiclePercentMustUseFractionalApiUnit() throws Exception {
        mockMvc.perform(post("/api/v1/vehicles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"plateNo":"京A10007","vehicleLicenseNo":"VL-京A10007","insuranceComplete":true,"energyType":"ELECTRIC",
                                 "maxLoad":30,"loadStandardType":"PERCENT","loadStandardPercent":80,"status":1}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void fixedVehicleStandardMustStayWithinMaximumLoad() throws Exception {
        for (String standard : new String[]{"-1", "50"}) {
            mockMvc.perform(post("/api/v1/vehicles")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"plateNo":"京A10008","vehicleLicenseNo":"VL-京A10008","insuranceComplete":true,"energyType":"ELECTRIC",
                                     "maxLoad":32,"loadStandardType":"FIXED","loadStandardMinLoad":%s,"status":1}
                                    """.formatted(standard)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("SYS_002"));
        }
        for (String standard : new String[]{"0", "32"}) {
            mockMvc.perform(post("/api/v1/vehicles")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"plateNo":"京A1000%s","vehicleLicenseNo":"VL-京A1000%s","insuranceComplete":true,"energyType":"ELECTRIC",
                                     "maxLoad":32,"loadStandardType":"FIXED","loadStandardMinLoad":%s,"status":1}
                                    """.formatted(standard.equals("0") ? "8" : "9", standard.equals("0") ? "8" : "9", standard)))
                    .andExpect(status().isOk());
        }
        long vehicleId = jdbcTemplate.queryForObject("SELECT id FROM vehicle WHERE plate_no='京A10008'", Long.class);
        mockMvc.perform(put("/api/v1/vehicles/%d".formatted(vehicleId))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"plateNo":"京A10008","vehicleLicenseNo":"VL-京A10008","insuranceComplete":true,"energyType":"ELECTRIC",
                                 "maxLoad":32,"loadStandardType":"FIXED","loadStandardMinLoad":50,"status":1}
                                """))
                .andExpect(status().isConflict());
        Number unchanged = jdbcTemplate.queryForObject("SELECT load_standard_min_load FROM vehicle WHERE id=?", Number.class, vehicleId);
        org.assertj.core.api.Assertions.assertThat(unchanged.intValue()).isZero();
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
                                {"driverType":"INTERNAL","name":"%s","phone":"%s","drivingLicenseNo":"DL-%s","idCardNo":"%s","status":1,
                                 "initialPassword":"local-test-only"}
                                """.formatted(name, phone, phone, testCard(phone))))
                .andExpect(status().isOk());
        return jdbcTemplate.queryForObject("SELECT id FROM driver WHERE phone = ?", Long.class, phone);
    }

    private long createVehicle(String plateNo) throws Exception {
        mockMvc.perform(post("/api/v1/vehicles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"plateNo":"%s","vehicleLicenseNo":"VL-%s","insuranceComplete":true,"energyType":"ELECTRIC","maxLoad":30.000,
                                 "loadStandardType":"PERCENT","loadStandardPercent":0.9000,"status":1}
                                """.formatted(plateNo, plateNo)))
                .andExpect(status().isOk());
        return jdbcTemplate.queryForObject("SELECT id FROM vehicle WHERE plate_no = ?", Long.class, plateNo);
    }

    private String testCard(String phone) {
        String first17 = "11010519900101" + phone.substring(phone.length() - 3);
        int[] weights = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
        int sum = 0;
        for (int i = 0; i < 17; i++) sum += (first17.charAt(i) - '0') * weights[i];
        return first17 + "10X98765432".charAt(sum % 11);
    }
}
