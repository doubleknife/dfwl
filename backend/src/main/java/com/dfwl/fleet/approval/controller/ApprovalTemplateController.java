package com.dfwl.fleet.approval.controller;

import com.dfwl.fleet.approval.dto.request.ApprovalTemplateCreateRequest;
import com.dfwl.fleet.approval.dto.response.ApprovalFlowConfigResponse;
import com.dfwl.fleet.approval.dto.response.ApprovalAvailableTemplateResponse;
import com.dfwl.fleet.approval.dto.response.ApprovalFlowResponse;
import com.dfwl.fleet.approval.dto.response.ApprovalTemplateResponse;
import com.dfwl.fleet.approval.policy.ApprovalAccessPolicy;
import com.dfwl.fleet.approval.service.ApprovalTemplateService;
import com.dfwl.fleet.common.api.ApiResponse;
import com.dfwl.fleet.common.web.RequestIdHolder;
import com.dfwl.fleet.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/approval-templates")
public class ApprovalTemplateController {

    private final ApprovalTemplateService service;
    private final ApprovalAccessPolicy accessPolicy;

    public ApprovalTemplateController(ApprovalTemplateService service, ApprovalAccessPolicy accessPolicy) {
        this.service = service;
        this.accessPolicy = accessPolicy;
    }

    @GetMapping("/available")
    @PreAuthorize("hasAuthority('approval:create')")
    public ApiResponse<List<ApprovalAvailableTemplateResponse>> available(
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.success(service.available(user), RequestIdHolder.get());
    }

    @GetMapping
    @PreAuthorize("hasAuthority('approval:flow:manage')")
    public ApiResponse<List<ApprovalTemplateResponse>> list(@AuthenticationPrincipal AuthenticatedUser user) {
        accessPolicy.ensureCanManageFlows(user);
        return ApiResponse.success(service.list(), RequestIdHolder.get());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('approval:flow:manage')")
    public ApiResponse<ApprovalFlowConfigResponse> create(@AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody ApprovalTemplateCreateRequest request) {
        accessPolicy.ensureCanManageFlows(user);
        return ApiResponse.success(service.create(request, user.id()), RequestIdHolder.get());
    }

    @GetMapping("/{templateId}")
    @PreAuthorize("hasAuthority('approval:flow:manage')")
    public ApiResponse<ApprovalTemplateResponse> detail(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long templateId) {
        accessPolicy.ensureCanManageFlows(user);
        return ApiResponse.success(service.find(templateId), RequestIdHolder.get());
    }

    @GetMapping("/{templateId}/versions")
    @PreAuthorize("hasAuthority('approval:flow:manage')")
    public ApiResponse<List<ApprovalFlowResponse>> versions(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long templateId) {
        accessPolicy.ensureCanManageFlows(user);
        return ApiResponse.success(service.versions(templateId), RequestIdHolder.get());
    }

    @GetMapping("/{templateId}/active")
    @PreAuthorize("hasAuthority('approval:flow:manage')")
    public ApiResponse<ApprovalFlowConfigResponse> active(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long templateId) {
        accessPolicy.ensureCanManageFlows(user);
        return ApiResponse.success(service.active(templateId), RequestIdHolder.get());
    }

    @GetMapping("/{templateId}/versions/{flowId}")
    @PreAuthorize("hasAuthority('approval:flow:manage')")
    public ApiResponse<ApprovalFlowConfigResponse> version(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long templateId, @PathVariable long flowId) {
        accessPolicy.ensureCanManageFlows(user);
        return ApiResponse.success(service.version(templateId, flowId), RequestIdHolder.get());
    }
}
