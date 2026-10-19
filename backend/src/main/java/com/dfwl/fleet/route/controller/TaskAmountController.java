package com.dfwl.fleet.route.controller;

import com.dfwl.fleet.common.api.ApiResponse;
import com.dfwl.fleet.common.web.RequestIdHolder;
import com.dfwl.fleet.route.dto.request.TaskAmountAdjustmentRequest;
import com.dfwl.fleet.route.dto.response.TaskAmountHistoryResponse;
import com.dfwl.fleet.route.service.TaskAmountService;
import com.dfwl.fleet.security.AuthenticatedUser;
import com.dfwl.fleet.security.CurrentUserService;
import jakarta.validation.Valid;
import java.util.List;
import com.dfwl.fleet.security.FunctionAccess;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transport-tasks/{taskId}")
public class TaskAmountController {
    private final TaskAmountService service;
    private final CurrentUserService users;

    public TaskAmountController(TaskAmountService service, CurrentUserService users) {
        this.service = service;
        this.users = users;
    }

    @GetMapping("/amount-history")
    @FunctionAccess(anyOf={"PAGE_ROUTES"})
    public ApiResponse<List<TaskAmountHistoryResponse>> history(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable long taskId) {
        users.denyAllDrivers(user);
        return ApiResponse.success(service.history(taskId), RequestIdHolder.get());
    }

    @PostMapping("/amount-adjustments")
    @FunctionAccess(anyOf={"BUTTON_ROUTES_AMOUNT_ADJUST"})
    public ApiResponse<Void> adjust(@AuthenticationPrincipal AuthenticatedUser user,
                                    @PathVariable long taskId,
                                    @Valid @RequestBody TaskAmountAdjustmentRequest request) {
        users.denyAllDrivers(user);
        service.adjust(taskId, request, user.id());
        return ApiResponse.success(RequestIdHolder.get());
    }
}
