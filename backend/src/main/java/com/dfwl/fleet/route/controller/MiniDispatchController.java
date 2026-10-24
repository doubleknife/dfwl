package com.dfwl.fleet.route.controller;

import com.dfwl.fleet.common.api.ApiResponse;
import com.dfwl.fleet.common.api.PageResponse;
import com.dfwl.fleet.common.web.RequestIdHolder;
import com.dfwl.fleet.master.service.MasterDataService;
import com.dfwl.fleet.route.dto.request.*;
import com.dfwl.fleet.route.dto.response.*;
import com.dfwl.fleet.route.service.*;
import com.dfwl.fleet.security.*;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Restricted mobile projections; all writes use the existing task services. */
@RestController
@RequestMapping("/api/v1/mini-dispatch")
public class MiniDispatchController {
    private final DispatchBatchService dispatch;
    private final TransportTaskService tasks;
    private final RouteTemplateService templates;
    private final MasterDataService master;
    private final CurrentUserService users;
    public MiniDispatchController(DispatchBatchService dispatch, TransportTaskService tasks,
            RouteTemplateService templates, MasterDataService master, CurrentUserService users) {
        this.dispatch = dispatch; this.tasks = tasks; this.templates = templates; this.master = master; this.users = users;
    }
    public record TemplateOption(long id, String stableCode, String name, String loadingPlace,
            String unloadingPlace, BigDecimal defaultUnitPrice, BigDecimal defaultInformationFee,
            BigDecimal defaultDriverSalary) { }
    public record TaskSummary(long id, String taskNo, LocalDate businessDate, String templateName,
            String driverName, String plateNo, String status, int version) { }
    public record TaskDetail(TaskSummary task, String customerName, String productName,
            String loadingPlace, String unloadingPlace, BigDecimal actualUnitPrice,
            BigDecimal actualInformationFee, BigDecimal grossWeight, BigDecimal tareWeight,
            BigDecimal netWeight, java.time.LocalDateTime completedAt) { }
    public record TransferPerson(long id, long driverId, String driverName, BigDecimal salary) { }
    public record TransferContext(int expectedVersion, Long expectedCurrentParticipantId,
            List<TransferPerson> participants) { }
    private <T> ApiResponse<T> ok(T value) { return ApiResponse.success(value, RequestIdHolder.get()); }
    private TaskSummary summary(TransportTaskResponse task) {
        String name = task.currentDriverName(), plate = task.currentVehiclePlate();
        return new TaskSummary(task.id(), task.taskNo(), task.businessDate(), task.templateName(),
                name, plate, task.status(), task.version());
    }
    @GetMapping("/templates") @FunctionAccess(anyOf={"PAGE_MINI_DISPATCH"})
    public ApiResponse<List<TemplateOption>> templates(@AuthenticationPrincipal AuthenticatedUser user) {
        users.denyAllDrivers(user);
        return ok(templates.list().stream().filter(RouteTemplateResponse::enabled).map(t -> new TemplateOption(
                t.id(), t.stableCode(), t.name(), t.loadingPlace(), t.unloadingPlace(),
                t.defaultUnitPrice(), t.defaultInformationFee(), t.defaultDriverSalary())).toList());
    }
    @GetMapping("/driver-options") @FunctionAccess(anyOf={"PAGE_MINI_DISPATCH"})
    public ApiResponse<PageResponse<DispatchBatchController.DriverOption>> drivers(
            @AuthenticationPrincipal AuthenticatedUser user, @RequestParam(defaultValue="1") int pageNo,
            @RequestParam(defaultValue="100") int pageSize) {
        users.denyAllDrivers(user);
        var page = master.listDrivers(pageNo, pageSize, "", "", 1);
        return ok(new PageResponse<>(page.pageNo(), page.pageSize(), page.total(),
                page.records().stream().map(DispatchBatchController.DriverOption::from).toList()));
    }
    @GetMapping("/tasks") @FunctionAccess(anyOf={"PAGE_MINI_DISPATCH"})
    public ApiResponse<PageResponse<TaskSummary>> list(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required=false) LocalDate businessDate, @RequestParam(required=false) String status,
            @RequestParam(defaultValue="1") int pageNo,
            @RequestParam(defaultValue="20") int pageSize) {
        users.denyAllDrivers(user);
        if (status != null && !status.isBlank() && !List.of("PUBLISHED", "IN_TRANSIT", "TRANSFER_PENDING", "COMPLETED", "CANCELLED").contains(status))
            throw new com.dfwl.fleet.common.error.BusinessException(com.dfwl.fleet.common.error.ErrorCode.SYS_002);
        var page = tasks.list(pageNo, pageSize, businessDate, status, null, null, null);
        var last = tasks.lastIdentities(page.records().stream().filter(t -> t.currentParticipantId() == null)
                .map(TransportTaskResponse::id).toList());
        return ok(new PageResponse<>(page.pageNo(), page.pageSize(), page.total(), page.records().stream().map(t -> {
            var identity = last.get(t.id());
            return identity == null ? summary(t) : new TaskSummary(t.id(), t.taskNo(), t.businessDate(),
                    t.templateName(), identity.driverName(), identity.plateNo(), t.status(), t.version());
        }).toList()));
    }
    @GetMapping("/tasks/{id}") @FunctionAccess(anyOf={"PAGE_MINI_DISPATCH"})
    public ApiResponse<TaskDetail> detail(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id) {
        users.denyAllDrivers(user);
        var task = tasks.find(id);
        var identity = task.currentParticipantId() == null ? tasks.lastIdentities(List.of(id)).get(id) : null;
        var summary = identity == null ? summary(task) : new TaskSummary(task.id(), task.taskNo(), task.businessDate(),
                task.templateName(), identity.driverName(), identity.plateNo(), task.status(), task.version());
        return ok(new TaskDetail(summary, task.customerName(), task.productName(), task.loadingPlace(),
                task.unloadingPlace(), task.actualUnitPrice(), task.actualInformationFee(), task.grossWeight(),
                task.tareWeight(), task.netWeight(), task.completedAt()));
    }
    @PostMapping("/preview") @FunctionAccess(anyOf={"BUTTON_MINI_DISPATCH_PUBLISH"})
    public ApiResponse<DispatchPreviewResponse> preview(@AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody DispatchBatchRequest request) {
        users.denyAllDrivers(user); return ok(dispatch.previewSingle(request, user.id()));
    }
    @PostMapping("/confirm") @FunctionAccess(anyOf={"BUTTON_MINI_DISPATCH_PUBLISH"})
    public ApiResponse<DispatchBatchResponse> confirm(@AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody DispatchConfirmRequest request) {
        users.denyAllDrivers(user); return ok(dispatch.confirmSingle(request, user.id()));
    }
    @GetMapping("/tasks/{id}/transfer-context") @FunctionAccess(anyOf={"BUTTON_MINI_DISPATCH_TRANSFER"})
    public ApiResponse<TransferContext> transferContext(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id) {
        users.denyAllDrivers(user);
        var task = tasks.find(id);
        if (!List.of("IN_TRANSIT", "TRANSFER_PENDING").contains(task.status()))
            throw new com.dfwl.fleet.common.error.BusinessException(com.dfwl.fleet.common.error.ErrorCode.ROUTE_002);
        return ok(new TransferContext(task.version(), task.currentParticipantId(), tasks.participants(id, true).stream()
                .map(p -> new TransferPerson(p.id(), p.driverId(), p.driverName(), p.salary())).toList()));
    }
    @PostMapping("/tasks/{id}/transfer") @FunctionAccess(anyOf={"BUTTON_MINI_DISPATCH_TRANSFER"})
    public ApiResponse<TaskSummary> transfer(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id,
            @Valid @RequestBody TaskTransferRequest request) {
        users.denyAllDrivers(user); return ok(summary(tasks.transfer(id, request, user.id())));
    }
    @PostMapping("/tasks/{id}/cancel") @FunctionAccess(anyOf={"BUTTON_MINI_DISPATCH_CANCEL"})
    public ApiResponse<TaskSummary> cancel(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id,
            @Valid @RequestBody TaskCancelRequest request) {
        users.denyAllDrivers(user); return ok(summary(tasks.cancel(id, request, user.id())));
    }
}
