package com.dfwl.fleet.system.controller;

import com.dfwl.fleet.system.dto.response.AuditLogResponse;
import com.dfwl.fleet.system.dto.response.PermissionResponse;
import com.dfwl.fleet.system.dto.request.RolePermissionRequest;
import com.dfwl.fleet.system.dto.request.RoleRequest;
import com.dfwl.fleet.system.dto.response.RoleResponse;
import com.dfwl.fleet.system.dto.request.UserRequest;
import com.dfwl.fleet.system.dto.response.UserResponse;

import com.dfwl.fleet.common.api.ApiResponse;
import com.dfwl.fleet.common.api.PageResponse;
import com.dfwl.fleet.common.web.RequestIdHolder;
import com.dfwl.fleet.security.AuthenticatedUser;
import com.dfwl.fleet.system.service.SystemManagementService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import com.dfwl.fleet.security.FunctionAccess;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1")
public class SystemManagementController {

    private final SystemManagementService service;

    public SystemManagementController(SystemManagementService service) {
        this.service = service;
    }

    @GetMapping("/users")
    @FunctionAccess(anyOf={"PAGE_USERS"})
    public ApiResponse<PageResponse<UserResponse>> listUsers(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success(service.listUsers(pageNo, pageSize), RequestIdHolder.get());
    }

    @GetMapping("/users/{id}")
    @FunctionAccess(anyOf={"PAGE_USERS"})
    public ApiResponse<UserResponse> findUser(@PathVariable long id) {
        return ApiResponse.success(service.findUser(id), RequestIdHolder.get());
    }

    @PostMapping("/users")
    @FunctionAccess(anyOf={"BUTTON_USERS_ADD"})
    public ApiResponse<UserResponse> createUser(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody UserRequest request) {
        return ApiResponse.success(service.createUser(request, user.id()), RequestIdHolder.get());
    }

    @PutMapping("/users/{id}")
    @FunctionAccess(anyOf={"BUTTON_USERS_EDIT"})
    public ApiResponse<UserResponse> updateUser(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long id,
            @Valid @RequestBody UserRequest request) {
        return ApiResponse.success(service.updateUser(id, request, user.id()), RequestIdHolder.get());
    }

    @GetMapping("/roles")
    @FunctionAccess(anyOf={"PAGE_ROLES"})
    public ApiResponse<PageResponse<RoleResponse>> listRoles(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success(service.listRoles(pageNo, pageSize), RequestIdHolder.get());
    }

    @GetMapping("/roles/{id}")
    @FunctionAccess(anyOf={"PAGE_ROLES"})
    public ApiResponse<RoleResponse> findRole(@PathVariable long id) {
        return ApiResponse.success(service.findRole(id), RequestIdHolder.get());
    }

    @PostMapping("/roles")
    @FunctionAccess(anyOf={"BUTTON_ROLES_ADD"})
    public ApiResponse<RoleResponse> createRole(@Valid @RequestBody RoleRequest request) {
        return ApiResponse.success(service.createRole(request), RequestIdHolder.get());
    }

    @PutMapping("/roles/{id}")
    @FunctionAccess(anyOf={"BUTTON_ROLES_EDIT"})
    public ApiResponse<RoleResponse> updateRole(
            @PathVariable long id,
            @Valid @RequestBody RoleRequest request) {
        return ApiResponse.success(service.updateRole(id, request), RequestIdHolder.get());
    }

    @PutMapping("/roles/{id}/status")
    @FunctionAccess(anyOf={"BUTTON_ROLES_EDIT"})
    public ApiResponse<RoleResponse> updateRoleStatus(
            @PathVariable long id,
            @RequestParam @Min(0) @Max(1) int status) {
        return ApiResponse.success(service.updateRoleStatus(id, status), RequestIdHolder.get());
    }

    @PutMapping("/roles/{id}/permissions")
    @FunctionAccess(anyOf={"BUTTON_ROLES_GRANT"})
    public ApiResponse<RoleResponse> replaceRolePermissions(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long id,
            @RequestBody RolePermissionRequest request) {
        return ApiResponse.success(service.replaceRolePermissions(id, request, user.id()), RequestIdHolder.get());
    }

    @GetMapping("/permissions")
    @FunctionAccess(anyOf={"PAGE_ROLES"})
    public ApiResponse<List<PermissionResponse>> listPermissions(
            @RequestParam(value = "permissionType", required = false) String permissionType) {
        return ApiResponse.success(service.listPermissions(permissionType), RequestIdHolder.get());
    }

    @GetMapping("/audit-logs")
    @FunctionAccess(anyOf={"PAGE_AUDIT"})
    public ApiResponse<PageResponse<AuditLogResponse>> listAuditLogs(
            @RequestParam(value = "module", required = false) String module,
            @RequestParam(value = "businessType", required = false) String businessType,
            @RequestParam(value = "operatorId", required = false) Long operatorId,
            @RequestParam(value = "startTime", required = false) String startTime,
            @RequestParam(value = "endTime", required = false) String endTime,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success(service.listAuditLogs(module, businessType, operatorId, startTime, endTime, pageNo, pageSize),
                RequestIdHolder.get());
    }
}
