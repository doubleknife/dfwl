package com.dfwl.fleet.salary.controller;

import com.dfwl.fleet.common.api.ApiResponse;
import com.dfwl.fleet.common.api.PageResponse;
import com.dfwl.fleet.common.web.RequestIdHolder;
import com.dfwl.fleet.salary.dto.request.DriverSalaryRequest;
import com.dfwl.fleet.salary.dto.response.DriverSalaryResponse;
import com.dfwl.fleet.salary.dto.response.DriverSalaryHistoryResponse;
import com.dfwl.fleet.salary.service.DriverSalaryService;
import com.dfwl.fleet.security.AuthenticatedUser;
import com.dfwl.fleet.security.CurrentUserService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/driver-salaries")
public class DriverSalaryController {

    private final DriverSalaryService salaryService;
    private final CurrentUserService currentUserService;

    public DriverSalaryController(DriverSalaryService salaryService, CurrentUserService currentUserService) {
        this.salaryService = salaryService;
        this.currentUserService = currentUserService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('salary:manage')")
    public ApiResponse<DriverSalaryResponse> upsert(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody DriverSalaryRequest request) {
        currentUserService.denyAllDrivers(user);
        return ApiResponse.success(salaryService.upsertManual(request, user.id()), RequestIdHolder.get());
    }

    @GetMapping
    @PreAuthorize("hasAuthority('salary:view')")
    public ApiResponse<PageResponse<DriverSalaryResponse>> list(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) Long driverId,
            @RequestParam(required = false) String businessMonth,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        currentUserService.denyAllDrivers(user);
        return ApiResponse.success(salaryService.list(driverId, businessMonth, pageNo, pageSize), RequestIdHolder.get());
    }

    @GetMapping("/me")
    @PreAuthorize("hasAuthority('salary:mine')")
    public ApiResponse<PageResponse<DriverSalaryResponse>> mine(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) String businessMonth,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success(salaryService.mySalary(user, businessMonth, pageNo, pageSize), RequestIdHolder.get());
    }

    @GetMapping("/drivers/{driverId}")
    @PreAuthorize("hasAnyAuthority('salary:mine','salary:view')")
    public ApiResponse<PageResponse<DriverSalaryResponse>> byDriver(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long driverId,
            @RequestParam(required = false) String businessMonth,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success(salaryService.salaryForDriver(user, driverId, businessMonth, pageNo, pageSize),
                RequestIdHolder.get());
    }

    @GetMapping("/routes/{routeId}/history")
    @PreAuthorize("hasAuthority('salary:view')")
    public ApiResponse<List<DriverSalaryHistoryResponse>> history(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long routeId) {
        currentUserService.denyAllDrivers(user);
        return ApiResponse.success(salaryService.history(routeId), RequestIdHolder.get());
    }
}
