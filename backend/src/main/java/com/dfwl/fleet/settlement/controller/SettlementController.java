package com.dfwl.fleet.settlement.controller;

import com.dfwl.fleet.common.api.ApiResponse;
import com.dfwl.fleet.common.web.RequestIdHolder;
import com.dfwl.fleet.security.AuthenticatedUser;
import com.dfwl.fleet.settlement.service.SettlementService;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/settlements")
public class SettlementController {

    private final SettlementService settlementService;

    public SettlementController(SettlementService settlementService) {
        this.settlementService = settlementService;
    }

    @GetMapping("/{yearMonth}/versions")
    @PreAuthorize("hasAuthority('settlement:view')")
    public ApiResponse<List<Map<String, Object>>> versions(@PathVariable String yearMonth) {
        return ApiResponse.success(settlementService.versions(yearMonth), RequestIdHolder.get());
    }

    @PostMapping("/{yearMonth}/versions")
    @PreAuthorize("hasAuthority('settlement:generate')")
    public ApiResponse<Map<String, Object>> generate(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String yearMonth) {
        return ApiResponse.success(settlementService.generate(yearMonth, user.id()), RequestIdHolder.get());
    }

    @GetMapping("/versions/{id}/details")
    @PreAuthorize("hasAuthority('settlement:view')")
    public ApiResponse<List<Map<String, Object>>> details(@PathVariable long id) {
        return ApiResponse.success(settlementService.details(id), RequestIdHolder.get());
    }

    @GetMapping("/versions/{id}/diff")
    @PreAuthorize("hasAuthority('settlement:diff:view')")
    public ApiResponse<List<Map<String, Object>>> diff(@PathVariable long id) {
        return ApiResponse.success(settlementService.diff(id), RequestIdHolder.get());
    }
}
