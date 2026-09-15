package com.dfwl.fleet.tire.api;

import com.dfwl.fleet.common.api.ApiResponse;
import com.dfwl.fleet.common.api.PageResponse;
import com.dfwl.fleet.common.web.RequestIdHolder;
import com.dfwl.fleet.security.AuthenticatedUser;
import com.dfwl.fleet.security.CurrentUserService;
import com.dfwl.fleet.tire.service.TireService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class TireController {

    private final TireService tireService;
    private final CurrentUserService currentUserService;

    public TireController(TireService tireService, CurrentUserService currentUserService) {
        this.tireService = tireService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/tires")
    @PreAuthorize("hasAuthority('tire:list')")
    public ApiResponse<PageResponse<TireResponse>> list(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success(tireService.list(pageNo, pageSize), RequestIdHolder.get());
    }

    @PostMapping("/tire-requests")
    @PreAuthorize("hasAuthority('tire:request')")
    public ApiResponse<TireRequestResponse> createRequest(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody TireRequestCreateRequest request) {
        currentUserService.ensureDriverCanUseTireRequest(user, request.driverId(), request.vehicleId());
        return ApiResponse.success(tireService.createRequest(request), RequestIdHolder.get());
    }

    @GetMapping("/tire-requests/{id}")
    @PreAuthorize("hasAuthority('tire:approval:view')")
    public ApiResponse<TireRequestResponse> requestDetail(@PathVariable long id) {
        return ApiResponse.success(tireService.findRequest(id), RequestIdHolder.get());
    }

    @PostMapping("/ocr/tire-number")
    @PreAuthorize("hasAuthority('tire:request')")
    public ApiResponse<OcrRecordResponse> recognize(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody OcrTireNumberRequest request) {
        currentUserService.denyOutsourcedDriver(user);
        return ApiResponse.success(tireService.recognize(request, user), RequestIdHolder.get());
    }

    @PostMapping("/ocr/{id}/confirm")
    @PreAuthorize("hasAuthority('tire:request')")
    public ApiResponse<OcrRecordResponse> confirm(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long id,
            @Valid @RequestBody OcrConfirmRequest request) {
        currentUserService.denyOutsourcedDriver(user);
        return ApiResponse.success(tireService.confirm(id, request, user.id()), RequestIdHolder.get());
    }
}
