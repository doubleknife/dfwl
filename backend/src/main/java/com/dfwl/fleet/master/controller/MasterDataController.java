package com.dfwl.fleet.master.controller;

import com.dfwl.fleet.master.dto.response.BindingHistoryResponse;
import com.dfwl.fleet.master.dto.request.BindTrailerVehicleRequest;
import com.dfwl.fleet.master.dto.request.BindVehicleRequest;
import com.dfwl.fleet.master.dto.request.DriverRequest;
import com.dfwl.fleet.master.dto.response.DriverResponse;
import com.dfwl.fleet.master.dto.request.ProductRequest;
import com.dfwl.fleet.master.dto.response.ProductResponse;
import com.dfwl.fleet.master.dto.request.StatusRequest;
import com.dfwl.fleet.master.dto.request.TrailerRequest;
import com.dfwl.fleet.master.dto.response.TrailerResponse;
import com.dfwl.fleet.master.dto.request.UnbindRequest;
import com.dfwl.fleet.master.dto.request.VehicleRequest;
import com.dfwl.fleet.master.dto.response.VehicleResponse;

import com.dfwl.fleet.common.api.ApiResponse;
import com.dfwl.fleet.common.api.PageResponse;
import com.dfwl.fleet.common.web.RequestIdHolder;
import com.dfwl.fleet.master.service.MasterDataService;
import com.dfwl.fleet.security.AuthenticatedUser;
import com.dfwl.fleet.security.CurrentUserService;
import jakarta.validation.Valid;
import java.util.List;
import com.dfwl.fleet.security.FunctionAccess;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class MasterDataController {

    private final MasterDataService masterDataService;
    private final CurrentUserService currentUserService;

    public MasterDataController(MasterDataService masterDataService, CurrentUserService currentUserService) {
        this.masterDataService = masterDataService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/products")
    @FunctionAccess(anyOf={"PAGE_PRODUCTS", "PAGE_TEMPLATES"})
    public ApiResponse<PageResponse<ProductResponse>> listProducts(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        currentUserService.denyAllDrivers(user);
        return ApiResponse.success(masterDataService.listProducts(pageNo, pageSize), RequestIdHolder.get());
    }

    @PostMapping("/products")
    @FunctionAccess(anyOf={"BUTTON_PRODUCTS_MANAGE"})
    public ApiResponse<ProductResponse> createProduct(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody ProductRequest request) {
        currentUserService.denyAllDrivers(user);
        return ApiResponse.success(masterDataService.createProduct(request), RequestIdHolder.get());
    }

    @PutMapping("/products/{id}")
    @FunctionAccess(anyOf={"BUTTON_PRODUCTS_MANAGE"})
    public ApiResponse<ProductResponse> updateProduct(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long id,
            @Valid @RequestBody ProductRequest request) {
        currentUserService.denyAllDrivers(user);
        return ApiResponse.success(masterDataService.updateProduct(id, request), RequestIdHolder.get());
    }

    @PutMapping("/products/{id}/status")
    @FunctionAccess(anyOf={"BUTTON_PRODUCTS_MANAGE"})
    public ApiResponse<ProductResponse> updateProductStatus(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long id,
            @Valid @RequestBody StatusRequest request) {
        currentUserService.denyAllDrivers(user);
        return ApiResponse.success(masterDataService.updateProductStatus(id, request.status()), RequestIdHolder.get());
    }

    @GetMapping("/drivers")
    @FunctionAccess(anyOf={"PAGE_DRIVERS"})
    public ApiResponse<PageResponse<DriverResponse>> listDrivers(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "") String driverType,
            @RequestParam(required = false) Integer status) {
        if (currentUserService.isDriverUser(user)) {
            return ApiResponse.success(masterDataService.listDriversForCurrentDriver(user, pageNo, pageSize), RequestIdHolder.get());
        }
        return ApiResponse.success(masterDataService.listDrivers(pageNo, pageSize, keyword, driverType, status), RequestIdHolder.get());
    }

    @GetMapping("/drivers/binding-candidates")
    @FunctionAccess(anyOf={"PAGE_BINDING"})
    public ApiResponse<PageResponse<com.dfwl.fleet.master.dto.response.BindingDriverOption>> bindingCandidates(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        currentUserService.denyAllDrivers(user);
        return ApiResponse.success(masterDataService.listBindingCandidates(pageNo, pageSize), RequestIdHolder.get());
    }

    @GetMapping("/bindings/vehicle-options")
    @FunctionAccess(anyOf={"PAGE_BINDING"})
    public ApiResponse<PageResponse<com.dfwl.fleet.master.dto.response.BindingVehicleOption>> bindingVehicles(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        currentUserService.denyAllDrivers(user);
        return ApiResponse.success(masterDataService.listBindingVehicles(pageNo, pageSize), RequestIdHolder.get());
    }

    @GetMapping("/bindings/trailer-options")
    @FunctionAccess(anyOf={"PAGE_BINDING"})
    public ApiResponse<PageResponse<com.dfwl.fleet.master.dto.response.BindingTrailerOption>> bindingTrailers(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        currentUserService.denyAllDrivers(user);
        return ApiResponse.success(masterDataService.listBindingTrailers(pageNo, pageSize), RequestIdHolder.get());
    }

    @PostMapping("/drivers")
    @FunctionAccess(anyOf={"BUTTON_DRIVERS_ADD"})
    public ApiResponse<DriverResponse> createDriver(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody DriverRequest request) {
        currentUserService.denyAllDrivers(user);
        return ApiResponse.success(masterDataService.createDriverWithAccount(request, user.id()), RequestIdHolder.get());
    }

    @GetMapping("/drivers/{id}")
    @FunctionAccess(anyOf={"PAGE_DRIVERS"})
    public ApiResponse<DriverResponse> findDriver(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id) {
        if (currentUserService.isDriverUser(user)) {
            return ApiResponse.success(masterDataService.findDriverForCurrentUser(user, id), RequestIdHolder.get());
        }
        return ApiResponse.success(masterDataService.findDriver(id), RequestIdHolder.get());
    }

    @PutMapping("/drivers/{id}")
    @FunctionAccess(anyOf={"BUTTON_DRIVERS_EDIT"})
    public ApiResponse<DriverResponse> updateDriver(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long id,
            @Valid @RequestBody DriverRequest request) {
        currentUserService.denyAllDrivers(user);
        return ApiResponse.success(masterDataService.updateDriver(id, request, user.id()), RequestIdHolder.get());
    }

    @PostMapping("/drivers/{id}/bind-vehicle")
    @FunctionAccess(anyOf={"BUTTON_BINDING_BIND_DRIVER"})
    public ApiResponse<Void> bindDriverVehicle(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long id,
            @Valid @RequestBody BindVehicleRequest request) {
        currentUserService.denyAllDrivers(user);
        masterDataService.bindDriverVehicle(id, request.vehicleId(), user.id());
        return ApiResponse.success(RequestIdHolder.get());
    }

    @PostMapping("/drivers/{id}/unbind-vehicle")
    @FunctionAccess(anyOf={"BUTTON_BINDING_UNBIND_DRIVER"})
    public ApiResponse<Void> unbindDriverVehicle(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long id,
            @RequestBody(required = false) UnbindRequest request) {
        currentUserService.denyAllDrivers(user);
        masterDataService.unbindDriverVehicle(id, user.id(), request == null ? null : request.reason());
        return ApiResponse.success(RequestIdHolder.get());
    }

    @GetMapping("/vehicles")
    @FunctionAccess(anyOf={"PAGE_VEHICLES"})
    public ApiResponse<PageResponse<VehicleResponse>> listVehicles(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        if (currentUserService.isDriverUser(user)) {
            return ApiResponse.success(masterDataService.listVehiclesForCurrentDriver(user, pageNo, pageSize), RequestIdHolder.get());
        }
        return ApiResponse.success(masterDataService.listVehicles(pageNo, pageSize), RequestIdHolder.get());
    }

    @PostMapping("/vehicles")
    @FunctionAccess(anyOf={"BUTTON_VEHICLES_ADD"})
    public ApiResponse<VehicleResponse> createVehicle(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody VehicleRequest request) {
        currentUserService.denyAllDrivers(user);
        return ApiResponse.success(masterDataService.createVehicle(request), RequestIdHolder.get());
    }

    @GetMapping("/vehicles/{id}")
    @FunctionAccess(anyOf={"PAGE_VEHICLES"})
    public ApiResponse<VehicleResponse> findVehicle(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id) {
        if (currentUserService.isDriverUser(user)) {
            return ApiResponse.success(masterDataService.findVehicleForCurrentDriver(user, id), RequestIdHolder.get());
        }
        return ApiResponse.success(masterDataService.findVehicle(id), RequestIdHolder.get());
    }

    @PutMapping("/vehicles/{id}")
    @FunctionAccess(anyOf={"BUTTON_VEHICLES_EDIT"})
    public ApiResponse<VehicleResponse> updateVehicle(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long id,
            @Valid @RequestBody VehicleRequest request) {
        currentUserService.denyAllDrivers(user);
        return ApiResponse.success(masterDataService.updateVehicle(id, request), RequestIdHolder.get());
    }

    @GetMapping("/vehicles/{id}/binding-history")
    @FunctionAccess(anyOf={"PAGE_BINDING"})
    public ApiResponse<List<BindingHistoryResponse>> vehicleBindingHistory(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long id) {
        currentUserService.denyAllDrivers(user);
        return ApiResponse.success(masterDataService.vehicleBindingHistory(id), RequestIdHolder.get());
    }

    @GetMapping("/drivers/{id}/binding-history")
    @FunctionAccess(anyOf={"PAGE_BINDING"})
    public ApiResponse<List<BindingHistoryResponse>> driverBindingHistory(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id) {
        currentUserService.denyAllDrivers(user);
        return ApiResponse.success(masterDataService.driverBindingHistory(id), RequestIdHolder.get());
    }

    @GetMapping("/vehicles/{id}/trailer-binding-history")
    @FunctionAccess(anyOf={"PAGE_BINDING"})
    public ApiResponse<List<BindingHistoryResponse>> trailerBindingHistory(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id) {
        currentUserService.denyAllDrivers(user);
        return ApiResponse.success(masterDataService.trailerBindingHistory(id), RequestIdHolder.get());
    }

    @GetMapping("/trailers")
    @FunctionAccess(anyOf={"PAGE_TRAILERS"})
    public ApiResponse<PageResponse<TrailerResponse>> listTrailers(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        currentUserService.denyAllDrivers(user);
        return ApiResponse.success(masterDataService.listTrailers(pageNo, pageSize), RequestIdHolder.get());
    }

    @PostMapping("/trailers")
    @FunctionAccess(anyOf={"BUTTON_TRAILERS_ADD"})
    public ApiResponse<TrailerResponse> createTrailer(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody TrailerRequest request) {
        currentUserService.denyAllDrivers(user);
        return ApiResponse.success(masterDataService.createTrailer(request), RequestIdHolder.get());
    }

    @PutMapping("/trailers/{id}")
    @FunctionAccess(anyOf={"BUTTON_TRAILERS_EDIT"})
    public ApiResponse<TrailerResponse> updateTrailer(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long id,
            @Valid @RequestBody TrailerRequest request) {
        currentUserService.denyAllDrivers(user);
        return ApiResponse.success(masterDataService.updateTrailer(id, request), RequestIdHolder.get());
    }

    @PostMapping("/trailers/{id}/bind-vehicle")
    @FunctionAccess(anyOf={"BUTTON_BINDING_BIND_TRAILER"})
    public ApiResponse<Void> bindTrailerVehicle(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long id,
            @Valid @RequestBody BindTrailerVehicleRequest request) {
        currentUserService.denyAllDrivers(user);
        masterDataService.bindVehicleTrailer(id, request.vehicleId(), user.id());
        return ApiResponse.success(RequestIdHolder.get());
    }

    @PostMapping("/trailers/{id}/unbind-vehicle")
    @FunctionAccess(anyOf={"BUTTON_BINDING_UNBIND_TRAILER"})
    public ApiResponse<Void> unbindTrailerVehicle(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long id,
            @RequestBody(required = false) UnbindRequest request) {
        currentUserService.denyAllDrivers(user);
        masterDataService.unbindVehicleTrailer(id, user.id(), request == null ? null : request.reason());
        return ApiResponse.success(RequestIdHolder.get());
    }
}
