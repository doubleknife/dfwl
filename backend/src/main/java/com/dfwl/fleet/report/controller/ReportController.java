package com.dfwl.fleet.report.controller;

import com.dfwl.fleet.common.api.ApiResponse;
import com.dfwl.fleet.common.api.PageResponse;
import com.dfwl.fleet.common.web.RequestIdHolder;
import com.dfwl.fleet.report.service.ReportService;
import com.dfwl.fleet.security.AuthenticatedUser;
import com.dfwl.fleet.security.CurrentUserService;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Retired from the V3-A runtime; V3 financial projections are rebuilt in V3-E.
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final ReportService reportService;
    private final CurrentUserService currentUserService;

    public ReportController(ReportService reportService, CurrentUserService currentUserService) {
        this.reportService = reportService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAuthority('report:dashboard')")
    public ApiResponse<Map<String, Object>> dashboard(@AuthenticationPrincipal AuthenticatedUser user) {
        currentUserService.denyAllDrivers(user);
        return ApiResponse.success(reportService.dashboard(), RequestIdHolder.get());
    }

    @GetMapping("/profit")
    @PreAuthorize("hasAuthority('report:profit')")
    public ApiResponse<List<Map<String, Object>>> profit(@AuthenticationPrincipal AuthenticatedUser user) {
        currentUserService.denyAllDrivers(user);
        return ApiResponse.success(reportService.profit(), RequestIdHolder.get());
    }

    @GetMapping("/completed-routes")
    @PreAuthorize("hasAuthority('report:profit')")
    public ApiResponse<PageResponse<Map<String, Object>>> completedRoutes(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status) {
        currentUserService.denyAllDrivers(user);
        return ApiResponse.success(reportService.completedRoutes(pageNo, pageSize, status), RequestIdHolder.get());
    }

    @GetMapping("/completed-routes/{routeId}")
    @PreAuthorize("hasAuthority('report:profit')")
    public ApiResponse<Map<String, Object>> routeArchive(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable long routeId,
            @RequestParam(defaultValue = "1") int expensePageNo,
            @RequestParam(defaultValue = "20") int expensePageSize) {
        currentUserService.denyAllDrivers(user);
        return ApiResponse.success(reportService.routeArchive(routeId, expensePageNo, expensePageSize), RequestIdHolder.get());
    }

    @GetMapping("/vehicle-expense")
    @PreAuthorize("hasAuthority('report:vehicle')")
    public ApiResponse<List<Map<String, Object>>> vehicleExpense(@AuthenticationPrincipal AuthenticatedUser user) {
        currentUserService.denyAllDrivers(user);
        return ApiResponse.success(reportService.vehicleExpense(), RequestIdHolder.get());
    }

    @GetMapping("/driver-expense")
    @PreAuthorize("hasAuthority('report:driver')")
    public ApiResponse<List<Map<String, Object>>> driverExpense(@AuthenticationPrincipal AuthenticatedUser user) {
        currentUserService.denyAllDrivers(user);
        return ApiResponse.success(reportService.driverExpense(), RequestIdHolder.get());
    }

    @GetMapping("/attendance")
    @PreAuthorize("hasAuthority('report:attendance')")
    public ApiResponse<List<Map<String, Object>>> attendance(@AuthenticationPrincipal AuthenticatedUser user) {
        currentUserService.denyAllDrivers(user);
        return ApiResponse.success(reportService.attendance(), RequestIdHolder.get());
    }

    @GetMapping("/energy")
    @PreAuthorize("hasAuthority('report:energy')")
    public ApiResponse<List<Map<String, Object>>> energy(@AuthenticationPrincipal AuthenticatedUser user) {
        currentUserService.denyAllDrivers(user);
        return ApiResponse.success(reportService.energy(), RequestIdHolder.get());
    }
}
