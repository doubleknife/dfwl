package com.dfwl.fleet.system;

import static org.assertj.core.api.Assertions.assertThat;

import static com.dfwl.fleet.security.TestFunctionGrants.grants;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:p16bdb;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-auth-test.sql"
})
@AutoConfigureMockMvc
class SystemManagementContractTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TokenAuthenticationService tokenService;

    @Autowired private com.dfwl.fleet.security.FunctionRegistry functionRegistry;
    @Autowired private com.dfwl.fleet.system.service.SystemManagementService management;
    @Autowired private org.springframework.transaction.PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM auth_session");
        jdbcTemplate.update("DELETE FROM audit_log");
        jdbcTemplate.update("DELETE FROM approval_flow_node");
        jdbcTemplate.update("DELETE FROM approval_flow");
        jdbcTemplate.update("DELETE FROM approval_template");
        jdbcTemplate.update("DELETE FROM driver");
        jdbcTemplate.update("DELETE FROM sys_role_permission");
        jdbcTemplate.update("DELETE FROM sys_permission");
        jdbcTemplate.update("DELETE FROM sys_user");
        jdbcTemplate.update("DELETE FROM sys_role");
        jdbcTemplate.update("DELETE FROM product");

        jdbcTemplate.update("INSERT INTO sys_role (id, role_code, role_name, status) VALUES (1, 'ADMIN', '管理员', 1)");
        jdbcTemplate.update("INSERT INTO sys_role (id, role_code, role_name, status) VALUES (2, 'FINANCE', '财务', 1)");
        for (var function : functionRegistry.all()) {
            jdbcTemplate.update("INSERT INTO sys_permission(permission_code,permission_name,permission_type,status) VALUES(?,?,?,1)",
                    function.code(), function.name(), function.type());
        }
        jdbcTemplate.update("""
                INSERT INTO sys_user (id, phone, password_hash, role_id, status)
                VALUES (1, '13800000000', ?, 1, 1)
                """, passwordEncoder.encode("adminPass"));
    }

    @Test
    void productCrudUsesRealApi() throws Exception {
        String token = token("PAGE_PRODUCTS", "BUTTON_PRODUCTS_MANAGE");

        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"货物一","enabled":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("货物一"));

        mockMvc.perform(get("/api/v1/products")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));

        mockMvc.perform(put("/api/v1/products/1")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"货物一更新","enabled":true,"expectedVersion":0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("货物一更新"));

        mockMvc.perform(put("/api/v1/products/1/status")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false));

        String templateOnly = token("PAGE_TEMPLATES");
        mockMvc.perform(get("/api/v1/products").header("Authorization", bearer(templateOnly)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/products").header("Authorization", bearer(templateOnly))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"不可新增\",\"enabled\":true}"))
                .andExpect(status().isForbidden());
        String productReadOnly = token("PAGE_PRODUCTS");
        mockMvc.perform(get("/api/v1/products").header("Authorization", bearer(productReadOnly)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/products").header("Authorization", bearer(productReadOnly))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"不可新增\",\"enabled\":true}"))
                .andExpect(status().isForbidden());
        String orphanButton = tokenService.issueToken(new AuthenticatedUser(1L, "13800000000", 1L,
                "ADMIN", "管理员", Set.of("BUTTON_PRODUCTS_MANAGE")));
        mockMvc.perform(post("/api/v1/products").header("Authorization", bearer(orphanButton))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"不可新增\",\"enabled\":true}"))
                .andExpect(status().isForbidden());
        String templateManager = token("BUTTON_TEMPLATES_MANAGE");
        mockMvc.perform(get("/api/v1/products").header("Authorization", bearer(templateManager)))
                .andExpect(status().isOk());
    }

    @Test
    void dispatchViewLoadsOnlyMinimalDriverCandidatesWithoutDriverManagementPermission() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO driver (id, driver_type, name, phone, driving_license_no, id_card_no, status)
                VALUES (101, 'INTERNAL', '调度候选', '13900000101', 'TEST-DL-101', '110105199001010117', 1),
                       (102, 'OUTSOURCED', '停用司机', '13900000102', 'TEST-DL-102', '110105199001010125', 0)
                """);
        String dispatcher = token("PAGE_DISPATCH", "BUTTON_DISPATCH_PUBLISH");
        mockMvc.perform(get("/api/v1/dispatch-batches/driver-options?pageNo=1&pageSize=100")
                        .header("Authorization", bearer(dispatcher)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].name").value("调度候选"))
                .andExpect(jsonPath("$.data.records[0].idCardNo").doesNotExist())
                .andExpect(jsonPath("$.data.records[0].drivingLicenseNo").doesNotExist());
        mockMvc.perform(get("/api/v1/route-templates")
                        .header("Authorization", bearer(dispatcher)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/drivers")
                        .header("Authorization", bearer(dispatcher)))
                .andExpect(status().isForbidden());
        String templateViewer = token("PAGE_TEMPLATES");
        mockMvc.perform(get("/api/v1/dispatch-batches/driver-options")
                        .header("Authorization", bearer(templateViewer)))
                .andExpect(status().isForbidden());
    }

    @Test
    void bindingAndDispatchCandidatesDoNotExposeFullVehicleOrDriverRecords() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO driver(id,driver_type,name,phone,driving_license_no,id_card_no,status)
                VALUES(201,'INTERNAL','绑定候选','13900000201','TEST-DL-201','110105199001010117',1)
                """);
        jdbcTemplate.update("""
                INSERT INTO vehicle(id,plate_no,vehicle_license_no,energy_type,max_load,
                                    load_standard_type,load_standard_min_load,status)
                VALUES(201,'测A201','SECRET-LICENSE-201','GAS',10,'FIXED',5,1)
                """);
        String binding = token("PAGE_BINDING");
        mockMvc.perform(get("/api/v1/drivers/binding-candidates").header("Authorization", bearer(binding)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].idCardNo").doesNotExist())
                .andExpect(jsonPath("$.data.records[0].drivingLicenseNo").doesNotExist());
        mockMvc.perform(get("/api/v1/bindings/vehicle-options").header("Authorization", bearer(binding)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].vehicleLicenseNo").doesNotExist());
        mockMvc.perform(get("/api/v1/vehicles").header("Authorization", bearer(binding)))
                .andExpect(status().isForbidden());
        String dispatch = token("PAGE_DISPATCH");
        mockMvc.perform(get("/api/v1/dispatch-batches/vehicle-options").header("Authorization", bearer(dispatch)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].vehicleLicenseNo").doesNotExist());
        mockMvc.perform(get("/api/v1/bindings/vehicle-options").header("Authorization", bearer(dispatch)))
                .andExpect(status().isForbidden());
    }

    @Test
    void userListCreateDisableResetAndSingleRoleAreSupported() throws Exception {
        String token = token("BUTTON_USERS_ADD", "BUTTON_USERS_EDIT", "BUTTON_USERS_RESET_PASSWORD");

        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"  财务姓名  ","phone":"13900000000","password":"newPass","roleId":2,"status":1}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roleId").value(2))
                .andExpect(jsonPath("$.data.name").value("财务姓名"));

        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2));

        mockMvc.perform(get("/api/v1/users/2")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.phone").value("13900000000"));

        mockMvc.perform(put("/api/v1/users/2/status")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":0}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/users/2/reset-password")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"resetPass\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/users/2")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"  更新姓名  ","phone":"13900000000","roleId":1,"status":1}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roleId").value(1))
                .andExpect(jsonPath("$.data.name").value("更新姓名"));
    }

    @Test
    void changingLoginAccountIsRejectedWithoutChangingOtherFields() throws Exception {
        String token = token("BUTTON_USERS_EDIT");
        jdbcTemplate.update("UPDATE sys_user SET name='原姓名' WHERE id=1");
        mockMvc.perform(put("/api/v1/users/1").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"不应保存","phone":"13900000001","roleId":2,"status":0}
                    """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("登录账号创建后不可修改"));
        assertThat(jdbcTemplate.queryForObject("SELECT phone FROM sys_user WHERE id=1", String.class)).isEqualTo("13800000000");
        assertThat(jdbcTemplate.queryForObject("SELECT name FROM sys_user WHERE id=1", String.class)).isEqualTo("原姓名");
        assertThat(jdbcTemplate.queryForObject("SELECT role_id FROM sys_user WHERE id=1", Long.class)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM sys_user WHERE id=1", Integer.class)).isEqualTo(1);
    }

    @Test
    void rolePermissionsCanBeSavedAgainstExistingRegistryOnly() throws Exception {
        String token = token("BUTTON_ROLES_ADD", "BUTTON_ROLES_GRANT");

        mockMvc.perform(post("/api/v1/roles")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roleName":"运营","status":1}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roleCode").value(org.hamcrest.Matchers.startsWith("ROLE_")));

        mockMvc.perform(put("/api/v1/roles/3/permissions")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"permissionCodes":["PAGE_USERS","PAGE_AUDIT"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.permissions[0].permissionCode").value("PAGE_AUDIT"))
                .andExpect(jsonPath("$.data.permissions[1].permissionCode").value("PAGE_USERS"));

        mockMvc.perform(get("/api/v1/roles/3")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.permissions.length()").value(2));

        mockMvc.perform(put("/api/v1/roles/3/permissions")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"permissionCodes":["made:up"]}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SYS_002"));
    }

    @Test
    void retiredPermissionRowsCannotBeListedOrGrantedEvenIfOldDatabaseContainsThem() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO sys_permission (permission_code, permission_name, permission_type)
                VALUES ('tire:request:process', '处理换胎申请', 'API')
                """);
        String token = token("BUTTON_ROLES_GRANT");
        mockMvc.perform(get("/api/v1/permissions")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(functionRegistry.all().size()));
        mockMvc.perform(put("/api/v1/roles/2/permissions")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissionCodes\":[\"tire:request:process\"]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SYS_002"));
    }

    @Test
    void roleListEditAndStatusUseRealApi() throws Exception {
        String token = token("BUTTON_ROLES_EDIT");

        mockMvc.perform(get("/api/v1/roles")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2));

        mockMvc.perform(put("/api/v1/roles/2")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roleCode":"FINANCE","roleName":"财务更新","status":1}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roleName").value("财务更新"));

        mockMvc.perform(put("/api/v1/roles/2/status?status=0")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(0));
    }

    @Test
    void disabledFinanceRoleBlocksLoginAndExistingSessionsUntilFreshLogin() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO sys_user (id, phone, password_hash, role_id, status)
                VALUES (2, '13900000002', ?, 2, 1)
                """, passwordEncoder.encode("financePass"));
        String adminToken = token("BUTTON_ROLES_EDIT");
        String oldFinanceToken = tokenService.issueToken(new AuthenticatedUser(
                2L, "13900000002", 2L, "FINANCE", "财务", Set.of()));
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"13900000002\",\"password\":\"financePass\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/roles/2/status?status=0")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"13900000002\",\"password\":\"financePass\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AUTH_006"))
                .andExpect(jsonPath("$.message").value("所属角色已停用"));
        mockMvc.perform(get("/api/v1/me").header("Authorization", bearer(oldFinanceToken)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put("/api/v1/roles/2/status?status=1")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/me").header("Authorization", bearer(oldFinanceToken)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"13900000002\",\"password\":\"financePass\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void permissionListCanBeFilteredByType() throws Exception {
        String token = token("PAGE_ROLES");

        mockMvc.perform(get("/api/v1/permissions?permissionType=PAGE")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].permissionType").value("PAGE"));
    }

    @Test
    void auditLogQueryReturnsBeforeAfterAndReason() throws Exception {
        String token = token("PAGE_AUDIT");
        jdbcTemplate.update("""
                INSERT INTO audit_log (id, module, business_type, business_id, operation_type, before_json,
                                       after_json, operator_id, reason, ip, terminal)
                VALUES (1, 'EXPENSE', 'expense_entry', 20, 'UPDATE', '{"status":"ACTIVE"}',
                        '{"status":"REVERSED"}', 1, '冲销', '127.0.0.1', 'WEB')
                """);

        mockMvc.perform(get("/api/v1/audit-logs?module=EXPENSE&businessType=expense_entry&operatorId=1")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].beforeJson").value("{\"status\":\"ACTIVE\"}"))
                .andExpect(jsonPath("$.data.records[0].afterJson").value("{\"status\":\"REVERSED\"}"))
                .andExpect(jsonPath("$.data.records[0].operatorPhone").value("13800000000"))
                .andExpect(jsonPath("$.data.records[0].reason").value("冲销"));
    }

    @Test
    void systemManagementApisRejectMissingPermission() throws Exception {
        String token = token();

        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_003"));
    }

    @Test
    void roleGrantChangeRefreshesRightsWithoutRevokingTheSession() throws Exception {
        String oldToken = token();
        management.replaceRolePermissions(1L,
                new com.dfwl.fleet.system.dto.request.RolePermissionRequest(java.util.List.of("PAGE_USERS")), 1L);
        mockMvc.perform(get("/api/v1/me").header("Authorization", bearer(oldToken)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/users").header("Authorization", bearer(oldToken)))
                .andExpect(status().isOk());
        management.replaceRolePermissions(1L,
                new com.dfwl.fleet.system.dto.request.RolePermissionRequest(java.util.List.of()), 1L);
        mockMvc.perform(get("/api/v1/users").header("Authorization", bearer(oldToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/me").header("Authorization", bearer(oldToken)))
                .andExpect(status().isOk());
    }

    @Test
    void rolledBackGrantChangeDoesNotLogOutOrRemoveExistingRights() throws Exception {
        management.replaceRolePermissions(1L,
                new com.dfwl.fleet.system.dto.request.RolePermissionRequest(java.util.List.of("PAGE_USERS")), 1L);
        String token = token("PAGE_USERS");
        new org.springframework.transaction.support.TransactionTemplate(transactionManager).execute(status -> {
            management.replaceRolePermissions(1L,
                    new com.dfwl.fleet.system.dto.request.RolePermissionRequest(java.util.List.of()), 1L);
            status.setRollbackOnly();
            return null;
        });
        mockMvc.perform(get("/api/v1/users").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
    }

    @Test
    void selfPasswordChangeRequiresCurrentPasswordAndRevokesSession() throws Exception {
        String token = token();
        mockMvc.perform(put("/api/v1/auth/password")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"wrong\",\"newPassword\":\"nextSecret99\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("原密码错误"));
        mockMvc.perform(put("/api/v1/auth/password")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"adminPass\",\"newPassword\":\"nextSecret99\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/me").header("Authorization", bearer(token)))
                .andExpect(status().isUnauthorized());
        org.junit.jupiter.api.Assertions.assertTrue(passwordEncoder.matches("nextSecret99",
                jdbcTemplate.queryForObject("SELECT password_hash FROM sys_user WHERE id = 1", String.class)));
    }

    @Test
    void namesAreReadFromTheirAuthoritativeSourceAndBlankNamesRejected() throws Exception {
        String token = token("BUTTON_USERS_ADD");
        jdbcTemplate.update("UPDATE sys_user SET name='系统姓名' WHERE id=1");
        mockMvc.perform(get("/api/v1/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.name").value("系统姓名"));
        jdbcTemplate.update("INSERT INTO driver(id,user_id,driver_type,name,phone,id_card_no,driving_license_no,status) "
                + "VALUES(91,1,'OUTSOURCED','司机姓名','13800000000','110105199001010117','TEST-91',1)");
        mockMvc.perform(get("/api/v1/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.name").value("司机姓名"));
        assertThat(jdbcTemplate.queryForObject("SELECT name FROM sys_user WHERE id=1", String.class)).isEqualTo("系统姓名");
        token = token("BUTTON_USERS_ADD");
        mockMvc.perform(post("/api/v1/users").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"   ","phone":"13900000000","password":"testPass","roleId":2,"status":1}
                """)).andExpect(status().isBadRequest());
    }

    @Test
    void oneTimeNameSqlFillsOnlyNonDriverAccounts() throws Exception {
        jdbcTemplate.update("INSERT INTO sys_user(id,phone,password_hash,role_id,status) VALUES(2,'13900000002','test',2,1)");
        jdbcTemplate.update("INSERT INTO driver(id,user_id,driver_type,name,phone,id_card_no,driving_license_no,status) "
                + "VALUES(91,1,'INTERNAL','司机姓名','13800000000','110105199001010117','TEST-91',1)");
        // Remove the baseline column only in this isolated H2 test to exercise the exact upgrade script.
        jdbcTemplate.execute("ALTER TABLE sys_user DROP COLUMN name");
        String sql = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/resources/db/migration/V2__user_name.sql"));
        new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(
                new org.springframework.core.io.ByteArrayResource(sql.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .execute(jdbcTemplate.getDataSource());
        assertThat(jdbcTemplate.queryForObject("SELECT name FROM sys_user WHERE id=1", String.class)).isNull();
        assertThat(jdbcTemplate.queryForObject("SELECT name FROM sys_user WHERE id=2", String.class)).isEqualTo("13900000002");
    }

    private String token(String... permissions) {
        return tokenService.issueToken(new AuthenticatedUser(1L, "13800000000", 1L, "ADMIN", "管理员", grants(permissions)));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
