package com.dfwl.fleet.approval.api;

import com.dfwl.fleet.approval.service.ApprovalService;
import com.dfwl.fleet.common.api.ApiResponse;
import com.dfwl.fleet.common.api.PageResponse;
import com.dfwl.fleet.common.web.RequestIdHolder;
import com.dfwl.fleet.security.AuthenticatedUser;
import com.dfwl.fleet.security.CurrentUserService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ApprovalController {

    private final ApprovalService approvalService;
    private final CurrentUserService currentUserService;

    public ApprovalController(ApprovalService approvalService, CurrentUserService currentUserService) {
        this.approvalService = approvalService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/approvals")
    @PreAuthorize("hasAnyAuthority('approval:history:view','approval:process','approval:return','approval:create')")
    public ApiResponse<PageResponse<ApprovalResponse>> list(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "ALL") ApprovalListScope scope,
            @RequestParam(value = "approvalType", required = false) String approvalType,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success(approvalService.list(scope, approvalType, status, user, pageNo, pageSize), RequestIdHolder.get());
    }

    @GetMapping("/approvals/{id}")
    @PreAuthorize("hasAnyAuthority('approval:history:view','approval:process','approval:return','approval:create')")
    public ApiResponse<ApprovalDetailResponse> detail(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long id) {
        return ApiResponse.success(approvalService.detail(id), RequestIdHolder.get());
    }

    @GetMapping("/approval-flows")
    @PreAuthorize("hasAuthority('approval:flow:manage')")
    public ApiResponse<PageResponse<ApprovalFlowResponse>> listFlows(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(value = "approvalType", required = false) String approvalType,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        currentUserService.denyAllDrivers(user);
        return ApiResponse.success(approvalService.listFlows(approvalType, status, pageNo, pageSize), RequestIdHolder.get());
    }

    @GetMapping("/approval-flows/{id}")
    @PreAuthorize("hasAuthority('approval:flow:manage')")
    public ApiResponse<ApprovalFlowDetailResponse> flowDetail(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long id) {
        currentUserService.denyAllDrivers(user);
        return ApiResponse.success(approvalService.flowDetail(id), RequestIdHolder.get());
    }

    @GetMapping("/approval-flows/types/{approvalType}/versions")
    @PreAuthorize("hasAuthority('approval:flow:manage')")
    public ApiResponse<java.util.List<ApprovalFlowResponse>> flowVersions(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String approvalType) {
        currentUserService.denyAllDrivers(user);
        return ApiResponse.success(approvalService.flowVersions(approvalType), RequestIdHolder.get());
    }

    @GetMapping("/approval-flows/types/{approvalType}/active")
    @PreAuthorize("hasAuthority('approval:flow:manage')")
    public ApiResponse<ApprovalFlowDetailResponse> activeFlow(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String approvalType) {
        currentUserService.denyAllDrivers(user);
        return ApiResponse.success(approvalService.activeFlowDetail(approvalType), RequestIdHolder.get());
    }

    @PostMapping("/approvals")
    @PreAuthorize("hasAuthority('approval:create')")
    public ApiResponse<ApprovalResponse> create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody ApprovalCreateRequest request) {
        if ("EXPENSE".equalsIgnoreCase(request.approvalType()) || "EXPENSE".equalsIgnoreCase(request.businessType())) {
            currentUserService.denyAllDrivers(user);
        } else {
            currentUserService.denyOutsourcedDriver(user);
        }
        return ApiResponse.success(approvalService.create(request, user.id()), RequestIdHolder.get());
    }

    @PostMapping("/approvals/{id}/approve")
    @PreAuthorize("hasAuthority('approval:process')")
    public ApiResponse<ApprovalResponse> approve(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long id,
            @RequestBody(required = false) ApprovalActionRequest request) {
        currentUserService.denyAllDrivers(user);
        return ApiResponse.success(approvalService.approve(id, request, user.id()), RequestIdHolder.get());
    }

    @PostMapping("/approvals/{id}/return-applicant")
    @PreAuthorize("hasAuthority('approval:return')")
    public ApiResponse<ApprovalResponse> returnApplicant(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long id,
            @RequestBody(required = false) ApprovalActionRequest request) {
        currentUserService.denyAllDrivers(user);
        return ApiResponse.success(approvalService.returnApplicant(id, request, user.id()), RequestIdHolder.get());
    }

    @PostMapping("/approvals/{id}/return-node")
    @PreAuthorize("hasAuthority('approval:return')")
    public ApiResponse<ApprovalResponse> returnNode(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long id,
            @RequestBody(required = false) ApprovalActionRequest request) {
        currentUserService.denyAllDrivers(user);
        return ApiResponse.success(approvalService.returnNode(id, request, user.id()), RequestIdHolder.get());
    }

    @PostMapping("/approvals/{id}/resubmit")
    @PreAuthorize("hasAuthority('approval:create')")
    public ApiResponse<ApprovalResponse> resubmit(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long id,
            @Valid @RequestBody ApprovalCreateRequest request) {
        if ("EXPENSE".equalsIgnoreCase(request.approvalType()) || "EXPENSE".equalsIgnoreCase(request.businessType())) {
            currentUserService.denyAllDrivers(user);
        } else {
            currentUserService.denyOutsourcedDriver(user);
        }
        return ApiResponse.success(approvalService.resubmit(id, request, user.id()), RequestIdHolder.get());
    }

    @PostMapping("/approval-flows/{id}/maintenance")
    @PreAuthorize("hasAuthority('approval:flow:manage')")
    public ApiResponse<Void> maintenance(@PathVariable long id) {
        approvalService.maintenance(id);
        return ApiResponse.success(RequestIdHolder.get());
    }

    @PutMapping("/approval-flows/{id}")
    @PreAuthorize("hasAuthority('approval:flow:manage')")
    public ApiResponse<ApprovalFlowResponse> updateFlow(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long id,
            @Valid @RequestBody ApprovalFlowUpdateRequest request) {
        currentUserService.denyAllDrivers(user);
        return ApiResponse.success(approvalService.updateFlow(id, request, user.id()), RequestIdHolder.get());
    }

    @PostMapping("/approval-flows/{id}/publish")
    @PreAuthorize("hasAuthority('approval:flow:manage')")
    public ApiResponse<ApprovalFlowResponse> publishFlow(@PathVariable long id) {
        return ApiResponse.success(approvalService.publishFlow(id), RequestIdHolder.get());
    }
}
