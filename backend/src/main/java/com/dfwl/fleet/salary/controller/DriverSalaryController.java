package com.dfwl.fleet.salary.controller;

import com.dfwl.fleet.common.api.ApiResponse;
import com.dfwl.fleet.common.api.PageResponse;
import com.dfwl.fleet.common.web.RequestIdHolder;
import com.dfwl.fleet.salary.dto.response.ParticipantSalaryHistoryResponse;
import com.dfwl.fleet.salary.dto.response.ParticipantSalaryResponse;
import com.dfwl.fleet.salary.dto.response.SalarySlipResponse;
import com.dfwl.fleet.salary.service.DriverSalaryService;
import com.dfwl.fleet.security.AuthenticatedUser;
import com.dfwl.fleet.security.CurrentUserService;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DriverSalaryController {
    private final DriverSalaryService salaries;
    private final CurrentUserService users;

    public DriverSalaryController(DriverSalaryService salaries, CurrentUserService users) {
        this.salaries = salaries;
        this.users = users;
    }

    @GetMapping("/api/v1/driver-salaries")
    @PreAuthorize("hasAuthority('salary:view')")
    public ApiResponse<PageResponse<ParticipantSalaryResponse>> list(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) Long driverId,
            @RequestParam(required = false) String month,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        users.denyAllDrivers(user);
        return ApiResponse.success(salaries.list(driverId, month, pageNo, pageSize), RequestIdHolder.get());
    }

    @GetMapping("/api/v1/driver-salaries/{participantId}/history")
    @PreAuthorize("hasAuthority('salary:view')")
    public ApiResponse<List<ParticipantSalaryHistoryResponse>> history(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable long participantId) {
        users.denyAllDrivers(user);
        return ApiResponse.success(salaries.history(participantId), RequestIdHolder.get());
    }

    @GetMapping("/api/v1/salary-slips/mine")
    @PreAuthorize("hasAuthority('salary:mine')")
    public ApiResponse<SalarySlipResponse> mine(
            @AuthenticationPrincipal AuthenticatedUser user, @RequestParam String month,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success(salaries.slip(user, month, pageNo, pageSize), RequestIdHolder.get());
    }
}
