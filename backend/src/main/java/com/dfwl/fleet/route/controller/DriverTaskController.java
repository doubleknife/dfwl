package com.dfwl.fleet.route.controller;

import com.dfwl.fleet.common.api.ApiResponse;
import com.dfwl.fleet.common.api.PageResponse;
import com.dfwl.fleet.common.web.RequestIdHolder;
import com.dfwl.fleet.route.dto.response.DriverTaskResponse;
import com.dfwl.fleet.route.service.TransportTaskService;
import com.dfwl.fleet.security.AuthenticatedUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/driver/transport-tasks")
public class DriverTaskController {
    private final TransportTaskService tasks;

    public DriverTaskController(TransportTaskService tasks) { this.tasks = tasks; }

    @GetMapping
    @PreAuthorize("hasAuthority('task:view')")
    public ApiResponse<PageResponse<DriverTaskResponse>> list(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "1") int pageNo, @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status) {
        return ApiResponse.success(tasks.listForDriver(user, pageNo, pageSize, status), RequestIdHolder.get());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('task:view')")
    public ApiResponse<DriverTaskResponse> find(@AuthenticationPrincipal AuthenticatedUser user,
                                                @PathVariable long id) {
        return ApiResponse.success(tasks.findForDriver(user, id), RequestIdHolder.get());
    }
}
