package com.dfwl.fleet.finance.v3;

import com.dfwl.fleet.common.api.ApiResponse;
import com.dfwl.fleet.common.api.PageResponse;
import com.dfwl.fleet.common.web.RequestIdHolder;
import com.dfwl.fleet.finance.v3.ExpenseLedgerService.Attribution;
import com.dfwl.fleet.finance.v3.ExpenseLedgerService.Filter;
import com.dfwl.fleet.finance.v3.ExpenseLedgerService.Reversal;
import com.dfwl.fleet.security.AuthenticatedUser;
import com.dfwl.fleet.security.CurrentUserService;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import com.dfwl.fleet.security.FunctionAccess;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FinanceV3Controller {
    private final ExpenseLedgerService ledger;
    private final FinanceFactsService facts;
    private final CurrentUserService users;

    public FinanceV3Controller(ExpenseLedgerService ledger, FinanceFactsService facts, CurrentUserService users) {
        this.ledger = ledger; this.facts = facts; this.users = users;
    }

    @GetMapping("/api/v1/expense-ledger/filter-options")
    @FunctionAccess(anyOf={"PAGE_EXPENSE_LEDGER", "PAGE_EXPENSE_UNASSIGNED", "PAGE_FINANCE_VEHICLES", "PAGE_FINANCE_DRIVERS", "PAGE_FINANCE_ENERGY"})
    public ApiResponse<PageResponse<Map<String, Object>>> expenseOptions(
            @AuthenticationPrincipal AuthenticatedUser user, @RequestParam String kind,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(required = false) Long vehicleId, @RequestParam(defaultValue = "1") int pageNo) {
        users.denyAllDrivers(user);
        return ok(ledger.options(kind, search, vehicleId, pageNo));
    }

    @GetMapping("/api/v1/expense-ledger")
    @FunctionAccess(anyOf={"PAGE_EXPENSE_LEDGER", "PAGE_FINANCE_VEHICLES", "PAGE_FINANCE_DRIVERS", "PAGE_FINANCE_ENERGY"})
    public ApiResponse<PageResponse<Map<String, Object>>> expenses(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) String month, @RequestParam(required = false) String expenseType,
            @RequestParam(required = false) String applicationScope, @RequestParam(required = false) Long driverId,
            @RequestParam(required = false) Long vehicleId, @RequestParam(required = false) Boolean bound,
            @RequestParam(required = false) Long taskId, @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) String status, @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        users.denyAllDrivers(user);
        return ok(ledger.list(filter(month, expenseType, applicationScope, driverId, vehicleId, bound,
                taskId, sourceType, status), pageNo, pageSize));
    }

    @GetMapping("/api/v1/expense-ledger/summary")
    @FunctionAccess(anyOf={"PAGE_EXPENSE_LEDGER", "PAGE_FINANCE_VEHICLES", "PAGE_FINANCE_DRIVERS", "PAGE_FINANCE_ENERGY"})
    public ApiResponse<Map<String, Object>> expenseSummary(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) String month, @RequestParam(required = false) String expenseType,
            @RequestParam(required = false) String applicationScope, @RequestParam(required = false) Long driverId,
            @RequestParam(required = false) Long vehicleId, @RequestParam(required = false) Boolean bound,
            @RequestParam(required = false) Long taskId, @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) String status) {
        users.denyAllDrivers(user);
        return ok(ledger.summary(filter(month, expenseType, applicationScope, driverId, vehicleId, bound,
                taskId, sourceType, status)));
    }

    @GetMapping("/api/v1/expense-ledger/pending-attribution")
    @FunctionAccess(anyOf={"PAGE_EXPENSE_UNASSIGNED"})
    public ApiResponse<PageResponse<Map<String, Object>>> pendingAttribution(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) String month, @RequestParam(required = false) String expenseType,
            @RequestParam(required = false) Long driverId, @RequestParam(required = false) Long vehicleId,
            @RequestParam(required = false) String sourceType,
            @RequestParam(defaultValue = "1") int pageNo, @RequestParam(defaultValue = "20") int pageSize) {
        users.denyAllDrivers(user);
        return ok(ledger.list(pendingFilter(month, expenseType, driverId, vehicleId, sourceType), pageNo, pageSize));
    }

    @GetMapping("/api/v1/expense-ledger/pending-attribution/summary")
    @FunctionAccess(anyOf={"PAGE_EXPENSE_UNASSIGNED"})
    public ApiResponse<Map<String, Object>> pendingAttributionSummary(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) String month, @RequestParam(required = false) String expenseType,
            @RequestParam(required = false) Long driverId, @RequestParam(required = false) Long vehicleId,
            @RequestParam(required = false) String sourceType) {
        users.denyAllDrivers(user);
        return ok(ledger.summary(pendingFilter(month, expenseType, driverId, vehicleId, sourceType)));
    }

    private Filter pendingFilter(String month, String expenseType, Long driverId, Long vehicleId, String sourceType) {
        return new Filter(month, expenseType, null, driverId, vehicleId, false, null, sourceType, "ACTIVE");
    }

    @GetMapping("/api/v1/expense-ledger/export")
    @FunctionAccess(anyOf={"BUTTON_EXPENSE_LEDGER_EXPORT"})
    public ResponseEntity<byte[]> export(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) String month, @RequestParam(required = false) String expenseType,
            @RequestParam(required = false) String applicationScope, @RequestParam(required = false) Long driverId,
            @RequestParam(required = false) Long vehicleId, @RequestParam(required = false) Boolean bound,
            @RequestParam(required = false) Long taskId, @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) String status) {
        users.denyAllDrivers(user);
        byte[] bytes = ledger.export(filter(month, expenseType, applicationScope, driverId, vehicleId, bound,
                taskId, sourceType, status));
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=expense-ledger.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }

    @GetMapping("/api/v1/expense-ledger/{id}")
    @FunctionAccess(anyOf={"PAGE_EXPENSE_LEDGER", "PAGE_EXPENSE_UNASSIGNED", "PAGE_FINANCE_VEHICLES", "PAGE_FINANCE_DRIVERS", "PAGE_FINANCE_ENERGY"})
    public ApiResponse<Map<String, Object>> expense(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long id) {
        users.denyAllDrivers(user);
        return ok(ledger.detail(id));
    }

    @PostMapping("/api/v1/expense-ledger/{id}/attribution")
    @FunctionAccess(anyOf={"BUTTON_EXPENSE_UNASSIGNED_ATTRIBUTE"})
    public ApiResponse<Map<String, Object>> attribute(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long id, @RequestBody Attribution command) {
        users.denyAllDrivers(user);
        return ok(ledger.attribute(id, command, user.id()));
    }

    @PostMapping("/api/v1/expense-ledger/{id}/reverse")
    @FunctionAccess(anyOf={"BUTTON_EXPENSE_LEDGER_REVERSE"})
    public ApiResponse<Map<String, Object>> reverse(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long id, @RequestBody Reversal command) {
        users.denyAllDrivers(user);
        return ok(ledger.reverse(id, command, user.id()));
    }

    @GetMapping("/api/v1/finance/task-summaries")
    @FunctionAccess(anyOf={"PAGE_FINANCE_TASKS"})
    public ApiResponse<List<Map<String, Object>>> taskSummaries(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam List<Long> taskIds) {
        users.denyAllDrivers(user);
        return ok(facts.tasksByIds(taskIds));
    }

    @GetMapping("/api/v1/finance/tasks")
    @FunctionAccess(anyOf={"PAGE_FINANCE_TASKS"})
    public ApiResponse<PageResponse<Map<String, Object>>> tasks(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) String month, @RequestParam(required = false) Long templateId,
            @RequestParam(defaultValue = "1") int pageNo, @RequestParam(defaultValue = "20") int pageSize) {
        users.denyAllDrivers(user);
        return ok(facts.tasks(month, templateId, pageNo, pageSize));
    }

    @GetMapping("/api/v1/finance/tasks/{id}")
    @FunctionAccess(anyOf={"PAGE_FINANCE_TASKS"})
    public ApiResponse<Map<String, Object>> task(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long id) {
        users.denyAllDrivers(user);
        return ok(Map.of("summary", facts.task(id), "participants", facts.taskParticipants(id),
                "expenses", facts.taskExpenses(id), "expenseTypes", facts.taskExpenseTypes(id)));
    }

    @GetMapping("/api/v1/transport-tasks/{id}/expenses")
    @FunctionAccess(anyOf={"PAGE_FINANCE_TASKS"})
    public ApiResponse<List<Map<String, Object>>> taskExpenses(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long id) {
        users.denyAllDrivers(user);
        return ok(facts.taskExpenses(id));
    }

    @GetMapping("/api/v1/finance/templates")
    @FunctionAccess(anyOf={"PAGE_FINANCE_TEMPLATES"})
    public ApiResponse<PageResponse<Map<String, Object>>> templates(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) String month,
            @RequestParam(defaultValue = "1") int pageNo, @RequestParam(defaultValue = "20") int pageSize) {
        users.denyAllDrivers(user);
        return ok(facts.templates(month, pageNo, pageSize));
    }

    @GetMapping("/api/v1/finance/company")
    @FunctionAccess(anyOf={"PAGE_FINANCE_COMPANY"})
    public ApiResponse<Map<String, Object>> company(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam String month) {
        users.denyAllDrivers(user);
        return ok(facts.company(month));
    }

    @GetMapping("/api/v1/finance/dashboard")
    @FunctionAccess(anyOf={"PAGE_DASHBOARD"})
    public ApiResponse<Map<String, Object>> dashboard(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam String month) {
        users.denyAllDrivers(user);
        return ok(facts.dashboard(month));
    }

    private Filter filter(String month, String type, String scope, Long driverId, Long vehicleId, Boolean bound,
                          Long taskId, String source, String status) {
        return new Filter(month, type, scope, driverId, vehicleId, bound, taskId, source, status);
    }
    private <T> ApiResponse<T> ok(T data) { return ApiResponse.success(data, RequestIdHolder.get()); }
}
