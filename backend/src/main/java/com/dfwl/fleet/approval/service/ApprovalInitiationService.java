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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
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
            requireSnapshot(request);
            access.ensureCanSubmit(user, approval.approvalType(), approval.businessType());
            return new Selected(flow, approval.businessType(), approval.businessId(), request.businessSnapshot());
        }
        VersionSettings settings = settings(flow.id());
        if ("NEEDS_REVIEW".equals(settings.configurationStatus())
                && template.templateCode().equals(approval.approvalType())) {
            requireSnapshot(request);
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

    public void validateResubmissionEvidence(long flowId, List<Long> attachmentIds) {
        validateEvidence(flowId, attachmentIds);
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
        requireSnapshot(request);
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
        if (!"EXPENSE".equals(template.category())) requireSnapshot(request);
        Map<String, Object> snapshot = new LinkedHashMap<>(
                request.businessSnapshot() == null ? Map.of() : request.businessSnapshot());
        if ("EXPENSE".equals(template.category())) {
            ExpenseSettings config = templates.expenseConfig(flow.id())
                    .orElseThrow(() -> new BusinessException(ErrorCode.APPROVAL_001));
            String suppliedType = string(snapshot.get("expenseType"));
            if (suppliedType != null && !suppliedType.equals(config.expenseType())) {
                throw new BusinessException(ErrorCode.APPROVAL_002);
            }
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
            List<Map<String, Object>> items = expenseItems(request, snapshot, config.expenseType(),
                    user, attribution, routeId);
            snapshot.clear();
            snapshot.put("templateId", template.id());
            snapshot.put("expenseType", config.expenseType());
            snapshot.put("attributionType", attribution);
            if (routeId != null) snapshot.put("routeId", routeId);
            snapshot.put("items", items);
        } else if (request.attributionType() != null || request.routeId() != null || request.items() != null) {
            throw new BusinessException(ErrorCode.APPROVAL_002);
        }
        return new Selected(flow, template.approvalType(), request.businessId(), snapshot);
    }

    private void requireCreatePermission(AuthenticatedUser user) {
        if (user == null || user.permissions() == null || !user.permissions().contains("approval:create")) {
            throw new AccessDeniedException("approval:create required");
        }
    }

    private void requireSnapshot(ApprovalCreateRequest request) {
        if (request.businessSnapshot() == null) throw new BusinessException(ErrorCode.SYS_002);
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

    private List<Map<String, Object>> expenseItems(ApprovalCreateRequest request,
                                                    Map<String, Object> original, String expenseType,
                                                    AuthenticatedUser user, String attribution, Long routeId) {
        boolean legacyFields = List.of("itemNo", "vehicleId", "driverId", "amount", "businessDate",
                "remark", "energyDetail", "penaltyDetail", "repairDetail", "tollDetail")
                .stream().anyMatch(original::containsKey);
        if (request.items() != null && (legacyFields || original.containsKey("items")
                || original.containsKey("expenseType"))) {
            throw new BusinessException(ErrorCode.SYS_002.name(), "items 与旧单条字段不能同时提交");
        }
        if (request.items() == null && original.containsKey("items")) {
            throw new BusinessException(ErrorCode.SYS_002.name(), "费用明细应通过 items 提交");
        }
        List<Map<String, Object>> supplied;
        if (request.items() != null) {
            supplied = request.items();
        } else {
            Map<String, Object> single = new LinkedHashMap<>();
            single.put("itemNo", "1");
            for (String key : List.of("vehicleId", "driverId", "amount", "businessDate", "remark",
                    "energyDetail", "penaltyDetail", "repairDetail", "tollDetail")) {
                if (original.containsKey(key)) single.put(key, original.get(key));
            }
            supplied = List.of(single);
        }
        if (supplied.isEmpty()) {
            throw new BusinessException(ErrorCode.SYS_002.name(), "items 不能为空");
        }
        List<Map<String, Object>> normalized = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();
        for (Map<String, Object> raw : supplied) {
            String itemNo = raw == null ? null : string(raw.get("itemNo"));
            if (itemNo == null || !itemNo.matches("[A-Za-z0-9_-]{1,64}")) {
                throw new BusinessException(ErrorCode.SYS_002.name(), "itemNo 不合法: " + itemNo);
            }
            if (!seen.add(itemNo)) {
                throw new BusinessException(ErrorCode.SYS_002.name(), "itemNo=" + itemNo + " 重复");
            }
            try {
                if (raw.keySet().stream().anyMatch(key -> !List.of("itemNo", "vehicleId", "driverId",
                        "amount", "businessDate", "remark", "energyDetail", "penaltyDetail",
                        "repairDetail", "tollDetail").contains(key))) {
                    throw new BusinessException(ErrorCode.SYS_002);
                }
                if (raw.get("remark") != null && raw.get("remark").toString().length() > 1000) {
                    throw new BusinessException(ErrorCode.SYS_002);
                }
                Long vehicleId = number(raw.get("vehicleId"));
                Long driverId = number(raw.get("driverId"));
                if (vehicleId == null || vehicleId <= 0 || driverId != null && driverId <= 0) {
                    throw new BusinessException(ErrorCode.SYS_002);
                }
                BigDecimal amount = new BigDecimal(Objects.toString(raw.get("amount"), ""));
                if (amount.signum() <= 0 || amount.scale() > 2 || amount.precision() > 18) {
                    throw new BusinessException(ErrorCode.SYS_002);
                }
                LocalDate businessDate = LocalDate.parse(Objects.toString(raw.get("businessDate"), ""));
                expenses.ensureCanSubmit(user, vehicleId, driverId, attribution, routeId);
                expenses.ensureValidDetails(expenseType, raw);
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("itemNo", itemNo);
                item.put("vehicleId", vehicleId);
                if (driverId != null) item.put("driverId", driverId);
                item.put("amount", amount);
                item.put("businessDate", businessDate.toString());
                for (String key : List.of("remark", "energyDetail", "penaltyDetail", "repairDetail", "tollDetail")) {
                    if (raw.get(key) != null) item.put(key, raw.get(key));
                }
                normalized.add(item);
            } catch (BusinessException ex) {
                throw new BusinessException(ex.code(), "itemNo=" + itemNo + ": " + ex.getMessage());
            } catch (AccessDeniedException ex) {
                throw new AccessDeniedException("itemNo=" + itemNo + ": " + ex.getMessage());
            } catch (NumberFormatException | DateTimeParseException ex) {
                throw new BusinessException(ErrorCode.SYS_002.name(), "itemNo=" + itemNo + ": 字段格式不合法");
            }
        }
        return normalized;
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
