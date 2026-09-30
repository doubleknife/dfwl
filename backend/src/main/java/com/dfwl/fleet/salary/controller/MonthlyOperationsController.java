package com.dfwl.fleet.salary.controller;

import com.dfwl.fleet.common.api.ApiResponse;
import com.dfwl.fleet.common.api.PageResponse;
import com.dfwl.fleet.common.error.BusinessException;
import com.dfwl.fleet.common.error.ErrorCode;
import com.dfwl.fleet.common.web.RequestIdHolder;
import com.dfwl.fleet.salary.service.MonthlyOperationsService;
import com.dfwl.fleet.security.AuthenticatedUser;
import com.dfwl.fleet.security.CurrentUserService;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MonthlyOperationsController {
    private final MonthlyOperationsService operations;
    private final CurrentUserService users;

    public MonthlyOperationsController(MonthlyOperationsService operations, CurrentUserService users) {
        this.operations = operations;
        this.users = users;
    }

    @GetMapping("/api/v1/attendance")
    @PreAuthorize("hasAuthority('attendance:view')")
    public ApiResponse<MonthlyOperationsService.MonthPage> attendance(
            @AuthenticationPrincipal AuthenticatedUser user, @RequestParam String month,
            @RequestParam(required = false) Long driverId, @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        users.denyAllDrivers(user);
        return ApiResponse.success(operations.attendance(month, driverId, pageNo, pageSize), RequestIdHolder.get());
    }

    @GetMapping("/api/v1/attendance/drivers")
    @PreAuthorize("hasAuthority('attendance:view')")
    public ApiResponse<PageResponse<MonthlyOperationsService.DriverOption>> attendanceDrivers(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        users.denyAllDrivers(user);
        return ApiResponse.success(operations.attendanceDrivers(search, pageNo, pageSize), RequestIdHolder.get());
    }

    @GetMapping("/api/v1/attendance/export")
    @PreAuthorize("hasAuthority('attendance:export')")
    public ResponseEntity<byte[]> attendanceExport(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam String month, @RequestParam(required = false) Long driverId) {
        users.denyAllDrivers(user);
        return file(operations.exportAttendance(month, driverId), "司机出勤-" + month + ".xlsx");
    }

    @GetMapping("/api/v1/dispatch-ledger/internal")
    @PreAuthorize("hasAuthority('dispatch:ledger:view')")
    public ApiResponse<MonthlyOperationsService.MonthPage> internal(
            @AuthenticationPrincipal AuthenticatedUser user, @RequestParam String month,
            @RequestParam(defaultValue = "1") int pageNo, @RequestParam(defaultValue = "20") int pageSize) {
        users.denyAllDrivers(user);
        return ApiResponse.success(operations.ledger(month, false, pageNo, pageSize), RequestIdHolder.get());
    }

    @GetMapping("/api/v1/dispatch-ledger/outsourced")
    @PreAuthorize("hasAuthority('dispatch:ledger:view')")
    public ApiResponse<MonthlyOperationsService.MonthPage> outsourced(
            @AuthenticationPrincipal AuthenticatedUser user, @RequestParam String month,
            @RequestParam(defaultValue = "1") int pageNo, @RequestParam(defaultValue = "20") int pageSize) {
        users.denyAllDrivers(user);
        return ApiResponse.success(operations.ledger(month, true, pageNo, pageSize), RequestIdHolder.get());
    }

    @GetMapping("/api/v1/dispatch-ledger/export")
    @PreAuthorize("hasAuthority('dispatch:ledger:export')")
    public ResponseEntity<byte[]> ledgerExport(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam String month, @RequestParam String scope) {
        users.denyAllDrivers(user);
        if (!"INTERNAL".equals(scope) && !"OUTSOURCED".equals(scope))
            throw new BusinessException(ErrorCode.SYS_002);
        return file(operations.exportLedger(month, "OUTSOURCED".equals(scope)),
                ("OUTSOURCED".equals(scope) ? "外协车辆月账-" : "自有车辆月账-") + month + ".xlsx");
    }

    private ResponseEntity<byte[]> file(byte[] bytes, String name) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .contentLength(bytes.length).header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build().toString())
                .body(bytes);
    }
}
