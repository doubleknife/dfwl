package com.dfwl.fleet.approval.service;

import com.dfwl.fleet.approval.domain.ApprovalEvidenceRequirement;
import com.dfwl.fleet.approval.domain.ApprovalTemplateCategory;
import com.dfwl.fleet.approval.dto.request.ApprovalExpenseConfigRequest;
import com.dfwl.fleet.approval.dto.request.ApprovalFlowUpdateRequest;
import com.dfwl.fleet.approval.dto.request.ApprovalTemplateCreateRequest;
import com.dfwl.fleet.approval.dto.response.ApprovalFlowConfigResponse;
import com.dfwl.fleet.approval.dto.response.ApprovalAvailableTemplateResponse;
import com.dfwl.fleet.approval.dto.response.ApprovalFlowResponse;
import com.dfwl.fleet.approval.dto.response.ApprovalTemplateResponse;
import com.dfwl.fleet.approval.repository.ApprovalRepository;
import com.dfwl.fleet.approval.repository.ApprovalTemplateRepository;
import com.dfwl.fleet.approval.repository.ApprovalTemplateRepository.ExpenseSettings;
import com.dfwl.fleet.approval.repository.ApprovalTemplateRepository.TemplateRecord;
import com.dfwl.fleet.approval.repository.ApprovalTemplateRepository.VersionSettings;
import com.dfwl.fleet.approval.spi.ApprovalExpenseResourcePolicy;
import com.dfwl.fleet.common.error.BusinessException;
import com.dfwl.fleet.common.error.ErrorCode;
import com.dfwl.fleet.security.AuthenticatedUser;
import com.dfwl.fleet.security.CurrentUserService;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApprovalTemplateService {

    private final ApprovalTemplateRepository templates;
    private final ApprovalRepository flows;
    private final CurrentUserService currentUsers;
    private final ApprovalExpenseResourcePolicy expensePolicy;

    public ApprovalTemplateService(ApprovalTemplateRepository templates, ApprovalRepository flows,
                                   CurrentUserService currentUsers, ApprovalExpenseResourcePolicy expensePolicy) {
        this.templates = templates;
        this.flows = flows;
        this.currentUsers = currentUsers;
        this.expensePolicy = expensePolicy;
    }

    public List<ApprovalAvailableTemplateResponse> available(AuthenticatedUser user) {
        if (user == null || user.roleId() == null || !user.permissions().contains("approval:create")) {
            throw new AccessDeniedException("approval:create required");
        }
        if (currentUsers.isDriverUser(user) && currentUsers.requireDriver(user).outsourced()) {
            return List.of();
        }
        return templates.available(user.roleId()).stream().map(row -> {
            List<String> modes = new java.util.ArrayList<>();
            List<String> attribution = new java.util.ArrayList<>();
            if ("EXPENSE".equals(row.category())) {
                if (row.allowSingle()) modes.add("SINGLE");
                if (row.allowExcel()) modes.add("EXCEL");
                if (row.allowRoute()) attribution.add("ROUTE");
                if (row.allowDaily()) attribution.add("DAILY");
            }
            return new ApprovalAvailableTemplateResponse(row.templateId(), row.templateCode(), row.category(),
                    row.flowId(), row.versionNo(), row.flowName(), row.evidenceRequirement(),
                    row.expenseType(), modes, attribution);
        }).toList();
    }

    public List<ApprovalTemplateResponse> list() {
        return templates.list().stream().map(this::response).toList();
    }

    public ApprovalTemplateResponse find(long templateId) {
        return response(requireTemplate(templateId));
    }

    public List<ApprovalFlowResponse> versions(long templateId) {
        requireTemplate(templateId);
        return templates.versionIds(templateId).stream()
                .map(id -> flows.flowResponse(id).orElseThrow(() -> new BusinessException(ErrorCode.DATA_001)))
                .toList();
    }

    public ApprovalFlowConfigResponse active(long templateId) {
        requireTemplate(templateId);
        long flowId = templates.activeFlowId(templateId)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPROVAL_001));
        return version(templateId, flowId);
    }

    public ApprovalFlowConfigResponse version(long templateId, long flowId) {
        requireTemplate(templateId);
        ApprovalRepository.FlowRecord flow = flows.findFlow(flowId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DATA_001));
        if (flow.templateId() != templateId) {
            throw new BusinessException(ErrorCode.DATA_001);
        }
        VersionSettings settings = templates.settings(flowId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DATA_001));
        ApprovalFlowConfigResponse.ExpenseConfig expense = templates.expenseConfig(flowId)
                .map(this::expenseResponse).orElse(null);
        return new ApprovalFlowConfigResponse(
                flows.flowResponse(flowId).orElseThrow(() -> new BusinessException(ErrorCode.DATA_001)),
                flows.flowNodes(flowId), settings.evidenceRequirement(), settings.configurationStatus(),
                templates.initiatorRoleIds(flowId), expense);
    }

    @Transactional
    public ApprovalFlowConfigResponse create(ApprovalTemplateCreateRequest request, long operatorId) {
        if (request.templateCode() == null || !request.templateCode().matches("[A-Z][A-Z0-9_]{0,63}")) {
            throw new BusinessException(ErrorCode.SYS_002);
        }
        ApprovalTemplateCategory category = category(request.category());
        // The dedicated tire creation path resolves TIRE_REQUEST by its legacy code.
        if (category == ApprovalTemplateCategory.TIRE && !"TIRE_REQUEST".equals(request.templateCode())) {
            throw new BusinessException(ErrorCode.APPROVAL_002);
        }
        String approvalType = switch (category) {
            case TIRE -> "TIRE_REQUEST";
            case EXPENSE -> "EXPENSE";
            case OTHER -> "GENERAL";
        };
        String evidence = evidence(request.evidenceRequirement());
        validateNodes(request.nodes());
        List<Long> roles = category == ApprovalTemplateCategory.TIRE && request.initiatorRoleIds() == null
                ? List.of() : validateRoles(request.initiatorRoleIds(), category != ApprovalTemplateCategory.TIRE);
        ExpenseSettings expense = expenseSettings(request.expenseConfig(), category == ApprovalTemplateCategory.EXPENSE);
        if (templates.codeExists(request.templateCode())) {
            throw new BusinessException(ErrorCode.DATA_002);
        }
        try {
            long templateId = templates.create(request.templateCode(), category.name(), approvalType, operatorId);
            long flowId = flows.createFlowVersion(templateId, approvalType, request.flowName(), 1,
                    evidence, "READY", operatorId);
            request.nodes().stream().sorted(Comparator.comparing(ApprovalFlowUpdateRequest.Node::nodeOrder))
                    .forEach(node -> flows.createFlowNode(flowId, node));
            templates.saveInitiatorRoles(flowId, roles);
            if (expense != null) {
                templates.saveExpenseConfig(flowId, expense);
            }
            return version(templateId, flowId);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.DATA_002);
        }
    }

    // Called by the existing maintenance path inside its row transaction.
    public void copyOrReplaceVersionSettings(long sourceFlowId, long newFlowId, long templateId,
                                             ApprovalFlowUpdateRequest request) {
        TemplateRecord template = requireTemplate(templateId);
        VersionSettings previous = templates.settings(sourceFlowId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DATA_001));
        List<Long> roles = request.initiatorRoleIds() == null
                ? templates.initiatorRoleIds(sourceFlowId)
                : validateRoles(request.initiatorRoleIds(), true);
        ExpenseSettings expense = null;
        if ("EXPENSE".equals(template.category())) {
            expense = request.expenseConfig() == null
                    ? templates.expenseConfig(sourceFlowId).orElse(null)
                    : expenseSettings(request.expenseConfig(), true);
        } else if (request.expenseConfig() != null) {
            throw new BusinessException(ErrorCode.APPROVAL_002);
        }
        if (request.evidenceRequirement() != null) {
            evidence(request.evidenceRequirement());
        }
        templates.saveInitiatorRoles(newFlowId, roles);
        if (expense != null) {
            templates.saveExpenseConfig(newFlowId, expense);
        }
        boolean ready = ("TIRE".equals(template.category()) || !roles.isEmpty())
                && (!"EXPENSE".equals(template.category()) || expense != null);
        templates.updateConfigurationStatus(newFlowId, ready ? "READY" : previous.configurationStatus());
    }

    public String evidenceForNewVersion(long sourceFlowId, String requested) {
        return requested == null
                ? templates.settings(sourceFlowId).orElseThrow(() -> new BusinessException(ErrorCode.DATA_001))
                        .evidenceRequirement()
                : evidence(requested);
    }

    private TemplateRecord requireTemplate(long id) {
        return templates.find(id).orElseThrow(() -> new BusinessException(ErrorCode.DATA_001));
    }

    private ApprovalTemplateResponse response(TemplateRecord template) {
        return new ApprovalTemplateResponse(template.id(), template.templateCode(), template.category(),
                template.approvalType(), template.activeFlowId());
    }

    private ApprovalFlowConfigResponse.ExpenseConfig expenseResponse(ExpenseSettings config) {
        List<String> modes = new java.util.ArrayList<>();
        if (config.allowSingle()) modes.add("SINGLE");
        if (config.allowExcel()) modes.add("EXCEL");
        List<String> attribution = new java.util.ArrayList<>();
        if (config.allowRoute()) attribution.add("ROUTE");
        if (config.allowDaily()) attribution.add("DAILY");
        return new ApprovalFlowConfigResponse.ExpenseConfig(config.expenseType(), modes, attribution);
    }

    private ApprovalTemplateCategory category(String raw) {
        try {
            return ApprovalTemplateCategory.valueOf(raw);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new BusinessException(ErrorCode.SYS_002);
        }
    }

    private String evidence(String raw) {
        try {
            return ApprovalEvidenceRequirement.valueOf(raw).name();
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new BusinessException(ErrorCode.SYS_002);
        }
    }

    private List<Long> validateRoles(List<Long> roleIds, boolean required) {
        if (roleIds == null || (required && roleIds.isEmpty()) || new HashSet<>(roleIds).size() != roleIds.size()) {
            throw new BusinessException(ErrorCode.APPROVAL_002);
        }
        for (Long id : roleIds) {
            if (id == null || !templates.activeRoleExists(id)) {
                throw new BusinessException(ErrorCode.APPROVAL_002);
            }
        }
        return roleIds;
    }

    private ExpenseSettings expenseSettings(ApprovalExpenseConfigRequest request, boolean required) {
        if (!required) {
            if (request != null) throw new BusinessException(ErrorCode.APPROVAL_002);
            return null;
        }
        if (request == null) throw new BusinessException(ErrorCode.APPROVAL_002);
        expensePolicy.ensureSupportedExpenseType(request.expenseType());
        Set<String> modes = validOptions(request.inputModes(), Set.of("SINGLE", "EXCEL"));
        Set<String> attribution = validOptions(request.attributionTypes(), Set.of("ROUTE", "DAILY"));
        return new ExpenseSettings(request.expenseType(), modes.contains("SINGLE"), modes.contains("EXCEL"),
                attribution.contains("ROUTE"), attribution.contains("DAILY"));
    }

    private Set<String> validOptions(List<String> values, Set<String> allowed) {
        if (values == null || values.isEmpty() || values.contains(null) || !allowed.containsAll(values)
                || new HashSet<>(values).size() != values.size()) {
            throw new BusinessException(ErrorCode.APPROVAL_002);
        }
        return Set.copyOf(values);
    }

    private void validateNodes(List<ApprovalFlowUpdateRequest.Node> nodes) {
        if (nodes == null || nodes.isEmpty()) throw new BusinessException(ErrorCode.APPROVAL_002);
        Set<Integer> orders = new HashSet<>();
        for (ApprovalFlowUpdateRequest.Node node : nodes) {
            if (node.nodeOrder() == null || node.nodeOrder() < 1 || !orders.add(node.nodeOrder())) {
                throw new BusinessException(ErrorCode.APPROVAL_002);
            }
        }
        for (int i = 1; i <= orders.size(); i++) {
            if (!orders.contains(i)) throw new BusinessException(ErrorCode.APPROVAL_002);
        }
    }
}
