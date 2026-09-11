package com.dfwl.fleet.expense.api;

import com.dfwl.fleet.common.api.ApiResponse;
import com.dfwl.fleet.common.api.PageResponse;
import com.dfwl.fleet.common.web.RequestIdHolder;
import com.dfwl.fleet.expense.service.ExpenseService;
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
@RequestMapping("/api/v1/expenses")
public class ExpenseController {

    private final ExpenseService expenseService;
    private final CurrentUserService currentUserService;

    public ExpenseController(ExpenseService expenseService, CurrentUserService currentUserService) {
        this.expenseService = expenseService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('expense:list')")
    public ApiResponse<PageResponse<ExpenseResponse>> list(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        currentUserService.denyAllDrivers(user);
        return ApiResponse.success(expenseService.list(pageNo, pageSize), RequestIdHolder.get());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('expense:add')")
    public ApiResponse<ExpenseResponse> create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody ExpenseRequest request) {
        currentUserService.denyAllDrivers(user);
        return ApiResponse.success(expenseService.create(request, user.id()), RequestIdHolder.get());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('expense:edit')")
    public ApiResponse<ExpenseResponse> update(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long id,
            @Valid @RequestBody ExpenseRequest request) {
        currentUserService.denyAllDrivers(user);
        return ApiResponse.success(expenseService.update(id, request, user.id()), RequestIdHolder.get());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('expense:delete')")
    public ApiResponse<Void> delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id) {
        currentUserService.denyAllDrivers(user);
        expenseService.delete(id, user.id());
        return ApiResponse.success(RequestIdHolder.get());
    }

    @PostMapping("/{id}/reversal")
    @PreAuthorize("hasAuthority('expense:reversal')")
    public ApiResponse<ExpenseResponse> reverse(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long id,
            @Valid @RequestBody ExpenseReversalRequest request) {
        currentUserService.denyAllDrivers(user);
        return ApiResponse.success(expenseService.reverse(id, request, user.id()), RequestIdHolder.get());
    }

    @PutMapping("/{id}/attribution")
    @PreAuthorize("hasAuthority('expense:attribution:edit')")
    public ApiResponse<ExpenseResponse> attribution(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long id,
            @Valid @RequestBody ExpenseAttributionRequest request) {
        currentUserService.denyAllDrivers(user);
        return ApiResponse.success(expenseService.adjustAttribution(id, request, user.id()), RequestIdHolder.get());
    }

    @GetMapping("/{id}/attribution-history")
    @PreAuthorize("hasAuthority('expense:attribution:edit')")
    public ApiResponse<List<ExpenseAttributionHistoryResponse>> attributionHistory(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long id) {
        currentUserService.denyAllDrivers(user);
        return ApiResponse.success(expenseService.attributionHistory(id), RequestIdHolder.get());
    }

    @GetMapping("/{id}/approval")
    @PreAuthorize("hasAuthority('expense:approval:view')")
    public ApiResponse<Object> approval(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id) {
        currentUserService.denyAllDrivers(user);
        return ApiResponse.success(expenseService.approvalChain(id), RequestIdHolder.get());
    }
}
