package com.dfwl.fleet.approval.service;

import com.dfwl.fleet.approval.dto.request.ApprovalCreateRequest;
import com.dfwl.fleet.approval.dto.response.ApprovalResponse;
import com.dfwl.fleet.approval.policy.ApprovalAccessPolicy;
import com.dfwl.fleet.approval.repository.ApprovalRepository;
import com.dfwl.fleet.approval.repository.ApprovalRepository.FlowRecord;
import com.dfwl.fleet.approval.repository.ApprovalTemplateRepository;
import com.dfwl.fleet.approval.repository.ApprovalTemplateRepository.ExpenseSettings;
import com.dfwl.fleet.approval.repository.ApprovalTemplateRepository.TemplateRecord;
import com.dfwl.fleet.approval.repository.ApprovalTemplateRepository.VersionSettings;
import com.dfwl.fleet.approval.spi.ApprovalCreationCommand;
import com.dfwl.fleet.approval.spi.ApprovalExpenseResourcePolicy;
import com.dfwl.fleet.common.error.BusinessException;
import com.dfwl.fleet.common.error.ErrorCode;
import com.dfwl.fleet.security.AuthenticatedUser;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/** Selects one immutable flow version and validates an application before any approval rows are written. */
@Service
public class ApprovalInitiationService {

    private final ApprovalTemplateRepository templates;
    private final ApprovalRepository flows;
    private final ApprovalAccessPolicy access;
    private final ApprovalExpenseResourcePolicy expenses;

    public ApprovalInitiationService(ApprovalTemplateRepository templates, ApprovalRepository flows,
                                     ApprovalAccessPolicy access, ApprovalExpenseResourcePolicy expenses) {
        this.templates = templates;
        this.flows = flows;
        this.access = access;
        this.expenses = expenses;
    }

    public Selected forCreate(ApprovalCreateRequest request, AuthenticatedUser user) {
        requireCreatePermission(user);
        if (request.templateId() == null) {
            return legacy(request, user);
        }
        TemplateRecord template = requireTemplate(request.templateId());
        templates.lock(template.id());
        FlowRecord flow = active(template.id());
        return configured(request, user, template, flow, false);
    }

    public Selected forResubmit(ApprovalResponse approval, ApprovalCreateRequest request, AuthenticatedUser user) {
        requireCreatePermission(user);
        if (!Objects.equals(approval.applicantUserId(), user.id())) {
            throw new AccessDeniedException("only original applicant may resubmit");
        }
        FlowRecord flow = flows.findFlow(approval.flowId())
                .orElseThrow(() -> new BusinessException(ErrorCode.APPROVAL_001));
        TemplateRecord template = requireTemplate(flow.templateId());
        if (request.templateId() != null && !request.templateId().equals(template.id())) {
            throw new BusinessException(ErrorCode.APPROVAL_002);
        }
        if (request.approvalType() != null && !request.approvalType().equals(approval.approvalType())) {
            throw new BusinessException(ErrorCode.APPROVAL_002);
        }
        if (request.businessType() != null && !request.businessType().equals(approval.businessType())) {
            throw new BusinessException(ErrorCode.APPROVAL_002);
        }
        if (request.businessId() != null && !request.businessId().equals(approval.businessId())) {
            throw new BusinessException(ErrorCode.APPROVAL_002);
        }
        if ("TIRE".equals(template.category())) {
            access.ensureCanSubmit(user, approval.approvalType(), approval.businessType());
            validateEvidence(flow.id(), request.attachmentIds());
            return new Selected(flow, approval.businessType(), approval.businessId(), request.businessSnapshot());
        }
        VersionSettings settings = settings(flow.id());
        if ("NEEDS_REVIEW".equals(settings.configurationStatus())
                && template.templateCode().equals(approval.approvalType())) {
            access.ensureCanSubmit(user, approval.approvalType(), approval.businessType());
            validateEvidence(flow.id(), request.attachmentIds());
            return new Selected(flow, approval.businessType(), approval.businessId(), request.businessSnapshot());
        }
        return configured(request, user, template, flow, true);
    }

    public FlowRecord forTire(ApprovalCreationCommand command) {
        if (!"TIRE_REQUEST".equals(command.approvalType())
                || !"TIRE_REQUEST".equals(command.businessType())) {
            throw new BusinessException(ErrorCode.APPROVAL_002);
        }
        TemplateRecord template = templates.findByCode("TIRE_REQUEST")
                .orElseThrow(() -> new BusinessException(ErrorCode.APPROVAL_001));
        if (!"TIRE".equals(template.category())) throw new BusinessException(ErrorCode.APPROVAL_002);
        templates.lock(template.id());
        FlowRecord flow = active(template.id());
        validateEvidence(flow.id(), command.attachmentIds());
        return flow;
    }

    private Selected legacy(ApprovalCreateRequest request, AuthenticatedUser user) {
        String type = request.approvalType();
        access.ensureCanSubmit(user, type, request.businessType());
        if ("TIRE_REQUEST".equals(type) || "TIRE_REQUEST".equals(request.businessType())) {
            throw new BusinessException(ErrorCode.APPROVAL_002);
        }
        if (type == null || type.isBlank() || templates.activeCountForType(type) != 1) {
            throw new BusinessException(ErrorCode.APPROVAL_001);
        }
        TemplateRecord template = templates.findByCode(type)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPROVAL_001));
        if ("TIRE".equals(template.category())) throw new BusinessException(ErrorCode.APPROVAL_002);
        templates.lock(template.id());
        if (templates.activeCountForType(type) != 1) throw new BusinessException(ErrorCode.APPROVAL_001);
        FlowRecord flow = active(template.id());
        VersionSettings settings = settings(flow.id());
        if ("READY".equals(settings.configurationStatus())) {
            return configured(request, user, template, flow, false);
        }
        if (!"NEEDS_REVIEW".equals(settings.configurationStatus())) {
            throw new BusinessException(ErrorCode.APPROVAL_001);
        }
        if (request.businessType() == null || request.businessType().isBlank()) {
            throw new BusinessException(ErrorCode.SYS_002);
        }
        validateEvidence(flow.id(), request.attachmentIds());
        return new Selected(flow, request.businessType(), request.businessId(), request.businessSnapshot());
    }

    private Selected configured(ApprovalCreateRequest request, AuthenticatedUser user,
                                TemplateRecord template, FlowRecord flow, boolean resubmit) {
        if ("TIRE".equals(template.category())) throw new BusinessException(ErrorCode.APPROVAL_002);
        if (!"READY".equals(settings(flow.id()).configurationStatus())) {
            throw new BusinessException(ErrorCode.APPROVAL_001);
        }
        if (user.roleId() == null || !templates.canInitiate(flow.id(), user.roleId())) {
            throw new AccessDeniedException("role cannot initiate this approval version");
        }
        access.ensureCanSubmitTemplate(user);
        if (request.approvalType() != null && !request.approvalType().equals(template.approvalType())) {
            throw new BusinessException(ErrorCode.APPROVAL_002);
        }
        if (request.businessType() != null && !request.businessType().equals(template.approvalType())) {
            throw new BusinessException(ErrorCode.APPROVAL_002);
        }
        validateEvidence(flow.id(), request.attachmentIds());
        Map<String, Object> snapshot = new LinkedHashMap<>(request.businessSnapshot());
        if ("EXPENSE".equals(template.category())) {
            ExpenseSettings config = templates.expenseConfig(flow.id())
                    .orElseThrow(() -> new BusinessException(ErrorCode.APPROVAL_001));
            String suppliedType = string(snapshot.get("expenseType"));
            if (suppliedType != null && !suppliedType.equals(config.expenseType())) {
                throw new BusinessException(ErrorCode.APPROVAL_002);
            }
            snapshot.put("expenseType", config.expenseType());
            String attribution = request.attributionType() == null
                    ? string(snapshot.get("attributionType")) : request.attributionType();
            if (request.attributionType() != null && snapshot.get("attributionType") != null
                    && !request.attributionType().equals(string(snapshot.get("attributionType")))) {
                throw new BusinessException(ErrorCode.EXPENSE_003);
            }
            Long snapshotRoute = number(snapshot.get("routeId"));
            Long routeId = request.routeId() == null ? snapshotRoute : request.routeId();
            if (request.routeId() != null && snapshotRoute != null && !request.routeId().equals(snapshotRoute)) {
                throw new BusinessException(ErrorCode.EXPENSE_003);
            }
            if (!("ROUTE".equals(attribution) && config.allowRoute()
                    || "DAILY".equals(attribution) && config.allowDaily())) {
                throw new BusinessException(ErrorCode.EXPENSE_003);
            }
            expenses.ensureCanSubmit(user, number(snapshot.get("vehicleId")), number(snapshot.get("driverId")),
                    attribution, routeId);
            snapshot.put("attributionType", attribution);
            if (routeId == null) snapshot.remove("routeId");
            else snapshot.put("routeId", routeId);
        } else if (request.attributionType() != null || request.routeId() != null) {
            throw new BusinessException(ErrorCode.APPROVAL_002);
        }
        return new Selected(flow, template.approvalType(), request.businessId(), snapshot);
    }

    private void requireCreatePermission(AuthenticatedUser user) {
        if (user == null || user.permissions() == null || !user.permissions().contains("approval:create")) {
            throw new AccessDeniedException("approval:create required");
        }
    }

    private TemplateRecord requireTemplate(long id) {
        return templates.find(id).orElseThrow(() -> new BusinessException(ErrorCode.DATA_001));
    }

    private FlowRecord active(long templateId) {
        long id = templates.activeFlowId(templateId)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPROVAL_001));
        return flows.findFlow(id).orElseThrow(() -> new BusinessException(ErrorCode.APPROVAL_001));
    }

    private VersionSettings settings(long flowId) {
        return templates.settings(flowId).orElseThrow(() -> new BusinessException(ErrorCode.APPROVAL_001));
    }

    private void validateEvidence(long flowId, List<Long> attachmentIds) {
        if (attachmentIds != null && (attachmentIds.stream().anyMatch(Objects::isNull)
                || new java.util.HashSet<>(attachmentIds).size() != attachmentIds.size())) {
            throw new BusinessException(ErrorCode.SYS_002);
        }
        if ("REQUIRED".equals(settings(flowId).evidenceRequirement())
                && (attachmentIds == null || attachmentIds.isEmpty())) {
            throw new BusinessException(ErrorCode.APPROVAL_004);
        }
    }

    private String string(Object value) {
        return value == null ? null : value.toString();
    }

    private Long number(Object value) {
        if (value == null) return null;
        try {
            return Long.valueOf(value.toString());
        } catch (NumberFormatException ex) {
            throw new BusinessException(ErrorCode.SYS_002);
        }
    }

    public record Selected(FlowRecord flow, String businessType, Long businessId,
                           Map<String, Object> snapshot) {
    }
}
