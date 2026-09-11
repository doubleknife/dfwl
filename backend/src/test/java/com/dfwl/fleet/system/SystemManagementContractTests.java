package com.dfwl.fleet.system;

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

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM audit_log");
        jdbcTemplate.update("DELETE FROM approval_flow_node");
        jdbcTemplate.update("DELETE FROM approval_flow");
        jdbcTemplate.update("DELETE FROM sys_role_permission");
        jdbcTemplate.update("DELETE FROM sys_permission");
        jdbcTemplate.update("DELETE FROM sys_user");
        jdbcTemplate.update("DELETE FROM sys_role");
        jdbcTemplate.update("DELETE FROM customer");
        jdbcTemplate.update("DELETE FROM product");

        jdbcTemplate.update("INSERT INTO sys_role (id, role_code, role_name, status) VALUES (1, 'ADMIN', '管理员', 1)");
        jdbcTemplate.update("INSERT INTO sys_role (id, role_code, role_name, status) VALUES (2, 'FINANCE', '财务', 1)");
        jdbcTemplate.update("""
                INSERT INTO sys_permission (id, permission_code, permission_name, permission_type)
                VALUES
                  (1, 'user:manage', '用户管理', 'API'),
                  (2, 'role:manage', '角色管理', 'API'),
                  (3, 'permission:manage', '权限管理', 'API'),
                  (4, 'audit:view', '审计日志查看', 'API'),
                  (5, 'approval:flow:manage', '审批流程维护', 'API'),
                  (6, 'route:list', '线路列表', 'API'),
                  (7, 'route:create', '线路创建', 'API'),
                  (8, 'route:edit', '线路编辑', 'API')
                """);
        jdbcTemplate.update("""
                INSERT INTO sys_user (id, phone, password_hash, role_id, status)
                VALUES (1, '13800000000', ?, 1, 1)
                """, passwordEncoder.encode("adminPass"));
    }

    @Test
    void customerCrudUsesRealApi() throws Exception {
        String token = token("route:list", "route:create", "route:edit");

        mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerCode":"C001","customerName":"客户一","status":1,"remark":"first"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.customerName").value("客户一"));

        mockMvc.perform(get("/api/v1/customers")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));

        mockMvc.perform(put("/api/v1/customers/1")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerCode":"C001","customerName":"客户一更新","status":1,"remark":"updated"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.customerName").value("客户一更新"));

        mockMvc.perform(put("/api/v1/customers/1/status")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(0));
    }

    @Test
    void productCrudUsesRealApi() throws Exception {
        String token = token("route:list", "route:create", "route:edit");

        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productCode":"P001","productName":"产品一","status":1,"remark":"first"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productName").value("产品一"));

        mockMvc.perform(get("/api/v1/products")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));

        mockMvc.perform(put("/api/v1/products/1")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productCode":"P001","productName":"产品一更新","status":1,"remark":"updated"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productName").value("产品一更新"));

        mockMvc.perform(put("/api/v1/products/1/status")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(0));
    }

    @Test
    void approvalFlowListVersionsActiveAndDetailAreQueryable() throws Exception {
        String token = token("approval:flow:manage");
        jdbcTemplate.update("""
                INSERT INTO approval_flow (id, approval_type, flow_name, version_no, status, created_by)
                VALUES (10, 'EXPENSE', '费用审批V1', 1, 'DRAFT', 1),
                       (11, 'EXPENSE', '费用审批V2', 2, 'ACTIVE', 1),
                       (12, 'TIRE', '轮胎审批V1', 1, 'MAINTENANCE', 1)
                """);
        jdbcTemplate.update("""
                INSERT INTO approval_flow_node (id, flow_id, node_order, node_name, position_id, approver_user_id, allow_return)
                VALUES (100, 11, 1, '财务', NULL, 1, 1),
                       (101, 11, 2, '经理', NULL, 1, 1)
                """);

        mockMvc.perform(get("/api/v1/approval-flows?approvalType=EXPENSE")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.records[0].versionNo").value(2));

        mockMvc.perform(get("/api/v1/approval-flows/types/EXPENSE/versions")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("ACTIVE"));

        mockMvc.perform(get("/api/v1/approval-flows/types/EXPENSE/active")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.flow.id").value(11))
                .andExpect(jsonPath("$.data.nodes[1].nodeOrder").value(2));

        mockMvc.perform(get("/api/v1/approval-flows/11")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nodes[0].nodeName").value("财务"));
    }

    @Test
    void userListCreateDisableResetAndSingleRoleAreSupported() throws Exception {
        String token = token("user:manage");

        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"13900000000","password":"newPass","roleId":2,"status":1}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roleId").value(2));

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
                                {"phone":"13900000001","roleId":1,"status":1}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roleId").value(1));
    }

    @Test
    void rolePermissionsCanBeSavedAgainstExistingRegistryOnly() throws Exception {
        String token = token("role:manage");

        mockMvc.perform(post("/api/v1/roles")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roleCode":"OPS","roleName":"运营","status":1}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roleCode").value("OPS"));

        mockMvc.perform(put("/api/v1/roles/3/permissions")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"permissionCodes":["user:manage","audit:view"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.permissions[0].permissionCode").value("audit:view"))
                .andExpect(jsonPath("$.data.permissions[1].permissionCode").value("user:manage"));

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
    void roleListEditAndStatusUseRealApi() throws Exception {
        String token = token("role:manage");

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
    void permissionListCanBeFilteredByType() throws Exception {
        String token = token("permission:manage");

        mockMvc.perform(get("/api/v1/permissions?permissionType=API")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].permissionType").value("API"));
    }

    @Test
    void auditLogQueryReturnsBeforeAfterAndReason() throws Exception {
        String token = token("audit:view");
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

    private String token(String... permissions) {
        return tokenService.issueToken(new AuthenticatedUser(1L, "13800000000", 1L, "ADMIN", "管理员", Set.of(permissions)));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
