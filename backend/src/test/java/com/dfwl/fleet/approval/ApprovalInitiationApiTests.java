package com.dfwl.fleet.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dfwl.fleet.security.AuthenticatedUser;
import com.dfwl.fleet.security.TokenAuthenticationService;
import com.dfwl.fleet.approval.dto.request.ApprovalCreateRequest;
import com.dfwl.fleet.approval.service.ApprovalService;
import com.jayway.jsonpath.JsonPath;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:approvalinitiationdb;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-auth-test.sql"
})
@AutoConfigureMockMvc
class ApprovalInitiationApiTests {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TokenAuthenticationService tokens;
    @Autowired private ApprovalService approvals;

    private String finance;
    private String driver;
    private String outsourced;
    private String approver;
    private String admin;
    private String otherFinance;

    @BeforeEach
    void setUp() {
        for (String table : new String[]{"approval_action", "approval_task", "approval_submission_version",
                "approval_instance", "file_attachment", "approval_flow_initiator_role",
                "approval_flow_expense_config", "approval_flow_node", "approval_flow", "approval_template",
                "expense_toll_detail", "expense_repair_detail", "expense_penalty_detail", "expense_energy_detail",
                "expense_entry", "route_task", "driver_vehicle_current", "driver", "vehicle", "sys_role"}) {
            jdbc.update("DELETE FROM " + table);
        }
        jdbc.update("INSERT INTO sys_role (id, role_code, role_name) VALUES (1, 'DRIVER', '司机'), (2, 'FINANCE', '财务'), (3, 'MANAGER', '经理')");
        jdbc.update("INSERT INTO driver (id, user_id, driver_type, name, phone) VALUES (11, 101, 'INTERNAL', '自有', '13900000101'), (12, 102, 'OUTSOURCED', '外协', '13900000102')");
        jdbc.update("""
                INSERT INTO vehicle (id, plate_no, energy_type, max_load, load_standard_type)
                VALUES (31, '云A00031', 'GAS', 30, 'PERCENT'), (32, '云A00032', 'GAS', 30, 'PERCENT')
                """);
        jdbc.update("INSERT INTO driver_vehicle_current (driver_id, vehicle_id, bound_at, bound_by) VALUES (11, 31, CURRENT_TIMESTAMP, 201), (12, 32, CURRENT_TIMESTAMP, 201)");
        jdbc.update("""
                INSERT INTO route_task (id, route_no, business_unique_key, business_date, customer_id, product_id,
                                        direction, loading_place, unloading_place, tax_unit_price, status,
                                        assigned_driver_id, departure_driver_id, departure_vehicle_id, created_by)
                VALUES (41, 'R41', 'K41', DATE '2026-09-10', 1, 1, 'OUTBOUND', 'A', 'B', 1, 'COMPLETED', 11, 11, 31, 201),
                       (42, 'R42', 'K42', DATE '2026-09-10', 1, 1, 'OUTBOUND', 'A', 'B', 1, 'COMPLETED', 12, 12, 32, 201)
                """);
        finance = token(201, 2, "FINANCE", Set.of("approval:create", "approval:history:view"));
        otherFinance = token(203, 2, "FINANCE", Set.of("approval:create"));
        driver = token(101, 1, "DRIVER", Set.of("approval:create"));
        outsourced = token(102, 1, "DRIVER", Set.of("approval:create"));
        approver = token(202, 3, "MANAGER", Set.of("approval:process", "approval:return"));
        admin = token(204, 3, "MANAGER", Set.of("approval:flow:manage"));
    }

    @Test
    void availableIsRoleScopedAndExcludesTireDraftAndNeedsReview() throws Exception {
        template(10, 100, "EXPENSE_WATER", "EXPENSE", "WATER", "REQUIRED", "ACTIVE", "READY", 1, 2);
        template(20, 200, "EXPENSE_TOLL", "EXPENSE", "TOLL", "NONE", "ACTIVE", "READY", 2);
        template(30, 300, "TIRE_REQUEST", "TIRE", null, "OPTIONAL", "ACTIVE", "READY", 1);
        template(40, 400, "OTHER_LEAVE", "OTHER", null, "OPTIONAL", "DRAFT", "READY", 2);
        template(50, 500, "OTHER_OLD", "OTHER", null, "OPTIONAL", "ACTIVE", "NEEDS_REVIEW", 2);
        mvc.perform(get("/api/v1/approval-templates/available").header("Authorization", bearer(finance)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].templateCode").value("EXPENSE_WATER"))
                .andExpect(jsonPath("$.data[0].expenseType").value("WATER"))
                .andExpect(jsonPath("$.data[0].allowedInputModes[0]").value("SINGLE"))
                .andExpect(jsonPath("$.data[1].templateCode").value("EXPENSE_TOLL"));
        mvc.perform(get("/api/v1/approval-templates/available").header("Authorization", bearer(driver)))
                .andExpect(jsonPath("$.data.length()").value(1));
        mvc.perform(get("/api/v1/approval-templates/available").header("Authorization", bearer(outsourced)))
                .andExpect(jsonPath("$.data.length()").value(0));
        mvc.perform(get("/api/v1/approval-templates/available").header("Authorization", bearer(approver)))
                .andExpect(status().isForbidden());
    }

    @Test
    void templateIdSelectsCorrectExpenseAndSingleExpenseStillPostsThroughHandler() throws Exception {
        template(10, 100, "EXPENSE_WATER", "EXPENSE", "WATER", "OPTIONAL", "ACTIVE", "READY", 2);
        template(20, 200, "EXPENSE_TOLL", "EXPENSE", "TOLL", "OPTIONAL", "ACTIVE", "READY", 2);
        create(finance, expense(10, "WATER", "DAILY", null, 31, null))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.flowId").value(100));
        long id = jdbc.queryForObject("SELECT id FROM approval_instance", Long.class);
        mvc.perform(post("/api/v1/approvals/{id}/approve", id)
                .header("Authorization", bearer(approver)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT expense_type FROM expense_entry WHERE approval_instance_id = ?", String.class, id))
                .isEqualTo("WATER");
        create(finance, expense(20, "TOLL", "DAILY", null, 31, null))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.flowId").value(200));
        create(finance, """
                {"approvalType":"EXPENSE","businessType":"EXPENSE","businessSnapshot":{}}
                """).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("APPROVAL_001"));
    }

    @Test
    void invalidTemplateRoleTypeAndEvidenceLeaveNoApprovalRows() throws Exception {
        template(10, 100, "EXPENSE_WATER", "EXPENSE", "WATER", "REQUIRED", "ACTIVE", "READY", 2);
        create(finance, expense(10, "WATER", "DAILY", null, 31, null))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("APPROVAL_004"));
        create(driver, expense(10, "WATER", "DAILY", null, 31, 11L)).andExpect(status().isForbidden());
        create(finance, expense(10, "TOLL", "DAILY", null, 31, null)).andExpect(status().isConflict());
        create(finance, expense(999, "WATER", "DAILY", null, 31, null)).andExpect(status().isConflict());
        template(30, 300, "TIRE_REQUEST", "TIRE", null, "OPTIONAL", "ACTIVE", "READY", 2);
        create(finance, "{" + "\"templateId\":30,\"businessSnapshot\":{}}")
                .andExpect(status().isConflict());
        assertThat(count("approval_instance")).isZero();
        assertThat(count("approval_submission_version")).isZero();
        assertThat(count("approval_task")).isZero();
    }

    @Test
    void inactiveOrIncompleteTemplateCannotBeSelectedAndClientFieldsCannotOverrideConfiguration() throws Exception {
        template(10, 100, "EXPENSE_WATER", "EXPENSE", "WATER", "OPTIONAL", "DRAFT", "READY", 2);
        create(finance, expense(10, "WATER", "DAILY", null, 31, null))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("APPROVAL_001"));
        jdbc.update("UPDATE approval_flow SET status = 'ACTIVE', configuration_status = 'NEEDS_REVIEW' WHERE id = 100");
        create(finance, expense(10, "WATER", "DAILY", null, 31, null))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("APPROVAL_001"));
        jdbc.update("UPDATE approval_flow SET configuration_status = 'READY' WHERE id = 100");
        create(finance, expense(10, "WATER", "DAILY", null, 31, null)
                .replace("\"templateId\":10,", "\"templateId\":10,\"approvalType\":\"GENERAL\","))
                .andExpect(status().isConflict());
        create(finance, expense(10, "WATER", "DAILY", null, 31, null)
                .replace("\"templateId\":10,", "\"templateId\":10,\"attributionType\":\"ROUTE\","))
                .andExpect(status().isConflict());
        assertThat(count("approval_instance")).isZero();
    }

    @Test
    void requiredEvidenceBindsOnlyRealTemporaryApprovalUpload() throws Exception {
        template(10, 100, "EXPENSE_WATER", "EXPENSE", "WATER", "REQUIRED", "ACTIVE", "READY", 2);
        attachment(77, 201);
        create(finance, expense(10, "WATER", "DAILY", null, 31, null).replace("\"templateId\":10,",
                "\"templateId\":10,\"attachmentIds\":[77],"))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT owner_type FROM file_attachment WHERE id = 77", String.class))
                .isEqualTo("APPROVAL");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM approval_submission_version WHERE business_snapshot_json LIKE '%\"attachmentIds\"%'", Integer.class))
                .isEqualTo(1);
        attachment(78, 203);
        create(finance, expense(10, "WATER", "DAILY", null, 31, null).replace("\"templateId\":10,",
                "\"templateId\":10,\"attachmentIds\":[78],"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ATTACHMENT_004"));
        assertThat(jdbc.queryForObject("SELECT owner_type FROM file_attachment WHERE id = 78", String.class))
                .isEqualTo("APPROVAL_UPLOAD");
    }

    @Test
    void snapshotCannotForgeEvidenceAndNoneStillAcceptsValidExtraAttachment() throws Exception {
        template(40, 400, "OTHER_LEAVE", "OTHER", null, "REQUIRED", "ACTIVE", "READY", 2);
        create(finance, "{" + "\"templateId\":40,\"businessSnapshot\":{\"attachmentIds\":[999]}}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("APPROVAL_004"));
        jdbc.update("UPDATE approval_flow SET evidence_requirement = 'NONE' WHERE id = 400");
        attachment(77, 201);
        create(finance, "{" + "\"templateId\":40,\"attachmentIds\":[77],\"businessSnapshot\":{\"attachmentIds\":[999]}}")
                .andExpect(status().isOk());
        String snapshot = jdbc.queryForObject("SELECT business_snapshot_json FROM approval_submission_version", String.class);
        assertThat(snapshot).contains("77").doesNotContain("999");
    }

    @Test
    void attributionAndDriverResourceRulesAreEnforced() throws Exception {
        template(10, 100, "EXPENSE_WATER", "EXPENSE", "WATER", "OPTIONAL", "ACTIVE", "READY", 1, 2);
        create(driver, expense(10, "WATER", "ROUTE", 41L, 31, 11L)).andExpect(status().isOk());
        create(driver, expense(10, "WATER", "ROUTE", 42L, 31, 11L)).andExpect(status().isForbidden());
        create(driver, expense(10, "WATER", "ROUTE", 41L, 32, 11L)).andExpect(status().isForbidden());
        create(driver, expense(10, "WATER", "DAILY", null, 31, 12L)).andExpect(status().isForbidden());
        create(outsourced, expense(10, "WATER", "ROUTE", 42L, 32, 12L)).andExpect(status().isForbidden());
        create(finance, expense(10, "WATER", "ROUTE", null, 31, null)).andExpect(status().isConflict());
        create(finance, expense(10, "WATER", "DAILY", 41L, 31, null)).andExpect(status().isConflict());
        create(finance, expense(10, "WATER", "PENDING_ATTRIBUTION", null, 31, null)).andExpect(status().isConflict());
        jdbc.update("UPDATE approval_flow_expense_config SET allow_daily = 0 WHERE flow_id = 100");
        create(finance, expense(10, "WATER", "DAILY", null, 31, null)).andExpect(status().isConflict());
    }

    @Test
    void otherTemplateCanStartButOutsourcedCannot() throws Exception {
        template(40, 400, "OTHER_LEAVE", "OTHER", null, "NONE", "ACTIVE", "READY", 1, 2);
        create(finance, "{" + "\"templateId\":40,\"businessSnapshot\":{\"reason\":\"leave\"}}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.approvalType").value("GENERAL"));
        create(outsourced, "{" + "\"templateId\":40,\"businessSnapshot\":{}}")
                .andExpect(status().isForbidden());
    }

    @Test
    void originalApplicantResubmitsOnV1AfterV2Publication() throws Exception {
        template(40, 400, "OTHER_LEAVE", "OTHER", null, "OPTIONAL", "ACTIVE", "READY", 2);
        create(finance, "{" + "\"templateId\":40,\"businessSnapshot\":{\"reason\":\"first\"}}")
                .andExpect(status().isOk());
        long id = jdbc.queryForObject("SELECT id FROM approval_instance", Long.class);
        mvc.perform(post("/api/v1/approvals/{id}/return-applicant", id)
                .header("Authorization", bearer(approver)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/approvals/{id}/resubmit", id)
                .header("Authorization", bearer(otherFinance)).contentType(MediaType.APPLICATION_JSON)
                .content("{" + "\"templateId\":40,\"businessSnapshot\":{}}"))
                .andExpect(status().isForbidden());
        assertThat(count("approval_submission_version")).isEqualTo(1);
        mvc.perform(post("/api/v1/approval-flows/400/maintenance").header("Authorization", bearer(admin)))
                .andExpect(status().isOk());
        String update = mvc.perform(put("/api/v1/approval-flows/400")
                .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"flowName":"V2","nodes":[{"nodeOrder":1,"nodeName":"新审批人","approverUserId":202}],
                         "evidenceRequirement":"REQUIRED","initiatorRoleIds":[1]}
                        """))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long v2 = ((Number) JsonPath.read(update, "$.data.id")).longValue();
        mvc.perform(post("/api/v1/approval-flows/{id}/publish", v2)
                .header("Authorization", bearer(admin))).andExpect(status().isOk());
        mvc.perform(post("/api/v1/approvals/{id}/resubmit", id)
                .header("Authorization", bearer(finance)).contentType(MediaType.APPLICATION_JSON)
                .content("{" + "\"templateId\":40,\"businessSnapshot\":{\"reason\":\"second\"}}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.flowId").value(400));
        assertThat(count("approval_submission_version")).isEqualTo(2);
        mvc.perform(get("/api/v1/approvals/{id}", id).header("Authorization", bearer(finance)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.templateId").value(40))
                .andExpect(jsonPath("$.data.flowConfiguration.flow.id").value(400))
                .andExpect(jsonPath("$.data.flowConfiguration.initiatorRoleIds[0]").value(2))
                .andExpect(jsonPath("$.data.flowConfiguration.evidenceRequirement").value("OPTIONAL"));
    }

    @Test
    void resubmitValidatesOriginalRequiredEvidenceEvenIfCurrentVersionIsOptional() throws Exception {
        template(40, 400, "OTHER_LEAVE", "OTHER", null, "REQUIRED", "ACTIVE", "READY", 2);
        attachment(77, 201);
        create(finance, "{" + "\"templateId\":40,\"attachmentIds\":[77],\"businessSnapshot\":{}}")
                .andExpect(status().isOk());
        long id = jdbc.queryForObject("SELECT id FROM approval_instance", Long.class);
        mvc.perform(post("/api/v1/approvals/{id}/return-applicant", id)
                .header("Authorization", bearer(approver)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        jdbc.update("UPDATE approval_flow SET status = 'MAINTENANCE' WHERE id = 400");
        jdbc.update("INSERT INTO approval_flow (id, template_id, approval_type, flow_name, version_no, status, evidence_requirement, configuration_status, created_by) VALUES (401, 40, 'GENERAL', 'V2', 2, 'ACTIVE', 'OPTIONAL', 'READY', 201)");
        jdbc.update("INSERT INTO approval_flow_node (flow_id, node_order, node_name, approver_user_id) VALUES (401, 1, '新审批人', 202)");
        jdbc.update("INSERT INTO approval_flow_initiator_role (flow_id, role_id) VALUES (401, 2)");
        mvc.perform(post("/api/v1/approvals/{id}/resubmit", id)
                .header("Authorization", bearer(finance)).contentType(MediaType.APPLICATION_JSON)
                .content("{" + "\"templateId\":40,\"businessSnapshot\":{}}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("APPROVAL_004"));
        assertThat(count("approval_submission_version")).isEqualTo(1);
        attachment(78, 201);
        mvc.perform(post("/api/v1/approvals/{id}/resubmit", id)
                .header("Authorization", bearer(finance)).contentType(MediaType.APPLICATION_JSON)
                .content("{" + "\"templateId\":40,\"attachmentIds\":[78],\"businessSnapshot\":{}}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.flowId").value(400));
    }

    @Test
    void publishingAndCreatingConcurrentlyNeverMixFlowVersions() throws Exception {
        template(40, 400, "OTHER_LEAVE", "OTHER", null, "OPTIONAL", "DRAFT", "READY", 2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var create = executor.submit(() -> {
                start.await();
                try {
                    return approvals.create(new ApprovalCreateRequest(null, null, null,
                                    Map.of("reason", "leave"), null, 40L, null, null),
                            new AuthenticatedUser(201L, "13900000201", 2L, "FINANCE", "财务",
                                    Set.of("approval:create"))).flowId();
                } catch (RuntimeException ex) {
                    return -1L;
                }
            });
            var publish = executor.submit(() -> { start.await(); return approvals.publishFlow(400).id(); });
            start.countDown();
            long createdFlow = create.get(10, TimeUnit.SECONDS);
            assertThat(publish.get(10, TimeUnit.SECONDS)).isEqualTo(400L);
            assertThat(createdFlow).isIn(-1L, 400L);
            assertThat(count("approval_instance")).isBetween(0, 1);
            if (createdFlow != -1L) {
                assertThat(jdbc.queryForObject("SELECT flow_id FROM approval_instance", Long.class)).isEqualTo(400L);
            }
            assertThat(count("approval_submission_version")).isEqualTo(count("approval_instance"));
        } finally {
            executor.shutdownNow();
        }
    }

    private void template(long id, long flowId, String code, String category, String expenseType,
                          String evidence, String status, String configStatus, long... roles) {
        String type = "TIRE".equals(category) ? "TIRE_REQUEST" : "EXPENSE".equals(category) ? "EXPENSE" : "GENERAL";
        jdbc.update("INSERT INTO approval_template (id, template_code, category, approval_type, created_by) VALUES (?, ?, ?, ?, 201)", id, code, category, type);
        jdbc.update("INSERT INTO approval_flow (id, template_id, approval_type, flow_name, version_no, status, evidence_requirement, configuration_status, created_by) VALUES (?, ?, ?, ?, 1, ?, ?, ?, 201)",
                flowId, id, type, code, status, evidence, configStatus);
        jdbc.update("INSERT INTO approval_flow_node (flow_id, node_order, node_name, approver_user_id) VALUES (?, 1, '审批人', 202)", flowId);
        for (long role : roles) jdbc.update("INSERT INTO approval_flow_initiator_role (flow_id, role_id) VALUES (?, ?)", flowId, role);
        if (expenseType != null) jdbc.update("INSERT INTO approval_flow_expense_config (flow_id, expense_type, allow_single, allow_excel, allow_route, allow_daily) VALUES (?, ?, 1, 1, 1, 1)", flowId, expenseType);
    }

    private void attachment(long id, long uploadedBy) {
        jdbc.update("INSERT INTO file_attachment (id, owner_type, owner_id, purpose, storage_key, original_filename, file_size, uploaded_by) VALUES (?, 'APPROVAL_UPLOAD', ?, 'APPROVAL_APPLICATION', ?, 'evidence.pdf', 10, ?)",
                id, uploadedBy, "evidence-" + id, uploadedBy);
    }

    private String expense(long templateId, String type, String attribution, Long routeId, long vehicleId, Long driverId) {
        return """
                {"templateId":%d,"businessSnapshot":{"expenseType":"%s","businessDate":"2026-09-10",
                 "vehicleId":%d,"driverId":%s,"attributionType":"%s","routeId":%s,"amount":"10.00"}}
                """.formatted(templateId, type, vehicleId, driverId == null ? "null" : driverId,
                attribution, routeId == null ? "null" : routeId);
    }

    private org.springframework.test.web.servlet.ResultActions create(String token, String body) throws Exception {
        return mvc.perform(post("/api/v1/approvals").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private String token(long id, long roleId, String roleCode, Set<String> permissions) {
        return tokens.issueToken(new AuthenticatedUser(id, "13900000" + id, roleId, roleCode, roleCode, permissions));
    }

    private String bearer(String token) { return "Bearer " + token; }
}
