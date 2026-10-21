package com.dfwl.fleet.approval.v3;

import com.dfwl.fleet.common.api.PageResponse;
import com.dfwl.fleet.attachment.service.AttachmentService;
import com.dfwl.fleet.common.error.BusinessException;
import com.dfwl.fleet.common.error.ErrorCode;
import com.dfwl.fleet.security.AuthenticatedUser;
import com.dfwl.fleet.security.CurrentUserService;
import com.dfwl.fleet.security.CurrentDriverContext;
import com.dfwl.fleet.security.TransactionalAuthorizationGuard;
import com.dfwl.fleet.notification.NotificationService;
import com.dfwl.fleet.system.service.AuditWriteService;
import com.dfwl.fleet.tire.v3.TireApprovalEffect;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The single V3 approval/expense write path. Flow nodes and submissions are immutable facts. */
@Service
public class ApprovalWorkflowService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final CurrentUserService users;
    private final AuditWriteService audit;
    private final AttachmentService attachments;
    private final TireApprovalEffect tireEffect;
    private final TransactionalAuthorizationGuard authorization;
    private final NotificationService notifications;

    public ApprovalWorkflowService(JdbcTemplate jdbc, ObjectMapper mapper, CurrentUserService users,
                                   AuditWriteService audit, AttachmentService attachments,
                                   TireApprovalEffect tireEffect, TransactionalAuthorizationGuard authorization,
                                   NotificationService notifications) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.users = users;
        this.audit = audit;
        this.attachments = attachments;
        this.tireEffect = tireEffect;
        this.authorization = authorization;
        this.notifications = notifications;
    }

    public record FlowNodeInput(String name, Long approverRoleId, Long approverUserId, Boolean allowReturn) {}
    public record TemplateInput(String stableCode, String name, String businessType, String expenseType,
                                List<Long> initiatorRoleIds, List<FlowNodeInput> nodes) {}
    public record ExpenseItem(LocalDate businessDate, Long vehicleId, Long driverId, Long targetTaskId,
                              String description, BigDecimal amount, String receiptNo, String invoiceNo,
                              String remark, String stationName, String trailerNo) {}
    public record Application(Long templateId, String applicationScope, String requestKey,
                              List<ExpenseItem> items, Map<String, Object> generalSnapshot, Long tireRequestId,
                              List<Long> attachmentIds) {}
    public record Action(String comment, Long targetFlowNodeId, Integer expectedVersion,
                         List<Long> attachmentIds) {}
    public record Resubmission(List<ExpenseItem> items, Map<String, Object> generalSnapshot,
                               List<Long> attachmentIds) {}

    public List<Map<String, Object>> templates(AuthenticatedUser user, boolean manage) {
        if (manage) {
            require(user, "PAGE_APPROVALS_FLOWS");
            users.denyAllDrivers(user);
            return jdbc.queryForList("""
                    SELECT t.*, f.id flow_id, f.version_no, f.status flow_status
                    FROM approval_template t LEFT JOIN approval_flow f ON f.template_id=t.id
                    AND f.version_no=(SELECT MAX(x.version_no) FROM approval_flow x WHERE x.template_id=t.id)
                    ORDER BY t.id DESC
                    """);
        }
        requireAny(user, "BUTTON_EXPENSE_APPLY_SUBMIT", "BUTTON_MINI_EXPENSE_APPLY_SUBMIT", "BUTTON_MINI_TIRE_REQUEST_SUBMIT");
        if (isOutsourced(user)) return List.of();
        List<Map<String, Object>> available = jdbc.queryForList("""
                SELECT t.id,t.stable_code,t.name,t.business_type,t.expense_type,
                       f.id flow_id,f.version_no
                FROM approval_template t JOIN approval_flow f ON f.template_id=t.id AND f.status='ACTIVE'
                JOIN approval_flow_initiator_role ir ON ir.flow_id=f.id AND ir.role_id=?
                WHERE t.enabled=1 ORDER BY t.name
                """, user.roleId());
        return available.stream().filter(template -> "TIRE_REPLACEMENT".equals(template.get("business_type"))
                ? has(user, "BUTTON_MINI_TIRE_REQUEST_SUBMIT")
                : has(user, "BUTTON_EXPENSE_APPLY_SUBMIT")).toList();
    }

    @Transactional
    public Map<String, Object> createTemplate(TemplateInput input, AuthenticatedUser user) {
        authorization.require(user.id(), "BUTTON_APPROVALS_FLOWS_MANAGE");
        require(user, "BUTTON_APPROVALS_FLOWS_MANAGE");
        users.denyAllDrivers(user);
        validateTemplate(input);
        if ("GENERAL".equals(input.businessType())) throw invalid("普通审批仅保留历史读取，不能新建模板");
        String name = templateName(input.businessType(), input.expenseType());
        Long duplicate = jdbc.queryForObject("""
                SELECT COUNT(*) FROM approval_template WHERE enabled=1 AND business_type=?
                  AND (expense_type=? OR (expense_type IS NULL AND ? IS NULL))
                """, Long.class, input.businessType(), input.expenseType(), input.expenseType());
        if (duplicate != null && duplicate > 0) throw invalid("该业务类型和费用大类已有模板，请维护现有模板版本");
        String stableCode = "APP_" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
        long templateId;
        try {
            templateId = insert("""
                    INSERT INTO approval_template(stable_code,name,business_type,expense_type)
                    VALUES(?,?,?,?)
                    """, stableCode, name, input.businessType(),
                    "EXPENSE".equals(input.businessType()) ? input.expenseType() : null);
        } catch (org.springframework.dao.DuplicateKeyException ex) {
            throw invalid("该业务类型和费用大类已有模板，请维护现有模板版本");
        }
        long flowId = createVersion(templateId, 1, input.nodes(), input.initiatorRoleIds(), user.id());
        audit.write("APPROVAL", "TEMPLATE", templateId, "CREATE", null,
                Map.of("flowId", flowId, "type", input.businessType()), user.id(), null);
        return flow(flowId);
    }

    @Transactional
    public Map<String, Object> nextVersion(long templateId, TemplateInput input, AuthenticatedUser user) {
        authorization.require(user.id(), "BUTTON_APPROVALS_FLOWS_MANAGE");
        require(user, "BUTTON_APPROVALS_FLOWS_MANAGE");
        users.denyAllDrivers(user);
        Map<String, Object> template = one("SELECT * FROM approval_template WHERE id=? FOR UPDATE", templateId);
        if (template == null) throw new BusinessException(ErrorCode.DATA_001);
        if ("GENERAL".equals(template.get("business_type"))) throw invalid("普通审批模板仅保留历史读取");
        validateTemplate(input);
        if (!Objects.equals(template.get("business_type"), input.businessType())
                || !Objects.equals(template.get("expense_type"), input.expenseType())) {
            throw invalid("模板稳定业务类型和费用大类不能在新版本中改变");
        }
        Long pending = jdbc.queryForObject("""
                SELECT COUNT(*) FROM approval_instance WHERE template_id=? AND status IN ('PENDING','RETURNED_TO_APPLICANT')
                """, Long.class, templateId);
        if (pending != null && pending > 0) throw invalid("存在在途审批，暂不能维护流程");
        Long draft = jdbc.queryForObject(
                "SELECT COUNT(*) FROM approval_flow WHERE template_id=? AND status='DRAFT'", Long.class, templateId);
        if (draft != null && draft > 0) throw invalid("请先发布当前草稿版本");
        Integer next = jdbc.queryForObject("SELECT COALESCE(MAX(version_no),0)+1 FROM approval_flow WHERE template_id=?",
                Integer.class, templateId);
        long flowId = createVersion(templateId, next, input.nodes(), input.initiatorRoleIds(), user.id());
        jdbc.update("UPDATE approval_template SET version=version+1 WHERE id=?", templateId);
        audit.write("APPROVAL", "TEMPLATE", templateId, "NEW_VERSION", null,
                Map.of("flowId", flowId), user.id(), null);
        return flow(flowId);
    }

    private long createVersion(long templateId, int version, List<FlowNodeInput> nodes,
                               List<Long> roles, long actor) {
        long flowId = insert("""
                INSERT INTO approval_flow(template_id,version_no,status,created_by) VALUES(?,?,'DRAFT',?)
                """, templateId, version, actor);
        for (int i = 0; i < nodes.size(); i++) {
            FlowNodeInput node = nodes.get(i);
            jdbc.update("""
                    INSERT INTO approval_flow_node(flow_id,node_order,node_name,approver_role_id,approver_user_id,allow_return)
                    VALUES(?,?,?,?,?,?)
                    """, flowId, i + 1, node.name().trim(), node.approverRoleId(), node.approverUserId(),
                    Boolean.FALSE.equals(node.allowReturn()) ? 0 : 1);
        }
        for (Long role : roles.stream().distinct().toList()) {
            Long exists = jdbc.queryForObject("SELECT COUNT(*) FROM sys_role WHERE id=? AND status=1", Long.class, role);
            if (exists == null || exists == 0) throw invalid("发起角色不存在或已停用");
            jdbc.update("INSERT INTO approval_flow_initiator_role(flow_id,role_id) VALUES(?,?)", flowId, role);
        }
        return flowId;
    }

    @Transactional
    public Map<String, Object> publish(long flowId, AuthenticatedUser user) {
        authorization.require(user.id(), "BUTTON_APPROVALS_FLOWS_MANAGE");
        require(user, "BUTTON_APPROVALS_FLOWS_MANAGE");
        users.denyAllDrivers(user);
        Map<String, Object> version = one("SELECT * FROM approval_flow WHERE id=? FOR UPDATE", flowId);
        if (version == null) throw new BusinessException(ErrorCode.DATA_001);
        if (!"DRAFT".equals(version.get("status"))) throw invalid("仅草稿流程可以发布");
        long templateId = num(version.get("template_id"));
        jdbc.update("UPDATE approval_flow SET status='ARCHIVED' WHERE template_id=? AND status='ACTIVE'", templateId);
        jdbc.update("UPDATE approval_flow SET status='ACTIVE',published_at=NOW(3) WHERE id=?", flowId);
        audit.write("APPROVAL", "FLOW", flowId, "PUBLISH", null, Map.of("templateId", templateId),
                user.id(), null);
        return flow(flowId);
    }

    public Map<String, Object> flow(long flowId) {
        Map<String, Object> row = one("""
                SELECT f.id,f.template_id,f.version_no,f.status,t.name,t.stable_code,t.business_type,t.expense_type
                FROM approval_flow f JOIN approval_template t ON t.id=f.template_id WHERE f.id=?
                """, flowId);
        if (row == null) throw new BusinessException(ErrorCode.DATA_001);
        row.put("nodes", jdbc.queryForList("""
                SELECT n.id flowNodeId,n.node_order,n.node_name,r.role_name approverRoleName,
                       u.phone approverPhone,n.approver_role_id,n.approver_user_id,
                       n.allow_return
                FROM approval_flow_node n JOIN sys_role r ON r.id=n.approver_role_id
                LEFT JOIN sys_user u ON u.id=n.approver_user_id
                WHERE n.flow_id=? ORDER BY n.node_order
                """, flowId));
        row.put("initiatorRoles", jdbc.queryForList("""
                SELECT r.id,r.role_name FROM approval_flow_initiator_role ir
                JOIN sys_role r ON r.id=ir.role_id WHERE ir.flow_id=? ORDER BY r.id
                """, flowId));
        return row;
    }

    private void validateTemplate(TemplateInput input) {
        if (input == null
                || !List.of("EXPENSE", "GENERAL", "TIRE_REPLACEMENT").contains(input.businessType())
                || input.nodes() == null || input.nodes().isEmpty()
                || input.initiatorRoleIds() == null || input.initiatorRoleIds().isEmpty())
            throw invalid("请完整填写业务类型、发起角色和审批步骤");
        if ("EXPENSE".equals(input.businessType())) expenseType(input.expenseType());
        else if (!blank(input.expenseType())) throw invalid("非费用模板不能配置费用大类");
        for (FlowNodeInput node : input.nodes()) {
            if (node == null || blank(node.name()) || node.approverRoleId() == null)
                throw invalid("审批步骤名称和审批角色不能为空");
            Long activeRole = jdbc.queryForObject("SELECT COUNT(*) FROM sys_role WHERE id=? AND status=1 AND role_code<>'DRIVER'",
                    Long.class, node.approverRoleId());
            if (activeRole == null || activeRole == 0) throw invalid("审批角色不存在或已停用");
            Long mayProcess = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM sys_role_permission rp JOIN sys_permission p ON p.id=rp.permission_id
                    WHERE rp.role_id=? AND p.permission_code IN ('BUTTON_APPROVALS_TODO_APPROVE','BUTTON_MINI_APPROVALS_APPROVE') AND p.status=1
                    """, Long.class, node.approverRoleId());
            if (mayProcess == null || mayProcess == 0) throw invalid("审批角色缺少审批通过权限");
            if (node.approverUserId() != null) {
                Long activeUser = jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE id=? AND status=1 AND role_id=?",
                        Long.class, node.approverUserId(), node.approverRoleId());
                if (activeUser == null || activeUser == 0) throw invalid("指定审批人须为该角色下的有效用户");
            }
        }
    }

    private String expenseType(String type) {
        if (blank(type)) throw invalid("请选择正式费用大类");
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM expense_category WHERE category_code=? AND enabled=1",
                Long.class, type);
        if (count == null || count == 0) throw invalid("费用大类不合法");
        return type;
    }

    private String templateName(String businessType, String expenseType) {
        if ("TIRE_REPLACEMENT".equals(businessType)) return "换胎申请";
        String categoryName = jdbc.queryForObject("""
                SELECT category_name FROM expense_category WHERE category_code=? AND enabled=1
                """, String.class, expenseType);
        if (blank(categoryName)) throw invalid("费用大类不合法");
        return categoryName + "申请";
    }

    public List<Map<String, Object>> taskCandidates(long driverId, AuthenticatedUser user) {
        requireAny(user, "BUTTON_EXPENSE_APPLY_SUBMIT", "BUTTON_MINI_EXPENSE_APPLY_SUBMIT");
        if (isOutsourced(user)) throw new AccessDeniedException("外协司机不能申请费用");
        if (users.isDriverUser(user) && users.requireDriver(user).driverId() != driverId)
            throw new AccessDeniedException("司机只能选择本人参与的任务");
        return jdbc.queryForList("""
                SELECT DISTINCT t.id taskId,t.task_no taskNo,t.business_date businessDate,
                       t.template_name_snapshot templateName,
                       t.loading_place_snapshot loadingPlace,t.unloading_place_snapshot unloadingPlace,
                       p.company_vehicle_id vehicleId,p.vehicle_plate_snapshot plateNo
                FROM transport_participant p JOIN transport_task t ON t.id=p.task_id
                WHERE p.driver_id=? AND p.driver_type_snapshot='INTERNAL'
                  AND p.company_vehicle_id IS NOT NULL AND t.deleted_at IS NULL
                  AND t.status <> 'CANCELLED'
                ORDER BY t.business_date DESC,t.id DESC
                """, driverId);
    }

    public Map<String, Object> expenseOptions(AuthenticatedUser user) {
        requireAny(user, "BUTTON_EXPENSE_APPLY_SUBMIT", "BUTTON_MINI_EXPENSE_APPLY_SUBMIT");
        if (isOutsourced(user)) throw new AccessDeniedException("外协司机不能申请费用");
        Long ownDriver = users.isDriverUser(user) ? users.requireDriver(user).driverId() : null;
        List<Map<String, Object>> drivers = ownDriver == null
                ? jdbc.queryForList("SELECT id,name,phone FROM driver WHERE status=1 AND deleted_at IS NULL ORDER BY name")
                : jdbc.queryForList("SELECT id,name,phone FROM driver WHERE id=? AND status=1 AND deleted_at IS NULL", ownDriver);
        return Map.of(
                "drivers", drivers,
                "vehicles", jdbc.queryForList("SELECT id,plate_no plateNo,energy_type energyType FROM vehicle WHERE status=1 AND deleted_at IS NULL ORDER BY plate_no"),
                "expenseTypes", jdbc.queryForList("SELECT category_code FROM expense_category WHERE enabled=1 ORDER BY display_order,category_code", String.class));
    }

    public Map<String, Boolean> expenseTrailerChecks(List<String> trailerNos, AuthenticatedUser user) {
        requireAny(user, "BUTTON_EXPENSE_APPLY_SUBMIT", "BUTTON_MINI_EXPENSE_APPLY_SUBMIT");
        if (isOutsourced(user)) throw new AccessDeniedException("外协司机不能申请费用");
        if (trailerNos == null || trailerNos.size() > 500) throw invalid("单次最多校验 500 个车挂号");
        List<String> requested = trailerNos.stream().filter(Objects::nonNull).map(String::trim)
                .filter(value -> !value.isEmpty()).distinct().toList();
        if (requested.stream().anyMatch(value -> value.length() > 64)) throw invalid("车挂号不能超过 64 字");
        var existing = existingTrailers(requested);
        Map<String, Boolean> result = new LinkedHashMap<>();
        for (String number : requested) result.put(number, existing.contains(number));
        return result;
    }

    private java.util.Set<String> existingTrailers(List<String> trailerNos) {
        if (trailerNos.isEmpty()) return java.util.Set.of();
        String placeholders = String.join(",", java.util.Collections.nCopies(trailerNos.size(), "?"));
        return new java.util.HashSet<>(jdbc.queryForList("SELECT trailer_no FROM trailer WHERE deleted_at IS NULL"
                + " AND trailer_no IN (" + placeholders + ")", String.class, trailerNos.toArray()));
    }

    public Map<String, Object> flowOptions(AuthenticatedUser user) {
        require(user, "PAGE_APPROVALS_FLOWS");
        users.denyAllDrivers(user);
        return Map.of(
                "roles", jdbc.queryForList("SELECT id,role_name roleName FROM sys_role WHERE status=1 ORDER BY id"),
                "approverRoles", jdbc.queryForList("""
                        SELECT r.id,r.role_name roleName,
                          EXISTS(SELECT 1 FROM sys_role_permission arp JOIN sys_permission ap ON ap.id=arp.permission_id
                            WHERE arp.role_id=r.id AND ap.status=1 AND ap.permission_code IN ('BUTTON_APPROVALS_TODO_APPROVE','BUTTON_MINI_APPROVALS_APPROVE')) canProcessApproval,
                          EXISTS(SELECT 1 FROM sys_role_permission trp JOIN sys_permission tp ON tp.id=trp.permission_id
                            WHERE trp.role_id=r.id AND tp.status=1 AND tp.permission_code IN ('BUTTON_APPROVALS_TODO_RETURN','BUTTON_MINI_APPROVALS_RETURN')) canReturnApproval
                        FROM sys_role r WHERE r.status=1 AND r.role_code<>'DRIVER'
                        ORDER BY r.id
                        """),
                "approvers", jdbc.queryForList("""
                        SELECT id,phone,role_id roleId FROM sys_user WHERE status=1 ORDER BY id DESC LIMIT 500
                        """),
                "expenseTypes", jdbc.queryForList("SELECT category_code categoryCode,category_name categoryName FROM expense_category WHERE enabled=1 ORDER BY display_order,category_code"));
    }

    @Transactional
    public Map<String, Object> create(Application input, AuthenticatedUser user) {
        authorization.require(user.id(), "BUTTON_EXPENSE_APPLY_SUBMIT", "BUTTON_MINI_EXPENSE_APPLY_SUBMIT", "BUTTON_MINI_TIRE_REQUEST_SUBMIT");
        requireAny(user, "BUTTON_EXPENSE_APPLY_SUBMIT", "BUTTON_MINI_EXPENSE_APPLY_SUBMIT", "BUTTON_MINI_TIRE_REQUEST_SUBMIT");
        if (isOutsourced(user)) throw new AccessDeniedException("外协司机不能发起审批或费用申请");
        if (input == null || input.templateId() == null || blank(input.requestKey())
                || input.requestKey().length() > 128) throw invalid("申请模板和请求标识不能为空");
        String digest = requestDigest(input);
        // Serialise this applicant's requests before checking the unique idempotency key.
        one("SELECT id FROM sys_user WHERE id=? FOR UPDATE", user.id());
        Map<String, Object> existing = one("""
                SELECT id,request_digest FROM approval_instance WHERE applicant_user_id=? AND request_key=?
                """, user.id(), input.requestKey());
        if (existing != null) {
            if (!digest.equals(existing.get("request_digest"))) throw invalid("同一请求标识不能提交不同的申请内容");
            return detail(num(existing.get("id")), user);
        }
        attachments.requireTemporaryApprovalAttachments(user.id(), input.attachmentIds());
        Map<String, Object> template = one("""
                SELECT t.*,f.id flow_id FROM approval_template t
                JOIN approval_flow f ON f.template_id=t.id AND f.status='ACTIVE'
                WHERE t.id=? AND t.enabled=1 FOR UPDATE
                """, input.templateId());
        if (template == null) throw invalid("当前模板没有已发布流程");
        long flowId = num(template.get("flow_id"));
        Long allowed = jdbc.queryForObject("""
                SELECT COUNT(*) FROM approval_flow_initiator_role WHERE flow_id=? AND role_id=?
                """, Long.class, flowId, user.roleId());
        if (allowed == null || allowed == 0) throw new AccessDeniedException("无权使用该审批模板");
        String type = (String) template.get("business_type");
        if ("TIRE_REPLACEMENT".equals(type)) require(user, "BUTTON_MINI_TIRE_REQUEST_SUBMIT");
        else requireAny(user, "BUTTON_EXPENSE_APPLY_SUBMIT", "BUTTON_MINI_EXPENSE_APPLY_SUBMIT");
        String scope = input.applicationScope();
        List<ExpenseItem> items = input.items();
        if ("EXPENSE".equals(type)) {
            if (!List.of("DAILY", "TASK").contains(scope)) throw invalid("请先选择日常费用或任务费用");
            validateItems(scope, items, (String) template.get("expense_type"), user);
        } else {
            if (scope != null || items != null && !items.isEmpty()) throw invalid("非费用审批不能提交费用明细");
            if ("TIRE_REPLACEMENT".equals(type)) tireEffect.validateRequest(input.tireRequestId(), user);
            else if (input.generalSnapshot() == null || input.generalSnapshot().isEmpty())
                throw invalid("请填写普通审批内容");
        }
        Map<String, Object> first = one("""
                SELECT * FROM approval_flow_node WHERE flow_id=? ORDER BY node_order LIMIT 1
                """, flowId);
        if (first == null) throw invalid("流程尚无审批步骤");
        long instanceId = insert("""
                INSERT INTO approval_instance(approval_no,template_id,flow_id,business_type,application_scope,
                                              applicant_user_id,request_key,request_digest,status,current_node_id)
                VALUES(?,?,?,?,?,?,?,?,'PENDING',?)
                """, "A" + UUID.randomUUID().toString().replace("-", ""), input.templateId(), flowId,
                type, scope, user.id(), input.requestKey(), digest, num(first.get("id")));
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("businessType", type);
        if ("EXPENSE".equals(type)) {
            snapshot.put("applicationScope", scope);
            snapshot.put("expenseType", template.get("expense_type"));
            snapshot.put("items", items);
        } else if ("GENERAL".equals(type)) snapshot.put("content", input.generalSnapshot());
        else snapshot.put("tireRequestId", input.tireRequestId());
        long submissionId = insert("""
                INSERT INTO approval_submission_version(instance_id,version_no,business_snapshot_json,submitted_by)
                VALUES(?,1,?,?)
                """, instanceId, json(snapshot), user.id());
        bindSubmissionAttachments(user.id(), input.attachmentIds(), instanceId, submissionId);
        if ("EXPENSE".equals(type))
            saveItems(instanceId, submissionId, (String) template.get("expense_type"), items);
        long taskId = insert("""
                INSERT INTO approval_task(instance_id,submission_version_id,node_id)
                VALUES(?,?,?)
                """, instanceId, submissionId, num(first.get("id")));
        notifyPendingTask(instanceId, taskId, num(first.get("id")), type);
        if ("TIRE_REPLACEMENT".equals(type)) tireEffect.onCreated(input.tireRequestId(), instanceId);
        audit.write("APPROVAL", "INSTANCE", instanceId, "CREATE", null,
                Map.of("type", type, "flowId", flowId), user.id(), null);
        return detail(instanceId, user);
    }

    private void validateItems(String scope, List<ExpenseItem> items, String expenseType, AuthenticatedUser actor) {
        if (items == null || items.isEmpty() || ("TASK".equals(scope) && items.size() != 1)
                || items.size() > 500) throw invalid("日常费用可多笔，任务费用只能单笔");
        var existingTrailers = existingTrailers(items.stream().filter(Objects::nonNull)
                .map(ExpenseItem::trailerNo).filter(Objects::nonNull).map(String::trim)
                .filter(value -> !value.isEmpty() && value.length() <= 64).distinct().toList());
        Long ownDriver = users.isDriverUser(actor) ? users.requireDriver(actor).driverId() : null;
        for (int index = 0; index < items.size(); index++) {
            ExpenseItem item = items.get(index);
            if (item == null || item.businessDate() == null || item.driverId() == null
                    || item.vehicleId() == null || item.amount() == null
                    || item.amount().scale() > 2 || item.amount().compareTo(BigDecimal.ZERO) <= 0)
                throw invalid("费用日期、司机、公司车辆必填，金额必须大于 0 且最多两位小数");
            String trailerNo = item.trailerNo() == null ? "" : item.trailerNo().trim();
            if (trailerNo.length() > 64)
                throw invalid("车挂号不能超过 64 字");
            if (!trailerNo.isEmpty() && !existingTrailers.contains(trailerNo))
                throw invalid("车挂号不存在");
            if (ownDriver != null && !ownDriver.equals(item.driverId()))
                throw new AccessDeniedException("司机只能申请本人费用");
            Long driver = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM driver WHERE id=? AND status=1 AND deleted_at IS NULL
                    """, Long.class, item.driverId());
            if (driver == null || driver == 0)
                throw invalid("司机不存在或已停用");
            List<String> vehicleEnergy;
            if ("DAILY".equals(scope)) {
                if (item.targetTaskId() != null) throw invalid("日常费用申请时不能指定任务");
                vehicleEnergy = jdbc.queryForList("""
                        SELECT energy_type FROM vehicle WHERE id=? AND status=1 AND deleted_at IS NULL
                        """, String.class, item.vehicleId());
                if (vehicleEnergy.isEmpty()) throw invalid("公司车辆不存在或已停用");
            } else {
                if (item.targetTaskId() == null) throw invalid("请选择该司机正式参与的任务");
                vehicleEnergy = jdbc.queryForList("""
                        SELECT v.energy_type FROM transport_participant p
                        JOIN transport_task t ON t.id=p.task_id
                        JOIN vehicle v ON v.id=p.company_vehicle_id
                        WHERE p.task_id=? AND p.driver_id=? AND p.company_vehicle_id=?
                          AND p.driver_type_snapshot='INTERNAL'
                          AND v.status=1 AND v.deleted_at IS NULL
                          AND t.deleted_at IS NULL AND t.status <> 'CANCELLED'
                        """, String.class, item.targetTaskId(), item.driverId(), item.vehicleId());
                if (vehicleEnergy.isEmpty())
                    throw invalid("任务、司机与公司车辆的正式参与事实不匹配");
            }
            String requiredEnergy = switch (expenseType) {
                case "ELECTRIC", "TEMP_ELECTRIC" -> "ELECTRIC";
                case "GAS", "TEMP_GAS" -> "GAS";
                default -> null;
            };
            if (requiredEnergy != null && !requiredEnergy.equals(vehicleEnergy.getFirst()))
                throw invalid("第 " + (index + 1) + " 条费用："
                        + ("ELECTRIC".equals(requiredEnergy) ? "电费、临时电费只能选择电车" : "气费、临时气费只能选择气车"));
        }
    }

    private void saveItems(long instanceId, long submissionId, String type, List<ExpenseItem> items) {
        int itemNo = 0;
        for (ExpenseItem item : items) {
            jdbc.update("""
                    INSERT INTO approval_expense_item(instance_id,submission_version_id,item_no,expense_type,
                        business_date,vehicle_id,driver_id,description,amount,receipt_no,invoice_no,
                        remark,station_name,trailer_no,target_task_id)
                    VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """, instanceId, submissionId, ++itemNo, type, item.businessDate(), item.vehicleId(),
                    item.driverId(), item.description(), item.amount(), item.receiptNo(), item.invoiceNo(),
                    item.remark(), item.stationName(), blank(item.trailerNo()) ? null : item.trailerNo().trim(),
                    item.targetTaskId());
        }
    }

    public PageResponse<Map<String, Object>> list(String scope, int pageNo, int pageSize, AuthenticatedUser user) {
        if (user == null) throw new AccessDeniedException("请登录");
        int page = Math.max(1, pageNo), size = Math.min(Math.max(1, pageSize), 100);
        String where;
        Object[] args;
        if ("TODO".equals(scope)) {
            where = """
                     AND ((a.status='RETURNED_TO_APPLICANT' AND a.applicant_user_id=?)
                       OR EXISTS(SELECT 1 FROM approval_task k
                       JOIN approval_flow_node n ON n.id=k.node_id
                       JOIN sys_user u ON u.id=? AND u.status=1 AND u.role_id=n.approver_role_id
                       JOIN sys_role r ON r.id=u.role_id AND r.status=1
                       WHERE k.instance_id=a.id AND a.status='PENDING'
                         AND k.status='PENDING' AND k.invalidated=0
                         AND (n.approver_user_id IS NULL OR n.approver_user_id=u.id)
                         AND (?=1 OR ?=1)
                     ))
                    """;
            args = new Object[]{user.id(), user.id(), has(user, "BUTTON_APPROVALS_TODO_APPROVE") ? 1 : 0,
                    has(user, "BUTTON_APPROVALS_TODO_RETURN") ? 1 : 0};
        } else if ("MINE".equals(scope)) {
            where = " AND a.applicant_user_id=?";
            args = new Object[]{user.id()};
        } else if ("PROCESSED".equals(scope)) {
            where = """
                    AND EXISTS(SELECT 1 FROM approval_action x JOIN approval_task k ON k.id=x.task_id
                      WHERE k.instance_id=a.id AND x.operator_id=?
                        AND x.action_type IN ('APPROVE','RETURN_STEP','RETURN_APPLICANT','REJECT'))
                    """;
            args = new Object[]{user.id()};
        } else if ("HISTORY".equals(scope)) {
            require(user, "PAGE_APPROVALS_HISTORY");
            where = "";
            args = new Object[]{};
        } else throw invalid("审批列表范围不合法");
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM approval_instance a WHERE 1=1 " + where,
                Long.class, args);
        List<Object> queryArgs = new ArrayList<>(List.of(args));
        queryArgs.add(size);
        queryArgs.add((page - 1) * size);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT a.id,a.approval_no approvalNo,a.business_type businessType,
                       a.application_scope applicationScope,a.status,a.created_at createdAt,
                       t.name templateName,u.phone applicantPhone,n.node_name currentStepName,
                       (SELECT MAX(x.operated_at) FROM approval_action x
                          JOIN approval_task k ON k.id=x.task_id WHERE k.instance_id=a.id) lastHandledAt,
                       (SELECT MAX(x.operated_at) FROM approval_action x
                          JOIN approval_task k ON k.id=x.task_id
                          WHERE k.instance_id=a.id AND x.action_type='RETURN_APPLICANT') lastReturnAt
                FROM approval_instance a JOIN approval_template t ON t.id=a.template_id
                JOIN sys_user u ON u.id=a.applicant_user_id
                LEFT JOIN approval_flow_node n ON n.id=a.current_node_id
                WHERE 1=1 %s ORDER BY a.id DESC LIMIT ? OFFSET ?
                """.formatted(where), queryArgs.toArray());
        List<Long> tireIds = rows.stream().filter(row -> "TIRE_REPLACEMENT".equals(row.get("businessType")))
                .map(row -> num(row.get("id"))).toList();
        if (!tireIds.isEmpty()) {
            String placeholders = String.join(",", java.util.Collections.nCopies(tireIds.size(), "?"));
            List<Map<String, Object>> tireRows = jdbc.queryForList("""
                    SELECT r.approval_instance_id approvalId,v.plate_no vehiclePlate,
                           r.confirmed_tire_no tireNo
                    FROM tire_request r JOIN vehicle v ON v.id=r.vehicle_id
                    WHERE r.approval_instance_id IN (%s)
                      AND r.batch_key=(SELECT b.batch_key FROM tire_request b
                        WHERE b.approval_instance_id=r.approval_instance_id ORDER BY b.id DESC LIMIT 1)
                    ORDER BY r.id
                    """.formatted(placeholders), tireIds.toArray());
            Map<Long, List<String>> summaries = new LinkedHashMap<>();
            for (Map<String, Object> tire : tireRows)
                summaries.computeIfAbsent(num(tire.get("approvalId")), ignored -> new ArrayList<>())
                        .add((String) tire.get("tireNo"));
            for (Map<String, Object> row : rows) if (summaries.containsKey(num(row.get("id")))) {
                String plate = tireRows.stream().filter(tire -> num(tire.get("approvalId")) == num(row.get("id")))
                        .map(tire -> String.valueOf(tire.get("vehiclePlate"))).findFirst().orElse("");
                row.put("businessSummary", plate + " · " + String.join("、", summaries.get(num(row.get("id")))));
            }
        }
        return new PageResponse<>(page, size, count == null ? 0 : count, rows);
    }

    public Map<String, Object> detail(long id, AuthenticatedUser user) {
        Map<String, Object> approval = one("""
                SELECT a.*,t.name template_name,t.expense_type,f.version_no,n.node_name current_step_name,
                  applicant.phone applicant_phone,driver.name applicant_name,
                  applicant_role.role_name applicant_role_name
                FROM approval_instance a JOIN approval_template t ON t.id=a.template_id
                JOIN approval_flow f ON f.id=a.flow_id
                JOIN sys_user applicant ON applicant.id=a.applicant_user_id
                LEFT JOIN driver ON driver.user_id=applicant.id
                LEFT JOIN sys_role applicant_role ON applicant_role.id=applicant.role_id
                LEFT JOIN approval_flow_node n ON n.id=a.current_node_id WHERE a.id=?
                """, id);
        if (approval == null) throw new BusinessException(ErrorCode.DATA_001);
        boolean visible = Objects.equals(num(approval.get("applicant_user_id")), user.id())
                || has(user, "PAGE_APPROVALS_HISTORY")
                || canHandleCurrent(id, user, "BUTTON_APPROVALS_TODO_APPROVE")
                || canHandleCurrent(id, user, "BUTTON_APPROVALS_TODO_RETURN")
                || !jdbc.queryForList("""
                        SELECT x.id FROM approval_action x JOIN approval_task k ON k.id=x.task_id
                        WHERE k.instance_id=? AND x.operator_id=? LIMIT 1
                        """, id, user.id()).isEmpty();
        if (!visible) throw new AccessDeniedException("无权查看该审批");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("approval", approval);
        result.put("flow", flow(num(approval.get("flow_id"))));
        result.put("submissions", jdbc.queryForList("""
                SELECT id,version_no,business_snapshot_json,submitted_at,submitted_by
                FROM approval_submission_version WHERE instance_id=? ORDER BY version_no
                """, id));
        result.put("tasks", jdbc.queryForList("""
                SELECT k.id,k.submission_version_id,k.node_id,n.node_name,n.approver_role_id,
                       n.approver_user_id,u.phone approver_phone,k.status,k.invalidated,k.created_at,k.completed_at
                FROM approval_task k JOIN approval_flow_node n ON n.id=k.node_id
                LEFT JOIN sys_user u ON u.id=n.approver_user_id
                WHERE k.instance_id=? ORDER BY k.id
                """, id));
        result.put("actions", jdbc.queryForList("""
                SELECT x.id,x.task_id,x.action_type,x.operator_id,x.comment,x.target_flow_node_id,
                       x.target_step_name_snapshot,x.operated_at,n.node_name node_name,
                       u.phone operator_phone,r.role_name operator_role
                FROM approval_action x JOIN approval_task k ON k.id=x.task_id
                JOIN approval_flow_node n ON n.id=k.node_id
                JOIN sys_user u ON u.id=x.operator_id JOIN sys_role r ON r.id=u.role_id
                WHERE k.instance_id=? ORDER BY x.id
                """, id));
        result.put("canProcess", canHandleCurrent(id, user, "BUTTON_APPROVALS_TODO_APPROVE"));
        result.put("canReturn", canHandleCurrent(id, user, "BUTTON_APPROVALS_TODO_RETURN"));
        result.put("expenseItems", jdbc.queryForList("""
                SELECT i.*,d.name driver_name,v.plate_no vehicle_plate,
                  t.task_no,t.business_date task_business_date,
                  t.template_name_snapshot task_template_name,
                  t.customer_name_snapshot task_customer_name,
                  t.product_name_snapshot task_product_name,
                  t.loading_place_snapshot task_loading_place,
                  t.unloading_place_snapshot task_unloading_place,
                  t.status task_status,t.completed_at task_completed_at,
                  current_driver.name task_current_driver,
                  CASE WHEN p.driver_type_snapshot='OUTSOURCED' THEN p.outsourced_plate_no
                       ELSE p.vehicle_plate_snapshot END task_current_plate,
                  p.departed_at task_departed_at,
                  w.net_weight task_net_weight
                FROM approval_expense_item i
                LEFT JOIN driver d ON d.id=i.driver_id
                LEFT JOIN vehicle v ON v.id=i.vehicle_id
                LEFT JOIN transport_task t ON t.id=i.target_task_id
                LEFT JOIN transport_participant p ON p.id=t.current_participant_id
                LEFT JOIN driver current_driver ON current_driver.id=p.driver_id
                LEFT JOIN task_weight_version w ON w.id=t.effective_weight_version_id
                WHERE i.instance_id=? ORDER BY i.submission_version_id,i.item_no
                """, id));
        result.put("attachments", jdbc.queryForList("""
                SELECT sa.submission_version_id, f.id attachmentId,f.original_filename filename,
                       f.content_type contentType
                FROM approval_submission_attachment sa JOIN file_attachment f ON f.id=sa.attachment_id
                JOIN approval_submission_version s ON s.id=sa.submission_version_id
                WHERE s.instance_id=? ORDER BY s.version_no,f.id
                """, id));
        if ("TIRE_REPLACEMENT".equals(approval.get("business_type"))) {
            result.put("tireRequests", jdbc.queryForList("""
                    SELECT r.confirmed_tire_no tireNo,r.status,d.name driverName,v.plate_no vehiclePlate,
                           r.created_at requestedAt,o.recognized_text recognizedText,
                           r.ocr_record_id ocrRecordId,r.photo_attachment_id photoAttachmentId
                    FROM tire_request r JOIN driver d ON d.id=r.applicant_driver_id
                    JOIN vehicle v ON v.id=r.vehicle_id JOIN ocr_record o ON o.id=r.ocr_record_id
                    WHERE r.approval_instance_id=? AND r.batch_key=(
                      SELECT b.batch_key FROM tire_request b WHERE b.approval_instance_id=?
                      ORDER BY b.id DESC LIMIT 1) ORDER BY r.id
                    """, id, id));
        }
        result.put("actionAttachments", jdbc.queryForList("""
                SELECT x.id actionId,f.id attachmentId,f.original_filename filename,f.content_type contentType
                FROM approval_action x JOIN approval_task k ON k.id=x.task_id
                JOIN file_attachment f ON f.owner_type='APPROVAL_ACTION' AND f.owner_id=x.id
                WHERE k.instance_id=? ORDER BY x.id,f.id
                """, id));
        result.put("returnableSteps", returnableSteps(id, user));
        return result;
    }

    public List<Map<String, Object>> returnableSteps(long id, AuthenticatedUser user) {
        Map<String, Object> approval = one("SELECT * FROM approval_instance WHERE id=?", id);
        if (approval == null) throw new BusinessException(ErrorCode.DATA_001);
        if (!"PENDING".equals(approval.get("status"))) return List.of();
        Map<String, Object> current = one("""
                SELECT k.*,n.node_order FROM approval_task k
                JOIN approval_flow_node n ON n.id=k.node_id
                WHERE k.instance_id=? AND k.status='PENDING' AND k.invalidated=0
                ORDER BY k.id DESC LIMIT 1
                """, id);
        if (current == null || !canHandleCurrent(id, user, "BUTTON_APPROVALS_TODO_RETURN")) return List.of();
        return jdbc.queryForList("""
                SELECT n.id flowNodeId,n.node_name stepName,n.node_order,u.phone approverPhone
                FROM approval_flow_node n LEFT JOIN sys_user u ON u.id=n.approver_user_id
                WHERE n.flow_id=? AND n.node_order<? AND n.allow_return=1
                  AND EXISTS (SELECT 1 FROM approval_task k WHERE k.instance_id=?
                    AND k.submission_version_id=? AND k.node_id=n.id
                    AND k.status='APPROVED' AND k.invalidated=0)
                ORDER BY n.node_order
                """, approval.get("flow_id"), current.get("node_order"), id,
                current.get("submission_version_id"));
    }

    private boolean canHandleCurrent(long instanceId, AuthenticatedUser user, String permission) {
        if (!has(user, permission)) return false;
        List<Long> nodes = jdbc.queryForList("""
                SELECT k.node_id FROM approval_task k JOIN approval_instance a ON a.id=k.instance_id
                WHERE k.instance_id=? AND a.status='PENDING' AND k.status='PENDING' AND k.invalidated=0
                ORDER BY k.id DESC LIMIT 1
                """, Long.class, instanceId);
        return !nodes.isEmpty() && eligibleForNode(nodes.getFirst(), user, permission);
    }

    private boolean eligibleForNode(long nodeId, AuthenticatedUser user, String permission) {
        if (!has(user, permission)) return false;
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM approval_flow_node n
                JOIN sys_role r ON r.id=n.approver_role_id AND r.status=1
                JOIN sys_user u ON u.role_id=r.id AND u.status=1
                WHERE n.id=? AND u.id=? AND (n.approver_user_id IS NULL OR n.approver_user_id=u.id)
                """, Long.class, nodeId, user.id());
        return count != null && count > 0;
    }

    private void notifyPendingTask(long approvalId, long taskId, long nodeId, String businessType) {
        String businessName = "TIRE_REPLACEMENT".equals(businessType) ? "换胎申请" : "费用申请";
        List<Long> recipients = jdbc.queryForList("""
                SELECT u.id FROM approval_flow_node n
                JOIN sys_role r ON r.id=n.approver_role_id AND r.status=1
                JOIN sys_user u ON u.role_id=r.id AND u.status=1
                WHERE n.id=? AND (n.approver_user_id IS NULL OR n.approver_user_id=u.id)
                  AND EXISTS (SELECT 1 FROM sys_role_permission rp
                              JOIN sys_permission p ON p.id=rp.permission_id AND p.status=1
                              WHERE rp.role_id=r.id AND p.permission_code IN
                              ('BUTTON_APPROVALS_TODO_APPROVE','BUTTON_APPROVALS_TODO_RETURN',
                               'BUTTON_MINI_APPROVALS_APPROVE','BUTTON_MINI_APPROVALS_RETURN'))
                """, Long.class, nodeId);
        for (Long recipient : recipients) {
            notifications.toEligibleApprover(recipient, "approval:task:" + taskId + ":" + recipient,
                    "收到待审批任务", businessName + "待处理，请查看审批详情", approvalId);
        }
    }

    @Transactional
    public Map<String, Object> withdraw(long id, AuthenticatedUser user) {
        authorization.require(user.id(), "BUTTON_APPROVALS_MINE_WITHDRAW", "BUTTON_MINI_APPROVALS_WITHDRAW");
        Map<String, Object> approval = one("SELECT * FROM approval_instance WHERE id=? FOR UPDATE", id);
        if (approval == null) throw new BusinessException(ErrorCode.DATA_001);
        if (user == null || num(approval.get("applicant_user_id")) != user.id())
            throw new AccessDeniedException("仅申请人本人可撤回");
        if (!List.of("PENDING", "RETURNED_TO_APPLICANT").contains(approval.get("status")))
            throw invalid("申请已生效或进入终态，不能撤回；已生效费用请按冲销规则处理");
        Map<String, Object> lastTask = one("SELECT id FROM approval_task WHERE instance_id=? ORDER BY id DESC LIMIT 1", id);
        if (lastTask == null) throw invalid("审批待办不存在");
        jdbc.update("""
                UPDATE approval_task SET status='WITHDRAWN',invalidated=1,completed_at=NOW(3)
                WHERE instance_id=? AND status='PENDING' AND invalidated=0
                """, id);
        jdbc.update("""
                UPDATE approval_instance SET status='WITHDRAWN',current_node_id=NULL,
                    completed_at=NOW(3),version=version+1 WHERE id=?
                """, id);
        if ("TIRE_REPLACEMENT".equals(approval.get("business_type"))) tireEffect.onWithdrawn(id);
        insert("INSERT INTO approval_action(task_id,action_type,operator_id,comment) VALUES(?,'WITHDRAW',?,?)",
                lastTask.get("id"), user.id(), "申请人撤回");
        audit.write("APPROVAL", "INSTANCE", id, "WITHDRAW", null,
                Map.of("status", "WITHDRAWN"), user.id(), null);
        return detail(id, user);
    }

    private void requireAny(AuthenticatedUser user, String... permissions) {
        if (user == null || java.util.Arrays.stream(permissions).noneMatch(permission -> has(user, permission)))
            throw new AccessDeniedException("缺少审批处理权限");
    }

    @Transactional
    public Map<String, Object> decide(long id, String decision, Action input, AuthenticatedUser user) {
        if ("APPROVE".equals(decision)) authorization.require(user.id(),
                "BUTTON_APPROVALS_TODO_APPROVE", "BUTTON_MINI_APPROVALS_APPROVE");
        else authorization.require(user.id(), "BUTTON_APPROVALS_TODO_RETURN", "BUTTON_MINI_APPROVALS_RETURN");
        if ("APPROVE".equals(decision)) require(user, "BUTTON_APPROVALS_TODO_APPROVE");
        else if (List.of("RETURN_STEP", "RETURN_APPLICANT").contains(decision)) require(user, "BUTTON_APPROVALS_TODO_RETURN");
        else throw invalid("审批动作不合法");
        Map<String, Object> approval = one("SELECT * FROM approval_instance WHERE id=? FOR UPDATE", id);
        if (approval == null) throw new BusinessException(ErrorCode.DATA_001);
        if (!"PENDING".equals(approval.get("status"))) throw invalid("当前审批不在待处理状态");
        if (input == null || input.expectedVersion() == null
                || input.expectedVersion() != ((Number) approval.get("version")).intValue())
            throw invalid("审批状态已变化，请刷新后重试");
        Map<String, Object> task = one("""
                SELECT k.*,n.node_order FROM approval_task k
                JOIN approval_flow_node n ON n.id=k.node_id
                WHERE k.instance_id=? AND k.status='PENDING' AND k.invalidated=0
                ORDER BY k.id DESC LIMIT 1 FOR UPDATE
                """, id);
        if (task == null || !eligibleForNode(num(task.get("node_id")), user, "APPROVE".equals(decision)
                ? "BUTTON_APPROVALS_TODO_APPROVE" : "BUTTON_APPROVALS_TODO_RETURN"))
            throw new AccessDeniedException("当前步骤不由你审批");
        if ("RETURN_STEP".equals(decision)) {
            if (input.targetFlowNodeId() == null || returnableSteps(id, user).stream()
                    .noneMatch(row -> num(row.get("flowNodeId")) == input.targetFlowNodeId()))
                throw invalid("只能选择当前实例实际经过的合法前置步骤");
        } else if (input.targetFlowNodeId() != null) throw invalid("此操作不能填写打回目标步骤");
        attachments.requireTemporaryApprovalActionAttachments(user.id(), input.attachmentIds());
        if (!"APPROVE".equals(decision) && blank(input.comment()))
            throw invalid("打回原因不能为空");
        int updated = jdbc.update("""
                UPDATE approval_task SET status=?,completed_at=NOW(3)
                WHERE id=? AND status='PENDING' AND invalidated=0
                """, "APPROVE".equals(decision) ? "APPROVED" : "RETURNED", task.get("id"));
        if (updated != 1) throw invalid("审批任务已被处理");
        Map<String, Object> target = input.targetFlowNodeId() == null ? null : one("""
                SELECT * FROM approval_flow_node WHERE id=? AND flow_id=?
                """, input.targetFlowNodeId(), approval.get("flow_id"));
        long actionId = insert("""
                INSERT INTO approval_action(task_id,action_type,operator_id,comment,target_flow_node_id,
                                            target_step_name_snapshot)
                VALUES(?,?,?,?,?,?)
                """, task.get("id"), decision, user.id(), input.comment(), input.targetFlowNodeId(),
                target == null ? null : target.get("node_name"));
        attachments.bindApprovalActionAttachments(user.id(), input.attachmentIds(), actionId);
        if ("RETURN_APPLICANT".equals(decision)) {
            jdbc.update("""
                    UPDATE approval_instance SET status='RETURNED_TO_APPLICANT',current_node_id=NULL,
                        version=version+1 WHERE id=?
                    """, id);
            if ("TIRE_REPLACEMENT".equals(approval.get("business_type")))
                tireEffect.onReturnedToApplicant(id);
            notifications.toUser(num(approval.get("applicant_user_id")),
                    "approval:return:" + actionId, "APPROVAL_RETURN", "审批已打回",
                    "请查看打回原因并修改后重新提交", "APPROVAL", id);
        } else if ("RETURN_STEP".equals(decision)) {
            jdbc.update("""
                    UPDATE approval_task SET invalidated=1
                    WHERE instance_id=? AND submission_version_id=?
                      AND node_id IN (SELECT id FROM approval_flow_node
                                      WHERE flow_id=? AND node_order>=?)
                    """, id, task.get("submission_version_id"), approval.get("flow_id"),
                    target.get("node_order"));
            Map<String, Object> submission = one("""
                    SELECT * FROM approval_submission_version WHERE id=?
                    """, task.get("submission_version_id"));
            Integer versionNo = jdbc.queryForObject("""
                    SELECT COALESCE(MAX(version_no),0)+1 FROM approval_submission_version WHERE instance_id=?
                    """, Integer.class, id);
            long newSubmission = insert("""
                    INSERT INTO approval_submission_version(instance_id,version_no,business_snapshot_json,submitted_by)
                    VALUES(?,?,?,?)
                    """, id, versionNo, submission.get("business_snapshot_json"), user.id());
            if ("EXPENSE".equals(approval.get("business_type"))) copyItems(id, num(submission.get("id")), newSubmission);
            long newTaskId = insert("""
                    INSERT INTO approval_task(instance_id,submission_version_id,node_id)
                    VALUES(?,?,?)
                    """, id, newSubmission, target.get("id"));
            notifyPendingTask(id, newTaskId, num(target.get("id")), (String) approval.get("business_type"));
            jdbc.update("""
                    UPDATE approval_instance SET current_node_id=?,version=version+1 WHERE id=?
                    """, target.get("id"), id);
        } else {
            Map<String, Object> next = one("""
                    SELECT * FROM approval_flow_node WHERE flow_id=? AND node_order>?
                    ORDER BY node_order LIMIT 1
                    """, approval.get("flow_id"), task.get("node_order"));
            if (next != null) {
                long nextTaskId = insert("""
                        INSERT INTO approval_task(instance_id,submission_version_id,node_id)
                        VALUES(?,?,?)
                        """, id, task.get("submission_version_id"), next.get("id"));
                notifyPendingTask(id, nextTaskId, num(next.get("id")), (String) approval.get("business_type"));
                jdbc.update("""
                        UPDATE approval_instance SET current_node_id=?,version=version+1 WHERE id=?
                        """, next.get("id"), id);
            } else {
                if ("EXPENSE".equals(approval.get("business_type")))
                    postExpenses(approval, num(task.get("submission_version_id")), user.id());
                if ("TIRE_REPLACEMENT".equals(approval.get("business_type")))
                    tireEffect.onApproved(id, user.id());
                jdbc.update("""
                        UPDATE approval_instance SET status='APPROVED',current_node_id=NULL,
                            completed_at=NOW(3),version=version+1 WHERE id=?
                        """, id);
            }
        }
        audit.write("APPROVAL", "INSTANCE", id, decision,
                Map.of("status", "PENDING"), Map.of("taskId", task.get("id")),
                user.id(), input.comment());
        return detail(id, user);
    }

    @Transactional
    public Map<String, Object> resubmit(long id, Resubmission input, AuthenticatedUser user) {
        authorization.require(user.id(), "BUTTON_EXPENSE_APPLY_SUBMIT", "BUTTON_MINI_EXPENSE_APPLY_SUBMIT");
        requireAny(user, "BUTTON_EXPENSE_APPLY_SUBMIT", "BUTTON_MINI_EXPENSE_APPLY_SUBMIT");
        Map<String, Object> approval = one("SELECT * FROM approval_instance WHERE id=? FOR UPDATE", id);
        if (approval == null) throw new BusinessException(ErrorCode.DATA_001);
        if (!"RETURNED_TO_APPLICANT".equals(approval.get("status"))
                || num(approval.get("applicant_user_id")) != user.id())
            throw new AccessDeniedException("只有被打回的原申请人可以重新提交");
        if (isOutsourced(user)) throw new AccessDeniedException("外协司机不能发起费用或审批");
        attachments.requireTemporaryApprovalAttachments(user.id(), input.attachmentIds());
        String type = (String) approval.get("business_type");
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("businessType", type);
        if ("EXPENSE".equals(type)) {
            String scope = (String) approval.get("application_scope");
            Map<String, Object> template = one("SELECT expense_type FROM approval_template WHERE id=?",
                    approval.get("template_id"));
            validateItems(scope, input.items(), (String) template.get("expense_type"), user);
            snapshot.put("applicationScope", scope);
            snapshot.put("expenseType", template.get("expense_type"));
            snapshot.put("items", input.items());
        } else if ("GENERAL".equals(type)) {
            if (input.generalSnapshot() == null || input.generalSnapshot().isEmpty())
                throw invalid("请填写普通审批内容");
            snapshot.put("content", input.generalSnapshot());
        } else throw invalid("换胎重新提交须通过换胎业务流程");
        Integer versionNo = jdbc.queryForObject("""
                SELECT COALESCE(MAX(version_no),0)+1 FROM approval_submission_version WHERE instance_id=?
                """, Integer.class, id);
        long submissionId = insert("""
                INSERT INTO approval_submission_version(instance_id,version_no,business_snapshot_json,submitted_by)
                VALUES(?,?,?,?)
                """, id, versionNo, json(snapshot), user.id());
        bindSubmissionAttachments(user.id(), input.attachmentIds(), id, submissionId);
        if ("EXPENSE".equals(type)) saveItems(id, submissionId,
                (String) snapshot.get("expenseType"), input.items());
        Map<String, Object> first = one("""
                SELECT * FROM approval_flow_node WHERE flow_id=? ORDER BY node_order LIMIT 1
                """, approval.get("flow_id"));
        long taskId = insert("""
                INSERT INTO approval_task(instance_id,submission_version_id,node_id)
                VALUES(?,?,?)
                """, id, submissionId, first.get("id"));
        notifyPendingTask(id, taskId, num(first.get("id")), type);
        jdbc.update("""
                UPDATE approval_instance SET status='PENDING',current_node_id=?,version=version+1 WHERE id=?
                """, first.get("id"), id);
        audit.write("APPROVAL", "INSTANCE", id, "RESUBMIT", null,
                Map.of("submissionVersion", versionNo), user.id(), null);
        return detail(id, user);
    }

    @Transactional
    public Map<String, Object> resubmitTire(long id, int expectedVersion, CurrentDriverContext driver,
                                            List<Map<String, Object>> rows, AuthenticatedUser user) {
        authorization.require(user.id(), "BUTTON_MINI_TIRE_REQUEST_SUBMIT");
        require(user, "BUTTON_MINI_TIRE_REQUEST_SUBMIT");
        Map<String, Object> approval = one("SELECT * FROM approval_instance WHERE id=? FOR UPDATE", id);
        if (approval == null) throw new BusinessException(ErrorCode.DATA_001);
        if (!"TIRE_REPLACEMENT".equals(approval.get("business_type"))
                || !"RETURNED_TO_APPLICANT".equals(approval.get("status"))
                || num(approval.get("applicant_user_id")) != user.id())
            throw new AccessDeniedException("只有被打回的原申请人可以重新提交换胎申请");
        if (((Number) approval.get("version")).intValue() != expectedVersion)
            throw invalid("审批状态已变化，请刷新后重试");
        Map<String, Object> previous = one("""
                SELECT * FROM tire_request WHERE approval_instance_id=? ORDER BY id DESC LIMIT 1 FOR UPDATE
                """, id);
        if (previous == null || num(previous.get("applicant_driver_id")) != driver.driverId()
                || num(previous.get("vehicle_id")) != driver.currentVehicleId())
            throw new AccessDeniedException("申请司机或当前公司车辆已变化，不能重新提交");
        Map<String, Object> lastTask = one("""
                SELECT id FROM approval_task WHERE instance_id=? ORDER BY id DESC LIMIT 1
                """, id);
        int versionNo = jdbc.queryForObject("""
                SELECT COALESCE(MAX(version_no),0)+1 FROM approval_submission_version WHERE instance_id=?
                """, Integer.class, id);
        String batchKey = UUID.randomUUID().toString();
        long firstRequestId = 0;
        for (Map<String, Object> row : rows) {
            long requestId = insert("""
                    INSERT INTO tire_request(batch_key,applicant_driver_id,approval_instance_id,vehicle_id,
                        confirmed_tire_no,ocr_record_id,photo_attachment_id,tire_id,status)
                    VALUES(?,?,?,?,?,?,?,?,'PENDING_APPROVAL')
                    """, batchKey, driver.driverId(), id, driver.currentVehicleId(), row.get("tireNo"),
                    row.get("ocrRecordId"), row.get("attachmentId"), row.get("tireId"));
            if (firstRequestId == 0) firstRequestId = requestId;
            jdbc.update("UPDATE ocr_record SET confirmed_text=? WHERE id=?",
                    row.get("tireNo"), row.get("ocrRecordId"));
        }
        long submissionId = insert("""
                INSERT INTO approval_submission_version(instance_id,version_no,business_snapshot_json,submitted_by)
                VALUES(?,?,?,?)
                """, id, versionNo, json(Map.of("businessType", "TIRE_REPLACEMENT",
                        "tireRequestId", firstRequestId)), user.id());
        for (Map<String, Object> row : rows) jdbc.update("""
                INSERT INTO approval_submission_attachment(submission_version_id,attachment_id) VALUES(?,?)
                """, submissionId, row.get("attachmentId"));
        Map<String, Object> firstNode = one("""
                SELECT id FROM approval_flow_node WHERE flow_id=? ORDER BY node_order LIMIT 1
                """, approval.get("flow_id"));
        long taskId = insert("INSERT INTO approval_task(instance_id,submission_version_id,node_id) VALUES(?,?,?)",
                id, submissionId, firstNode.get("id"));
        notifyPendingTask(id, taskId, num(firstNode.get("id")), "TIRE_REPLACEMENT");
        jdbc.update("""
                UPDATE approval_instance SET status='PENDING',current_node_id=?,version=version+1 WHERE id=?
                """, firstNode.get("id"), id);
        insert("""
                INSERT INTO approval_action(task_id,action_type,operator_id,comment)
                VALUES(?,'RESUBMIT',?,'修改换胎申请并重新提交')
                """, lastTask.get("id"), user.id());
        audit.write("APPROVAL", "INSTANCE", id, "RESUBMIT", null,
                Map.of("submissionVersion", versionNo), user.id(), null);
        return detail(id, user);
    }

    private void copyItems(long instanceId, long fromSubmission, long toSubmission) {
        jdbc.update("""
                INSERT INTO approval_expense_item(instance_id,submission_version_id,item_no,expense_type,
                    business_date,vehicle_id,driver_id,description,amount,receipt_no,invoice_no,remark,
                    station_name,trailer_no,target_task_id)
                SELECT instance_id,?,item_no,expense_type,business_date,vehicle_id,driver_id,description,
                       amount,receipt_no,invoice_no,remark,station_name,trailer_no,target_task_id
                FROM approval_expense_item WHERE instance_id=? AND submission_version_id=?
                """, toSubmission, instanceId, fromSubmission);
    }

    private void bindSubmissionAttachments(long userId, List<Long> ids, long instanceId, long submissionId) {
        attachments.bindApprovalApplicationAttachments(userId, ids, instanceId, submissionId);
        if (ids != null) for (Long id : ids) jdbc.update("""
                INSERT INTO approval_submission_attachment(submission_version_id,attachment_id)
                VALUES(?,?)
                """, submissionId, id);
    }

    private void postExpenses(Map<String, Object> approval, long submissionId, long actor) {
        long instanceId = num(approval.get("id"));
        List<Map<String, Object>> items = jdbc.queryForList("""
                SELECT * FROM approval_expense_item WHERE instance_id=? AND submission_version_id=?
                ORDER BY item_no
                """, instanceId, submissionId);
        if (items.isEmpty()) throw invalid("费用审批缺少正式明细");
        for (Map<String, Object> item : items) {
            int itemNo = ((Number) item.get("item_no")).intValue();
            String key = instanceId + ":" + itemNo;
            jdbc.update("""
                    INSERT INTO expense_entry(expense_no,approval_instance_id,approval_item_no,
                        application_scope,current_task_id,vehicle_id,driver_id,expense_type,business_date,
                        description,amount,receipt_no,invoice_no,remark,station_name,trailer_no,
                        source_type,source_record_key,status,created_by)
                    VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'APPROVAL',?,'ACTIVE',?)
                    ON DUPLICATE KEY UPDATE id=id
                    """, "E" + UUID.randomUUID().toString().replace("-", ""), instanceId, itemNo,
                    approval.get("application_scope"), item.get("target_task_id"), item.get("vehicle_id"),
                    item.get("driver_id"), item.get("expense_type"), item.get("business_date"),
                    item.get("description"), item.get("amount"), item.get("receipt_no"),
                    item.get("invoice_no"), item.get("remark"), item.get("station_name"),
                    item.get("trailer_no"), key, actor);
        }
        audit.write("EXPENSE", "APPROVAL", instanceId, "POST_ITEMS", null,
                Map.of("count", items.size(), "submissionId", submissionId), actor, null);
    }

    private boolean isOutsourced(AuthenticatedUser user) {
        return users.isDriverUser(user) && users.requireDriver(user).outsourced();
    }

    private void require(AuthenticatedUser user, String permission) {
        if (!has(user, permission))
            throw new AccessDeniedException(permission + " required");
    }

    private boolean has(AuthenticatedUser user, String code) {
        if (user == null || user.permissions() == null) return false;
        String alternate = switch (code) {
            case "BUTTON_APPROVALS_TODO_APPROVE" -> "BUTTON_MINI_APPROVALS_APPROVE";
            case "BUTTON_APPROVALS_TODO_RETURN" -> "BUTTON_MINI_APPROVALS_RETURN";
            case "BUTTON_EXPENSE_APPLY_SUBMIT" -> "BUTTON_MINI_EXPENSE_APPLY_SUBMIT";
            default -> null;
        };
        return hasDirect(user, code) || alternate != null && hasDirect(user, alternate);
    }

    private boolean hasDirect(AuthenticatedUser user, String code) {
        if (!user.permissions().contains(code)) return false;
        if (!code.startsWith("BUTTON_")) return true;
        String parent = switch (code) {
            case "BUTTON_APPROVALS_TODO_APPROVE", "BUTTON_APPROVALS_TODO_RETURN" -> "PAGE_APPROVALS_TODO";
            case "BUTTON_MINI_APPROVALS_APPROVE", "BUTTON_MINI_APPROVALS_RETURN" -> "PAGE_MINI_APPROVALS";
            case "BUTTON_EXPENSE_APPLY_SUBMIT" -> "PAGE_EXPENSE_APPLY";
            case "BUTTON_MINI_EXPENSE_APPLY_SUBMIT" -> "PAGE_MINI_EXPENSE_APPLY";
            case "BUTTON_MINI_TIRE_REQUEST_SUBMIT" -> "PAGE_MINI_TIRE_REQUEST";
            case "BUTTON_APPROVALS_FLOWS_MANAGE" -> "PAGE_APPROVALS_FLOWS";
            default -> null;
        };
        return parent == null || user.permissions().contains(parent);
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
    private BusinessException invalid(String message) { return new BusinessException("SYS_002", message); }
    private long num(Object value) { return ((Number) value).longValue(); }
    private Map<String, Object> one(String sql, Object... args) {
        return jdbc.queryForList(sql, args).stream().findFirst().map(LinkedHashMap::new).orElse(null);
    }
    private long insert(String sql, Object... args) {
        KeyHolder holder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, new String[]{"id"});
            for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
            return statement;
        }, holder);
        return Objects.requireNonNull(holder.getKey()).longValue();
    }
    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw invalid("申请摘要格式不正确"); }
    }
    private String requestDigest(Application input) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(json(input).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
