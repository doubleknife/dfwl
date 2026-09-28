package com.dfwl.fleet.route.controller;

import com.dfwl.fleet.route.dto.request.RouteReasonRequest;
import com.dfwl.fleet.route.dto.request.RouteRequest;
import com.dfwl.fleet.route.dto.request.RoutePublishRequest;
import com.dfwl.fleet.route.dto.request.RouteTransferRequest;
import com.dfwl.fleet.route.dto.request.ParticipantSalaryAdjustmentRequest;
import com.dfwl.fleet.route.dto.response.RouteResponse;
import com.dfwl.fleet.route.dto.response.RouteWeightVersionResponse;
import com.dfwl.fleet.route.dto.request.UnloadRequest;
import com.dfwl.fleet.route.dto.request.WeightAdjustmentRequest;

import com.dfwl.fleet.common.api.ApiResponse;
import com.dfwl.fleet.common.api.PageResponse;
import com.dfwl.fleet.common.web.RequestIdHolder;
import com.dfwl.fleet.route.service.RouteService;
import com.dfwl.fleet.route.policy.RouteAccessPolicy;
import com.dfwl.fleet.security.AuthenticatedUser;
import com.dfwl.fleet.security.CurrentUserService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
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
@RequestMapping("/api/v1/routes")
public class RouteController {

    private final RouteService routeService;
    private final CurrentUserService currentUserService;
    private final RouteAccessPolicy routeAccessPolicy;

    public RouteController(RouteService routeService, CurrentUserService currentUserService, RouteAccessPolicy routeAccessPolicy) {
        this.routeService = routeService;
        this.currentUserService = currentUserService;
        this.routeAccessPolicy = routeAccessPolicy;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('route:list')")
    public ApiResponse<?> list(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Long driverId,
            @RequestParam(required = false) String keyword) {
        if (currentUserService.isDriverUser(user)) {
            return ApiResponse.success(routeService.listForDriver(user, pageNo, pageSize, status), RequestIdHolder.get());
        }
        return ApiResponse.success(routeService.list(pageNo, pageSize, status, businessDate, customerId, driverId, keyword), RequestIdHolder.get());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('route:create')")
    public ApiResponse<RouteResponse> create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody RouteRequest request) {
        currentUserService.denyAllDrivers(user);
        return ApiResponse.success(routeService.create(request, user.id()), RequestIdHolder.get());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('route:list')")
    public ApiResponse<?> find(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id) {
        if (currentUserService.isDriverUser(user)) {
            return ApiResponse.success(routeService.findForDriver(user, id), RequestIdHolder.get());
        }
        return ApiResponse.success(routeService.find(id), RequestIdHolder.get());
    }

    @GetMapping("/{id}/participants")
    @PreAuthorize("hasAuthority('route:list')")
    public ApiResponse<?> participants(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id) {
        currentUserService.denyAllDrivers(user);
        boolean salaryAllowed = user.permissions().contains("salary:view")
                || user.permissions().contains("route:transfer")
                || user.permissions().contains("route:salary:adjust");
        return ApiResponse.success(routeService.participants(id, salaryAllowed), RequestIdHolder.get());
    }

    @GetMapping("/{id}/participants/{participantId}/salary-history")
    @PreAuthorize("hasAuthority('route:list') and hasAuthority('salary:view')")
    public ApiResponse<?> participantSalaryHistory(@AuthenticationPrincipal AuthenticatedUser user,
                                                     @PathVariable long id, @PathVariable long participantId) {
        currentUserService.denyAllDrivers(user);
        return ApiResponse.success(routeService.participantSalaryHistory(id, participantId), RequestIdHolder.get());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('route:edit')")
    public ApiResponse<RouteResponse> update(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long id,
            @Valid @RequestBody RouteRequest request) {
        currentUserService.denyAllDrivers(user);
        return ApiResponse.success(routeService.update(id, request, user.id()), RequestIdHolder.get());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('route:delete')")
    public ApiResponse<Void> delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id) {
        currentUserService.denyAllDrivers(user);
        routeService.delete(id, user.id());
        return ApiResponse.success(RequestIdHolder.get());
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAuthority('route:publish')")
    public ApiResponse<RouteResponse> publish(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id,
                                               @Valid @RequestBody RoutePublishRequest request) {
        currentUserService.denyAllDrivers(user);
        return ApiResponse.success(routeService.publish(id, request, user.id()), RequestIdHolder.get());
    }

    @PostMapping("/{id}/transfer")
    @PreAuthorize("hasAuthority('route:transfer')")
    public ApiResponse<RouteResponse> transfer(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id,
                                                @Valid @RequestBody RouteTransferRequest request) {
        currentUserService.denyAllDrivers(user);
        return ApiResponse.success(routeService.transfer(id, request, user.id()), RequestIdHolder.get());
    }

    @PostMapping("/{id}/participants/{participantId}/salary-adjustments")
    @PreAuthorize("hasAuthority('route:salary:adjust')")
    public ApiResponse<RouteResponse> adjustParticipantSalary(@AuthenticationPrincipal AuthenticatedUser user,
                                                                @PathVariable long id, @PathVariable long participantId,
                                                                @Valid @RequestBody ParticipantSalaryAdjustmentRequest request) {
        currentUserService.denyAllDrivers(user);
        return ApiResponse.success(routeService.adjustParticipantSalary(id, participantId, request, user.id()), RequestIdHolder.get());
    }

    @PostMapping("/{id}/depart")
    @PreAuthorize("hasAuthority('route:depart')")
    public ApiResponse<?> depart(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id) {
        routeAccessPolicy.ensureDriverCanDepart(user, id);
        RouteResponse route = routeService.depart(id, user.id());
        return ApiResponse.success(currentUserService.isDriverUser(user)
                ? routeService.driverResponse(route) : route, RequestIdHolder.get());
    }

    @PostMapping("/{id}/unload")
    @PreAuthorize("hasAuthority('route:unload')")
    public ApiResponse<?> unload(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long id,
            @Valid @RequestBody UnloadRequest request) {
        routeAccessPolicy.ensureDriverCanUnload(user, id);
        RouteResponse route = routeService.unload(id, request, user);
        return ApiResponse.success(routeService.driverResponse(route), RequestIdHolder.get());
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('route:cancel')")
    public ApiResponse<RouteResponse> cancel(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long id,
            @RequestBody(required = false) RouteReasonRequest request) {
        currentUserService.denyAllDrivers(user);
        return ApiResponse.success(routeService.cancel(id, request, user.id()), RequestIdHolder.get());
    }

    @PostMapping("/{id}/void")
    @PreAuthorize("hasAuthority('route:void')")
    public ApiResponse<RouteResponse> voidRoute(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long id,
            @RequestBody(required = false) RouteReasonRequest request) {
        currentUserService.denyAllDrivers(user);
        return ApiResponse.success(routeService.voidRoute(id, request, user.id()), RequestIdHolder.get());
    }

    @PostMapping("/{id}/reactivate")
    @PreAuthorize("hasAuthority('route:reactivate')")
    public ApiResponse<RouteResponse> reactivate(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id) {
        currentUserService.denyAllDrivers(user);
        return ApiResponse.success(routeService.reactivate(id, user.id()), RequestIdHolder.get());
    }

    @PostMapping("/{id}/weight-adjustments")
    @PreAuthorize("hasAuthority('route:weight:adjust')")
    public ApiResponse<RouteResponse> adjustWeight(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long id,
            @Valid @RequestBody WeightAdjustmentRequest request) {
        currentUserService.denyAllDrivers(user);
        return ApiResponse.success(routeService.adjustWeight(id, request, user.id()), RequestIdHolder.get());
    }

    @GetMapping("/{id}/weight-versions")
    @PreAuthorize("hasAuthority('route:list')")
    public ApiResponse<List<RouteWeightVersionResponse>> weightVersions(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id) {
        currentUserService.denyAllDrivers(user);
        return ApiResponse.success(routeService.weightVersions(id), RequestIdHolder.get());
    }
}
