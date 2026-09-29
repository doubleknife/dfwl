package com.dfwl.fleet.route.controller;

import com.dfwl.fleet.common.api.ApiResponse;
import com.dfwl.fleet.common.web.RequestIdHolder;
import com.dfwl.fleet.route.dto.request.DispatchBatchRequest;
import com.dfwl.fleet.route.dto.request.DispatchConfirmRequest;
import com.dfwl.fleet.route.dto.response.DispatchBatchResponse;
import com.dfwl.fleet.route.dto.response.DispatchPreviewResponse;
import com.dfwl.fleet.route.service.DispatchBatchService;
import com.dfwl.fleet.security.AuthenticatedUser;
import com.dfwl.fleet.security.CurrentUserService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dispatch-batches")
public class DispatchBatchController {
    private final DispatchBatchService service;
    private final CurrentUserService users;

    public DispatchBatchController(DispatchBatchService service, CurrentUserService users) {
        this.service = service;
        this.users = users;
    }

    @PostMapping("/preview")
    @PreAuthorize("hasAuthority('task:create')")
    public ApiResponse<DispatchPreviewResponse> preview(@AuthenticationPrincipal AuthenticatedUser user,
                                                         @Valid @RequestBody DispatchBatchRequest request) {
        users.denyAllDrivers(user);
        return ApiResponse.success(service.preview(request, user.id()), RequestIdHolder.get());
    }

    @PostMapping("/confirm")
    @PreAuthorize("hasAuthority('task:publish')")
    public ApiResponse<DispatchBatchResponse> confirm(@AuthenticationPrincipal AuthenticatedUser user,
                                                       @Valid @RequestBody DispatchConfirmRequest request) {
        users.denyAllDrivers(user);
        return ApiResponse.success(service.confirm(request, user.id()), RequestIdHolder.get());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('task:view')")
    public ApiResponse<DispatchBatchResponse> find(@AuthenticationPrincipal AuthenticatedUser user,
                                                    @PathVariable long id) {
        users.denyAllDrivers(user);
        return ApiResponse.success(service.find(id), RequestIdHolder.get());
    }
}
