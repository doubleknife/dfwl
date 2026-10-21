package com.dfwl.fleet.route.controller;

import com.dfwl.fleet.common.api.ApiResponse;
import com.dfwl.fleet.common.api.PageResponse;
import com.dfwl.fleet.common.web.RequestIdHolder;
import com.dfwl.fleet.master.dto.response.DriverResponse;
import com.dfwl.fleet.master.service.MasterDataService;
import com.dfwl.fleet.route.dto.request.DispatchBatchRequest;
import com.dfwl.fleet.route.dto.request.DispatchConfirmRequest;
import com.dfwl.fleet.route.dto.response.DispatchBatchResponse;
import com.dfwl.fleet.route.dto.response.DispatchPreviewResponse;
import com.dfwl.fleet.route.service.DispatchBatchService;
import com.dfwl.fleet.security.AuthenticatedUser;
import com.dfwl.fleet.security.CurrentUserService;
import jakarta.validation.Valid;
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
@RequestMapping("/api/v1/dispatch-batches")
public class DispatchBatchController {
    private final DispatchBatchService service;
    private final MasterDataService masterData;
    private final CurrentUserService users;

    public DispatchBatchController(DispatchBatchService service, MasterDataService masterData, CurrentUserService users) {
        this.service = service;
        this.masterData = masterData;
        this.users = users;
    }

    public record DriverOption(Long id, String driverType, String name, String phone, Integer age, Integer status,
                               Long currentVehicleId, String currentVehiclePlateNo) {
        static DriverOption from(DriverResponse driver) {
            return new DriverOption(driver.id(), driver.driverType(), driver.name(), driver.phone(), driver.age(), driver.status(),
                    driver.currentVehicleId(), driver.currentVehiclePlateNo());
        }
    }

    @GetMapping("/driver-options")
    @FunctionAccess(anyOf={"PAGE_DISPATCH"})
    public ApiResponse<PageResponse<DriverOption>> driverOptions(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "100") int pageSize) {
        users.denyAllDrivers(user);
        PageResponse<DriverResponse> page = masterData.listDrivers(pageNo, pageSize, "", "", 1);
        return ApiResponse.success(new PageResponse<>(page.pageNo(), page.pageSize(), page.total(),
                page.records().stream().map(DriverOption::from).toList()), RequestIdHolder.get());
    }

    @GetMapping("/vehicle-options")
    @FunctionAccess(anyOf={"PAGE_DISPATCH"})
    public ApiResponse<PageResponse<com.dfwl.fleet.master.dto.response.BindingVehicleOption>> vehicleOptions(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "100") int pageSize) {
        users.denyAllDrivers(user);
        return ApiResponse.success(masterData.listBindingVehicles(pageNo, pageSize), RequestIdHolder.get());
    }

    @PostMapping("/preview")
    @FunctionAccess(anyOf={"BUTTON_DISPATCH_PUBLISH"})
    public ApiResponse<DispatchPreviewResponse> preview(@AuthenticationPrincipal AuthenticatedUser user,
                                                         @Valid @RequestBody DispatchBatchRequest request) {
        users.denyAllDrivers(user);
        return ApiResponse.success(service.preview(request, user.id()), RequestIdHolder.get());
    }

    @PostMapping("/confirm")
    @FunctionAccess(anyOf={"BUTTON_DISPATCH_PUBLISH"})
    public ApiResponse<DispatchBatchResponse> confirm(@AuthenticationPrincipal AuthenticatedUser user,
                                                       @Valid @RequestBody DispatchConfirmRequest request) {
        users.denyAllDrivers(user);
        return ApiResponse.success(service.confirm(request, user.id()), RequestIdHolder.get());
    }

    @GetMapping("/{id}")
    @FunctionAccess(anyOf={"PAGE_DISPATCH"})
    public ApiResponse<DispatchBatchResponse> find(@AuthenticationPrincipal AuthenticatedUser user,
                                                    @PathVariable long id) {
        users.denyAllDrivers(user);
        return ApiResponse.success(service.find(id), RequestIdHolder.get());
    }
}
