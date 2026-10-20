package com.dfwl.fleet.master.controller;

import com.dfwl.fleet.common.api.ApiResponse;
import com.dfwl.fleet.common.web.RequestIdHolder;
import com.dfwl.fleet.master.service.DriverVehicleInitialImportService;
import com.dfwl.fleet.security.AuthenticatedUser;
import com.dfwl.fleet.security.CurrentUserService;
import com.dfwl.fleet.security.FunctionAccess;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/driver-vehicle-imports")
public class DriverVehicleInitialImportController {
    private final DriverVehicleInitialImportService service;
    private final CurrentUserService users;

    public DriverVehicleInitialImportController(DriverVehicleInitialImportService service, CurrentUserService users) {
        this.service = service;
        this.users = users;
    }

    @GetMapping("/template")
    @FunctionAccess(anyOf = {"PAGE_DRIVERS"})
    public ResponseEntity<byte[]> template(@AuthenticationPrincipal AuthenticatedUser user) {
        users.denyAllDrivers(user);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("自有司机车辆初始导入模板.xlsx", StandardCharsets.UTF_8).build().toString())
                .body(service.template());
    }

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @FunctionAccess(anyOf = {"PAGE_DRIVERS"}, readOnlyPost = true)
    public ApiResponse<DriverVehicleInitialImportService.Preview> preview(
            @AuthenticationPrincipal AuthenticatedUser user, @RequestPart("file") MultipartFile file) {
        users.denyAllDrivers(user);
        return ApiResponse.success(service.preview(file), RequestIdHolder.get());
    }

    @PostMapping(value = "/confirm", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @FunctionAccess(anyOf = {"BUTTON_DRIVERS_IMPORT"})
    public ApiResponse<DriverVehicleInitialImportService.Confirmation> confirm(
            @AuthenticationPrincipal AuthenticatedUser user, @RequestPart("file") MultipartFile file) {
        users.denyAllDrivers(user);
        return ApiResponse.success(service.confirm(file, user.id()), RequestIdHolder.get());
    }
}
