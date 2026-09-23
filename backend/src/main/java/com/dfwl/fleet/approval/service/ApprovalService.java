package com.dfwl.fleet.approval.service;

import com.dfwl.fleet.approval.dto.request.ApprovalActionRequest;
import com.dfwl.fleet.approval.dto.request.ApprovalCreateRequest;
import com.dfwl.fleet.approval.dto.response.ApprovalDetailResponse;
import com.dfwl.fleet.approval.dto.response.ApprovalFlowDetailResponse;
import com.dfwl.fleet.approval.dto.response.ApprovalFlowResponse;
import com.dfwl.fleet.approval.dto.request.ApprovalFlowUpdateRequest;
import com.dfwl.fleet.approval.dto.query.ApprovalListScope;
import com.dfwl.fleet.approval.dto.response.ApprovalResponse;
import com.dfwl.fleet.approval.repository.ApprovalRepository;
import com.dfwl.fleet.approval.repository.ApprovalTemplateRepository;
import com.dfwl.fleet.approval.spi.ApprovalBusinessContext;
import com.dfwl.fleet.approval.spi.ApprovalBusinessHandler;
import com.dfwl.fleet.approval.spi.ApprovalCreationCommand;
import com.dfwl.fleet.approval.spi.ApprovalSubmissionContext;
import com.dfwl.fleet.approval.spi.ApprovalResubmissionContext;
import com.dfwl.fleet.attachment.service.AttachmentService;
import com.dfwl.fleet.approval.repository.ApprovalRepository.FlowNodeRecord;
import com.dfwl.fleet.approval.repository.ApprovalRepository.FlowRecord;
import com.dfwl.fleet.approval.repository.ApprovalRepository.SubmissionRecord;
import com.dfwl.fleet.approval.repository.ApprovalRepository.TaskRecord;
import com.dfwl.fleet.common.api.PageResponse;
import com.dfwl.fleet.common.error.BusinessException;
import com.dfwl.fleet.common.error.ErrorCode;
import com.dfwl.fleet.security.AuthenticatedUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApprovalService {

    private static final DateTimeFormatter APPROVAL_NO_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final ApprovalRepository repository;
    private final ApprovalTemplateRepository templateRepository;
    private final ApprovalTemplateService templateService;
    private final ApprovalInitiationService initiationService;
    private final AttachmentService attachmentService;
    private final ObjectMapper objectMapper;
    private final List<ApprovalBusinessHandler> businessHandlers;

    public ApprovalService(ApprovalRepository repository, ApprovalTemplateRepository templateRepository,
                           ApprovalTemplateService templateService, ApprovalInitiationService initiationService,
                           AttachmentService attachmentService,
                           ObjectMapper objectMapper, List<ApprovalBusinessHandler> businessHandlers) {
        this.repository = repository;
        this.templateRepository = templateRepository;
        this.templateService = templateService;
        this.initiationService = initiationService;
        this.attachmentService = attachmentService;
        this.objectMapper = objectMapper;
        this.businessHandlers = businessHandlers;
    }

    public PageResponse<ApprovalResponse> list(int pageNo, int pageSize) {
        return repository.list(pageNo, Math.min(Math.max(pageSize, 1), 200));
    }

    public PageResponse<ApprovalResponse> list(ApprovalListScope scope, String approvalType, String status,
                                               AuthenticatedUser user, int pageNo, int pageSize) {
        ApprovalListScope effectiveScope = scope == null ? ApprovalListScope.ALL : scope;
        if (effectiveScope == ApprovalListScope.ALL && !user.permissions().contains("approval:history:view")) {
            throw new AccessDeniedException(ErrorCode.AUTH_003.defaultMessage());
        }
        return repository.list(effectiveScope, approvalType, status, user.id(), pageNo, Math.min(Math.max(pageSize, 1), 200));
    }

    public PageResponse<ApprovalFlowResponse> listFlows(String approvalType, String status, int pageNo, int pageSize) {
        return repository.listFlows(approvalType, status, pageNo, Math.min(Math.max(pageSize, 1), 200));
    }

    public ApprovalFlowDetailResponse flowDetail(long flowId) {
        return repository.flowDetail(flowId).orElseThrow(() -> new BusinessException(ErrorCode.DATA_001));
    }

    public List<ApprovalFlowResponse> flowVersions(String approvalType) {
        return repository.flowVersions(approvalType);
    }

    public ApprovalFlowDetailResponse activeFlowDetail(String approvalType) {
        return repository.activeFlowDetail(approvalType).orElseThrow(() -> new BusinessException(ErrorCode.DATA_001));
    }

    @Transactional
    public ApprovalResponse create(ApprovalCreateRequest request, AuthenticatedUser user) {
        ApprovalInitiationService.Selected selected = initiationService.forCreate(request, user);
        return createSelected(selected.flow(), selected.businessType(), selected.businessId(),
                selected.snapshot(), request.attachmentIds(), List.of(), user.id());
    }

    @Transactional
    public ApprovalResponse createBusinessApproval(ApprovalCreationCommand request, long operatorId) {
        FlowRecord flow = initiationService.forTire(request);
        return createSelected(flow, request.businessType(), request.businessId(), request.businessSnapshot(),
                List.of(), request.attachmentIds(), operatorId);
    }

    private ApprovalResponse createSelected(FlowRecord flow, String businessType, Long businessId,
                                            Map<String, Object> businessSnapshot, List<Long> attachmentIds,
                                            List<Long> referencedEvidenceIds, long operatorId) {
        attachmentService.requireTemporaryApprovalAttachments(operatorId, attachmentIds);
        attachmentService.requireTireOcrEvidence(operatorId, referencedEvidenceIds);
        FlowNodeRecord firstNode = repository.firstNode(flow.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.APPROVAL_001));
        long instanceId = repository.createInstance(nextApprovalNo(), flow, businessType, businessId, operatorId);
        Map<String, Object> snapshot = snapshotWithAttachments(businessSnapshot, attachmentIds);
        long submissionId = repository.createSubmission(instanceId, 1, toJson(snapshot), operatorId);
        attachmentService.bindApprovalApplicationAttachments(operatorId, attachmentIds, instanceId, submissionId);
        repository.linkSubmissionEvidence(submissionId, evidenceIds(attachmentIds, referencedEvidenceIds));
        repository.createTask(instanceId, submissionId, firstNode);
        ApprovalResponse approval = find(instanceId);
        ApprovalSubmissionContext submission = toSubmissionContext(requireSubmission(submissionId));
        ApprovalBusinessContext context = toBusinessContext(approval);
        businessHandlers.stream()
                .filter(handler -> handler.supports(context))
                .forEach(handler -> handler.onCreated(context, submission, operatorId));
        return find(instanceId);
    }

    @Transactional
    public ApprovalResponse approve(long id, ApprovalActionRequest request, long operatorId) {
        ApprovalResponse approval = requirePending(id);
        TaskRecord task = requireCurrentTask(id, operatorId);
        attachmentService.requireTemporaryApprovalActionAttachments(operatorId, actionAttachmentIds(request));
        if (!repository.finishTask(task.id(), "APPROVED")) {
            throw new BusinessException(ErrorCode.APPROVAL_002);
        }
        long actionId = repository.insertAction(task.id(), "APPROVE", operatorId, request == null ? null : request.comment(), null);
        attachmentService.bindApprovalActionAttachments(operatorId, actionAttachmentIds(request), actionId);
        repository.nextNode(approval.flowId(), task.nodeOrder())
                .ifPresentOrElse(next -> {
                    repository.moveToNode(id, next.nodeOrder());
                    repository.createTask(id, task.submissionVersionId(), next);
                }, () -> {
                    ApprovalSubmissionContext submission = toSubmissionContext(requireSubmission(task.submissionVersionId()));
                    ApprovalBusinessContext context = toBusinessContext(approval);
                    businessHandlers.stream()
                            .filter(handler -> handler.supports(context))
                            .forEach(handler -> handler.onApproved(context, submission, operatorId));
                    repository.complete(id);
                });
        return find(id);
    }

    @Transactional
    public ApprovalResponse returnApplicant(long id, ApprovalActionRequest request, long operatorId) {
        ApprovalResponse approval = requirePending(id);
        TaskRecord task = requireCurrentTask(id, operatorId);
        attachmentService.requireTemporaryApprovalActionAttachments(operatorId, actionAttachmentIds(request));
        if (!repository.finishTask(task.id(), "RETURNED")) {
            throw new BusinessException(ErrorCode.APPROVAL_002);
        }
        long actionId = repository.insertAction(task.id(), "RETURN_APPLICANT", operatorId, request == null ? null : request.comment(), null);
        attachmentService.bindApprovalActionAttachments(operatorId, actionAttachmentIds(request), actionId);
        repository.invalidatePendingTasksForSubmission(id, task.submissionVersionId());
        ApprovalSubmissionContext submission = toSubmissionContext(requireSubmission(task.submissionVersionId()));
        ApprovalBusinessContext context = toBusinessContext(approval);
        businessHandlers.stream()
                .filter(handler -> handler.supports(context))
                .forEach(handler -> handler.onReturnedToApplicant(context, submission, operatorId));
        repository.returnApplicant(id);
        return find(id);
    }

    @Transactional
    public ApprovalResponse returnNode(long id, ApprovalActionRequest request, long operatorId) {
        ApprovalResponse approval = requirePending(id);
        TaskRecord task = requireCurrentTask(id, operatorId);
        int targetOrder = request == null || request.targetNodeOrder() == null ? 1 : request.targetNodeOrder();
        if (targetOrder >= task.nodeOrder() || repository.nodeByOrder(approval.flowId(), targetOrder).isEmpty()) {
            throw new BusinessException(ErrorCode.APPROVAL_002);
        }
        attachmentService.requireTemporaryApprovalActionAttachments(operatorId, actionAttachmentIds(request));
        if (!repository.finishTask(task.id(), "RETURNED")) {
            throw new BusinessException(ErrorCode.APPROVAL_002);
        }
        long actionId = repository.insertAction(task.id(), "RETURN_NODE", operatorId, request == null ? null : request.comment(), targetOrder);
        attachmentService.bindApprovalActionAttachments(operatorId, actionAttachmentIds(request), actionId);
        repository.invalidatePendingTasksForSubmission(id, task.submissionVersionId());
        FlowNodeRecord target = repository.nodeByOrder(approval.flowId(), targetOrder)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPROVAL_002));
        repository.moveToNode(id, target.nodeOrder());
        repository.createTask(id, task.submissionVersionId(), target);
        return find(id);
    }

    @Transactional
    public ApprovalResponse resubmit(long id, ApprovalCreateRequest request, AuthenticatedUser user) {
        repository.lockInstance(id);
        ApprovalResponse approval = find(id);
        if (!"RETURNED_TO_APPLICANT".equals(approval.status())) {
            throw new BusinessException(ErrorCode.APPROVAL_002);
        }
        ApprovalInitiationService.Selected selected = initiationService.forResubmit(approval, request, user);
        long operatorId = user.id();
        ApprovalBusinessContext context = toBusinessContext(approval);
        ApprovalResubmissionContext prepared = new ApprovalResubmissionContext(selected.snapshot(), List.of());
        for (ApprovalBusinessHandler handler : businessHandlers) {
            if (handler.supports(context)) {
                prepared = handler.prepareResubmission(context, prepared.businessSnapshot(), operatorId);
            }
        }
        if ("TIRE_REQUEST".equals(approval.businessType())) {
            initiationService.validateResubmissionEvidence(approval.flowId(),
                    evidenceIds(request.attachmentIds(), prepared.referencedEvidenceIds()));
        }
        attachmentService.requireTemporaryApprovalAttachments(operatorId, request.attachmentIds());
        attachmentService.requireTireOcrEvidence(operatorId, prepared.referencedEvidenceIds());
        FlowNodeRecord firstNode = repository.firstNode(approval.flowId())
                .orElseThrow(() -> new BusinessException(ErrorCode.APPROVAL_001));
        repository.invalidatePendingTasks(id);
        Map<String, Object> snapshot = snapshotWithAttachments(prepared.businessSnapshot(), request.attachmentIds());
        long submissionId = repository.createSubmission(id, repository.nextSubmissionVersion(id), toJson(snapshot), operatorId);
        attachmentService.bindApprovalApplicationAttachments(operatorId, request.attachmentIds(), id, submissionId);
        repository.linkSubmissionEvidence(submissionId,
                evidenceIds(request.attachmentIds(), prepared.referencedEvidenceIds()));
        repository.moveToNode(id, firstNode.nodeOrder());
        repository.createTask(id, submissionId, firstNode);
        ApprovalSubmissionContext submission = toSubmissionContext(requireSubmission(submissionId));
        businessHandlers.stream()
                .filter(handler -> handler.supports(context))
                .forEach(handler -> handler.onResubmitted(context, submission, operatorId));
        return find(id);
    }

    @Transactional
    public void maintenance(long flowId) {
        FlowRecord flow = repository.findFlow(flowId).orElseThrow(() -> new BusinessException(ErrorCode.APPROVAL_001));
        templateRepository.lock(flow.templateId());
        flow = repository.findFlow(flowId).orElseThrow(() -> new BusinessException(ErrorCode.APPROVAL_001));
        if (!"ACTIVE".equals(flow.status())) {
            throw new BusinessException(ErrorCode.APPROVAL_002);
        }
        repository.updateFlowStatus(flowId, "MAINTENANCE");
    }

    @Transactional
    public ApprovalFlowResponse updateFlow(long flowId, ApprovalFlowUpdateRequest request, long operatorId) {
        FlowRecord flow = repository.findFlow(flowId).orElseThrow(() -> new BusinessException(ErrorCode.APPROVAL_001));
        templateRepository.lock(flow.templateId());
        flow = repository.findFlow(flowId).orElseThrow(() -> new BusinessException(ErrorCode.APPROVAL_001));
        if (!"MAINTENANCE".equals(flow.status())) {
            throw new BusinessException(ErrorCode.APPROVAL_002);
        }
        if (!repository.latestFlow(flow.templateId(), flowId)) {
            throw new BusinessException(ErrorCode.APPROVAL_002);
        }
        if (repository.hasPendingInstancesForTemplate(flow.templateId())) {
            throw new BusinessException(ErrorCode.APPROVAL_002);
        }
        validateNodes(request.nodes());
        long newFlowId = repository.createFlowVersion(flow.templateId(), flow.approvalType(), request.flowName(),
                repository.nextFlowVersion(flow.templateId()),
                templateService.evidenceForNewVersion(flowId, request.evidenceRequirement()), "NEEDS_REVIEW", operatorId);
        request.nodes().stream()
                .sorted(Comparator.comparing(ApprovalFlowUpdateRequest.Node::nodeOrder))
                .forEach(node -> repository.createFlowNode(newFlowId, node));
        templateService.copyOrReplaceVersionSettings(flowId, newFlowId, flow.templateId(), request);
        return repository.flowResponse(newFlowId).orElseThrow(() -> new BusinessException(ErrorCode.DATA_001));
    }

    @Transactional
    public ApprovalFlowResponse publishFlow(long flowId) {
        FlowRecord flow = repository.findFlow(flowId).orElseThrow(() -> new BusinessException(ErrorCode.APPROVAL_001));
        templateRepository.lock(flow.templateId());
        flow = repository.findFlow(flowId).orElseThrow(() -> new BusinessException(ErrorCode.APPROVAL_001));
        if (!repository.flowHasNodes(flowId)) {
            throw new BusinessException(ErrorCode.APPROVAL_001);
        }
        if (!"DRAFT".equals(flow.status()) && !"MAINTENANCE".equals(flow.status())) {
            throw new BusinessException(ErrorCode.APPROVAL_002);
        }
        if (templateRepository.activeFlowId(flow.templateId()).isPresent()) {
            throw new BusinessException(ErrorCode.APPROVAL_002);
        }
        repository.updateFlowStatus(flowId, "ACTIVE");
        return repository.flowResponse(flowId).orElseThrow(() -> new BusinessException(ErrorCode.DATA_001));
    }

    public ApprovalResponse find(long id) {
        return repository.find(id).orElseThrow(() -> new BusinessException(ErrorCode.DATA_001));
    }

    public ApprovalDetailResponse detail(long id, AuthenticatedUser user) {
        ApprovalDetailResponse detail = repository.detail(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.DATA_001));
        if (!user.permissions().contains("approval:history:view")
                && !repository.canViewInstance(id, user.id())) {
            throw new AccessDeniedException(ErrorCode.AUTH_003.defaultMessage());
        }
        FlowRecord flow = repository.findFlow(detail.approval().flowId())
                .orElseThrow(() -> new BusinessException(ErrorCode.APPROVAL_001));
        return new ApprovalDetailResponse(detail.approval(), detail.submissions(), detail.tasks(),
                flow.templateId(), templateService.version(flow.templateId(), flow.id()));
    }

    private ApprovalResponse requirePending(long id) {
        ApprovalResponse approval = find(id);
        if (!"PENDING".equals(approval.status())) {
            throw new BusinessException(ErrorCode.APPROVAL_002);
        }
        return approval;
    }

    private TaskRecord requireCurrentTask(long id, long operatorId) {
        TaskRecord task = repository.pendingTask(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPROVAL_002));
        if (task.approverUserId() != operatorId) {
            throw new BusinessException(ErrorCode.APPROVAL_003);
        }
        return task;
    }

    private void validateNodes(List<ApprovalFlowUpdateRequest.Node> nodes) {
        Set<Integer> orders = new HashSet<>();
        for (ApprovalFlowUpdateRequest.Node node : nodes) {
            if (node.nodeOrder() == null || node.nodeOrder() < 1 || !orders.add(node.nodeOrder())) {
                throw new BusinessException(ErrorCode.APPROVAL_002);
            }
        }
        for (int i = 1; i <= orders.size(); i++) {
            if (!orders.contains(i)) {
                throw new BusinessException(ErrorCode.APPROVAL_002);
            }
        }
    }

    private ApprovalBusinessContext toBusinessContext(ApprovalResponse approval) {
        return new ApprovalBusinessContext(approval.id(), approval.approvalType(), approval.businessType(), approval.businessId());
    }

    private ApprovalSubmissionContext toSubmissionContext(SubmissionRecord submission) {
        return new ApprovalSubmissionContext(submission.businessSnapshotJson());
    }

    private SubmissionRecord requireSubmission(long id) {
        return repository.findSubmission(id).orElseThrow(() -> new BusinessException(ErrorCode.APPROVAL_002));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.SYS_003);
        }
    }

    private Map<String, Object> snapshotWithAttachments(Map<String, Object> businessSnapshot, List<Long> attachmentIds) {
        Map<String, Object> snapshot = new LinkedHashMap<>(businessSnapshot);
        // Only the validated top-level IDs may become formal submission evidence.
        snapshot.remove("attachmentIds");
        if (attachmentIds != null && !attachmentIds.isEmpty()) {
            snapshot.put("attachmentIds", List.copyOf(attachmentIds));
        }
        return snapshot;
    }

    private List<Long> evidenceIds(List<Long> applicationIds, List<Long> referencedIds) {
        Set<Long> ids = new java.util.LinkedHashSet<>();
        if (applicationIds != null) ids.addAll(applicationIds);
        if (referencedIds != null) ids.addAll(referencedIds);
        return List.copyOf(ids);
    }

    private List<Long> actionAttachmentIds(ApprovalActionRequest request) {
        return request == null ? null : request.attachmentIds();
    }

    private String nextApprovalNo() {
        return "AP" + LocalDateTime.now().format(APPROVAL_NO_TIME) + UUID.randomUUID().toString().substring(0, 6);
    }
}
