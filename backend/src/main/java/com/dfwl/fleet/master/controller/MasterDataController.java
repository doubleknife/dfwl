package com.dfwl.fleet.master.controller;

import com.dfwl.fleet.master.dto.response.BindingHistoryResponse;
import com.dfwl.fleet.master.dto.request.BindTrailerVehicleRequest;
import com.dfwl.fleet.master.dto.request.BindVehicleRequest;
import com.dfwl.fleet.master.dto.request.CustomerRequest;
import com.dfwl.fleet.master.dto.response.CustomerResponse;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
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

    @GetMapping("/customers")
    @PreAuthorize("hasAuthority('route:list')")
    public ApiResponse<PageResponse<CustomerResponse>> listCustomers(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        currentUserService.denyAllDrivers(user);
        return ApiResponse.success(masterDataService.listCustomers(pageNo, pageSize), RequestIdHolder.get());
    }

    @PostMapping("/customers")
    @PreAuthorize("hasAuthority('route:create')")
    public ApiResponse<CustomerResponse> createCustomer(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CustomerRequest request) {
        currentUserService.denyAllDrivers(user);
        return ApiResponse.success(masterDataService.createCustomer(request), RequestIdHolder.get());
    }

    @PutMapping("/customers/{id}")
    @PreAuthorize("hasAuthority('route:edit')")
    public ApiResponse<CustomerResponse> updateCustomer(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long id,
            @Valid @RequestBody CustomerRequest request) {
        currentUserService.denyAllDrivers(user);
        return ApiResponse.success(masterDataService.updateCustomer(id, request), RequestIdHolder.get());
    }

    @PutMapping("/customers/{id}/status")
    @PreAuthorize("hasAuthority('route:edit')")
    public ApiResponse<CustomerResponse> updateCustomerStatus(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long id,
            @Valid @RequestBody StatusRequest request) {
        currentUserService.denyAllDrivers(user);
        return ApiResponse.success(masterDataService.updateCustomerStatus(id, request.status()), RequestIdHolder.get());
    }

    @GetMapping("/products")
    @PreAuthorize("hasAuthority('route:list')")
    public ApiResponse<PageResponse<ProductResponse>> listProducts(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        currentUserService.denyAllDrivers(user);
        return ApiResponse.success(masterDataService.listProducts(pageNo, pageSize), RequestIdHolder.get());
    }

    @PostMapping("/products")
    @PreAuthorize("hasAuthority('route:create')")
    public ApiResponse<ProductResponse> createProduct(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody ProductRequest request) {
        currentUserService.denyAllDrivers(user);
        return ApiResponse.success(masterDataService.createProduct(request), RequestIdHolder.get());
    }

    @PutMapping("/products/{id}")
    @PreAuthorize("hasAuthority('route:edit')")
    public ApiResponse<ProductResponse> updateProduct(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long id,
            @Valid @RequestBody ProductRequest request) {
        currentUserService.denyAllDrivers(user);
        return ApiResponse.success(masterDataService.updateProduct(id, request), RequestIdHolder.get());
    }

    @PutMapping("/products/{id}/status")
    @PreAuthorize("hasAuthority('route:edit')")
    public ApiResponse<ProductResponse> updateProductStatus(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long id,
            @Valid @RequestBody StatusRequest request) {
        currentUserService.denyAllDrivers(user);
        return ApiResponse.success(masterDataService.updateProductStatus(id, request.status()), RequestIdHolder.get());
    }

    @GetMapping("/drivers")
    @PreAuthorize("hasAuthority('driver:list')")
    public ApiResponse<PageResponse<DriverResponse>> listDrivers(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        if (currentUserService.isDriverUser(user)) {
            return ApiResponse.success(masterDataService.listDriversForCurrentDriver(user, pageNo, pageSize), RequestIdHolder.get());
        }
        return ApiResponse.success(masterDataService.listDrivers(pageNo, pageSize), RequestIdHolder.get());
    }

    @PostMapping("/drivers")
    @PreAuthorize("hasAuthority('driver:add')")
    public ApiResponse<DriverResponse> createDriver(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody DriverRequest request) {
        currentUserService.denyAllDrivers(user);
        return ApiResponse.success(masterDataService.createDriver(request), RequestIdHolder.get());
    }

    @GetMapping("/drivers/{id}")
    @PreAuthorize("hasAuthority('driver:list')")
    public ApiResponse<DriverResponse> findDriver(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id) {
        if (currentUserService.isDriverUser(user)) {
            return ApiResponse.success(masterDataService.findDriverForCurrentUser(user, id), RequestIdHolder.get());
        }
        return ApiResponse.success(masterDataService.findDriver(id), RequestIdHolder.get());
    }

    @PutMapping("/drivers/{id}")
    @PreAuthorize("hasAuthority('driver:edit')")
    public ApiResponse<DriverResponse> updateDriver(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long id,
            @Valid @RequestBody DriverRequest request) {
        currentUserService.denyAllDrivers(user);
        return ApiResponse.success(masterDataService.updateDriver(id, request), RequestIdHolder.get());
    }

    @PostMapping("/drivers/{id}/bind-vehicle")
    @PreAuthorize("hasAuthority('driver:bindVehicle')")
    public ApiResponse<Void> bindDriverVehicle(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long id,
            @Valid @RequestBody BindVehicleRequest request) {
        currentUserService.denyAllDrivers(user);
        masterDataService.bindDriverVehicle(id, request.vehicleId(), user.id());
        return ApiResponse.success(RequestIdHolder.get());
    }

    @PostMapping("/drivers/{id}/unbind-vehicle")
    @PreAuthorize("hasAuthority('driver:unbindVehicle')")
    public ApiResponse<Void> unbindDriverVehicle(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long id,
            @RequestBody(required = false) UnbindRequest request) {
        currentUserService.denyAllDrivers(user);
        masterDataService.unbindDriverVehicle(id, user.id(), request == null ? null : request.reason());
        return ApiResponse.success(RequestIdHolder.get());
    }

    @GetMapping("/vehicles")
    @PreAuthorize("hasAuthority('vehicle:list')")
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
    @PreAuthorize("hasAuthority('vehicle:add')")
    public ApiResponse<VehicleResponse> createVehicle(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody VehicleRequest request) {
        currentUserService.denyAllDrivers(user);
        return ApiResponse.success(masterDataService.createVehicle(request), RequestIdHolder.get());
    }

    @GetMapping("/vehicles/{id}")
    @PreAuthorize("hasAuthority('vehicle:view')")
    public ApiResponse<VehicleResponse> findVehicle(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id) {
        if (currentUserService.isDriverUser(user)) {
            return ApiResponse.success(masterDataService.findVehicleForCurrentDriver(user, id), RequestIdHolder.get());
        }
        return ApiResponse.success(masterDataService.findVehicle(id), RequestIdHolder.get());
    }

    @PutMapping("/vehicles/{id}")
    @PreAuthorize("hasAuthority('vehicle:edit')")
    public ApiResponse<VehicleResponse> updateVehicle(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long id,
            @Valid @RequestBody VehicleRequest request) {
        currentUserService.denyAllDrivers(user);
        return ApiResponse.success(masterDataService.updateVehicle(id, request), RequestIdHolder.get());
    }

    @DeleteMapping("/vehicles/{id}")
    @PreAuthorize("hasAuthority('vehicle:delete')")
    public ApiResponse<Void> deleteVehicle(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id) {
        currentUserService.denyAllDrivers(user);
        masterDataService.deleteVehicle(id);
        return ApiResponse.success(RequestIdHolder.get());
    }

    @GetMapping("/vehicles/{id}/binding-history")
    @PreAuthorize("hasAuthority('vehicle:bindingHistory')")
    public ApiResponse<List<BindingHistoryResponse>> vehicleBindingHistory(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long id) {
        currentUserService.denyAllDrivers(user);
        return ApiResponse.success(masterDataService.vehicleBindingHistory(id), RequestIdHolder.get());
    }

    @GetMapping("/trailers")
    @PreAuthorize("hasAuthority('trailer:list')")
    public ApiResponse<PageResponse<TrailerResponse>> listTrailers(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        currentUserService.denyAllDrivers(user);
        return ApiResponse.success(masterDataService.listTrailers(pageNo, pageSize), RequestIdHolder.get());
    }

    @PostMapping("/trailers")
    @PreAuthorize("hasAuthority('trailer:add')")
    public ApiResponse<TrailerResponse> createTrailer(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody TrailerRequest request) {
        currentUserService.denyAllDrivers(user);
        return ApiResponse.success(masterDataService.createTrailer(request), RequestIdHolder.get());
    }

    @PutMapping("/trailers/{id}")
    @PreAuthorize("hasAuthority('trailer:edit')")
    public ApiResponse<TrailerResponse> updateTrailer(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long id,
            @Valid @RequestBody TrailerRequest request) {
        currentUserService.denyAllDrivers(user);
        return ApiResponse.success(masterDataService.updateTrailer(id, request), RequestIdHolder.get());
    }

    @PostMapping("/trailers/{id}/bind-vehicle")
    @PreAuthorize("hasAuthority('trailer:bindVehicle')")
    public ApiResponse<Void> bindTrailerVehicle(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long id,
            @Valid @RequestBody BindTrailerVehicleRequest request) {
        currentUserService.denyAllDrivers(user);
        masterDataService.bindVehicleTrailer(id, request.vehicleId(), user.id());
        return ApiResponse.success(RequestIdHolder.get());
    }

    @PostMapping("/trailers/{id}/unbind-vehicle")
    @PreAuthorize("hasAuthority('trailer:unbindVehicle')")
    public ApiResponse<Void> unbindTrailerVehicle(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long id,
            @RequestBody(required = false) UnbindRequest request) {
        currentUserService.denyAllDrivers(user);
        masterDataService.unbindVehicleTrailer(id, user.id(), request == null ? null : request.reason());
        return ApiResponse.success(RequestIdHolder.get());
    }
}
