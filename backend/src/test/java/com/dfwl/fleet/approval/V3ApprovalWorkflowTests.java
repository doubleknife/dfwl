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
import com.dfwl.fleet.attachment.repository.FileAttachmentRepository;
import com.dfwl.fleet.common.error.BusinessException;
import com.dfwl.fleet.security.AuthenticatedUser;
import com.dfwl.fleet.tire.v3.TireV3Controller;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.bind.annotation.PostMapping;

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
    @Autowired FileAttachmentRepository attachments;
    @Autowired ApplicationContext applicationContext;

    private final AuthenticatedUser admin = new AuthenticatedUser(100L, "13900000100", 2L, "ADMIN",
            "管理员", grants("BUTTON_EXPENSE_APPLY_SUBMIT","BUTTON_APPROVALS_TODO_APPROVE","BUTTON_APPROVALS_TODO_RETURN",
            "PAGE_APPROVALS_HISTORY","BUTTON_APPROVALS_FLOWS_MANAGE"));
    private final AuthenticatedUser approver = new AuthenticatedUser(101L, "13900000101", 2L, "ADMIN",
            "管理员", grants("BUTTON_APPROVALS_TODO_APPROVE","BUTTON_APPROVALS_TODO_RETURN","PAGE_APPROVALS_HISTORY"));
    private final AuthenticatedUser secondApprover = new AuthenticatedUser(104L, "13900000104", 2L, "ADMIN",
            "管理员", grants("BUTTON_APPROVALS_TODO_APPROVE","BUTTON_APPROVALS_TODO_RETURN"));
    private final AuthenticatedUser internal = new AuthenticatedUser(102L, "13900000102", 1L, "DRIVER",
            "司机", grants("BUTTON_MINI_EXPENSE_APPLY_SUBMIT","BUTTON_MINI_TIRE_REQUEST_SUBMIT"));
    private final AuthenticatedUser outsourced = new AuthenticatedUser(103L, "13900000103", 1L, "DRIVER",
            "司机", grants("BUTTON_MINI_EXPENSE_APPLY_SUBMIT"));

    private static Set<String> grants(String... codes) {
        java.util.HashSet<String> granted = new java.util.HashSet<>();
        for (String code : codes) {
            granted.add(code);
            var function = new com.dfwl.fleet.security.FunctionRegistry().find(code);
            if (function != null && function.parentCode() != null) granted.add(function.parentCode());
        }
        return Set.copyOf(granted);
    }

    @BeforeEach
    void setUp() {
        for (String table : List.of("approval_submission_attachment","tire_request","ocr_record","file_attachment","tire_history","tire","expense_entry","approval_expense_item",
                "approval_action","approval_task","approval_submission_version","approval_instance",
                "approval_flow_initiator_role","approval_flow_node","approval_flow","approval_template",
                "audit_log","transport_participant","transport_task","route_template",
                "driver_vehicle_current","vehicle","driver","sys_user","product")) {
            jdbc.update("DELETE FROM " + table);
        }
        for (int id = 100; id <= 104; id++) {
            jdbc.update("INSERT INTO sys_user(id,phone,password_hash,role_id,status) VALUES(?,?,?,?,1)",
                    id, "13900000" + id, "HASH", id == 102 || id == 103 ? 1 : 2);
        }
        jdbc.update("DELETE FROM sys_role_permission WHERE role_id=3");
        jdbc.update("DELETE FROM sys_role WHERE id=3");
        jdbc.update("INSERT INTO driver(id,user_id,driver_type,name,phone,id_card_no,driving_license_no,status) VALUES(10,102,'INTERNAL','自有司机','13900000102','110105199001010109','TEST-DL-010',1)");
        jdbc.update("INSERT INTO driver(id,user_id,driver_type,name,phone,id_card_no,driving_license_no,status) VALUES(11,103,'OUTSOURCED','外协司机','13900000103','110105199001010117','TEST-DL-011',1)");
        jdbc.update("""
                INSERT INTO vehicle(id,plate_no,vehicle_license_no,insurance_complete,energy_type,max_load,load_standard_type,
                                    load_standard_percent,status)
                VALUES(20,'云A100','TEST-VL-020',1,'ELECTRIC',30,'PERCENT',0.9,1),(21,'云A101','TEST-VL-021',1,'ELECTRIC',30,'PERCENT',0.9,1)
                """);
    }

    private void tireReviewRole() {
        jdbc.update("INSERT INTO sys_role(id,role_code,role_name,status) VALUES(3,'TIRE_REVIEW','换胎复核',1)");
        jdbc.update("""
                INSERT INTO sys_role_permission(role_id,permission_id)
                SELECT 3,id FROM sys_permission
                WHERE permission_code IN ('PAGE_APPROVALS_TODO','BUTTON_APPROVALS_TODO_APPROVE','BUTTON_APPROVALS_TODO_RETURN')
                """);
        jdbc.update("INSERT INTO sys_user(id,phone,password_hash,role_id,status) VALUES(105,'13900000105','HASH',3,1)");
        jdbc.update("INSERT INTO sys_user(id,phone,password_hash,role_id,status) VALUES(106,'13900000106','HASH',3,1)");
    }

    private AuthenticatedUser tireReviewer(long id) {
        return new AuthenticatedUser(id, "13900000" + id, 3L, "TIRE_REVIEW", "换胎复核",
                grants("BUTTON_APPROVALS_TODO_APPROVE", "BUTTON_APPROVALS_TODO_RETURN"));
    }

    private long tireTemplate(List<FlowNodeInput> nodes) {
        Map<String, Object> flow = approvals.createTemplate(new TemplateInput(null, null,
                "TIRE_REPLACEMENT", null, List.of(1L), nodes), admin);
        approvals.publish(((Number) flow.get("id")).longValue(), admin);
        return ((Number) flow.get("template_id")).longValue();
    }

    private void tireRequestFact() {
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
    }

    private long template(String type, int nodes) {
        if ("GENERAL".equals(type)) return historicalGeneral(nodes, 101L);
        List<FlowNodeInput> steps = new java.util.ArrayList<>();
        for (int i = 0; i < nodes; i++) steps.add(new FlowNodeInput("审批步骤" + (i + 1), 2L, 101L, true));
        Map<String,Object> draft = approvals.createTemplate(new TemplateInput("T" + System.nanoTime(),
                type + "模板", type, "EXPENSE".equals(type) ? "REPAIR" : null, List.of(1L, 2L), steps), admin);
        approvals.publish(((Number) draft.get("id")).longValue(), admin);
        return ((Number) draft.get("template_id")).longValue();
    }

    private long historicalGeneral(int nodes, Long namedApprover) {
        String code = "HIST_" + System.nanoTime();
        jdbc.update("INSERT INTO approval_template(stable_code,name,business_type) VALUES(?,?,'GENERAL')", code, "既有普通审批");
        long templateId = jdbc.queryForObject("SELECT id FROM approval_template WHERE stable_code=?", Long.class, code);
        jdbc.update("INSERT INTO approval_flow(template_id,version_no,status,created_by) VALUES(?,1,'ACTIVE',100)", templateId);
        long flowId = jdbc.queryForObject("SELECT id FROM approval_flow WHERE template_id=?", Long.class, templateId);
        for (int i = 1; i <= nodes; i++) jdbc.update("INSERT INTO approval_flow_node(flow_id,node_order,node_name,approver_role_id,approver_user_id,allow_return) VALUES(?,?,?,2,?,1)", flowId, i, "审批步骤" + i, namedApprover);
        jdbc.update("INSERT INTO approval_flow_initiator_role(flow_id,role_id) VALUES(?,1),(?,2)", flowId, flowId);
        return templateId;
    }

    @Test
    void generalTemplatesRemainReadableButCannotBeCreated() {
        long historical = historicalGeneral(1, 101L);
        assertThat(approvals.flow(jdbc.queryForObject("SELECT id FROM approval_flow WHERE template_id=?", Long.class, historical))
                .get("business_type")).isEqualTo("GENERAL");
        assertThatThrownBy(() -> approvals.createTemplate(new TemplateInput("IGNORED", "新普通审批", "GENERAL",
                null, List.of(2L), List.of(new FlowNodeInput("审批", 2L, 101L, true))), admin))
                .isInstanceOf(BusinessException.class).hasMessageContaining("不能新建模板");
    }

    @Test
    void expenseCategoryCatalogExcludesOtherAndIncludesTemporaryGas() {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> categories = (List<Map<String, Object>>) approvals.flowOptions(admin).get("expenseTypes");
        assertThat(categories).extracting(row -> row.get("categoryCode"))
                .contains("TEMP_GAS").doesNotContain("OTHER");
    }

    @Test
    void tireFlowAcceptsRoleWithUnifiedApprovalPermissionOnly() {
        jdbc.update("INSERT INTO sys_role(id,role_code,role_name,status) VALUES(3,'TIRE_REVIEW','换胎复核',1)");
        jdbc.update("""
                INSERT INTO sys_role_permission(role_id,permission_id)
                SELECT 3,id FROM sys_permission WHERE permission_code='BUTTON_APPROVALS_TODO_APPROVE'
                """);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> roles = (List<Map<String, Object>>) approvals.flowOptions(admin).get("approverRoles");
        Map<String, Object> reviewRole = roles.stream()
                .filter(role -> ((Number) role.get("id")).longValue() == 3L).findFirst().orElseThrow();
        assertThat(String.valueOf(reviewRole.get("canProcessApproval"))).isIn("1", "true");
        assertThat(String.valueOf(reviewRole.get("canReturnApproval"))).isIn("0", "false");
        Map<String, Object> created = approvals.createTemplate(new TemplateInput(null, null, "TIRE_REPLACEMENT", null,
                List.of(1L), List.of(new FlowNodeInput("换胎复核", 3L, null, true))), admin);
        assertThat(created.get("id")).isNotNull();
    }

    @Test
    void permissionSeedSeparatesTireRequestFromUnifiedApprovalActions() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_permission WHERE permission_code='BUTTON_MINI_TIRE_REQUEST_SUBMIT'",
                Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM sys_role_permission rp JOIN sys_permission p ON p.id=rp.permission_id
                WHERE rp.role_id=1 AND p.permission_code='BUTTON_MINI_TIRE_REQUEST_SUBMIT'
                """, Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM sys_permission
                WHERE permission_code IN ('tire:request:process','tire:request:create',
                    'tire:approve','tire:approval','tire:approval:view')
                """, Long.class)).isZero();
    }

    @Test
    void tireApprovalHasNoDedicatedDecisionEndpoint() {
        assertThat(applicationContext.containsBean("approvalService")).isFalse();
        List<String> routes = java.util.Arrays.stream(TireV3Controller.class.getDeclaredMethods())
                .map(method -> method.getAnnotation(PostMapping.class))
                .filter(java.util.Objects::nonNull)
                .flatMap(mapping -> java.util.Arrays.stream(mapping.value())).toList();
        assertThat(routes).contains("/api/v1/tire-requests");
        assertThat(routes).noneMatch(route -> route.contains("/api/v1/tire-requests")
                && (route.contains("approve") || route.contains("reject")
                || route.contains("return") || route.contains("todo")));
    }

    @Test
    void templateIdentityAndNameAreGeneratedAndDuplicateBusinessIsRejected() {
        TemplateInput expense = new TemplateInput("CLIENT_CODE", "客户端名称", "EXPENSE", "REPAIR",
                List.of(2L), List.of(new FlowNodeInput("财务复核", 2L, 101L, true)));
        Map<String, Object> created = approvals.createTemplate(expense, admin);
        assertThat(created.get("name")).isEqualTo("维修费申请");
        assertThat(created.get("stable_code")).asString().startsWith("APP_").isNotEqualTo("CLIENT_CODE");
        approvals.publish(((Number) created.get("id")).longValue(), admin);
        Map<String, Object> next = approvals.nextVersion(((Number) created.get("template_id")).longValue(),
                new TemplateInput(null, null, "EXPENSE", "REPAIR", List.of(2L),
                        List.of(new FlowNodeInput("复核新版", 2L, null, true))), admin);
        assertThat(next.get("name")).isEqualTo("维修费申请");
        assertThat(next.get("stable_code")).isEqualTo(created.get("stable_code"));
        assertThat(next.get("version_no")).isEqualTo(2);
        assertThatThrownBy(() -> approvals.createTemplate(expense, admin))
                .isInstanceOf(BusinessException.class).hasMessageContaining("已有模板");
        TemplateInput tire = new TemplateInput("CUSTOM", "随意名称", "TIRE_REPLACEMENT", null,
                List.of(2L), List.of(new FlowNodeInput("轮胎复核", 2L, 101L, true)));
        Map<String, Object> tireFlow = approvals.createTemplate(tire, admin);
        assertThat(tireFlow.get("name")).isEqualTo("换胎申请");
        assertThatThrownBy(() -> approvals.createTemplate(tire, admin))
                .isInstanceOf(BusinessException.class).hasMessageContaining("已有模板");
    }

    @Test
    void inactiveExpenseCategoryCannotBeUsedForNewTemplate() {
        jdbc.update("UPDATE expense_category SET enabled=0 WHERE category_code='REPAIR'");
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> categories = (List<Map<String, Object>>) approvals.flowOptions(admin).get("expenseTypes");
            assertThat(categories).extracting(row -> row.get("categoryCode")).doesNotContain("REPAIR");
            assertThatThrownBy(() -> approvals.createTemplate(new TemplateInput(null, null, "EXPENSE", "REPAIR",
                    List.of(2L), List.of(new FlowNodeInput("财务复核", 2L, null, true))), admin))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("费用大类不合法");
        } finally {
            jdbc.update("UPDATE expense_category SET enabled=1 WHERE category_code='REPAIR'");
        }
    }

    @Test
    void roleStepIsSharedButOnlyOneAuthorizedUserCanCompleteIt() {
        long templateId = historicalGeneral(1, null);
        var current = approvals.create(new Application(templateId,
                null, "group-approval", null, Map.of("description", "请示"), null, null), admin);
        long id = approvalId(current);
        assertThat(approvals.list("TODO", 1, 20, approver).total()).isEqualTo(1);
        assertThat(approvals.list("TODO", 1, 20, secondApprover).total()).isEqualTo(1);
        current = approvals.decide(id, "APPROVE", new Action(null, null, version(current), null), secondApprover);
        assertThat(approvals.list("TODO", 1, 20, approver).total()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM approval_action WHERE operator_id=104", Long.class)).isEqualTo(1);
        var completed = current;
        assertThatThrownBy(() -> approvals.decide(id, "APPROVE",
                new Action(null, null, version(completed), null), approver)).isInstanceOf(BusinessException.class);
    }

    @Test
    void namedApproverDoesNotFallBackWhenDisabledOrMoved() {
        long templateId = template("GENERAL", 1);
        var current = approvals.create(new Application(templateId, null, "named-approval", null,
                Map.of("description", "请示"), null, null), admin);
        long id = approvalId(current);
        assertThat(approvals.list("TODO", 1, 20, secondApprover).total()).isZero();
        jdbc.update("UPDATE sys_user SET status=0 WHERE id=101");
        var pending = current;
        assertThatThrownBy(() -> approvals.decide(id, "APPROVE",
                new Action(null, null, version(pending), null), approver))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThat(((Map<?,?>) approvals.detail(id, admin).get("approval")).get("status")).isEqualTo("PENDING");
        jdbc.update("UPDATE sys_user SET status=1,role_id=1 WHERE id=101");
        assertThatThrownBy(() -> approvals.decide(id, "APPROVE",
                new Action(null, null, version(pending), null), approver))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    void roleWithoutApprovalPermissionCannotBeConfiguredAsAProcessingStep() {
        jdbc.update("INSERT INTO sys_role(id,role_code,role_name,status) VALUES(30,'VIEW_ONLY','只读角色',1)");
        assertThatThrownBy(() -> approvals.createTemplate(new TemplateInput("INVALID" + System.nanoTime(),
                "无权处理的流程", "EXPENSE", "REPAIR", List.of(2L),
                List.of(new FlowNodeInput("无法处理", 30L, null, false))), admin))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("缺少审批通过权限");
    }

    @Test
    void applicantWithdrawsPendingApprovalAndReappliesWithCurrentTemplate() {
        long templateId = template("EXPENSE", 1);
        var first = create(templateId, "DAILY", "withdraw-first", List.of(item(20, BigDecimal.ONE)));
        long id = approvalId(first);
        var withdrawn = approvals.withdraw(id, internal);
        assertThat(((Map<?,?>) withdrawn.get("approval")).get("status")).isEqualTo("WITHDRAWN");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM approval_task WHERE instance_id=? AND status='PENDING'", Long.class, id)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM approval_action WHERE action_type='WITHDRAW'", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM expense_entry", Long.class)).isZero();
        assertThatThrownBy(() -> approvals.decide(id, "APPROVE",
                new Action(null, null, version(withdrawn), null), approver)).isInstanceOf(BusinessException.class);
        var replacement = create(templateId, "DAILY", "withdraw-reapply", List.of(item(20, BigDecimal.ONE)));
        assertThat(approvalId(replacement)).isNotEqualTo(id);
        assertThatThrownBy(() -> approvals.withdraw(approvalId(replacement), secondApprover))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
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
        long templateId = template("EXPENSE", 2);
        var original = create(templateId, "DAILY", "flow-freeze", List.of(item(20, BigDecimal.ONE)));
        var input = new TemplateInput(jdbc.queryForObject(
                "SELECT stable_code FROM approval_template WHERE id=?", String.class, templateId),
                "新流程名称", "EXPENSE", "REPAIR", List.of(1L, 2L),
                List.of(new FlowNodeInput("新步骤", 2L, 101L, true)));
        assertThatThrownBy(() -> approvals.nextVersion(templateId, input, admin))
                .isInstanceOf(BusinessException.class);
        assertThat(((Map<?, ?>) original.get("flow")).get("version_no")).isEqualTo(1);
    }

    @Test
    void tireApprovalUsesTireHandlerAndNeverPostsOrdinaryExpense() {
        long templateId = template("TIRE_REPLACEMENT", 1);
        tireRequestFact();
        var current = approvals.create(new Application(templateId, null, "tire-1", null,
                null, 61L, null), internal);
        long id = approvalId(current);
        current = decide(id, "APPROVE", current, null);
        assertThat(((Map<?, ?>) current.get("approval")).get("status")).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("SELECT status FROM tire WHERE id=60", String.class)).isEqualTo("CLAIMED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tire_history WHERE tire_id=60", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM expense_entry", Long.class)).isZero();
    }

    @Test
    void tireFlowUsesConfiguredNonAdminRoleAndLegalReturnBeforeSingleEffect() {
        tireReviewRole();
        long templateId = tireTemplate(List.of(
                new FlowNodeInput("换胎复核", 3L, null, true),
                new FlowNodeInput("最终确认", 2L, 101L, true)));
        tireRequestFact();
        var current = approvals.create(new Application(templateId, null, "tire-role-flow", null,
                null, 61L, null), internal);
        long id = approvalId(current);
        long flowId = jdbc.queryForObject("SELECT flow_id FROM approval_instance WHERE id=?", Long.class, id);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM approval_flow_node WHERE flow_id=?", Long.class, flowId))
                .isEqualTo(2);
        assertThat(approvals.list("TODO", 1, 20, tireReviewer(105)).total()).isEqualTo(1);
        assertThat(approvals.list("TODO", 1, 20, tireReviewer(106)).total()).isEqualTo(1);
        assertThat(approvals.list("TODO", 1, 20, approver).total()).isZero();
        assertThat(attachments.canViewSubmissionEvidence(1, 105, false, true)).isTrue();
        assertThat(attachments.canViewSubmissionEvidence(1, 101, false, true)).isFalse();
        AuthenticatedUser adminWithApprovalPermission = new AuthenticatedUser(100L, "13900000100", 2L,
                "ADMIN", "管理员", grants("BUTTON_APPROVALS_TODO_APPROVE", "BUTTON_APPROVALS_TODO_RETURN"));
        var firstPending = current;
        assertThatThrownBy(() -> approvals.decide(id, "APPROVE",
                new Action(null, null, version(firstPending), null), adminWithApprovalPermission))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        current = approvals.decide(id, "APPROVE", new Action(null, null, version(current), null), tireReviewer(105));
        assertThat(approvals.list("TODO", 1, 20, tireReviewer(106)).total()).isZero();
        assertThat(approvals.list("TODO", 1, 20, approver).total()).isEqualTo(1);
        long firstNode = jdbc.queryForObject("SELECT id FROM approval_flow_node WHERE flow_id=? AND node_order=1",
                Long.class, flowId);
        current = approvals.decide(id, "RETURN_STEP", new Action("请复核", firstNode, version(current), null), approver);
        assertThat(approvals.list("TODO", 1, 20, tireReviewer(106)).total()).isEqualTo(1);
        current = approvals.decide(id, "APPROVE", new Action(null, null, version(current), null), tireReviewer(106));
        current = approvals.decide(id, "APPROVE", new Action(null, null, version(current), null), approver);
        assertThat(((Map<?, ?>) current.get("approval")).get("status")).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tire_history WHERE tire_id=60", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM expense_entry", Long.class)).isZero();
        assertThat(jdbc.queryForList("SELECT operator_id FROM approval_action WHERE task_id IN "
                + "(SELECT id FROM approval_task WHERE instance_id=?) ORDER BY id", id))
                .extracting(row -> ((Number) row.get("operator_id")).longValue())
                .containsExactly(105L, 101L, 106L, 101L);
    }

    @Test
    void namedTireApproverDoesNotFallBackAndMissingPermissionsRejectProcessing() {
        tireReviewRole();
        long templateId = tireTemplate(List.of(new FlowNodeInput("指定复核", 3L, 105L, true)));
        tireRequestFact();
        var current = approvals.create(new Application(templateId, null, "tire-named", null,
                null, 61L, null), internal);
        long id = approvalId(current);
        assertThat(approvals.list("TODO", 1, 20, tireReviewer(106)).total()).isZero();
        assertThat(attachments.canViewSubmissionEvidence(1, 106, false, true)).isFalse();
        var namedPending = current;
        assertThatThrownBy(() -> approvals.decide(id, "APPROVE",
                new Action(null, null, version(namedPending), null), tireReviewer(106)))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        AuthenticatedUser noApprovalPermission = new AuthenticatedUser(105L, "13900000105", 3L,
                "TIRE_REVIEW", "换胎复核", grants("PAGE_MINI_TIRE_REQUEST"));
        assertThatThrownBy(() -> approvals.list("TODO", 1, 20, noApprovalPermission))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(() -> approvals.decide(id, "APPROVE",
                new Action(null, null, version(namedPending), null), noApprovalPermission))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        jdbc.update("UPDATE sys_user SET status=0 WHERE id=105");
        assertThat(approvals.list("TODO", 1, 20, tireReviewer(105)).total()).isZero();
        var pending = current;
        assertThatThrownBy(() -> approvals.decide(id, "APPROVE",
                new Action(null, null, version(pending), null), tireReviewer(105)))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThat(approvals.list("TODO", 1, 20, tireReviewer(106)).total()).isZero();
        jdbc.update("UPDATE sys_user SET status=1 WHERE id=105");
        jdbc.update("DELETE FROM sys_role_permission WHERE role_id=3 AND permission_id="
                + "(SELECT id FROM sys_permission WHERE permission_code='BUTTON_APPROVALS_TODO_APPROVE')");
        AuthenticatedUser returnOnly = new AuthenticatedUser(105L, "13900000105", 3L,
                "TIRE_REVIEW", "换胎复核", grants("BUTTON_APPROVALS_TODO_RETURN"));
        assertThat(approvals.list("TODO", 1, 20, returnOnly).total()).isEqualTo(1);
        assertThat(((Map<?, ?>) approvals.detail(id, returnOnly)).get("canProcess")).isEqualTo(false);
        assertThat(attachments.canViewSubmissionEvidence(1, 105, false, true)).isTrue();
        jdbc.update("DELETE FROM sys_role_permission WHERE role_id=3 AND permission_id="
                + "(SELECT id FROM sys_permission WHERE permission_code='BUTTON_APPROVALS_TODO_RETURN')");
        AuthenticatedUser revoked = new AuthenticatedUser(105L, "13900000105", 3L,
                "TIRE_REVIEW", "换胎复核", grants("PAGE_APPROVALS_TODO"));
        assertThatThrownBy(() -> approvals.list("TODO", 1, 20, revoked))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThat(attachments.canViewSubmissionEvidence(1, 105, false, false)).isFalse();
        assertThatThrownBy(() -> approvals.decide(id, "APPROVE",
                new Action(null, null, version(pending), null), revoked))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(() -> approvals.nextVersion(templateId,
                new TemplateInput(null, null, "TIRE_REPLACEMENT", null, List.of(1L),
                        List.of(new FlowNodeInput("无审批权限", 3L, null, true))), admin))
                .isInstanceOf(BusinessException.class).hasMessageContaining("审批通过权限");
    }

    @Test
    void tireInstanceKeepsFrozenRoleWhenNewFlowVersionIsPublished() {
        tireReviewRole();
        long templateId = tireTemplate(List.of(new FlowNodeInput("原角色", 3L, null, true)));
        long originalFlow = jdbc.queryForObject("SELECT id FROM approval_flow WHERE template_id=? AND version_no=1",
                Long.class, templateId);
        Map<String, Object> draft = approvals.nextVersion(templateId,
                new TemplateInput(null, null, "TIRE_REPLACEMENT", null, List.of(1L),
                        List.of(new FlowNodeInput("新版角色", 2L, 101L, true))), admin);
        tireRequestFact();
        var current = approvals.create(new Application(templateId, null, "tire-frozen", null,
                null, 61L, null), internal);
        long id = approvalId(current);
        approvals.publish(((Number) draft.get("id")).longValue(), admin);
        assertThat(jdbc.queryForObject("SELECT flow_id FROM approval_instance WHERE id=?", Long.class, id))
                .isEqualTo(originalFlow);
        assertThat(approvals.list("TODO", 1, 20, tireReviewer(105)).total()).isEqualTo(1);
        assertThat(approvals.list("TODO", 1, 20, approver).total()).isZero();
        current = approvals.decide(id, "APPROVE", new Action(null, null, version(current), null), tireReviewer(105));
        assertThat(((Map<?, ?>) current.get("approval")).get("status")).isEqualTo("APPROVED");
    }

    @Test
    void returnedTireRequestCanStartANewApprovalWithoutReusingOldActions() {
        tireReviewRole();
        long templateId = tireTemplate(List.of(new FlowNodeInput("换胎复核", 3L, null, true)));
        tireRequestFact();
        var original = approvals.create(new Application(templateId, null, "tire-original", null,
                null, 61L, null), internal);
        long oldId = approvalId(original);
        original = approvals.decide(oldId, "RETURN_APPLICANT",
                new Action("补充照片", null, version(original), null), tireReviewer(105));
        assertThat(((Map<?, ?>) original.get("approval")).get("status"))
                .isEqualTo("RETURNED_TO_APPLICANT");
        assertThat(jdbc.queryForObject("SELECT status FROM tire_request WHERE id=61", String.class))
                .isEqualTo("RETURNED_TO_APPLICANT");
        jdbc.update("""
                INSERT INTO file_attachment(id,owner_type,owner_id,purpose,storage_key,original_filename,
                    content_type,file_size,file_hash,uploaded_by)
                VALUES(2,'TIRE_OCR',0,'TIRE_OCR','test/tire-again.png','tire-again.png','image/png',1,'test-2',102)
                """);
        jdbc.update("INSERT INTO ocr_record(id,attachment_id,raw_result_json,ocr_status,confirmed_text) "
                + "VALUES(2,2,'{}','SUCCESS','胎-V3D-60')");
        jdbc.update("INSERT INTO tire_request(id,applicant_driver_id,vehicle_id,confirmed_tire_no,"
                + "ocr_record_id,photo_attachment_id,tire_id,status) "
                + "VALUES(62,10,20,'胎-V3D-60',2,2,60,'DRAFT')");
        var replacement = approvals.create(new Application(templateId, null, "tire-reapplied", null,
                null, 62L, null), internal);
        long newId = approvalId(replacement);
        assertThat(newId).isNotEqualTo(oldId);
        replacement = approvals.decide(newId, "APPROVE",
                new Action(null, null, version(replacement), null), tireReviewer(106));
        assertThat(((Map<?, ?>) replacement.get("approval")).get("status")).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tire_history WHERE tire_id=60", Long.class)).isEqualTo(1);
        var returned = original;
        assertThatThrownBy(() -> approvals.decide(oldId, "APPROVE",
                new Action(null, null, version(returned), null), tireReviewer(105)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void twoTireRoleMembersCannotBothCompleteOneNode() throws Exception {
        tireReviewRole();
        long templateId = tireTemplate(List.of(new FlowNodeInput("共同待办", 3L, null, true)));
        tireRequestFact();
        var current = approvals.create(new Application(templateId, null, "tire-race", null,
                null, 61L, null), internal);
        long id = approvalId(current);
        int expected = version(current);
        CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> attemptTireDecision(id, expected, tireReviewer(105), ready, start, success));
            var second = pool.submit(() -> attemptTireDecision(id, expected, tireReviewer(106), ready, start, success));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        }
        assertThat(success.get()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM approval_action WHERE task_id IN "
                + "(SELECT id FROM approval_task WHERE instance_id=?)", Long.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tire_history WHERE tire_id=60", Long.class)).isEqualTo(1);
    }

    private void attemptTireDecision(long id, int expected, AuthenticatedUser user, CountDownLatch ready,
                                     CountDownLatch start, AtomicInteger success) {
        ready.countDown();
        try {
            if (!start.await(5, TimeUnit.SECONDS)) return;
            approvals.decide(id, "APPROVE", new Action(null, null, expected, null), user);
            success.incrementAndGet();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException ignored) {
            // The second contender must fail after the instance is completed or its version changes.
        }
    }
}
