package com.dfwl.fleet.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dfwl.fleet.security.AuthenticatedUser;
import com.dfwl.fleet.security.TokenAuthenticationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:p17bdb;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-auth-test.sql",
        "fleet.storage.local-root=${java.io.tmpdir}/fleet-p17b-tests"
})
@AutoConfigureMockMvc
class P17BMiniProgramContractTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TokenAuthenticationService tokenService;

    @Autowired
    private ObjectMapper objectMapper;

    private String applicantToken;
    private String approverToken;
    private String otherApproverToken;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM approval_action");
        jdbcTemplate.update("DELETE FROM approval_task");
        jdbcTemplate.update("DELETE FROM approval_submission_version");
        jdbcTemplate.update("DELETE FROM approval_instance");
        jdbcTemplate.update("DELETE FROM approval_flow_node");
        jdbcTemplate.update("DELETE FROM approval_flow");
        jdbcTemplate.update("DELETE FROM file_attachment");
        jdbcTemplate.update("DELETE FROM driver_vehicle_current");
        jdbcTemplate.update("DELETE FROM driver");
        jdbcTemplate.update("DELETE FROM vehicle");

        jdbcTemplate.update("INSERT INTO approval_flow (id, approval_type, flow_name, version_no, status, created_by) VALUES (1, 'GENERAL', '通用审批', 1, 'ACTIVE', 1)");
        jdbcTemplate.update("INSERT INTO approval_flow_node (id, flow_id, node_order, node_name, approver_user_id) VALUES (10, 1, 1, '审批', 2)");
        applicantToken = tokenService.issueToken(new AuthenticatedUser(1L, "13800000001", 1L, "APPLICANT", "申请人",
                Set.of("approval:create", "approval:history:view")));
        approverToken = tokenService.issueToken(new AuthenticatedUser(2L, "13800000002", 2L, "APPROVER", "审批人",
                Set.of("approval:process", "approval:return")));
        otherApproverToken = tokenService.issueToken(new AuthenticatedUser(4L, "13800000004", 4L, "APPROVER", "其他审批人",
                Set.of("approval:process")));
    }

    @Test
    void todoDoneMineAndAllScopesAreServerSideFiltered() throws Exception {
        seedApproval(101, 1, 2, "PENDING", "PENDING", null);
        seedApproval(102, 1, 2, "APPROVED", "APPROVED", 2L);
        seedApproval(103, 3, 4, "PENDING", "PENDING", null);

        mockMvc.perform(get("/api/v1/approvals?scope=TODO")
                        .header("Authorization", "Bearer " + approverToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(101));

        mockMvc.perform(get("/api/v1/approvals?scope=DONE")
                        .header("Authorization", "Bearer " + approverToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(102));

        mockMvc.perform(get("/api/v1/approvals?scope=MINE")
                        .header("Authorization", "Bearer " + applicantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2));

        mockMvc.perform(get("/api/v1/approvals?scope=ALL")
                        .header("Authorization", "Bearer " + approverToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_003"));

        mockMvc.perform(get("/api/v1/approvals?scope=TODO")
                        .header("Authorization", "Bearer " + otherApproverToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(103));
    }

    @Test
    void todoScopeOnlyReturnsCurrentUserPendingTasks() throws Exception {
        seedApproval(101, 1, 2, "PENDING", "PENDING", null);
        seedApproval(102, 1, 4, "PENDING", "PENDING", null);

        mockMvc.perform(get("/api/v1/approvals?scope=TODO")
                        .header("Authorization", "Bearer " + approverToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(101));
    }

    @Test
    void doneScopeOnlyReturnsCurrentUserProcessedApprovals() throws Exception {
        seedApproval(101, 1, 2, "APPROVED", "APPROVED", 2L);
        seedApproval(102, 1, 2, "APPROVED", "APPROVED", 4L);

        mockMvc.perform(get("/api/v1/approvals?scope=DONE")
                        .header("Authorization", "Bearer " + approverToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(101));
    }

    @Test
    void mineScopeOnlyReturnsCurrentUserApplications() throws Exception {
        seedApproval(101, 1, 2, "PENDING", "PENDING", null);
        seedApproval(102, 3, 2, "PENDING", "PENDING", null);

        mockMvc.perform(get("/api/v1/approvals?scope=MINE")
                        .header("Authorization", "Bearer " + applicantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(101));
    }

    @Test
    void allScopeRequiresHistoryPermission() throws Exception {
        seedApproval(101, 1, 2, "PENDING", "PENDING", null);

        mockMvc.perform(get("/api/v1/approvals?scope=ALL")
                        .header("Authorization", "Bearer " + approverToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_003"));
    }

    @Test
    void preCreateApprovalAttachmentBindsToApprovalAndSnapshot() throws Exception {
        long attachmentId = uploadTemporaryApprovalAttachment(applicantToken, 1);

        long approvalId = createApprovalWithAttachments(applicantToken, attachmentId);

        assertThat(jdbcTemplate.queryForObject("SELECT owner_type FROM file_attachment WHERE id = ?", String.class, attachmentId))
                .isEqualTo("APPROVAL");
        assertThat(jdbcTemplate.queryForObject("SELECT owner_id FROM file_attachment WHERE id = ?", Long.class, attachmentId))
                .isEqualTo(approvalId);
        String snapshot = jdbcTemplate.queryForObject("""
                SELECT business_snapshot_json
                FROM approval_submission_version
                WHERE approval_instance_id = ? AND version_no = 1
                """, String.class, approvalId);
        assertThat(snapshot).contains("\"attachmentIds\":[" + attachmentId + "]");
    }

    @Test
    void createdApprovalSnapshotContainsAttachmentIds() throws Exception {
        long attachmentId = uploadTemporaryApprovalAttachment(applicantToken, 1);

        long approvalId = createApprovalWithAttachments(applicantToken, attachmentId);

        assertThat(snapshot(approvalId, 1)).contains("\"attachmentIds\":[" + attachmentId + "]");
    }

    @Test
    void failedCreateKeepsAttachmentInTemporaryRecoverableState() throws Exception {
        long attachmentId = uploadTemporaryApprovalAttachment(applicantToken, 1);

        mockMvc.perform(post("/api/v1/approvals")
                        .header("Authorization", "Bearer " + applicantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"approvalType":"MISSING","businessType":"GENERAL","businessId":1,
                                 "businessSnapshot":{"amount":1},"attachmentIds":[%d]}
                                """.formatted(attachmentId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("APPROVAL_001"));

        assertThat(jdbcTemplate.queryForObject("SELECT owner_type FROM file_attachment WHERE id = ?", String.class, attachmentId))
                .isEqualTo("APPROVAL_UPLOAD");
        assertThat(jdbcTemplate.queryForObject("SELECT owner_id FROM file_attachment WHERE id = ?", Long.class, attachmentId))
                .isEqualTo(1L);
    }

    @Test
    void resubmittedVersionUsesNewAttachmentsWithoutReusingOldSnapshot() throws Exception {
        long firstAttachment = uploadTemporaryApprovalAttachment(applicantToken, 1);
        long approvalId = createApprovalWithAttachments(applicantToken, firstAttachment);

        mockMvc.perform(post("/api/v1/approvals/%d/return-applicant".formatted(approvalId))
                        .header("Authorization", "Bearer " + approverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"补充单据\"}"))
                .andExpect(status().isOk());

        long secondAttachment = uploadTemporaryApprovalAttachment(applicantToken, 1);
        mockMvc.perform(post("/api/v1/approvals/%d/resubmit".formatted(approvalId))
                        .header("Authorization", "Bearer " + applicantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"approvalType":"GENERAL","businessType":"GENERAL","businessId":1,
                                 "businessSnapshot":{"amount":2},"attachmentIds":[%d]}
                                """.formatted(secondAttachment)))
                .andExpect(status().isOk());

        String firstSnapshot = snapshot(approvalId, 1);
        String secondSnapshot = snapshot(approvalId, 2);
        assertThat(firstSnapshot).contains("\"attachmentIds\":[" + firstAttachment + "]");
        assertThat(firstSnapshot).doesNotContain(String.valueOf(secondAttachment));
        assertThat(secondSnapshot).contains("\"attachmentIds\":[" + secondAttachment + "]");
        assertThat(secondSnapshot).doesNotContain(String.valueOf(firstAttachment));
    }

    @Test
    void meReturnsDriverIdentityAndCurrentVehicle() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO vehicle (id, plate_no, insurance_complete, energy_type, max_load, load_standard_type, load_standard_percent, status)
                VALUES (11, '沪A12345', 1, 'ELECTRIC', 49.000, 'PERCENT', 0.9000, 1)
                """);
        jdbcTemplate.update("""
                INSERT INTO driver (id, user_id, driver_type, name, phone, status)
                VALUES (9, 1, 'INTERNAL', '张三', '13800000001', 1)
                """);
        jdbcTemplate.update("""
                INSERT INTO driver_vehicle_current (driver_id, vehicle_id, bound_at, bound_by)
                VALUES (9, 11, CURRENT_TIMESTAMP, 1)
                """);

        mockMvc.perform(get("/api/v1/me")
                        .header("Authorization", "Bearer " + applicantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.driverId").value(9))
                .andExpect(jsonPath("$.data.driverType").value("INTERNAL"))
                .andExpect(jsonPath("$.data.driverName").value("张三"))
                .andExpect(jsonPath("$.data.currentVehicleId").value(11))
                .andExpect(jsonPath("$.data.currentPlateNo").value("沪A12345"));
    }

    private void seedApproval(long id, long applicantId, long approverId, String instanceStatus,
                              String taskStatus, Long actionOperatorId) {
        jdbcTemplate.update("""
                INSERT INTO approval_instance (id, approval_no, approval_type, flow_id, flow_version, business_type,
                                               business_id, applicant_user_id, status, current_node_order)
                VALUES (?, ?, 'GENERAL', 1, 1, 'GENERAL', 1, ?, ?, ?)
                """, id, "AP" + id, applicantId, instanceStatus, "PENDING".equals(instanceStatus) ? 1 : null);
        jdbcTemplate.update("""
                INSERT INTO approval_submission_version (id, approval_instance_id, version_no, business_snapshot_json, submitted_by)
                VALUES (?, ?, 1, '{"amount":1}', ?)
                """, id * 10, id, applicantId);
        jdbcTemplate.update("""
                INSERT INTO approval_task (id, approval_instance_id, submission_version_id, node_id, node_order, approver_user_id, status)
                VALUES (?, ?, ?, 10, 1, ?, ?)
                """, id * 100, id, id * 10, approverId, taskStatus);
        if (actionOperatorId != null) {
            jdbcTemplate.update("""
                    INSERT INTO approval_action (task_id, action_type, operator_id, comment)
                    VALUES (?, 'APPROVE', ?, 'done')
                    """, id * 100, actionOperatorId);
        }
    }

    private long uploadTemporaryApprovalAttachment(String token, long ownerId) throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/v1/attachments")
                        .file(new MockMultipartFile("file", "receipt.jpg", "image/jpeg", new byte[]{1, 2, 3}))
                        .param("ownerType", "APPROVAL_UPLOAD")
                        .param("ownerId", String.valueOf(ownerId))
                        .param("purpose", "APPROVAL_APPLICATION")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ownerType").value("APPROVAL_UPLOAD"))
                .andReturn();
        return readId(result);
    }

    private long createApprovalWithAttachments(String token, long attachmentId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/approvals")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"approvalType":"GENERAL","businessType":"GENERAL","businessId":1,
                                 "businessSnapshot":{"amount":1},"attachmentIds":[%d]}
                                """.formatted(attachmentId)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray()).path("data").path("id").asLong();
    }

    private long readId(MvcResult result) throws Exception {
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        return json.path("data").path("id").asLong();
    }

    private String snapshot(long approvalId, int versionNo) {
        return jdbcTemplate.queryForObject("""
                SELECT business_snapshot_json
                FROM approval_submission_version
                WHERE approval_instance_id = ? AND version_no = ?
                """, String.class, approvalId, versionNo);
    }
}
