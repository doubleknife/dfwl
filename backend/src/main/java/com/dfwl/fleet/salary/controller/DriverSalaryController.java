package com.dfwl.fleet.salary.controller;

import com.dfwl.fleet.common.api.ApiResponse;
import com.dfwl.fleet.common.api.PageResponse;
import com.dfwl.fleet.common.web.RequestIdHolder;
import com.dfwl.fleet.salary.dto.response.ParticipantSalaryHistoryResponse;
import com.dfwl.fleet.salary.dto.response.SalarySlipResponse;
import com.dfwl.fleet.salary.service.DriverSalaryService;
import com.dfwl.fleet.salary.service.SalaryLedgerService;
import com.dfwl.fleet.security.AuthenticatedUser;
import com.dfwl.fleet.security.CurrentUserService;
import java.util.List;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import com.dfwl.fleet.security.FunctionAccess;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DriverSalaryController {
    private final DriverSalaryService salaries;
    private final SalaryLedgerService ledger;
    private final CurrentUserService users;

    public DriverSalaryController(DriverSalaryService salaries, SalaryLedgerService ledger, CurrentUserService users) {
        this.salaries = salaries;
        this.ledger = ledger;
        this.users = users;
    }

    @GetMapping("/api/v1/driver-salaries")
    @FunctionAccess(anyOf={"PAGE_SALARIES", "PAGE_SALARY_TRANSFERS"})
    public ApiResponse<PageResponse<SalaryLedgerService.Row>> list(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) Long driverId,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) String driverType,
            @RequestParam(required = false) Long templateId,
            @RequestParam(required = false) Long taskId,
            @RequestParam(required = false) Boolean transferOnly,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        users.denyAllDrivers(user);
        return ApiResponse.success(ledger.list(new SalaryLedgerService.Filter(month, driverId, driverType,
                templateId, taskId, transferOnly), pageNo, pageSize), RequestIdHolder.get());
    }

    @GetMapping("/api/v1/driver-salaries/summary")
    @FunctionAccess(anyOf={"PAGE_SALARIES"})
    public ApiResponse<SalaryLedgerService.Summary> summary(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) String month, @RequestParam(required = false) Long driverId,
            @RequestParam(required = false) String driverType, @RequestParam(required = false) Long templateId,
            @RequestParam(required = false) Long taskId, @RequestParam(required = false) Boolean transferOnly) {
        users.denyAllDrivers(user);
        return ApiResponse.success(ledger.summary(new SalaryLedgerService.Filter(month, driverId, driverType,
                templateId, taskId, transferOnly)), RequestIdHolder.get());
    }

    @GetMapping("/api/v1/driver-salaries/filter-options")
    @FunctionAccess(anyOf={"PAGE_SALARIES"})
    public ApiResponse<PageResponse<SalaryLedgerService.FilterOption>> filterOptions(
            @AuthenticationPrincipal AuthenticatedUser user, @RequestParam String kind,
            @RequestParam(required = false) String search, @RequestParam(required = false) String month,
            @RequestParam(defaultValue = "1") int pageNo, @RequestParam(defaultValue = "20") int pageSize) {
        users.denyAllDrivers(user);
        return ApiResponse.success(ledger.options(kind, search, month, pageNo, pageSize), RequestIdHolder.get());
    }

    @GetMapping("/api/v1/driver-salaries/drivers")
    @FunctionAccess(anyOf={"PAGE_SALARIES"})
    public ApiResponse<PageResponse<SalaryLedgerService.DriverSummary>> drivers(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) String month, @RequestParam(required = false) Long driverId,
            @RequestParam(required = false) String driverType, @RequestParam(required = false) Long templateId,
            @RequestParam(required = false) Long taskId, @RequestParam(required = false) Boolean transferOnly,
            @RequestParam(defaultValue = "1") int pageNo, @RequestParam(defaultValue = "20") int pageSize) {
        users.denyAllDrivers(user);
        return ApiResponse.success(ledger.drivers(new SalaryLedgerService.Filter(month, driverId, driverType,
                templateId, taskId, transferOnly), pageNo, pageSize), RequestIdHolder.get());
    }

    @GetMapping("/api/v1/driver-salaries/export")
    @FunctionAccess(anyOf={"BUTTON_SALARIES_EXPORT"})
    public ResponseEntity<byte[]> export(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) String month, @RequestParam(required = false) Long driverId,
            @RequestParam(required = false) String driverType, @RequestParam(required = false) Long templateId,
            @RequestParam(required = false) Long taskId, @RequestParam(required = false) Boolean transferOnly) {
        users.denyAllDrivers(user);
        byte[] file = ledger.export(new SalaryLedgerService.Filter(month, driverId, driverType,
                templateId, taskId, transferOnly));
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .contentLength(file.length).header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("工资台账.xlsx", StandardCharsets.UTF_8).build().toString())
                .body(file);
    }

    @GetMapping("/api/v1/driver-salaries/{participantId}/history")
    @FunctionAccess(anyOf={"PAGE_SALARIES"})
    public ApiResponse<List<ParticipantSalaryHistoryResponse>> history(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable long participantId) {
        users.denyAllDrivers(user);
        return ApiResponse.success(salaries.history(participantId), RequestIdHolder.get());
    }

    @GetMapping("/api/v1/salary-slips/mine")
    @FunctionAccess(anyOf={"PAGE_MINI_SALARY"})
    public ApiResponse<SalarySlipResponse> mine(
            @AuthenticationPrincipal AuthenticatedUser user, @RequestParam String month,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success(salaries.slip(user, month, pageNo, pageSize), RequestIdHolder.get());
    }
}
