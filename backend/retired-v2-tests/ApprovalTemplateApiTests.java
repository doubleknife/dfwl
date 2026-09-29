package com.dfwl.fleet.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dfwl.fleet.approval.repository.ApprovalRepository;
import com.dfwl.fleet.approval.service.ApprovalService;
import com.dfwl.fleet.security.AuthenticatedUser;
import com.dfwl.fleet.security.TokenAuthenticationService;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
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
        "spring.datasource.url=jdbc:h2:mem:approvaltemplatedb;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-auth-test.sql"
})
@AutoConfigureMockMvc
class ApprovalTemplateApiTests {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TokenAuthenticationService tokens;
    @Autowired private ApprovalService approvals;
    @Autowired private ApprovalRepository flows;

    private String token;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM approval_action");
        jdbc.update("DELETE FROM approval_task");
        jdbc.update("DELETE FROM approval_submission_version");
        jdbc.update("DELETE FROM approval_instance");
        jdbc.update("DELETE FROM approval_flow_initiator_role");
        jdbc.update("DELETE FROM approval_flow_expense_config");
        jdbc.update("DELETE FROM approval_flow_node");
        jdbc.update("DELETE FROM approval_flow");
        jdbc.update("DELETE FROM approval_template");
        jdbc.update("DELETE FROM sys_user");
        jdbc.update("DELETE FROM sys_role");
        jdbc.update("INSERT INTO sys_role (id, role_code, role_name) VALUES (1, 'DRIVER', '司机'), (2, 'FINANCE', '财务'), (3, 'MANAGER', '经理')");
        token = tokens.issueToken(new AuthenticatedUser(90L, "13900000090", 3L, "ADMIN", "管理员",
                Set.of("approval:flow:manage", "approval:create", "approval:history:view")));
    }

    @Test
    void twoExpenseTemplatesEachHaveV1AndCanBothBeActive() throws Exception {
        long water = create("EXPENSE_WATER", "EXPENSE", "WATER", "SINGLE", "ROUTE", "REQUIRED");
        long toll = create("EXPENSE_TOLL", "EXPENSE", "TOLL", "EXCEL", "DAILY", "NONE");
        long waterFlow = versionId(water);
        long tollFlow = versionId(toll);
        assertThat(waterFlow).isNotEqualTo(tollFlow);
        publish(waterFlow).andExpect(status().isOk());
        publish(tollFlow).andExpect(status().isOk());
        assertThat(count("SELECT COUNT(*) FROM approval_flow WHERE status = 'ACTIVE' AND approval_type = 'EXPENSE'"))
                .isEqualTo(2);
        mvc.perform(get("/api/v1/approval-templates/{id}/active", water).header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.flow.versionNo").value(1))
                .andExpect(jsonPath("$.data.expenseConfig.expenseType").value("WATER"))
                .andExpect(jsonPath("$.data.expenseConfig.inputModes[0]").value("SINGLE"))
                .andExpect(jsonPath("$.data.evidenceRequirement").value("REQUIRED"));
        mvc.perform(get("/api/v1/approval-templates/{id}/active", toll).header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.expenseConfig.expenseType").value("TOLL"));
    }

    @Test
    void versionedRolesAndConfigurationPreserveOldInstanceAndNodes() throws Exception {
        long templateId = create("OTHER_LEAVE", "OTHER", null, null, null, "OPTIONAL");
        long v1 = versionId(templateId);
        publish(v1).andExpect(status().isOk());
        jdbc.update("""
                INSERT INTO approval_instance (approval_no, approval_type, flow_id, flow_version, business_type,
                                               applicant_user_id, status)
                VALUES ('OLD-1', 'GENERAL', ?, 1, 'GENERAL', 90, 'APPROVED')
                """, v1);
        mvc.perform(post("/api/v1/approval-flows/{id}/maintenance", v1)
                        .header("Authorization", bearer())).andExpect(status().isOk());
        mvc.perform(put("/api/v1/approval-flows/{id}", v1)
                        .header("Authorization", bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"flowName":"请假V2","evidenceRequirement":"REQUIRED",
                                 "initiatorRoleIds":[2,3],
                                 "nodes":[{"nodeOrder":1,"nodeName":"经理复核","approverUserId":91}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.versionNo").value(2));
        long v2 = versionId(templateId);
        publish(v2).andExpect(status().isOk());
        mvc.perform(get("/api/v1/approval-templates/{id}/versions/{flow}", templateId, v1)
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.initiatorRoleIds[0]").value(1))
                .andExpect(jsonPath("$.data.initiatorRoleIds[1]").value(2))
                .andExpect(jsonPath("$.data.evidenceRequirement").value("OPTIONAL"))
                .andExpect(jsonPath("$.data.nodes[0].nodeName").value("审批"));
        mvc.perform(get("/api/v1/approval-templates/{id}/versions/{flow}", templateId, v2)
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.initiatorRoleIds[0]").value(2))
                .andExpect(jsonPath("$.data.initiatorRoleIds[1]").value(3))
                .andExpect(jsonPath("$.data.evidenceRequirement").value("REQUIRED"))
                .andExpect(jsonPath("$.data.nodes[0].nodeName").value("经理复核"));
        assertThat(jdbc.queryForObject("SELECT flow_id FROM approval_instance WHERE approval_no = 'OLD-1'", Long.class))
                .isEqualTo(v1);
    }

    @Test
    void expenseSettingsCanBeCopiedAndThenChangedWithoutChangingV1() throws Exception {
        long templateId = create("EXPENSE_REPAIR", "EXPENSE", "REPAIR", "SINGLE,EXCEL", "ROUTE,DAILY", "OPTIONAL");
        long v1 = versionId(templateId);
        publish(v1);
        mvc.perform(post("/api/v1/approval-flows/{id}/maintenance", v1)
                        .header("Authorization", bearer())).andExpect(status().isOk());
        mvc.perform(put("/api/v1/approval-flows/{id}", v1)
                        .header("Authorization", bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"flowName":"维修V2","nodes":[{"nodeOrder":1,"nodeName":"财务","approverUserId":91}]}
                                """))
                .andExpect(status().isOk());
        long v2 = versionId(templateId);
        mvc.perform(get("/api/v1/approval-templates/{id}/versions/{flow}", templateId, v2)
                        .header("Authorization", bearer()))
                .andExpect(jsonPath("$.data.expenseConfig.inputModes.length()").value(2))
                .andExpect(jsonPath("$.data.expenseConfig.attributionTypes.length()").value(2));
        assertThat(count("SELECT COUNT(*) FROM approval_flow_expense_config WHERE flow_id IN (" + v1 + "," + v2 + ")"))
                .isEqualTo(2);
    }

    @Test
    void newExpenseVersionCanChangeModesAttributionAndRolesWithoutOverwritingV1() throws Exception {
        long templateId = create("EXPENSE_PENALTY", "EXPENSE", "PENALTY", "SINGLE", "ROUTE", "OPTIONAL");
        long v1 = versionId(templateId);
        publish(v1);
        mvc.perform(post("/api/v1/approval-flows/{id}/maintenance", v1)
                        .header("Authorization", bearer())).andExpect(status().isOk());
        mvc.perform(put("/api/v1/approval-flows/{id}", v1)
                        .header("Authorization", bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"flowName":"罚款V2","evidenceRequirement":"REQUIRED","initiatorRoleIds":[2,3],
                                 "expenseConfig":{"expenseType":"PENALTY","inputModes":["EXCEL"],
                                                  "attributionTypes":["DAILY"]},
                                 "nodes":[{"nodeOrder":1,"nodeName":"经理","approverUserId":91}]}
                                """))
                .andExpect(status().isOk());
        long v2 = versionId(templateId);
        mvc.perform(get("/api/v1/approval-templates/{id}/versions/{flow}", templateId, v1)
                        .header("Authorization", bearer()))
                .andExpect(jsonPath("$.data.expenseConfig.inputModes[0]").value("SINGLE"))
                .andExpect(jsonPath("$.data.expenseConfig.attributionTypes[0]").value("ROUTE"));
        mvc.perform(get("/api/v1/approval-templates/{id}/versions/{flow}", templateId, v2)
                        .header("Authorization", bearer()))
                .andExpect(jsonPath("$.data.expenseConfig.inputModes[0]").value("EXCEL"))
                .andExpect(jsonPath("$.data.expenseConfig.attributionTypes[0]").value("DAILY"))
                .andExpect(jsonPath("$.data.initiatorRoleIds[0]").value(2))
                .andExpect(jsonPath("$.data.initiatorRoleIds[1]").value(3))
                .andExpect(jsonPath("$.data.evidenceRequirement").value("REQUIRED"));
    }

    @Test
    void duplicateCodeUnknownRoleAndInvalidSettingsAreRejected() throws Exception {
        create("EXPENSE_WATER", "EXPENSE", "WATER", "SINGLE", "ROUTE", "OPTIONAL");
        createRequest("EXPENSE_WATER", "EXPENSE", "WATER", "SINGLE", "ROUTE", "OPTIONAL", "[1,2]")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DATA_002"));
        createRequest("EXPENSE_BAD_ROLE", "EXPENSE", "WATER", "SINGLE", "ROUTE", "OPTIONAL", "[999]")
                .andExpect(status().isConflict());
        createRequest("EXPENSE_BAD_MODE", "EXPENSE", "WATER", "", "ROUTE", "OPTIONAL", "[1]")
                .andExpect(status().isConflict());
        createRequest("EXPENSE_BAD_TYPE", "EXPENSE", "NOT_A_TYPE", "SINGLE", "ROUTE", "OPTIONAL", "[1]")
                .andExpect(status().isConflict());
        createRequest("OTHER_BAD_CONFIG", "OTHER", "WATER", "SINGLE", "ROUTE", "OPTIONAL", "[1]")
                .andExpect(status().isConflict());
        createRequest("TIRE_NEW", "TIRE", null, null, null, "OPTIONAL", "[1]")
                .andExpect(status().isConflict());
        createRequest("INVALID_CATEGORY", "UNKNOWN", null, null, null, "OPTIONAL", "[1]")
                .andExpect(status().isConflict());
    }

    @Test
    void tireTemplateUsesDedicatedLegacyCodeWithoutExpenseOptions() throws Exception {
        long templateId = create("TIRE_REQUEST", "TIRE", null, null, null, "OPTIONAL");
        long flowId = versionId(templateId);
        publish(flowId).andExpect(status().isOk());
        assertThat(flows.activeFlow("TIRE_REQUEST")).get()
                .extracting(ApprovalRepository.FlowRecord::id).isEqualTo(flowId);
        mvc.perform(get("/api/v1/approval-templates/{id}/versions/{flow}", templateId, flowId)
                        .header("Authorization", bearer()))
                .andExpect(jsonPath("$.data.expenseConfig").doesNotExist());
    }

    @Test
    void concurrentPublicationCannotProduceTwoActiveVersions() throws Exception {
        long templateId = create("OTHER_CONCURRENT", "OTHER", null, null, null, "OPTIONAL");
        long first = versionId(templateId);
        long second = flows.createFlowVersion(templateId, "GENERAL", "并发V2", 2, "OPTIONAL", "READY", 90);
        jdbc.update("INSERT INTO approval_flow_node (flow_id, node_order, node_name, approver_user_id) VALUES (?, 1, '审批', 91)", second);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var left = executor.submit(() -> { start.await(); return publishSucceeded(first); });
            var right = executor.submit(() -> { start.await(); return publishSucceeded(second); });
            start.countDown();
            assertThat(List.of(left.get(10, TimeUnit.SECONDS), right.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
            assertThat(count("SELECT COUNT(*) FROM approval_flow WHERE template_id = " + templateId + " AND status = 'ACTIVE'"))
                    .isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private boolean publishSucceeded(long flowId) {
        try {
            approvals.publishFlow(flowId);
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private long create(String code, String category, String type, String modes, String attribution, String evidence)
            throws Exception {
        String json = createRequest(code, category, type, modes, attribution, evidence, "[1,2]")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.flow.versionNo").value(1))
                .andExpect(jsonPath("$.data.flow.status").value("DRAFT"))
                .andReturn().getResponse().getContentAsString();
        long flowId = ((Number) JsonPath.read(json, "$.data.flow.id")).longValue();
        return jdbc.queryForObject("SELECT template_id FROM approval_flow WHERE id = ?", Long.class, flowId);
    }

    private org.springframework.test.web.servlet.ResultActions createRequest(String code, String category,
            String type, String modes, String attribution, String evidence, String roles) throws Exception {
        String config = type == null ? "null" : """
                {"expenseType":"%s","inputModes":%s,"attributionTypes":%s}
                """.formatted(type, array(modes), array(attribution));
        return mvc.perform(post("/api/v1/approval-templates")
                .header("Authorization", bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"templateCode":"%s","category":"%s","flowName":"审批",
                         "evidenceRequirement":"%s","initiatorRoleIds":%s,
                         "nodes":[{"nodeOrder":1,"nodeName":"审批","approverUserId":91}],
                         "expenseConfig":%s}
                        """.formatted(code, category, evidence, roles, config)));
    }

    private String array(String csv) {
        if (csv == null || csv.isEmpty()) return "[]";
        return "[\"" + csv.replace(",", "\",\"") + "\"]";
    }

    private long versionId(long templateId) {
        return jdbc.queryForObject("SELECT MAX(id) FROM approval_flow WHERE template_id = ?", Long.class, templateId);
    }

    private org.springframework.test.web.servlet.ResultActions publish(long flowId) throws Exception {
        return mvc.perform(post("/api/v1/approval-flows/{id}/publish", flowId)
                .header("Authorization", bearer()));
    }

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    private String bearer() {
        return "Bearer " + token;
    }
}
