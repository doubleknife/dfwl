package com.dfwl.fleet.route.controller;

import com.dfwl.fleet.common.api.ApiResponse;
import com.dfwl.fleet.common.api.PageResponse;
import com.dfwl.fleet.common.web.RequestIdHolder;
import com.dfwl.fleet.route.dto.request.TaskCancelRequest;
import com.dfwl.fleet.route.dto.request.TaskDepartRequest;
import com.dfwl.fleet.route.dto.request.TaskSalariesConfirmRequest;
import com.dfwl.fleet.route.dto.request.TaskTransferRequest;
import com.dfwl.fleet.route.dto.request.TaskUnloadRequest;
import com.dfwl.fleet.route.dto.request.TaskWeightAdjustmentRequest;
import com.dfwl.fleet.route.dto.response.TaskWeightVersionResponse;
import com.dfwl.fleet.route.dto.response.TransportParticipantResponse;
import com.dfwl.fleet.route.dto.response.TransportTaskResponse;
import com.dfwl.fleet.route.service.TransportTaskService;
import com.dfwl.fleet.security.AuthenticatedUser;
import com.dfwl.fleet.security.CurrentUserService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import com.dfwl.fleet.security.FunctionAccess;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transport-tasks")
public class TransportTaskController {
    private final TransportTaskService tasks;
    private final CurrentUserService users;

    public TransportTaskController(TransportTaskService tasks, CurrentUserService users) {
        this.tasks = tasks;
        this.users = users;
    }

    @GetMapping
    @FunctionAccess(anyOf={"PAGE_ROUTES"})
    public ApiResponse<PageResponse<TransportTaskResponse>> list(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "1") int pageNo, @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate,
            @RequestParam(required = false) String status, @RequestParam(required = false) Long templateId,
            @RequestParam(required = false) Long driverId, @RequestParam(required = false) String taskNo) {
        users.denyAllDrivers(user);
        return ApiResponse.success(tasks.list(pageNo, pageSize, businessDate, status, templateId, driverId, taskNo),
                RequestIdHolder.get());
    }

    @GetMapping("/{id}")
    @FunctionAccess(anyOf={"PAGE_ROUTES"})
    public ApiResponse<TransportTaskResponse> find(@AuthenticationPrincipal AuthenticatedUser user,
                                                   @PathVariable long id) {
        users.denyAllDrivers(user);
        return ApiResponse.success(tasks.find(id), RequestIdHolder.get());
    }

    @GetMapping("/{id}/participants")
    @FunctionAccess(anyOf={"PAGE_ROUTES", "PAGE_SALARIES"})
    public ApiResponse<List<TransportParticipantResponse>> participants(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id) {
        users.denyAllDrivers(user);
        return ApiResponse.success(tasks.participants(id,
                user.permissions().contains("PAGE_SALARIES")),
                RequestIdHolder.get());
    }

    @GetMapping("/{id}/weight-versions")
    @FunctionAccess(anyOf={"PAGE_ROUTES"})
    public ApiResponse<List<TaskWeightVersionResponse>> weightVersions(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id) {
        users.denyAllDrivers(user);
        return ApiResponse.success(tasks.weightVersions(id), RequestIdHolder.get());
    }

    @PostMapping("/{id}/participant-salaries/confirm")
    @FunctionAccess(anyOf={"BUTTON_SALARIES_ADJUST"})
    public ApiResponse<List<TransportParticipantResponse>> confirmParticipantSalaries(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id,
            @Valid @RequestBody TaskSalariesConfirmRequest request) {
        users.denyAllDrivers(user);
        return ApiResponse.success(tasks.confirmParticipantSalaries(id, request, user.id()),
                RequestIdHolder.get());
    }

    @PostMapping("/{id}/transfer")
    @FunctionAccess(anyOf={"BUTTON_ROUTES_TRANSFER"})
    public ApiResponse<TransportTaskResponse> transfer(@AuthenticationPrincipal AuthenticatedUser user,
                                                       @PathVariable long id,
                                                       @Valid @RequestBody TaskTransferRequest request) {
        users.denyAllDrivers(user);
        return ApiResponse.success(tasks.transfer(id, request, user.id()), RequestIdHolder.get());
    }

    @PostMapping("/{id}/depart")
    @FunctionAccess(anyOf={"BUTTON_ROUTES_DEPART", "BUTTON_MINI_ROUTES_DEPART"})
    public ApiResponse<?> depart(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id,
                                 @Valid @RequestBody TaskDepartRequest request) {
        TransportTaskResponse result = tasks.depart(id, request, user);
        return ApiResponse.success(tasks.findForDriver(user, result.id()), RequestIdHolder.get());
    }

    @PostMapping("/{id}/unload")
    @FunctionAccess(anyOf={"BUTTON_ROUTES_UNLOAD", "BUTTON_MINI_ROUTES_UNLOAD"})
    public ApiResponse<?> unload(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id,
                                 @Valid @RequestBody TaskUnloadRequest request) {
        TransportTaskResponse result = tasks.unload(id, request, user);
        return ApiResponse.success(tasks.findForDriver(user, result.id()), RequestIdHolder.get());
    }

    @PostMapping("/{id}/weight-adjustments")
    @FunctionAccess(anyOf={"BUTTON_ROUTES_WEIGHT_ADJUST"})
    public ApiResponse<TransportTaskResponse> adjustWeight(@AuthenticationPrincipal AuthenticatedUser user,
                                                            @PathVariable long id,
                                                            @Valid @RequestBody TaskWeightAdjustmentRequest request) {
        users.denyAllDrivers(user);
        return ApiResponse.success(tasks.adjustWeight(id, request, user.id()), RequestIdHolder.get());
    }

    @PostMapping("/{id}/cancel")
    @FunctionAccess(anyOf={"BUTTON_ROUTES_CANCEL"})
    public ApiResponse<TransportTaskResponse> cancel(@AuthenticationPrincipal AuthenticatedUser user,
                                                     @PathVariable long id,
                                                     @Valid @RequestBody TaskCancelRequest request) {
        users.denyAllDrivers(user);
        return ApiResponse.success(tasks.cancel(id, request, user.id()), RequestIdHolder.get());
    }
}
