package com.dfwl.fleet.route.api;

import com.dfwl.fleet.common.api.ApiResponse;
import com.dfwl.fleet.common.api.PageResponse;
import com.dfwl.fleet.common.web.RequestIdHolder;
import com.dfwl.fleet.route.service.RouteService;
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
@RequestMapping("/api/v1/routes")
public class RouteController {

    private final RouteService routeService;
    private final CurrentUserService currentUserService;

    public RouteController(RouteService routeService, CurrentUserService currentUserService) {
        this.routeService = routeService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('route:list')")
    public ApiResponse<?> list(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        if (currentUserService.isDriverUser(user)) {
            return ApiResponse.success(routeService.listForDriver(user, pageNo, pageSize), RequestIdHolder.get());
        }
        return ApiResponse.success(routeService.list(pageNo, pageSize), RequestIdHolder.get());
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
    public ApiResponse<RouteResponse> publish(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id) {
        currentUserService.denyAllDrivers(user);
        return ApiResponse.success(routeService.publish(id, user.id()), RequestIdHolder.get());
    }

    @PostMapping("/{id}/depart")
    @PreAuthorize("hasAuthority('route:depart')")
    public ApiResponse<RouteResponse> depart(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id) {
        currentUserService.ensureDriverCanDepart(user, id);
        return ApiResponse.success(routeService.depart(id, user.id()), RequestIdHolder.get());
    }

    @PostMapping("/{id}/unload")
    @PreAuthorize("hasAuthority('route:unload')")
    public ApiResponse<RouteResponse> unload(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long id,
            @Valid @RequestBody UnloadRequest request) {
        currentUserService.ensureDriverCanUnload(user, id);
        return ApiResponse.success(routeService.unload(id, request, user.id()), RequestIdHolder.get());
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
    public ApiResponse<List<RouteWeightVersionResponse>> weightVersions(@PathVariable long id) {
        return ApiResponse.success(routeService.weightVersions(id), RequestIdHolder.get());
    }
}
