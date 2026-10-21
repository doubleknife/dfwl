package com.dfwl.fleet.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.dfwl.fleet.security.TokenAuthenticationService;
import com.dfwl.fleet.tire.v3.TireV3Controller;
import com.dfwl.fleet.tire.v3.TireV3Service;
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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import static org.mockito.Mockito.when;
import java.util.Optional;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:v3-approval;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
        "spring.datasource.password=", "spring.flyway.enabled=false", "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-auth-test.sql"
})
@AutoConfigureMockMvc
class V3ApprovalWorkflowTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired ApprovalWorkflowService approvals;
    @Autowired DailyExpenseWorkbook workbook;
    @Autowired FileAttachmentRepository attachments;
    @Autowired TireV3Service tires;
    @Autowired ApplicationContext applicationContext;
    @Autowired MockMvc mvc;
    @MockBean TokenAuthenticationService tokenAuthenticationService;

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
        for (String table : List.of("in_app_notification","approval_submission_attachment","tire_request","ocr_record","file_attachment","tire_history","tire","expense_entry","approval_expense_item",
                "approval_action","approval_task","approval_submission_version","approval_instance",
                "approval_flow_initiator_role","approval_flow_node","approval_flow","approval_template",
                "audit_log","transport_participant","transport_task","route_template",
                "driver_vehicle_current","vehicle_trailer_current","trailer","vehicle","driver","sys_user","product")) {
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
        jdbc.update("INSERT INTO trailer(id,trailer_no,status) VALUES(30,'贵A123挂',1)");
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
        jdbc.update("INSERT INTO tire_request(id,batch_key,applicant_driver_id,vehicle_id,confirmed_tire_no,"
                + "ocr_record_id,photo_attachment_id,tire_id,status) "
                + "VALUES(61,'test-61',10,20,'胎-V3D-60',1,1,60,'DRAFT')");
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

    private long energyExpenseTemplate(String category) {
        Map<String, Object> draft = approvals.createTemplate(new TemplateInput(null, null,
                "EXPENSE", category, List.of(1L, 2L),
                List.of(new FlowNodeInput("财务复核", 2L, 101L, true))), admin);
        approvals.publish(((Number) draft.get("id")).longValue(), admin);
        return ((Number) draft.get("template_id")).longValue();
    }

    private ExpenseItem taskItem(long vehicle, long taskId) {
        return new ExpenseItem(LocalDate.of(2026, 9, 30), vehicle, 10L, taskId,
                "充能", BigDecimal.ONE, null, null, null, null, null);
    }

    private void taskParticipantOnGasVehicle() {
        jdbc.update("INSERT INTO product(id,name) VALUES(1,'矿石')");
        jdbc.update("""
                INSERT INTO route_template(id,stable_code,name,customer_name,product_id,loading_place,
                    unloading_place,default_unit_price,default_information_fee,default_driver_salary,
                    created_by,updated_by)
                VALUES(1,'ENERGY_TEST','能源测试','客户',1,'装货地','卸货地',1,0,0,100,100)
                """);
        jdbc.update("""
                INSERT INTO transport_task(id,task_no,template_id,template_code_snapshot,template_name_snapshot,
                    customer_name_snapshot,product_name_snapshot,loading_place_snapshot,unloading_place_snapshot,
                    actual_unit_price,actual_information_fee,business_date,status,created_by)
                VALUES(1,'ENERGY_TASK',1,'ENERGY_TEST','能源测试','客户','矿石','装货地','卸货地',1,0,
                    DATE '2026-09-30','PUBLISHED',100)
                """);
        jdbc.update("""
                INSERT INTO transport_participant(task_id,sequence_no,driver_id,driver_type_snapshot,
                    company_vehicle_id,vehicle_plate_snapshot,source_type,status,salary_amount,created_by)
                VALUES(1,1,10,'INTERNAL',21,'云A101','INITIAL','ASSIGNED',0,100)
                """);
    }

    @Test
    @SuppressWarnings("unchecked")
    void taskExpenseApprovalExposesItsLinkedTaskToAuthorizedReviewer() {
        taskParticipantOnGasVehicle();
        jdbc.update("UPDATE transport_task SET current_participant_id=(SELECT id FROM transport_participant WHERE task_id=1) WHERE id=1");
        long templateId = template("EXPENSE", 1);
        long id = approvalId(create(templateId, "TASK", "linked-task-review", List.of(taskItem(21, 1))));
        Map<String, Object> detail = approvals.detail(id, approver);
        Map<String, Object> item = ((List<Map<String, Object>>) detail.get("expenseItems")).get(0);
        assertThat(item).containsEntry("target_task_id", 1L)
                .containsEntry("task_no", "ENERGY_TASK")
                .containsEntry("task_template_name", "能源测试")
                .containsEntry("task_customer_name", "客户")
                .containsEntry("task_product_name", "矿石")
                .containsEntry("task_loading_place", "装货地")
                .containsEntry("task_unloading_place", "卸货地")
                .containsEntry("task_current_driver", "自有司机")
                .containsEntry("task_current_plate", "云A101");
    }

    @Test
    @SuppressWarnings("unchecked")
    void expenseCandidatesAreCompleteBeyondFormerCapsAndKeepScopeFilters() {
        List<Object[]> driverRows = new java.util.ArrayList<>();
        List<Object[]> vehicleRows = new java.util.ArrayList<>();
        for (int index = 0; index < 501; index++) {
            driverRows.add(new Object[] {1000 + index, "候选司机" + index, "188000" + String.format("%05d", index),
                    "TEST-DL-" + index, String.format("110105199001%06d", index)});
            vehicleRows.add(new Object[] {1000 + index, "测A" + index, "TEST-VL-" + index});
        }
        jdbc.batchUpdate("""
                INSERT INTO driver(id,driver_type,name,phone,driving_license_no,id_card_no,status)
                VALUES(?,'INTERNAL',?,?,?,?,1)
                """, driverRows);
        jdbc.batchUpdate("""
                INSERT INTO vehicle(id,plate_no,vehicle_license_no,insurance_complete,energy_type,max_load,
                    load_standard_type,load_standard_percent,status)
                VALUES(?,?,?,1,'ELECTRIC',30,'PERCENT',0.9,1)
                """, vehicleRows);
        jdbc.update("UPDATE driver SET status=0 WHERE id=1500");
        jdbc.update("UPDATE vehicle SET status=0 WHERE id=1500");
        var adminOptions = approvals.expenseOptions(admin);
        assertThat((List<Map<String, Object>>) adminOptions.get("drivers")).hasSize(502)
                .noneMatch(row -> ((Number) row.get("id")).longValue() == 1500L);
        assertThat((List<Map<String, Object>>) adminOptions.get("vehicles")).hasSize(502)
                .noneMatch(row -> ((Number) row.get("id")).longValue() == 1500L);
        var ownOptions = approvals.expenseOptions(internal);
        assertThat((List<Map<String, Object>>) ownOptions.get("drivers")).hasSize(1)
                .first().satisfies(row -> assertThat(((Number) row.get("id")).longValue()).isEqualTo(10L));

        taskParticipantOnGasVehicle();
        List<Object[]> taskRows = new java.util.ArrayList<>();
        List<Object[]> participantRows = new java.util.ArrayList<>();
        for (int index = 0; index < 101; index++) {
            long taskId = 1000L + index;
            taskRows.add(new Object[] {taskId, "CANDIDATE_" + index, index == 100 ? "CANCELLED" : "PUBLISHED"});
            participantRows.add(new Object[] {taskId});
        }
        jdbc.batchUpdate("""
                INSERT INTO transport_task(id,task_no,template_id,template_code_snapshot,template_name_snapshot,
                    customer_name_snapshot,product_name_snapshot,loading_place_snapshot,unloading_place_snapshot,
                    actual_unit_price,actual_information_fee,business_date,status,created_by)
                VALUES(?,?,1,'ENERGY_TEST','能源测试','客户','矿石','装货地','卸货地',1,0,
                    DATE '2026-09-30',?,100)
                """, taskRows);
        jdbc.batchUpdate("""
                INSERT INTO transport_participant(task_id,sequence_no,driver_id,driver_type_snapshot,
                    company_vehicle_id,vehicle_plate_snapshot,source_type,status,salary_amount,created_by)
                VALUES(?,1,10,'INTERNAL',21,'云A101','INITIAL','ASSIGNED',0,100)
                """, participantRows);
        var candidates = approvals.taskCandidates(10, internal);
        assertThat(candidates).hasSize(101)
                .anySatisfy(row -> assertThat(((Number) row.get("taskId")).longValue()).isEqualTo(1L))
                .noneMatch(row -> ((Number) row.get("taskId")).longValue() == 1100L);
        assertThat(candidates).allSatisfy(row -> {
            assertThat(row.get("templateName")).isEqualTo("能源测试");
            assertThat(row.get("loadingPlace")).isEqualTo("装货地");
            assertThat(row.get("unloadingPlace")).isEqualTo("卸货地");
        });
        assertThat(approvals.taskCandidates(10, admin)).hasSize(101);
        assertThatThrownBy(() -> approvals.taskCandidates(11, internal))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    void energyExpenseUsesSelectedPlateForDailyAndParticipantVehicleForTask() {
        jdbc.update("UPDATE vehicle SET energy_type='GAS' WHERE id=21");
        taskParticipantOnGasVehicle();
        var options = approvals.expenseOptions(internal);
        assertThat((List<Map<String, Object>>) options.get("vehicles"))
                .anySatisfy(vehicle -> {
                    assertThat(((Number) vehicle.get("id")).longValue()).isEqualTo(21L);
                    assertThat(vehicle.get("energyType")).isEqualTo("GAS");
                });
        long gasTemplate = 0;
        for (String category : List.of("ELECTRIC", "TEMP_ELECTRIC", "GAS", "TEMP_GAS")) {
            long templateId = energyExpenseTemplate(category);
            if ("GAS".equals(category)) gasTemplate = templateId;
            boolean electric = category.contains("ELECTRIC");
            long matchingVehicle = electric ? 20L : 21L;
            long wrongVehicle = electric ? 21L : 20L;
            String expected = electric ? "只能选择电车" : "只能选择气车";
            create(templateId, "DAILY", "energy-daily-" + category, List.of(item(matchingVehicle, BigDecimal.ONE)));
            assertThatThrownBy(() -> create(templateId, "DAILY", "energy-wrong-" + category,
                    List.of(item(wrongVehicle, BigDecimal.ONE))))
                    .isInstanceOf(BusinessException.class).hasMessageContaining(expected);
            assertThatThrownBy(() -> create(templateId, "DAILY", "energy-batch-" + category,
                    List.of(item(matchingVehicle, BigDecimal.ONE), item(wrongVehicle, BigDecimal.ONE))))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("第 2 条费用");
            if (electric) {
                assertThatThrownBy(() -> create(templateId, "TASK", "energy-task-wrong-" + category,
                        List.of(taskItem(21, 1))))
                        .isInstanceOf(BusinessException.class).hasMessageContaining(expected);
            } else {
                create(templateId, "TASK", "energy-task-" + category, List.of(taskItem(21, 1)));
            }
        }
        var current = create(gasTemplate, "DAILY", "energy-resubmit", List.of(item(21, BigDecimal.ONE)));
        long id = approvalId(current);
        decide(id, "RETURN_APPLICANT", current, null);
        assertThatThrownBy(() -> approvals.resubmit(id,
                new Resubmission(List.of(item(20, BigDecimal.ONE)), null, null), internal))
                .isInstanceOf(BusinessException.class).hasMessageContaining("只能选择气车");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM approval_submission_version WHERE instance_id=?",
                Long.class, id)).isEqualTo(1);
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
    void baselineExpenseCategoriesExcludeCarryingAndCarWashButKeepToll() {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> categories = (List<Map<String, Object>>) approvals.flowOptions(admin).get("expenseTypes");
        assertThat(categories).extracting(row -> row.get("categoryCode"))
                .contains("TOLL").doesNotContain("CARRYING", "CAR_WASH");
        assertThat(approvals.expenseOptions(admin).get("expenseTypes"))
                .asList().contains("TOLL").doesNotContain("CARRYING", "CAR_WASH");
        Map<String, Object> toll = approvals.createTemplate(new TemplateInput(null, null, "EXPENSE", "TOLL",
                List.of(2L), List.of(new FlowNodeInput("财务复核", 2L, null, true))), admin);
        assertThat(toll.get("name")).isEqualTo("过路费申请");
        for (String category : List.of("CARRYING", "CAR_WASH")) {
            assertThatThrownBy(() -> approvals.createTemplate(new TemplateInput(null, null, "EXPENSE", category,
                    List.of(2L), List.of(new FlowNodeInput("财务复核", 2L, null, true))), admin))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("费用大类不合法");
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
                "维修", amount, "P-1", "F-1", "测试", "维修站", "贵A123挂");
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

    @Test
    void approvalDetailShowsApplicantIdentityStartTimeAndCurrentRole() {
        long templateId = template("EXPENSE", 1);
        long id = approvalId(create(templateId, "DAILY", "applicant-facts", List.of(item(20, BigDecimal.ONE))));
        Map<?, ?> approval = (Map<?, ?>) approvals.detail(id, admin).get("approval");
        assertThat(approval.get("applicant_phone")).isEqualTo("13900000102");
        assertThat(approval.get("applicant_name")).isEqualTo("自有司机");
        assertThat(approval.get("applicant_role_name")).isEqualTo("司机");
        assertThat(approval.get("created_at")).isNotNull();
    }
    private Map<String,Object> decide(long id, String kind, Map<String,Object> detail, Long target) {
        return approvals.decide(id, kind, new Action("审批意见", target, version(detail), null), approver);
    }

    @Test
    void approvalNotificationsFollowTaskAssignmentAndApplicantReturn() {
        long template = template("EXPENSE", 2);
        var current = create(template, "DAILY", "notification-flow", List.of(item(20, BigDecimal.ONE)));
        long id = approvalId(current);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM in_app_notification WHERE user_id=101 AND business_id=? AND type='APPROVAL_TODO'", Long.class, id)).isEqualTo(1);
        create(template, "DAILY", "notification-flow", List.of(item(20, BigDecimal.ONE)));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM in_app_notification WHERE user_id=101 AND business_id=?", Long.class, id)).isEqualTo(1);
        current = decide(id, "APPROVE", current, null);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM in_app_notification WHERE user_id=101 AND business_id=? AND type='APPROVAL_TODO'", Long.class, id)).isEqualTo(2);
        decide(id, "RETURN_APPLICANT", current, null);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM in_app_notification WHERE user_id=102 AND business_id=? AND type='APPROVAL_RETURN'", Long.class, id)).isEqualTo(1);
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
        assertThat(jdbc.queryForList("SELECT trailer_no FROM expense_entry WHERE approval_instance_id=? ORDER BY approval_item_no", id))
                .extracting(row -> row.get("trailer_no")).containsExactly("贵A123挂", "贵A123挂");
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
                item(20, new BigDecimal("10.00")),
                new ExpenseItem(LocalDate.of(2026, 9, 30), 21L, 10L, null,
                        "维修", new BigDecimal("3.00"), null, null, null, null, null));
        assertThatThrownBy(() -> create(template, "DAILY", "bad-final-row", List.of(
                finalItems.get(0), item(21, BigDecimal.ZERO)))).isInstanceOf(BusinessException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM approval_instance", Long.class)).isZero();
        assertThatThrownBy(() -> create(template, "DAILY", "unknown-trailer", List.of(
                new ExpenseItem(LocalDate.of(2026, 9, 30), 20L, 10L, null,
                        "维修", new BigDecimal("8.00"), null, null, null, null, "不存在的车挂"))))
                .isInstanceOf(BusinessException.class).hasMessageContaining("车挂号不存在");
        var current = create(template, "DAILY", "edited-final-rows", finalItems);
        long id = approvalId(current);
        assertThat(jdbc.queryForList("SELECT amount FROM approval_expense_item WHERE instance_id=? ORDER BY item_no", id))
                .extracting(row -> (BigDecimal) row.get("amount"))
                .containsExactly(new BigDecimal("10.00"), new BigDecimal("3.00"));
        assertThat(jdbc.queryForList("SELECT trailer_no FROM approval_expense_item WHERE instance_id=? ORDER BY item_no", id))
                .extracting(row -> row.get("trailer_no")).containsExactly("贵A123挂", null);
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
    void multipleImagesStayWithTheirSubmissionVersionAndActualApprovalAction() {
        long template = template("EXPENSE", 1);
        for (long id : List.of(201L, 202L, 203L)) temporaryApprovalImage(id, 102L, "APPROVAL_APPLICATION");
        for (long id : List.of(301L, 302L)) temporaryApprovalImage(id, 101L, "APPROVAL_ACTION");
        var current = approvals.create(new Application(template, "DAILY", "images-version-1",
                List.of(item(20, new BigDecimal("5.00"))), null, null, List.of(201L, 202L)), internal);
        long id = approvalId(current);
        assertThat(((List<?>) current.get("attachments"))).hasSize(2);
        current = approvals.decide(id, "RETURN_APPLICANT",
                new Action("请补充", null, version(current), List.of(301L)), approver);
        assertThat(((List<?>) current.get("actionAttachments"))).hasSize(1);
        current = approvals.resubmit(id, new Resubmission(
                List.of(item(20, new BigDecimal("6.00"))), null, List.of(203L)), internal);
        assertThat(((List<?>) current.get("submissions"))).hasSize(2);
        assertThat(((List<?>) current.get("attachments"))).hasSize(3);
        current = approvals.decide(id, "APPROVE",
                new Action("同意", null, version(current), List.of(302L)), approver);
        assertThat(((List<?>) current.get("actionAttachments"))).hasSize(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM expense_entry WHERE approval_instance_id=?", Long.class, id))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT a.submission_version_id) "
                + "FROM approval_submission_attachment a JOIN approval_submission_version s "
                + "ON s.id=a.submission_version_id WHERE s.instance_id=?", Long.class, id)).isEqualTo(2);
        assertThatThrownBy(() -> approvals.create(new Application(template, "DAILY", "images-reuse",
                List.of(item(20, BigDecimal.ONE)), null, null, List.of(201L)), internal))
                .isInstanceOf(BusinessException.class);
    }

    private void temporaryApprovalImage(long id, long userId, String purpose) {
        jdbc.update("INSERT INTO file_attachment(id,owner_type,owner_id,purpose,storage_key,original_filename,"
                        + "content_type,file_size,file_hash,uploaded_by) VALUES(?,?,?,?,?,?,?,?,?,?)",
                id, "APPROVAL_UPLOAD", userId, purpose, "test/approval-" + id + ".png",
                "approval-" + id + ".png", "image/png", 1, "test-" + id, userId);
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
        assertThat(approvals.expenseTrailerChecks(List.of("贵A123挂", "不存在的车挂"), internal))
                .containsEntry("贵A123挂", true).containsEntry("不存在的车挂", false);
        jdbc.update("UPDATE trailer SET status=0 WHERE id=30");
        assertThat(approvals.expenseTrailerChecks(List.of("贵A123挂"), internal)).containsEntry("贵A123挂", true);
        jdbc.update("UPDATE trailer SET status=1 WHERE id=30");
        byte[] template = workbook.template();
        assertThat(template).isNotEmpty();
        try (Workbook book = new XSSFWorkbook(new java.io.ByteArrayInputStream(template));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var row = book.getSheetAt(0).createRow(1);
            for (int i = 0; i < 4; i++)
                row.createCell(i).setCellValue(List.of("2026-09-30","云A100","贵A123挂","13900000102").get(i));
            row.createCell(4).setCellValue("8.00");
            book.write(out);
            var file = new MockMultipartFile("file", "daily.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
            assertThat(workbook.preview(file).errorCount()).isZero();
            assertThat(workbook.preview(file).rows().get(0).trailerNo()).isEqualTo("贵A123挂");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM approval_instance", Long.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM expense_entry", Long.class)).isZero();
            row.getCell(2).setCellValue("不存在的车挂");
            out.reset(); book.write(out);
            var badTrailer = new MockMultipartFile("file", "bad-trailer.xlsx", file.getContentType(), out.toByteArray());
            assertThat(workbook.preview(badTrailer).rows().get(0).errors()).contains("车挂号不存在");
            book.getSheetAt(0).getRow(0).getCell(0).setCellValue("任意表头");
            out.reset(); book.write(out);
            var invalid = new MockMultipartFile("file", "bad.xlsx", file.getContentType(), out.toByteArray());
            assertThatThrownBy(() -> workbook.preview(invalid)).isInstanceOf(BusinessException.class);
        }
    }

    @Test
    void dailyWorkbookRequiresCurrentTenColumnTemplate() throws Exception {
        try (Workbook book = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var header = book.createSheet("日常费用").createRow(0);
            var names = List.of("业务日期", "公司车牌", "司机手机号", "金额", "明细", "票据号", "发票号", "备注", "站点名称");
            for (int i = 0; i < names.size(); i++) header.createCell(i).setCellValue(names.get(i));
            book.write(out);
            var file = new MockMultipartFile("file", "old.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
            assertThatThrownBy(() -> workbook.preview(file)).isInstanceOf(BusinessException.class);
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
        var claimed = tires.inventory(1, 20, "胎-V3D-60", "CLAIMED").records().getFirst();
        assertThat(claimed).containsEntry("applicantName", "自有司机")
                .containsEntry("currentVehiclePlate", "云A100");
        assertThat(tires.detail(60)).containsEntry("applicantName", "自有司机")
                .containsEntry("currentVehiclePlate", "云A100");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tire_history WHERE tire_id=60", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM expense_entry", Long.class)).isZero();
    }

    @Test
    void oneTireApprovalClaimsEveryPhotoAndTireInBatch() {
        long templateId = template("TIRE_REPLACEMENT", 1);
        tireRequestFact();
        jdbc.update("INSERT INTO tire(id,tire_no,material_description,arrived_at,status,created_by) "
                + "VALUES(62,'胎-V3D-62','轮胎',CURRENT_TIMESTAMP,'AVAILABLE',100)");
        jdbc.update("""
                INSERT INTO file_attachment(id,owner_type,owner_id,purpose,storage_key,original_filename,
                    content_type,file_size,file_hash,uploaded_by)
                VALUES(2,'TIRE_OCR',0,'TIRE_OCR','test/tire-2.png','tire-2.png','image/png',1,'test-2',102)
                """);
        jdbc.update("INSERT INTO ocr_record(id,attachment_id,raw_result_json,ocr_status,confirmed_text) "
                + "VALUES(2,2,'{}','SUCCESS','胎-V3D-62')");
        jdbc.update("INSERT INTO tire_request(id,batch_key,applicant_driver_id,vehicle_id,confirmed_tire_no,"
                + "ocr_record_id,photo_attachment_id,tire_id,status) "
                + "VALUES(62,'test-61',10,20,'胎-V3D-62',2,2,62,'DRAFT')");
        var current = approvals.create(new Application(templateId, null, "tire-batch", null,
                null, 61L, null), internal);
        long id = approvalId(current);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tire_request WHERE approval_instance_id=?", Long.class, id))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM approval_submission_attachment", Long.class))
                .isEqualTo(2);
        assertThat((List<?>) approvals.detail(id, internal).get("tireRequests")).hasSize(2);
        decide(id, "APPROVE", current, null);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tire WHERE id IN (60,62) AND status='CLAIMED'", Long.class))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tire_request WHERE approval_instance_id=? AND status='APPROVED'",
                Long.class, id)).isEqualTo(2);
    }

    @Test
    void tireInventoryValidationReturnsPreciseErrorForEachUploadedPhoto() {
        tireRequestFact();
        jdbc.update("INSERT INTO driver_vehicle_current(driver_id,vehicle_id,bound_at,bound_by) "
                + "VALUES(10,20,CURRENT_TIMESTAMP,100)");
        jdbc.update("""
                INSERT INTO file_attachment(id,owner_type,owner_id,purpose,storage_key,original_filename,
                    content_type,file_size,file_hash,uploaded_by)
                VALUES(2,'TIRE_OCR',0,'TIRE_OCR','test/missing.png','missing.png','image/png',1,'missing',102)
                """);
        jdbc.update("INSERT INTO ocr_record(id,attachment_id,raw_result_json,ocr_status) "
                + "VALUES(2,2,'{}','SUCCESS')");
        var result = tires.validateRequest(new TireV3Service.TireRequestInput(null, List.of(
                new TireV3Service.TireRequestItem(1L, "胎-V3D-60"),
                new TireV3Service.TireRequestItem(2L, "胎-不存在")), null), internal);
        assertThat(result.get("allValid")).isEqualTo(false);
        var rows = (List<Map<String, Object>>) result.get("rows");
        assertThat(rows.getFirst().get("valid")).isEqualTo(true);
        assertThat(rows.get(1).get("message")).isEqualTo("胎号不存在");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM approval_instance", Long.class)).isZero();
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
        assertThat(approvals.list("TODO", 1, 20, noApprovalPermission).total()).isZero();
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
        assertThat(approvals.list("TODO", 1, 20, revoked).total()).isZero();
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
    void returnedTireRequestReentersSameApprovalWithNewSubmissionAndOriginalActions() {
        tireReviewRole();
        long templateId = tireTemplate(List.of(new FlowNodeInput("换胎复核", 3L, null, true)));
        tireRequestFact();
        jdbc.update("INSERT INTO driver_vehicle_current(driver_id,vehicle_id,bound_at,bound_by) "
                + "VALUES(10,20,CURRENT_TIMESTAMP,100)");
        var original = approvals.create(new Application(templateId, null, "tire-original", null,
                null, 61L, null), internal);
        long oldId = approvalId(original);
        original = approvals.decide(oldId, "RETURN_APPLICANT",
                new Action("补充照片", null, version(original), null), tireReviewer(105));
        assertThat(((Map<?, ?>) original.get("approval")).get("status"))
                .isEqualTo("RETURNED_TO_APPLICANT");
        assertThat(jdbc.queryForObject("SELECT status FROM tire_request WHERE id=61", String.class))
                .isEqualTo("RETURNED_TO_APPLICANT");
        assertThat(approvals.list("TODO", 1, 20, internal).total()).isEqualTo(1);
        assertThat(approvals.list("MINE", 1, 20, internal).total()).isEqualTo(1);
        assertThat(approvals.list("PROCESSED", 1, 20, internal).total()).isZero();
        assertThat(approvals.list("PROCESSED", 1, 20, tireReviewer(105)).total()).isEqualTo(1);
        jdbc.update("""
                INSERT INTO file_attachment(id,owner_type,owner_id,purpose,storage_key,original_filename,
                    content_type,file_size,file_hash,uploaded_by)
                VALUES(2,'TIRE_OCR',0,'TIRE_OCR','test/tire-again.png','tire-again.png','image/png',1,'test-2',102)
                """);
        jdbc.update("INSERT INTO ocr_record(id,attachment_id,raw_result_json,ocr_status,confirmed_text) "
                + "VALUES(2,2,'{}','SUCCESS','胎-V3D-60')");
        var replacement = tires.resubmitRequest(oldId, new TireV3Service.TireResubmission(
                List.of(new TireV3Service.TireRequestItem(2L, "胎-V3D-60")), version(original)), internal);
        assertThat(approvalId(replacement)).isEqualTo(oldId);
        assertThat(((List<?>) replacement.get("submissions"))).hasSize(2);
        assertThat(((List<?>) replacement.get("actions"))).hasSize(2);
        assertThat(((List<?>) replacement.get("tireRequests"))).hasSize(1);
        assertThat(approvals.list("TODO", 1, 20, internal).total()).isZero();
        assertThat(approvals.list("TODO", 1, 20, tireReviewer(105)).total()).isEqualTo(1);
        var actions = (List<Map<String, Object>>) replacement.get("actions");
        assertThat(actions.getFirst()).containsEntry("operator_phone", "13900000105")
                .containsEntry("operator_role", "换胎复核");
        assertThat(actions.getFirst().get("comment")).isEqualTo("补充照片");
        replacement = approvals.decide(oldId, "APPROVE",
                new Action(null, null, version(replacement), null), tireReviewer(106));
        assertThat(((Map<?, ?>) replacement.get("approval")).get("status")).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tire_history WHERE tire_id=60", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM tire_request WHERE id=61", String.class))
                .isEqualTo("RETURNED_TO_APPLICANT");
        var chain = (List<Map<String, Object>>) tires.detail(60).get("approvalChain");
        assertThat(chain).extracting(row -> row.get("eventType"))
                .containsExactly("SUBMIT", "RETURN_APPLICANT", "RESUBMIT", "APPROVE");
        assertThat(chain).extracting(row -> row.get("versionNo"))
                .containsExactly(1, 1, 2, 2);
        assertThat(chain.get(1)).containsEntry("operatorPhone", "13900000105")
                .containsEntry("operatorRole", "换胎复核")
                .containsEntry("nodeName", "换胎复核")
                .containsEntry("comment", "补充照片");
        assertThat(chain.get(2)).containsEntry("operatorPhone", "13900000102")
                .containsEntry("operatorRole", "司机");
        assertThat(chain.get(3)).containsEntry("operatorPhone", "13900000106")
                .containsEntry("operatorRole", "换胎复核");
        assertThat(chain).allSatisfy(row -> assertThat(row.get("operatedAt")).isNotNull());
    }

    @Test
    void approvalApisExposePersonalScopesReturnActorAndTireResubmission() throws Exception {
        tireReviewRole();
        long templateId = tireTemplate(List.of(new FlowNodeInput("换胎复核", 3L, null, true)));
        tireRequestFact();
        jdbc.update("INSERT INTO driver_vehicle_current(driver_id,vehicle_id,bound_at,bound_by) "
                + "VALUES(10,20,CURRENT_TIMESTAMP,100)");
        var current = approvals.create(new Application(templateId, null, "tire-api", null,
                null, 61L, null), internal);
        long id = approvalId(current);
        current = approvals.decide(id, "RETURN_APPLICANT",
                new Action("照片模糊，请重拍", null, version(current), null), tireReviewer(105));
        when(tokenAuthenticationService.authenticate("driver-api")).thenReturn(Optional.of(
                UsernamePasswordAuthenticationToken.authenticated(internal, "driver-api", Set.of())));
        when(tokenAuthenticationService.authenticate("reviewer-api")).thenReturn(Optional.of(
                UsernamePasswordAuthenticationToken.authenticated(tireReviewer(105), "reviewer-api", Set.of())));
        when(tokenAuthenticationService.authenticate("other-api")).thenReturn(Optional.of(
                UsernamePasswordAuthenticationToken.authenticated(outsourced, "other-api", Set.of())));
        var inventoryUser = new AuthenticatedUser(100L, "13900000100", 2L, "ADMIN",
                "管理员", grants("PAGE_TIRES"));
        when(tokenAuthenticationService.authenticate("inventory-api")).thenReturn(Optional.of(
                UsernamePasswordAuthenticationToken.authenticated(inventoryUser, "inventory-api", Set.of())));
        mvc.perform(get("/api/v1/tires/60").header("Authorization", "Bearer inventory-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.approvalChain[0].eventType").value("SUBMIT"))
                .andExpect(jsonPath("$.data.approvalChain[1].eventType").value("RETURN_APPLICANT"))
                .andExpect(jsonPath("$.data.approvalChain[1].operatorPhone").value("13900000105"))
                .andExpect(jsonPath("$.data.approvalChain[1].operatorRole").value("换胎复核"));
        mvc.perform(get("/api/v1/tires/60").header("Authorization", "Bearer driver-api"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/notifications").header("Authorization", "Bearer driver-api"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].type").value("APPROVAL_RETURN"))
                .andExpect(jsonPath("$.data.records[0].businessId").value(id));
        mvc.perform(get("/api/v1/notifications").header("Authorization", "Bearer reviewer-api"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].type").value("APPROVAL_TODO"));
        mvc.perform(get("/api/v1/notifications").header("Authorization", "Bearer other-api"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(get("/api/v1/approvals?scope=MINE&pageNo=1&pageSize=20")
                .header("Authorization", "Bearer driver-api"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
        mvc.perform(get("/api/v1/approvals?scope=TODO&pageNo=1&pageSize=20")
                .header("Authorization", "Bearer driver-api"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].businessSummary").value("云A100 · 胎-V3D-60"));
        mvc.perform(get("/api/v1/approvals?scope=PROCESSED&pageNo=1&pageSize=20")
                .header("Authorization", "Bearer driver-api"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(get("/api/v1/approvals?scope=PROCESSED&pageNo=1&pageSize=20")
                .header("Authorization", "Bearer reviewer-api"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
        mvc.perform(get("/api/v1/approvals/{id}", id).header("Authorization", "Bearer driver-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.actions[0].operator_phone").value("13900000105"))
                .andExpect(jsonPath("$.data.actions[0].operator_role").value("换胎复核"))
                .andExpect(jsonPath("$.data.actions[0].comment").value("照片模糊，请重拍"));
        mvc.perform(get("/api/v1/approvals/{id}", id).header("Authorization", "Bearer other-api"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/tire-requests/approvals/{id}/resubmit", id)
                .header("Authorization", "Bearer driver-api")
                .contentType("application/json")
                .content("{\"items\":[{\"ocrRecordId\":1,\"confirmedTireNo\":\"胎-V3D-60\"}],"
                        + "\"expectedVersion\":" + version(current) + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("第 1 条照片已用于原申请，请重新上传照片后提交"));
        jdbc.update("""
                INSERT INTO file_attachment(id,owner_type,owner_id,purpose,storage_key,original_filename,
                    content_type,file_size,file_hash,uploaded_by)
                VALUES(2,'TIRE_OCR',0,'TIRE_OCR','test/tire-new.png','tire-new.png','image/png',1,'test-new',102)
                """);
        jdbc.update("INSERT INTO ocr_record(id,attachment_id,raw_result_json,ocr_status) "
                + "VALUES(2,2,'{}','SUCCESS')");
        mvc.perform(post("/api/v1/tire-requests/approvals/{id}/resubmit", id)
                .header("Authorization", "Bearer driver-api")
                .contentType("application/json")
                .content("{\"items\":[{\"ocrRecordId\":2,\"confirmedTireNo\":\"胎-V3D-60\"}],"
                        + "\"expectedVersion\":" + version(current) + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.approval.status").value("PENDING"))
                .andExpect(jsonPath("$.data.submissions.length()").value(2));
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
