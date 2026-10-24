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
import com.dfwl.fleet.master.repository.MasterDataRepository;
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
    private MasterDataRepository repository;

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
                "PAGE_TRAILERS", "BUTTON_TRAILERS_ADD", "BUTTON_TRAILERS_EDIT", "BUTTON_BINDING_BIND_TRAILER", "BUTTON_BINDING_UNBIND_TRAILER"));
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
    void existingAccountCannotBeLinkedToNewDriverAndDriverInsertRollsBack() throws Exception {
        for (int roleId : new int[]{2, 3}) {
            String phone = "1381000080" + roleId;
            jdbcTemplate.update("INSERT INTO sys_user(phone,password_hash,role_id,status) VALUES(?,?,?,1)",
                    phone, "existing-test-hash", roleId);
            mockMvc.perform(post("/api/v1/drivers")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"driverType":"INTERNAL","name":"待新增司机","phone":"%s",
                                     "drivingLicenseNo":"DL-%s","idCardNo":"%s","status":1,
                                     "initialPassword":"local-test-only"}
                                    """.formatted(phone, phone, testCard(phone))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("该手机号已有登录账号，不能新增司机"));
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM driver WHERE phone=?", Integer.class, phone))
                    .isZero();
            assertThat(jdbcTemplate.queryForObject("SELECT password_hash FROM sys_user WHERE phone=?", String.class, phone))
                    .isEqualTo("existing-test-hash");
        }
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
        mockMvc.perform(get("/api/v1/drivers/binding-candidates").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
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
        for (String standard : new String[]{"-1", "0", "50", "1.0001"}) {
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
        for (String standard : new String[]{"1", "32"}) {
            mockMvc.perform(post("/api/v1/vehicles")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"plateNo":"京A1000%s","vehicleLicenseNo":"VL-京A1000%s","insuranceComplete":true,"energyType":"ELECTRIC",
                                     "maxLoad":32,"loadStandardType":"FIXED","loadStandardMinLoad":%s,"status":1}
                                    """.formatted(standard.equals("1") ? "8" : "9", standard.equals("1") ? "8" : "9", standard)))
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
        org.assertj.core.api.Assertions.assertThat(unchanged.intValue()).isEqualTo(1);
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

    @Test
    void disablingVehicleUnbindsDriverAndRejectsFutureBindings() throws Exception {
        long driverId = createDriver("停用测试司机", "13810000011");
        long vehicleId = createVehicle("京A11011");
        long trailerId = createTrailer("T11011", "京B11011");
        bindDriverVehicle(driverId, vehicleId);
        bindVehicleTrailer(trailerId, vehicleId);

        mockMvc.perform(put("/api/v1/vehicles/{id}", vehicleId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(vehicleJson("京A11011", 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(0))
                .andExpect(jsonPath("$.data.currentDriverId").doesNotExist())
                .andExpect(jsonPath("$.data.currentTrailerId").doesNotExist());
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM driver_vehicle_current WHERE vehicle_id=?", Integer.class, vehicleId)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM vehicle_trailer_current WHERE vehicle_id=?", Integer.class, vehicleId)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT unbind_reason FROM driver_vehicle_history WHERE vehicle_id=?", String.class, vehicleId))
                .isEqualTo("车辆停用自动解绑");
        assertThat(jdbcTemplate.queryForObject("SELECT unbind_reason FROM vehicle_trailer_history WHERE trailer_id=?", String.class, trailerId))
                .isEqualTo("车辆停用自动解绑车挂");
        mockMvc.perform(post("/api/v1/drivers/{id}/bind-vehicle", driverId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vehicleId\":" + vehicleId + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VEHICLE_DISABLED"));
        mockMvc.perform(post("/api/v1/trailers/{id}/bind-vehicle", trailerId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vehicleId\":" + vehicleId + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VEHICLE_DISABLED"));
    }

    @Test
    void disablingDriverUnbindsVehicleButPreservesVehicleTrailerBinding() throws Exception {
        long driverId = createDriver("停用解绑司机", "13810000031");
        long vehicleId = createVehicle("京A11031");
        long trailerId = createTrailer("T11031", "京B11031");
        bindDriverVehicle(driverId, vehicleId);
        bindVehicleTrailer(trailerId, vehicleId);

        mockMvc.perform(get("/api/v1/drivers/{id}", driverId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentTrailerPlateNo").value("京B11031"));
        mockMvc.perform(get("/api/v1/drivers")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].currentTrailerPlateNo").value("京B11031"));
        mockMvc.perform(get("/api/v1/vehicles")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].currentDriverName").value("停用解绑司机"))
                .andExpect(jsonPath("$.data.records[0].currentTrailerPlateNo").value("京B11031"));
        mockMvc.perform(get("/api/v1/trailers")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].currentDriverName").value("停用解绑司机"));

        mockMvc.perform(put("/api/v1/drivers/{id}", driverId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"driverType":"INTERNAL","name":"停用解绑司机","phone":"13810000031",
                                 "drivingLicenseNo":"DL-13810000031","idCardNo":"%s","status":0}
                                """.formatted(testCard("13810000031"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentVehicleId").doesNotExist())
                .andExpect(jsonPath("$.data.currentTrailerPlateNo").doesNotExist());
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM driver_vehicle_current WHERE driver_id=?", Integer.class, driverId)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT unbind_reason FROM driver_vehicle_history WHERE driver_id=?", String.class, driverId))
                .isEqualTo("司机停用自动解绑");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM vehicle_trailer_current WHERE trailer_id=?", Integer.class, trailerId)).isEqualTo(1);
        mockMvc.perform(get("/api/v1/vehicles")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].currentDriverName").doesNotExist())
                .andExpect(jsonPath("$.data.records[0].currentTrailerPlateNo").value("京B11031"));
        mockMvc.perform(get("/api/v1/trailers")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].currentDriverName").doesNotExist());
    }

    @Test
    void unfinishedTaskRejectsDriverDisableAndKeepsBinding() throws Exception {
        long driverId = createDriver("任务中司机", "13810000032");
        long vehicleId = createVehicle("京A11032");
        long trailerId = createTrailer("T11032", "京B11032");
        bindDriverVehicle(driverId, vehicleId);
        bindVehicleTrailer(trailerId, vehicleId);
        insertCurrentTask(driverId, vehicleId, trailerId, "PUBLISHED");

        mockMvc.perform(put("/api/v1/drivers/{id}", driverId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"driverType":"INTERNAL","name":"任务中司机","phone":"13810000032",
                                 "drivingLicenseNo":"DL-13810000032","idCardNo":"%s","status":0}
                                """.formatted(testCard("13810000032"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DRIVER_ACTIVE_TASKS"));
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM driver WHERE id=?", Integer.class, driverId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT vehicle_id FROM driver_vehicle_current WHERE driver_id=?", Long.class, driverId)).isEqualTo(vehicleId);
    }

    @Test
    void bindingPickersPageOnlyEnabledUnboundVehiclesAndTrailers() throws Exception {
        long driverId = createDriver("候选筛选司机", "13810000033");
        long boundVehicle = createVehicle("京A11033");
        long availableVehicle = createVehicle("京A11034");
        long disabledVehicle = createVehicle("京A11035");
        long boundTrailer = createTrailer("T11033", "京B11033");
        long availableTrailer = createTrailer("T11034", "京B11034");
        long disabledTrailer = createTrailer("T11035", "京B11035");
        bindDriverVehicle(driverId, boundVehicle);
        bindVehicleTrailer(boundTrailer, availableVehicle);
        mockMvc.perform(put("/api/v1/vehicles/{id}", disabledVehicle)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(vehicleJson("京A11035", 0)))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/trailers/{id}", disabledTrailer)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(trailerJson("T11035", "京B11035", 0)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/bindings/vehicle-options")
                        .param("onlyUnboundDriver", "true").param("pageSize", "1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(availableVehicle))
                .andExpect(jsonPath("$.data.records[0].currentTrailerId").value(boundTrailer));
        mockMvc.perform(get("/api/v1/bindings/trailer-options")
                        .param("onlyUnboundVehicle", "true").param("pageSize", "1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(availableTrailer));
        mockMvc.perform(get("/api/v1/bindings/vehicle-options")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3));
        mockMvc.perform(get("/api/v1/bindings/trailer-options")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3));
    }

    @Test
    void disablingTrailerUnbindsVehicleAndRejectsFutureBindings() throws Exception {
        long vehicleId = createVehicle("京A11012");
        long trailerId = createTrailer("T11012", "京B11012");
        bindVehicleTrailer(trailerId, vehicleId);

        mockMvc.perform(put("/api/v1/trailers/{id}", trailerId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(trailerJson("T11012", "京B11012", 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(0))
                .andExpect(jsonPath("$.data.currentVehicleId").doesNotExist());
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM vehicle_trailer_current WHERE trailer_id=?", Integer.class, trailerId)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT unbind_reason FROM vehicle_trailer_history WHERE trailer_id=?", String.class, trailerId))
                .isEqualTo("车挂停用自动解绑");
        mockMvc.perform(post("/api/v1/trailers/{id}/bind-vehicle", trailerId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vehicleId\":" + vehicleId + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRAILER_DISABLED"));
    }

    @Test
    void automaticVehicleUnbindOnlyTouchesTheExpectedBinding() throws Exception {
        long driverId = createDriver("改绑测试司机", "13810000013");
        long oldVehicleId = createVehicle("京A11013");
        long newVehicleId = createVehicle("京A11014");
        long trailerId = createTrailer("T11013", "京B11013");
        bindDriverVehicle(driverId, oldVehicleId);
        bindVehicleTrailer(trailerId, oldVehicleId);
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        repository.unbindDriverVehicle(driverId, 1L, "手工换绑", now);
        repository.unbindVehicleTrailer(trailerId, 1L, "手工换绑", now);
        bindDriverVehicle(driverId, newVehicleId);
        bindVehicleTrailer(trailerId, newVehicleId);

        assertThat(repository.unbindDriverVehicleFromVehicle(driverId, oldVehicleId, 1L, "旧车停用", now)).isFalse();
        assertThat(repository.unbindVehicleTrailerFromVehicle(oldVehicleId, trailerId, 1L, "旧车停用", now)).isFalse();
        assertThat(jdbcTemplate.queryForObject("SELECT vehicle_id FROM driver_vehicle_current WHERE driver_id=?", Long.class, driverId))
                .isEqualTo(newVehicleId);
        assertThat(jdbcTemplate.queryForObject("SELECT vehicle_id FROM vehicle_trailer_current WHERE trailer_id=?", Long.class, trailerId))
                .isEqualTo(newVehicleId);
    }

    @Test
    void publishedAndInTransitTasksBlockVehicleAndTrailerDisable() throws Exception {
        var states = java.util.List.of("PUBLISHED", "IN_TRANSIT", "TRANSFER_PENDING");
        for (int i = 0; i < states.size(); i++) {
            String state = states.get(i);
            String suffix = "0" + (21 + i);
            String plate = "京A12" + suffix;
            String trailerNo = "T12" + suffix;
            String trailerPlate = "京B12" + suffix;
            long driverId = createDriver("任务司机" + state, "138100000" + (21 + i));
            long vehicleId = createVehicle(plate);
            long trailerId = createTrailer(trailerNo, trailerPlate);
            bindDriverVehicle(driverId, vehicleId);
            bindVehicleTrailer(trailerId, vehicleId);
            insertCurrentTask(driverId, vehicleId, trailerId, state);

            mockMvc.perform(put("/api/v1/vehicles/{id}", vehicleId)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(vehicleJson(plate, 0)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("VEHICLE_ACTIVE_TASKS"));
            mockMvc.perform(put("/api/v1/trailers/{id}", trailerId)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(trailerJson(trailerNo, trailerPlate, 0)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("TRAILER_ACTIVE_TASKS"));
            assertThat(jdbcTemplate.queryForObject("SELECT status FROM vehicle WHERE id=?", Integer.class, vehicleId)).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject("SELECT status FROM trailer WHERE id=?", Integer.class, trailerId)).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM driver_vehicle_current WHERE vehicle_id=?", Integer.class, vehicleId)).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM vehicle_trailer_current WHERE trailer_id=?", Integer.class, trailerId)).isEqualTo(1);
        }
    }

    private void bindDriverVehicle(long driverId, long vehicleId) throws Exception {
        mockMvc.perform(post("/api/v1/drivers/{id}/bind-vehicle", driverId)
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vehicleId\":" + vehicleId + "}"))
                .andExpect(status().isOk());
    }

    private void bindVehicleTrailer(long trailerId, long vehicleId) throws Exception {
        mockMvc.perform(post("/api/v1/trailers/{id}/bind-vehicle", trailerId)
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vehicleId\":" + vehicleId + "}"))
                .andExpect(status().isOk());
    }

    private long createTrailer(String trailerNo, String plateNo) throws Exception {
        mockMvc.perform(post("/api/v1/trailers")
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content(trailerJson(trailerNo, plateNo, 1)))
                .andExpect(status().isOk());
        return jdbcTemplate.queryForObject("SELECT id FROM trailer WHERE trailer_no=?", Long.class, trailerNo);
    }

    private String vehicleJson(String plateNo, int state) {
        return """
                {"plateNo":"%s","vehicleLicenseNo":"VL-%s","insuranceComplete":true,"energyType":"ELECTRIC",
                 "maxLoad":30.000,"loadStandardType":"PERCENT","loadStandardPercent":0.9000,"status":%d}
                """.formatted(plateNo, plateNo, state);
    }

    private String trailerJson(String trailerNo, String plateNo, int state) {
        return """
                {"trailerNo":"%s","plateNo":"%s","insuranceComplete":true,"status":%d}
                """.formatted(trailerNo, plateNo, state);
    }

    private void insertCurrentTask(long driverId, long vehicleId, long trailerId, String state) {
        jdbcTemplate.update("INSERT INTO product (name, enabled) VALUES (?, 1)", "测试货物" + state);
        long productId = jdbcTemplate.queryForObject("SELECT id FROM product WHERE name=?", Long.class, "测试货物" + state);
        jdbcTemplate.update("""
                INSERT INTO route_template (stable_code, name, customer_name, product_id,
                    loading_place, unloading_place, default_unit_price, default_information_fee,
                    default_driver_salary, created_by, updated_by)
                VALUES (?, '验收线路', '客户', ?, 'A', 'B', 1.00, 0.00, 0.00, 1, 1)
                """, "T-" + state, productId);
        long templateId = jdbcTemplate.queryForObject("SELECT id FROM route_template WHERE stable_code=?", Long.class, "T-" + state);
        jdbcTemplate.update("""
                INSERT INTO transport_task (task_no, template_id, business_date,
                    template_code_snapshot, template_name_snapshot, customer_name_snapshot,
                    product_name_snapshot, loading_place_snapshot, unloading_place_snapshot,
                    actual_unit_price, actual_information_fee, status, created_by)
                VALUES (?, ?, CURRENT_DATE, ?, '验收线路', '客户',
                    '测试货物', 'A', 'B', 1.00, 0.00, ?, 1)
                """, "TASK-" + state, templateId, "T-" + state, state);
        long taskId = jdbcTemplate.queryForObject("SELECT id FROM transport_task WHERE task_no=?", Long.class, "TASK-" + state);
        jdbcTemplate.update("""
                INSERT INTO transport_participant (task_id, sequence_no, driver_id, driver_type_snapshot,
                    company_vehicle_id, vehicle_plate_snapshot, trailer_id, trailer_plate_snapshot,
                    source_type, status, salary_amount, is_current)
                VALUES (?, 1, ?, 'INTERNAL', ?, '车辆', ?, '车挂', 'INITIAL', ?, 0.00, 1)
                """, taskId, driverId, vehicleId, trailerId, "IN_TRANSIT".equals(state) ? "IN_TRANSIT" : "ASSIGNED");
        long participantId = jdbcTemplate.queryForObject("SELECT id FROM transport_participant WHERE task_id=?", Long.class, taskId);
        jdbcTemplate.update("UPDATE transport_task SET current_participant_id=? WHERE id=?", participantId, taskId);
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
