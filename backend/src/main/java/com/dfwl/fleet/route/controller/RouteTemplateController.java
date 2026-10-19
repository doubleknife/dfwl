package com.dfwl.fleet.route.controller;

import com.dfwl.fleet.common.api.ApiResponse;
import com.dfwl.fleet.common.web.RequestIdHolder;
import com.dfwl.fleet.route.dto.request.RouteTemplateRequest;
import com.dfwl.fleet.route.dto.response.RouteTemplateResponse;
import com.dfwl.fleet.route.service.RouteTemplateService;
import com.dfwl.fleet.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.List;
import com.dfwl.fleet.security.FunctionAccess;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/route-templates")
public class RouteTemplateController {
    private final RouteTemplateService service;

    public RouteTemplateController(RouteTemplateService service) { this.service = service; }

    @GetMapping
    @FunctionAccess(anyOf={"PAGE_TEMPLATES", "PAGE_DISPATCH"})
    public ApiResponse<List<RouteTemplateResponse>> list() {
        return ApiResponse.success(service.list(), RequestIdHolder.get());
    }

    @GetMapping("/{id}")
    @FunctionAccess(anyOf={"PAGE_TEMPLATES", "PAGE_DISPATCH"})
    public ApiResponse<RouteTemplateResponse> find(@PathVariable long id) {
        return ApiResponse.success(service.find(id), RequestIdHolder.get());
    }

    @PostMapping
    @FunctionAccess(anyOf={"BUTTON_TEMPLATES_MANAGE"})
    public ApiResponse<RouteTemplateResponse> create(@AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody RouteTemplateRequest request) {
        return ApiResponse.success(service.create(request, user.id()), RequestIdHolder.get());
    }

    @PatchMapping("/{id}")
    @FunctionAccess(anyOf={"BUTTON_TEMPLATES_MANAGE"})
    public ApiResponse<RouteTemplateResponse> update(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long id, @Valid @RequestBody RouteTemplateRequest request) {
        return ApiResponse.success(service.update(id, request, user.id()), RequestIdHolder.get());
    }

    @PostMapping("/{id}/enable")
    @FunctionAccess(anyOf={"BUTTON_TEMPLATES_MANAGE"})
    public ApiResponse<RouteTemplateResponse> enable(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long id, @RequestBody VersionRequest request) {
        return ApiResponse.success(service.setEnabled(id, request.expectedVersion(), true, user.id()), RequestIdHolder.get());
    }

    @PostMapping("/{id}/disable")
    @FunctionAccess(anyOf={"BUTTON_TEMPLATES_MANAGE"})
    public ApiResponse<RouteTemplateResponse> disable(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long id, @RequestBody VersionRequest request) {
        return ApiResponse.success(service.setEnabled(id, request.expectedVersion(), false, user.id()), RequestIdHolder.get());
    }

    public record VersionRequest(int expectedVersion) {}
}
