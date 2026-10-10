package com.dfwl.fleet.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dfwl.fleet.approval.v3.ApprovalWorkflowService;
import com.dfwl.fleet.approval.v3.ApprovalWorkflowService.Action;
import com.dfwl.fleet.approval.v3.ApprovalWorkflowService.Application;
import com.dfwl.fleet.approval.v3.ApprovalWorkflowService.ExpenseItem;
import com.dfwl.fleet.approval.v3.ApprovalWorkflowService.FlowNodeInput;
import com.dfwl.fleet.approval.v3.ApprovalWorkflowService.Resubmission;
import com.dfwl.fleet.approval.v3.ApprovalWorkflowService.TemplateInput;
import com.dfwl.fleet.approval.v3.DailyExpenseWorkbook;
import com.dfwl.fleet.common.error.BusinessException;
import com.dfwl.fleet.security.AuthenticatedUser;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:v3-approval;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
        "spring.datasource.password=", "spring.flyway.enabled=false", "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-auth-test.sql"
})
class V3ApprovalWorkflowTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired ApprovalWorkflowService approvals;
    @Autowired DailyExpenseWorkbook workbook;

    private final AuthenticatedUser admin = new AuthenticatedUser(100L, "13900000100", 2L, "ADMIN",
            "管理员", Set.of("approval:create","approval:process","approval:return",
            "approval:history:view","approval:flow:manage"));
    private final AuthenticatedUser approver = new AuthenticatedUser(101L, "13900000101", 2L, "ADMIN",
            "管理员", Set.of("approval:process","approval:return","approval:history:view","tire:request:process"));
    private final AuthenticatedUser internal = new AuthenticatedUser(102L, "13900000102", 1L, "DRIVER",
            "司机", Set.of("approval:create","tire:request:create"));
    private final AuthenticatedUser outsourced = new AuthenticatedUser(103L, "13900000103", 1L, "DRIVER",
            "司机", Set.of("approval:create"));

    @BeforeEach
    void setUp() {
        for (String table : List.of("approval_submission_attachment","tire_request","ocr_record","file_attachment","tire_history","tire","expense_entry","approval_expense_item",
                "approval_action","approval_task","approval_submission_version","approval_instance",
                "approval_flow_initiator_role","approval_flow_node","approval_flow","approval_template",
                "audit_log","transport_participant","transport_task","route_template",
                "driver_vehicle_current","vehicle","driver","sys_user","product")) {
            jdbc.update("DELETE FROM " + table);
        }
        for (int id = 100; id <= 103; id++) {
            jdbc.update("INSERT INTO sys_user(id,phone,password_hash,role_id,status) VALUES(?,?,?,?,1)",
                    id, "13900000" + id, "HASH", id >= 102 ? 1 : 2);
        }
        jdbc.update("INSERT INTO driver(id,user_id,driver_type,name,phone,status) VALUES(10,102,'INTERNAL','自有司机','13900000102',1)");
        jdbc.update("INSERT INTO driver(id,user_id,driver_type,name,phone,status) VALUES(11,103,'OUTSOURCED','外协司机','13900000103',1)");
        jdbc.update("""
                INSERT INTO vehicle(id,plate_no,insurance_complete,energy_type,max_load,load_standard_type,
                                    load_standard_percent,status)
                VALUES(20,'云A100',1,'ELECTRIC',30,'PERCENT',0.9,1),(21,'云A101',1,'ELECTRIC',30,'PERCENT',0.9,1)
                """);
    }

    private long template(String type, int nodes) {
        List<FlowNodeInput> steps = new java.util.ArrayList<>();
        for (int i = 0; i < nodes; i++) steps.add(new FlowNodeInput("审批步骤" + (i + 1), 101L, true));
        Map<String,Object> draft = approvals.createTemplate(new TemplateInput("T" + System.nanoTime(),
                type + "模板", type, "EXPENSE".equals(type) ? "REPAIR" : null, List.of(1L, 2L), steps), admin);
        approvals.publish(((Number) draft.get("id")).longValue(), admin);
        return ((Number) draft.get("template_id")).longValue();
    }
    private ExpenseItem item(long vehicle, BigDecimal amount) {
        return new ExpenseItem(LocalDate.of(2026, 9, 30), vehicle, 10L, null,
                "维修", amount, "P-1", "F-1", "测试", "维修站");
    }
    private long approvalId(Map<String,Object> detail) {
        return ((Number) ((Map<?,?>) detail.get("approval")).get("id")).longValue();
    }
    private int version(Map<String,Object> detail) {
        return ((Number) ((Map<?,?>) detail.get("approval")).get("version")).intValue();
    }
    private Map<String,Object> create(long templateId, String scope, String key, List<ExpenseItem> items) {
        return approvals.create(new Application(templateId, scope, key, items, null, null, null), internal);
    }
    private Map<String,Object> decide(long id, String kind, Map<String,Object> detail, Long target) {
        return approvals.decide(id, kind, new Action("审批意见", target, version(detail), null), approver);
    }

    @Test
    void dailyBatchCreatesOneApprovalAndExactlyNExpensesAfterFinalApproval() {
        long template = template("EXPENSE", 2);
        var application = create(template, "DAILY", "batch-1",
                List.of(item(20, new BigDecimal("8.00")), item(21, new BigDecimal("9.00"))));
        long id = approvalId(application);
        assertThat(approvalId(create(template, "DAILY", "batch-1",
                List.of(item(20, new BigDecimal("8.00")), item(21, new BigDecimal("9.00")))))).isEqualTo(id);
        assertThatThrownBy(() -> create(template, "DAILY", "batch-1",
                List.of(item(20, new BigDecimal("8.00")))))
                .isInstanceOf(BusinessException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM approval_instance", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM expense_entry", Long.class)).isZero();
        application = decide(id, "APPROVE", application, null);
        application = decide(id, "APPROVE", application, null);
        assertThat(((Map<?,?>) application.get("approval")).get("status")).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM expense_entry WHERE approval_instance_id=?", Long.class, id))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT SUM(amount) FROM expense_entry WHERE approval_instance_id=?",
                BigDecimal.class, id)).isEqualByComparingTo("17.00");
        assertThat(jdbc.queryForList("SELECT application_scope,current_task_id FROM expense_entry WHERE approval_instance_id=?", id))
                .allSatisfy(row -> { assertThat(row.get("application_scope")).isEqualTo("DAILY");
                    assertThat(row.get("current_task_id")).isNull(); });
        int finalVersion = version(application);
        assertThatThrownBy(() -> approvals.decide(id, "APPROVE",
                new Action(null, null, finalVersion, null), approver)).isInstanceOf(BusinessException.class);
    }

    @Test
    void dailyFinalSubmittedItemsAreAuthoritativeAfterDraftEdits() {
        long template = template("EXPENSE", 1);
        // The uploaded workbook is a draft aid: its original rows never reach this write path.
        // This is the edited first row plus a manual row; the deleted workbook row is absent.
        List<ExpenseItem> finalItems = List.of(
                item(20, new BigDecimal("10.00")), item(21, new BigDecimal("3.00")));
        assertThatThrownBy(() -> create(template, "DAILY", "bad-final-row", List.of(
                finalItems.get(0), item(21, BigDecimal.ZERO)))).isInstanceOf(BusinessException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM approval_instance", Long.class)).isZero();
        var current = create(template, "DAILY", "edited-final-rows", finalItems);
        long id = approvalId(current);
        assertThat(jdbc.queryForList("SELECT amount FROM approval_expense_item WHERE instance_id=? ORDER BY item_no", id))
                .extracting(row -> (BigDecimal) row.get("amount"))
                .containsExactly(new BigDecimal("10.00"), new BigDecimal("3.00"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM expense_entry", Long.class)).isZero();
        current = decide(id, "APPROVE", current, null);
        assertThat(jdbc.queryForList("SELECT amount FROM expense_entry WHERE approval_instance_id=? ORDER BY approval_item_no", id))
                .extracting(row -> (BigDecimal) row.get("amount"))
                .containsExactly(new BigDecimal("10.00"), new BigDecimal("3.00"));
    }

    @Test
    void stableReturnTargetIsPreviouslyApprovedNodeNotRoleOrOrder() {
        long template = template("GENERAL", 3);
        var current = approvals.create(new Application(template, null, "general-1", null,
                Map.of("description", "请示事项"), null, null), admin);
        long id = approvalId(current);
        assertThat(approvals.returnableSteps(id, approver)).isEmpty();
        current = decide(id, "APPROVE", current, null);
        assertThat(approvals.returnableSteps(id, approver)).hasSize(1);
        current = decide(id, "APPROVE", current, null);
        var candidates = approvals.returnableSteps(id, approver);
        assertThat(candidates).hasSize(2);
        long target = ((Number) candidates.get(0).get("flowNodeId")).longValue();
        int beforeReturnVersion = version(current);
        assertThatThrownBy(() -> approvals.decide(id, "RETURN_STEP",
                new Action("返回", 9999L, beforeReturnVersion, null), approver))
                .isInstanceOf(BusinessException.class);
        current = decide(id, "RETURN_STEP", current, target);
        assertThat(((List<?>) current.get("submissions"))).hasSize(2);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM approval_task WHERE instance_id=? AND invalidated=1
                """, Long.class, id)).isGreaterThan(0);
        current = decide(id, "APPROVE", current, null);
        current = decide(id, "APPROVE", current, null);
        current = decide(id, "APPROVE", current, null);
        assertThat(((Map<?,?>) current.get("approval")).get("status")).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM expense_entry", Long.class)).isZero();
    }

    @Test
    void applicantReturnCreatesNewSubmissionWithoutChangingOriginalScope() {
        long template = template("EXPENSE", 1);
        var current = create(template, "DAILY", "return-1", List.of(item(20, new BigDecimal("3.00"))));
        long id = approvalId(current);
        current = decide(id, "RETURN_APPLICANT", current, null);
        current = approvals.resubmit(id, new Resubmission(
                List.of(item(20, new BigDecimal("4.00"))), null, null), internal);
        assertThat(((List<?>) current.get("submissions"))).hasSize(2);
        current = decide(id, "APPROVE", current, null);
        assertThat(jdbc.queryForObject("SELECT amount FROM expense_entry WHERE approval_instance_id=?",
                BigDecimal.class, id)).isEqualByComparingTo("4.00");
    }

    @Test
    void dailyDoesNotRequireCurrentBindingAndOutsourcedCannotCreate() {
        long template = template("EXPENSE", 1);
        assertThat(approvals.taskCandidates(10, internal)).isEmpty();
        assertThatThrownBy(() -> approvals.taskCandidates(11, internal))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThat(create(template, "DAILY", "unbound", List.of(item(21, BigDecimal.ONE)))).isNotNull();
        assertThatThrownBy(() -> approvals.create(new Application(template, "DAILY", "outsource",
                List.of(item(20, BigDecimal.ONE)), null, null, null), outsourced))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(() -> create(template, "TASK", "task-multi",
                List.of(item(20, BigDecimal.ONE), item(20, BigDecimal.ONE))))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> create(template, "DAILY", "zero",
                List.of(item(20, BigDecimal.ZERO)))).isInstanceOf(BusinessException.class);
    }

    @Test
    void fixedWorkbookRejectsChangedHeaderAndInvalidRows() throws Exception {
        byte[] template = workbook.template();
        assertThat(template).isNotEmpty();
        try (Workbook book = new XSSFWorkbook(new java.io.ByteArrayInputStream(template));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var row = book.getSheetAt(0).createRow(1);
            for (int i = 0; i < 4; i++)
                row.createCell(i).setCellValue(List.of("2026-09-30","云A100","13900000102","8.00").get(i));
            book.write(out);
            var file = new MockMultipartFile("file", "daily.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
            assertThat(workbook.preview(file).errorCount()).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM approval_instance", Long.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM expense_entry", Long.class)).isZero();
            book.getSheetAt(0).getRow(0).getCell(0).setCellValue("任意表头");
            out.reset(); book.write(out);
            var invalid = new MockMultipartFile("file", "bad.xlsx", file.getContentType(), out.toByteArray());
            assertThatThrownBy(() -> workbook.preview(invalid)).isInstanceOf(BusinessException.class);
        }
    }

    @Test
    void publishedFlowCannotBeEditedWhileAnInstanceIsPending() {
        long templateId = template("GENERAL", 2);
        var original = approvals.create(new Application(templateId, null, "flow-freeze", null,
                Map.of("description", "仍在审批"), null, null), admin);
        var input = new TemplateInput(jdbc.queryForObject(
                "SELECT stable_code FROM approval_template WHERE id=?", String.class, templateId),
                "新流程名称", "GENERAL", null, List.of(1L, 2L),
                List.of(new FlowNodeInput("新步骤", 101L, true)));
        assertThatThrownBy(() -> approvals.nextVersion(templateId, input, admin))
                .isInstanceOf(BusinessException.class);
        assertThat(((Map<?, ?>) original.get("flow")).get("version_no")).isEqualTo(1);
    }

    @Test
    void tireApprovalUsesTireHandlerAndNeverPostsOrdinaryExpense() {
        long templateId = template("TIRE_REPLACEMENT", 1);
        jdbc.update("INSERT INTO tire(id,tire_no,material_description,arrived_at,status,created_by) "
                + "VALUES(60,'胎-V3D-60','轮胎',CURRENT_TIMESTAMP,'AVAILABLE',100)");
        jdbc.update("""
                INSERT INTO file_attachment(id,owner_type,owner_id,purpose,storage_key,original_filename,
                    content_type,file_size,file_hash,uploaded_by)
                VALUES(1,'TIRE_OCR',0,'TIRE_OCR','test/tire.png','tire.png','image/png',1,'test',102)
                """);
        jdbc.update("""
                INSERT INTO ocr_record(id,attachment_id,raw_result_json,ocr_status,confirmed_text)
                VALUES(1,1,'{}','SUCCESS','胎-V3D-60')
                """);
        jdbc.update("INSERT INTO tire_request(id,applicant_driver_id,vehicle_id,confirmed_tire_no,"
                + "ocr_record_id,photo_attachment_id,tire_id,status) "
                + "VALUES(61,10,20,'胎-V3D-60',1,1,60,'DRAFT')");
        var current = approvals.create(new Application(templateId, null, "tire-1", null,
                null, 61L, null), internal);
        long id = approvalId(current);
        current = decide(id, "APPROVE", current, null);
        assertThat(((Map<?, ?>) current.get("approval")).get("status")).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("SELECT status FROM tire WHERE id=60", String.class)).isEqualTo("CLAIMED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tire_history WHERE tire_id=60", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM expense_entry", Long.class)).isZero();
    }
}
