package com.dfwl.fleet.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dfwl.fleet.security.AuthenticatedUser;
import com.dfwl.fleet.security.TokenAuthenticationService;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:approvaldb;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-auth-test.sql"
})
@AutoConfigureMockMvc
class ApprovalApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TokenAuthenticationService tokenAuthenticationService;

    private String applicantToken;
    private String approverAToken;
    private String approverBToken;
    private String approverCToken;
    private String approverDToken;
    private String newApproverToken;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM driver_vehicle_current");
        jdbcTemplate.update("DELETE FROM driver");
        jdbcTemplate.update("INSERT INTO driver (id, user_id, name, phone, driver_type, status) VALUES (1, 101, 'Driver', '13800000101', 'INTERNAL', 1)");
        jdbcTemplate.update("DELETE FROM approval_action");
        jdbcTemplate.update("DELETE FROM approval_task");
        jdbcTemplate.update("DELETE FROM approval_submission_version");
        jdbcTemplate.update("DELETE FROM approval_instance");
        jdbcTemplate.update("DELETE FROM approval_flow_node");
        jdbcTemplate.update("DELETE FROM approval_flow");
        jdbcTemplate.update("INSERT INTO approval_flow (id, approval_type, flow_name, version_no, status, created_by) VALUES (1, 'GENERAL', '通用审批', 1, 'ACTIVE', 1)");
        jdbcTemplate.update("INSERT INTO approval_flow_node (flow_id, node_order, node_name, approver_user_id) VALUES (1, 1, 'A', 2)");
        jdbcTemplate.update("INSERT INTO approval_flow_node (flow_id, node_order, node_name, approver_user_id) VALUES (1, 2, 'B', 3)");
        jdbcTemplate.update("INSERT INTO approval_flow_node (flow_id, node_order, node_name, approver_user_id) VALUES (1, 3, 'C', 4)");
        jdbcTemplate.update("INSERT INTO approval_flow_node (flow_id, node_order, node_name, approver_user_id) VALUES (1, 4, 'D', 5)");

        Set<String> applicantPermissions = Set.of("approval:create", "approval:flow:manage", "approval:history:view");
        applicantToken = tokenAuthenticationService.issueToken(new AuthenticatedUser(1L, "1380", 1L, "admin", "管理员", applicantPermissions));
        approverAToken = token(2L);
        approverBToken = token(3L);
        approverCToken = token(4L);
        approverDToken = token(5L);
        newApproverToken = token(6L);
    }

    @Test
    void normalFlowPassesThroughAtoBtoCtoD() throws Exception {
        long id = createApproval(100);

        approve(id, approverAToken).andExpect(jsonPath("$.data.currentNodeOrder").value(2));
        approve(id, approverBToken).andExpect(jsonPath("$.data.currentNodeOrder").value(3));
        approve(id, approverCToken).andExpect(jsonPath("$.data.currentNodeOrder").value(4));
        approve(id, approverDToken)
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.flowId").value(1))
                .andExpect(jsonPath("$.data.flowVersion").value(1));

        assertThat(count("SELECT COUNT(*) FROM approval_action WHERE action_type = 'APPROVE'")).isEqualTo(4);
    }

    @Test
    void returnNodeFromDToBRequiresReRunningBtoCtoD() throws Exception {
        long id = moveToD(createApproval(100));

        returnNode(id, approverDToken, 2).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentNodeOrder").value(2));

        approve(id, approverBToken).andExpect(jsonPath("$.data.currentNodeOrder").value(3));
        approve(id, approverCToken).andExpect(jsonPath("$.data.currentNodeOrder").value(4));
        approve(id, approverDToken).andExpect(jsonPath("$.data.status").value("APPROVED"));

        assertThat(count("SELECT COUNT(*) FROM approval_action WHERE action_type = 'APPROVE'")).isEqualTo(6);
        assertThat(count("SELECT COUNT(*) FROM approval_action WHERE action_type = 'RETURN_NODE'")).isEqualTo(1);
    }

    @Test
    void returnedOldCAndDTasksCannotBeApprovedAgain() throws Exception {
        long id = moveToD(createApproval(100));
        returnNode(id, approverDToken, 2).andExpect(status().isOk());

        approveExpectConflict(id, approverCToken, "APPROVAL_003");
        approveExpectConflict(id, approverDToken, "APPROVAL_003");
    }

    @Test
    void returnedApplicantResubmitCreatesNewSubmissionVersion() throws Exception {
        long id = createApproval(100);
        approve(id, approverAToken);

        returnApplicant(id, approverBToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RETURNED_TO_APPLICANT"));
        resubmit(id, 200).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentNodeOrder").value(1));

        assertThat(count("SELECT COUNT(*) FROM approval_submission_version WHERE approval_instance_id = " + id)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT s.version_no
                FROM approval_task t
                JOIN approval_submission_version s ON s.id = t.submission_version_id
                WHERE t.approval_instance_id = ? AND t.status = 'PENDING'
                """, Integer.class, id)).isEqualTo(2);
    }

    @Test
    void oldSubmissionTasksCannotTriggerAfterResubmit() throws Exception {
        long id = createApproval(100);
        approve(id, approverAToken);
        returnApplicant(id, approverBToken);
        resubmit(id, 200);

        approveExpectConflict(id, approverBToken, "APPROVAL_003");
        approve(id, approverAToken).andExpect(jsonPath("$.data.currentNodeOrder").value(2));
        approve(id, approverBToken).andExpect(jsonPath("$.data.currentNodeOrder").value(3));
    }

    @Test
    void returningToFutureNodeIsRejected() throws Exception {
        long id = createApproval(100);
        approve(id, approverAToken);

        returnNode(id, approverBToken, 3).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("APPROVAL_002"));
    }

    @Test
    void maintenanceBlocksNewApplicationsImmediately() throws Exception {
        enterMaintenance(1);

        mockMvc.perform(post("/api/v1/approvals")
                        .header("Authorization", "Bearer " + applicantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(approvalJson(100)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("APPROVAL_001"));
    }

    @Test
    void openApprovalsBlockFlowModification() throws Exception {
        createApproval(100);
        enterMaintenance(1);

        updateFlow(1, 6).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("APPROVAL_002"));
    }

    @Test
    void completedApprovalsAllowFlowModification() throws Exception {
        approveAll(createApproval(100));
        enterMaintenance(1);

        updateFlow(1, 6).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.versionNo").value(2))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    @Test
    void publishingModifiedFlowCreatesNewActiveVersion() throws Exception {
        long newFlowId = createNewDraftAfterCompletion(6);

        publishFlow(newFlowId).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.versionNo").value(2))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void newInstanceUsesNewApproverAfterFlowChange() throws Exception {
        publishFlow(createNewDraftAfterCompletion(6));
        long id = createApproval(300);

        approve(id, newApproverToken).andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.flowVersion").value(2));
    }

    @Test
    void oldInstanceKeepsOldFlowVersionAndApproverAfterMaintenance() throws Exception {
        long id = createApproval(100);
        enterMaintenance(1);

        approve(id, approverAToken).andExpect(jsonPath("$.data.flowId").value(1))
                .andExpect(jsonPath("$.data.flowVersion").value(1))
                .andExpect(jsonPath("$.data.currentNodeOrder").value(2));
    }

    @Test
    void oldApproverCannotApproveNewInstanceAfterApproverChange() throws Exception {
        publishFlow(createNewDraftAfterCompletion(6));
        long id = createApproval(300);

        approveExpectConflict(id, approverAToken, "APPROVAL_003");
    }

    @Test
    void detailReturnsSubmissionsTasksActionsAndInvalidationFlags() throws Exception {
        long id = moveToD(createApproval(100));
        returnNode(id, approverDToken, 2);

        mockMvc.perform(get("/api/v1/approvals/%d".formatted(id))
                        .header("Authorization", "Bearer " + applicantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.submissions[0].versionNo").value(1))
                .andExpect(jsonPath("$.data.tasks[0].nodeName").value("A"))
                .andExpect(jsonPath("$.data.tasks[3].actions[0].actionType").value("RETURN_NODE"))
                .andExpect(jsonPath("$.data.tasks[3].actions[0].targetNodeOrder").value(2))
                .andExpect(jsonPath("$.data.tasks[4].nodeName").value("B"))
                .andExpect(jsonPath("$.data.tasks[4].invalidated").value(false));
    }

    private String token(long userId) {
        return tokenAuthenticationService.issueToken(new AuthenticatedUser(
                userId, "13" + userId, 1L, "approver", "审批人" + userId,
                Set.of("approval:process", "approval:return")));
    }


    @ParameterizedTest
    @CsvSource({
            "DRIVER,INTERNAL,EXPENSE,GENERAL,true", "DRIVER,OUTSOURCED,EXPENSE,GENERAL,true",
            "DRIVER,INTERNAL,GENERAL,expense,true", "DRIVER,OUTSOURCED,GENERAL,expense,true",
            "DRIVER,INTERNAL,expense,EXPENSE,true", "DRIVER,OUTSOURCED,EXPENSE,EXPENSE,true",
            "DRIVER,INTERNAL,GENERAL,GENERAL,false", "DRIVER,OUTSOURCED,GENERAL,GENERAL,true",
            "DRIVER,EXTERNAL,GENERAL,GENERAL,true", "DRIVER,OUTSOURCE,GENERAL,GENERAL,true",
            "DRIVER,外协,GENERAL,GENERAL,true", "FINANCE,OUTSOURCED,EXPENSE,EXPENSE,false"
    })
    void submissionEntryRulesAndErrorPriorityMatchForCreateAndResubmit(String role, String driverType,
            String approvalType, String businessType, boolean denied) throws Exception {
        String auth = entryToken(role, driverType);
        String body = "{\"approvalType\":\"%s\",\"businessType\":\"%s\",\"businessId\":1,\"businessSnapshot\":{\"amount\":100}}".formatted(approvalType, businessType);
        for (String url : new String[] {"/api/v1/approvals", "/api/v1/approvals/999/resubmit"}) {
            var result = mockMvc.perform(post(url).header("Authorization", "Bearer " + auth)
                    .header("X-Request-Id", "r43b-entry").contentType(MediaType.APPLICATION_JSON).content(body));
            if (denied) {
                result.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("AUTH_003"))
                        .andExpect(jsonPath("$.message").value("无权限"))
                        .andExpect(jsonPath("$.requestId").value("r43b-entry"));
            } else if (url.endsWith("resubmit") || approvalType.equals("EXPENSE")) {
                result.andExpect(status().isConflict());
            } else {
                result.andExpect(status().isOk());
            }
        }
        if (denied) {
            assertThat(count("SELECT COUNT(*) FROM approval_instance")).isZero();
            assertThat(count("SELECT COUNT(*) FROM approval_action")).isZero();
        }
    }

    @ParameterizedTest
    @CsvSource({"INTERNAL", "OUTSOURCED"})
    void allDriversRemainDeniedAtProcessAndExistingFlowManagementGuards(String driverType) throws Exception {
        String auth = entryToken("DRIVER", driverType);
        for (String operation : new String[] {"approve", "return-applicant", "return-node"}) {
            mockMvc.perform(post("/api/v1/approvals/999/" + operation)
                            .header("Authorization", "Bearer " + auth).header("X-Request-Id", "r43b-process")
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("AUTH_003"))
                    .andExpect(jsonPath("$.message").value("无权限"))
                    .andExpect(jsonPath("$.requestId").value("r43b-process"));
        }
        for (String path : new String[] {"/approval-flows", "/approval-flows/1", "/approval-flows/types/GENERAL/versions", "/approval-flows/types/GENERAL/active"}) {
            mockMvc.perform(get("/api/v1" + path).header("Authorization", "Bearer " + auth))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("AUTH_003"));
        }
        mockMvc.perform(put("/api/v1/approval-flows/1").header("Authorization", "Bearer " + auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"flowName\":\"unchanged\",\"nodes\":[{\"nodeOrder\":1,\"nodeName\":\"A\",\"approverUserId\":2}]}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("AUTH_003"));
    }

    @Test
    void internalDriverCanCreateAndResubmitOwnGeneralApproval() throws Exception {
        applicantToken = entryToken("DRIVER", "INTERNAL");
        long id = createApproval(100);
        returnApplicant(id, approverAToken).andExpect(status().isOk());
        resubmit(id, 200).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PENDING"));
        assertThat(count("SELECT COUNT(*) FROM approval_submission_version WHERE approval_instance_id = " + id)).isEqualTo(2);
    }

    private String entryToken(String role, String driverType) {
        jdbcTemplate.update("UPDATE driver SET driver_type = ? WHERE id = 1", driverType);
        return tokenAuthenticationService.issueToken(new AuthenticatedUser(101L, "13800000101", 2L, role, role,
                Set.of("approval:create", "approval:process", "approval:return", "approval:flow:manage", "approval:history:view")));
    }

    private long createApproval(int amount) throws Exception {
        mockMvc.perform(post("/api/v1/approvals")
                        .header("Authorization", "Bearer " + applicantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(approvalJson(amount)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"));
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM approval_instance", Long.class);
    }

    private String approvalJson(int amount) {
        return """
                {"approvalType":"GENERAL","businessType":"GENERAL","businessId":1,"businessSnapshot":{"amount":%d}}
                """.formatted(amount);
    }

    private long moveToD(long id) throws Exception {
        approve(id, approverAToken);
        approve(id, approverBToken);
        approve(id, approverCToken);
        return id;
    }

    private void approveAll(long id) throws Exception {
        approve(id, approverAToken);
        approve(id, approverBToken);
        approve(id, approverCToken);
        approve(id, approverDToken);
    }

    private org.springframework.test.web.servlet.ResultActions approve(long id, String token) throws Exception {
        return mockMvc.perform(post("/api/v1/approvals/%d/approve".formatted(id))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"ok\"}"))
                .andExpect(status().isOk());
    }

    private void approveExpectConflict(long id, String token, String code) throws Exception {
        mockMvc.perform(post("/api/v1/approvals/%d/approve".formatted(id))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"late\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(code));
    }

    private org.springframework.test.web.servlet.ResultActions returnApplicant(long id, String token) throws Exception {
        return mockMvc.perform(post("/api/v1/approvals/%d/return-applicant".formatted(id))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"退回申请人\"}"));
    }

    private org.springframework.test.web.servlet.ResultActions returnNode(long id, String token, int targetNodeOrder) throws Exception {
        return mockMvc.perform(post("/api/v1/approvals/%d/return-node".formatted(id))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"退回节点\",\"targetNodeOrder\":%d}".formatted(targetNodeOrder)));
    }

    private org.springframework.test.web.servlet.ResultActions resubmit(long id, int amount) throws Exception {
        return mockMvc.perform(post("/api/v1/approvals/%d/resubmit".formatted(id))
                        .header("Authorization", "Bearer " + applicantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(approvalJson(amount)));
    }

    private void enterMaintenance(long flowId) throws Exception {
        mockMvc.perform(post("/api/v1/approval-flows/%d/maintenance".formatted(flowId))
                        .header("Authorization", "Bearer " + applicantToken))
                .andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions updateFlow(long flowId, long approverUserId) throws Exception {
        return mockMvc.perform(put("/api/v1/approval-flows/%d".formatted(flowId))
                        .header("Authorization", "Bearer " + applicantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"flowName":"新版通用审批","nodes":[
                                  {"nodeOrder":1,"nodeName":"新版审批人","approverUserId":%d}
                                ]}
                                """.formatted(approverUserId)));
    }

    private long createNewDraftAfterCompletion(long approverUserId) throws Exception {
        approveAll(createApproval(100));
        enterMaintenance(1);
        updateFlow(1, approverUserId).andExpect(status().isOk());
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM approval_flow", Long.class);
    }

    private org.springframework.test.web.servlet.ResultActions publishFlow(long flowId) throws Exception {
        return mockMvc.perform(post("/api/v1/approval-flows/%d/publish".formatted(flowId))
                .header("Authorization", "Bearer " + applicantToken));
    }

    private int count(String sql) {
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }
}
