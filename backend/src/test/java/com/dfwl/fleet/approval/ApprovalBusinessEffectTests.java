package com.dfwl.fleet.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:approval-effect-db;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-auth-test.sql"
})
@AutoConfigureMockMvc
class ApprovalBusinessEffectTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TokenAuthenticationService tokenAuthenticationService;

    private String applicantToken;
    private String approverToken;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM expense_reversal_link");
        jdbcTemplate.update("DELETE FROM expense_energy_detail");
        jdbcTemplate.update("DELETE FROM expense_penalty_detail");
        jdbcTemplate.update("DELETE FROM expense_repair_detail");
        jdbcTemplate.update("DELETE FROM expense_toll_detail");
        jdbcTemplate.update("DELETE FROM expense_entry");
        jdbcTemplate.update("DELETE FROM tire_claim");
        jdbcTemplate.update("DELETE FROM tire_request_item");
        jdbcTemplate.update("DELETE FROM tire_request");
        jdbcTemplate.update("DELETE FROM approval_action");
        jdbcTemplate.update("DELETE FROM approval_task");
        jdbcTemplate.update("DELETE FROM approval_submission_version");
        jdbcTemplate.update("DELETE FROM approval_instance");
        jdbcTemplate.update("DELETE FROM approval_flow_node");
        jdbcTemplate.update("DELETE FROM approval_flow");
        jdbcTemplate.update("DELETE FROM route_task");
        jdbcTemplate.update("DELETE FROM tire");
        jdbcTemplate.update("DELETE FROM vehicle");
        jdbcTemplate.update("DELETE FROM driver");

        jdbcTemplate.update("""
                INSERT INTO driver (id, name, phone, id_card_no, driver_type, status)
                VALUES (1, 'Driver A', '13800000001', '110101199001010011', 'INTERNAL', 1)
                """);
        jdbcTemplate.update("""
                INSERT INTO vehicle (id, plate_no, insurance_complete, energy_type, max_load, load_standard_type,
                                     load_standard_percent, status)
                VALUES (1, '京A10001', 1, 'ELECTRIC', 30.000, 'PERCENT', 0.9000, 1)
                """);
        jdbcTemplate.update("""
                INSERT INTO route_task (id, route_no, business_unique_key, business_date, customer_id, product_id,
                                        direction, loading_place, unloading_place, tax_unit_price, status, created_by)
                VALUES (1, 'R001', 'K001', DATE '2026-09-10', 1, 1, 'OUTBOUND', 'A', 'B', 1.0000, 'PUBLISHED', 1)
                """);
        jdbcTemplate.update("INSERT INTO tire (id, tire_no, barcode, status, data_source) VALUES (1, 'TIRE-001', 'BC001', 'IN_STOCK', 'MANUAL')");
        jdbcTemplate.update("INSERT INTO tire (id, tire_no, barcode, status, data_source) VALUES (2, 'TIRE-002', 'BC002', 'IN_STOCK', 'MANUAL')");

        jdbcTemplate.update("INSERT INTO approval_flow (id, approval_type, flow_name, version_no, status, created_by) VALUES (1, 'EXPENSE', '费用审批', 1, 'ACTIVE', 1)");
        jdbcTemplate.update("INSERT INTO approval_flow_node (flow_id, node_order, node_name, approver_user_id) VALUES (1, 1, '财务', 2)");
        jdbcTemplate.update("INSERT INTO approval_flow (id, approval_type, flow_name, version_no, status, created_by) VALUES (2, 'TIRE_REQUEST', '换胎审批', 1, 'ACTIVE', 1)");
        jdbcTemplate.update("INSERT INTO approval_flow_node (flow_id, node_order, node_name, approver_user_id) VALUES (2, 1, '库管', 2)");

        applicantToken = tokenAuthenticationService.issueToken(new AuthenticatedUser(1L, "1380", 1L, "admin", "管理员", Set.of(
                "approval:create", "approval:return", "tire:request", "expense:edit", "expense:delete", "expense:reversal", "expense:approval:view")));
        approverToken = tokenAuthenticationService.issueToken(new AuthenticatedUser(2L, "1381", 1L, "finance", "财务", Set.of(
                "approval:process", "approval:return")));
    }

    @Test
    void repairApprovalCreatesFormalExpenseFromSnapshot() throws Exception {
        long approvalId = createExpenseApproval("""
                {"expenseType":"REPAIR","businessDate":"2026-09-10","vehicleId":1,
                 "attributionType":"DAILY","amount":"123.45","remark":"repair approval",
                 "repairDetail":{"detail":"换刹车片","receiptNo":"RC-1","invoiceNo":"INV-1","repairShop":"维修厂"}}
                """);

        approve(approvalId).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("APPROVED"));

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM expense_entry WHERE source_type = 'APPROVAL' AND approval_instance_id = ?", Integer.class, approvalId)).isEqualTo(1);
        Long expenseId = jdbcTemplate.queryForObject("SELECT id FROM expense_entry WHERE approval_instance_id = ?", Long.class, approvalId);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM expense_repair_detail WHERE expense_id = ?", Integer.class, expenseId)).isEqualTo(1);
    }

    @Test
    void routeExpenseRequiresRouteIdAndDoesNotApproveWhenBusinessEffectFails() throws Exception {
        long approvalId = createExpenseApproval("""
                {"expenseType":"WATER","businessDate":"2026-09-10","vehicleId":1,
                 "attributionType":"ROUTE","amount":"10.00"}
                """);

        approve(approvalId).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EXPENSE_003"));

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM approval_instance WHERE id = ?", String.class, approvalId)).isEqualTo("PENDING");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM expense_entry WHERE approval_instance_id = ?", Integer.class, approvalId)).isZero();
    }

    @Test
    void dailyExpenseMustNotBindRoute() throws Exception {
        long approvalId = createExpenseApproval("""
                {"expenseType":"WATER","businessDate":"2026-09-10","vehicleId":1,
                 "attributionType":"DAILY","routeId":1,"amount":"10.00"}
                """);

        approve(approvalId).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EXPENSE_003"));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM expense_entry WHERE approval_instance_id = ?", Integer.class, approvalId)).isZero();
    }

    @Test
    void repeatedFinalApprovalDoesNotCreateDuplicateExpense() throws Exception {
        long approvalId = createExpenseApproval("""
                {"expenseType":"TOLL","businessDate":"2026-09-10","vehicleId":1,
                 "attributionType":"ROUTE","routeId":1,"amount":"33.00",
                 "tollDetail":{"detail":"高速费","receiptNo":"TOLL-1"}}
                """);

        approve(approvalId).andExpect(status().isOk());
        approve(approvalId).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("APPROVAL_002"));

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM expense_entry WHERE approval_instance_id = ?", Integer.class, approvalId)).isEqualTo(1);
    }

    @Test
    void approvalExpenseCannotEditOrDeleteAndReversalKeepsApprovalTrace() throws Exception {
        long approvalId = createExpenseApproval("""
                {"expenseType":"REPAIR","businessDate":"2026-09-10","vehicleId":1,
                 "attributionType":"DAILY","amount":"80.00","repairDetail":{"detail":"补胎"}}
                """);
        approve(approvalId).andExpect(status().isOk());
        Long expenseId = jdbcTemplate.queryForObject("SELECT id FROM expense_entry WHERE approval_instance_id = ?", Long.class, approvalId);

        mockMvc.perform(put("/api/v1/expenses/%d".formatted(expenseId))
                        .header("Authorization", "Bearer " + applicantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expenseType":"REPAIR","businessDate":"2026-09-10","vehicleId":1,
                                 "attributionType":"DAILY","amount":81.00,"sourceType":"MANUAL"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EXPENSE_004"));

        mockMvc.perform(delete("/api/v1/expenses/%d".formatted(expenseId)).header("Authorization", "Bearer " + applicantToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EXPENSE_004"));

        mockMvc.perform(post("/api/v1/expenses/%d/reversal".formatted(expenseId))
                        .header("Authorization", "Bearer " + applicantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"审批费用错误\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REVERSAL"))
                .andExpect(jsonPath("$.data.approvalInstanceId").value(approvalId));
    }

    @Test
    void tireRequestReservesStockAndSecondRequestFails() throws Exception {
        createTireRequest(1).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PENDING"));
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM tire WHERE id = 1", String.class)).isEqualTo("APPROVAL_RESERVED");

        createTireRequest(1).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TIRE_001"));
    }

    @Test
    void tireApprovalClaimsTireAndIsIdempotentAtEndpointBoundary() throws Exception {
        createTireRequest(1).andExpect(status().isOk());
        long requestId = jdbcTemplate.queryForObject("SELECT id FROM tire_request", Long.class);
        long approvalId = createTireApproval(requestId);

        approve(approvalId).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("APPROVED"));
        approve(approvalId).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("APPROVAL_002"));

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM tire WHERE id = 1", String.class)).isEqualTo("CLAIMED");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tire_claim WHERE tire_id = 1 AND source_type = 'APPROVAL'", Integer.class)).isEqualTo(1);
    }

    @Test
    void returnApplicantReleasesReservedTire() throws Exception {
        createTireRequest(1).andExpect(status().isOk());
        long requestId = jdbcTemplate.queryForObject("SELECT id FROM tire_request", Long.class);
        long approvalId = createTireApproval(requestId);

        mockMvc.perform(post("/api/v1/approvals/%d/return-applicant".formatted(approvalId))
                        .header("Authorization", "Bearer " + approverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"补材料\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RETURNED_TO_APPLICANT"));

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM tire WHERE id = 1", String.class)).isEqualTo("IN_STOCK");
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM tire_request WHERE id = ?", String.class, requestId)).isEqualTo("RETURNED_TO_APPLICANT");
    }

    @Test
    void resubmitUsesLatestSubmissionVersionOnly() throws Exception {
        long approvalId = createExpenseApproval("""
                {"expenseType":"WATER","businessDate":"2026-09-10","vehicleId":1,
                 "attributionType":"DAILY","routeId":1,"amount":"10.00"}
                """);
        mockMvc.perform(post("/api/v1/approvals/%d/return-applicant".formatted(approvalId))
                        .header("Authorization", "Bearer " + approverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"修正\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/approvals/%d/resubmit".formatted(approvalId))
                        .header("Authorization", "Bearer " + applicantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"approvalType":"EXPENSE","businessType":"EXPENSE","businessSnapshot":
                                 {"expenseType":"WATER","businessDate":"2026-09-10","vehicleId":1,
                                  "attributionType":"DAILY","amount":"12.00"}}
                                """))
                .andExpect(status().isOk());

        approve(approvalId).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("APPROVED"));

        assertThat(jdbcTemplate.queryForObject("SELECT amount FROM expense_entry WHERE approval_instance_id = ?", java.math.BigDecimal.class, approvalId))
                .isEqualByComparingTo("12.00");
    }

    private long createExpenseApproval(String snapshot) throws Exception {
        mockMvc.perform(post("/api/v1/approvals")
                        .header("Authorization", "Bearer " + applicantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"approvalType":"EXPENSE","businessType":"EXPENSE","businessSnapshot":%s}
                                """.formatted(snapshot)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"));
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM approval_instance", Long.class);
    }

    private long createTireApproval(long requestId) throws Exception {
        mockMvc.perform(post("/api/v1/approvals")
                        .header("Authorization", "Bearer " + applicantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"approvalType":"TIRE_REQUEST","businessType":"TIRE_REQUEST","businessId":%d,
                                 "businessSnapshot":{"requestId":%d,"installTime":"2026-09-11T09:00:00"}}
                                """.formatted(requestId, requestId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"));
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM approval_instance", Long.class);
    }

    private org.springframework.test.web.servlet.ResultActions createTireRequest(long tireId) throws Exception {
        return mockMvc.perform(post("/api/v1/tire-requests")
                .header("Authorization", "Bearer " + applicantToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"driverId":1,"vehicleId":1,
                         "items":[{"tireId":%d,"confirmedTireNo":"TIRE-%03d"}]}
                        """.formatted(tireId, tireId)));
    }

    private org.springframework.test.web.servlet.ResultActions approve(long approvalId) throws Exception {
        return mockMvc.perform(post("/api/v1/approvals/%d/approve".formatted(approvalId))
                .header("Authorization", "Bearer " + approverToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"comment\":\"ok\"}"));
    }
}
