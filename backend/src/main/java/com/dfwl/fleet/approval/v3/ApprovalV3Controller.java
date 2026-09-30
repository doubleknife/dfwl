package com.dfwl.fleet.approval.v3;

import com.dfwl.fleet.approval.v3.ApprovalWorkflowService.Action;
import com.dfwl.fleet.approval.v3.ApprovalWorkflowService.Application;
import com.dfwl.fleet.approval.v3.ApprovalWorkflowService.Resubmission;
import com.dfwl.fleet.approval.v3.ApprovalWorkflowService.TemplateInput;
import com.dfwl.fleet.common.api.ApiResponse;
import com.dfwl.fleet.common.api.PageResponse;
import com.dfwl.fleet.common.web.RequestIdHolder;
import com.dfwl.fleet.security.AuthenticatedUser;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Only exposed V3 approval and expense-application endpoints. */
@RestController
public class ApprovalV3Controller {
    private final ApprovalWorkflowService workflows;
    private final DailyExpenseWorkbook workbook;
    public ApprovalV3Controller(ApprovalWorkflowService workflows, DailyExpenseWorkbook workbook) {
        this.workflows = workflows;
        this.workbook = workbook;
    }

    @GetMapping("/api/v1/approval-templates")
    @PreAuthorize("hasAuthority('approval:flow:manage')")
    public ApiResponse<List<Map<String, Object>>> templates(@AuthenticationPrincipal AuthenticatedUser user) {
        return ok(workflows.templates(user, true));
    }
    @GetMapping("/api/v1/approval-templates/available")
    @PreAuthorize("hasAuthority('approval:create')")
    public ApiResponse<List<Map<String, Object>>> available(@AuthenticationPrincipal AuthenticatedUser user) {
        return ok(workflows.templates(user, false));
    }
    @GetMapping("/api/v1/approval-templates/options")
    @PreAuthorize("hasAuthority('approval:flow:manage')")
    public ApiResponse<Map<String, Object>> flowOptions(@AuthenticationPrincipal AuthenticatedUser user) {
        return ok(workflows.flowOptions(user));
    }
    @PostMapping("/api/v1/approval-templates")
    @PreAuthorize("hasAuthority('approval:flow:manage')")
    public ApiResponse<Map<String, Object>> createTemplate(@RequestBody TemplateInput input,
                                                            @AuthenticationPrincipal AuthenticatedUser user) {
        return ok(workflows.createTemplate(input, user));
    }
    @PostMapping("/api/v1/approval-templates/{id}/versions")
    @PreAuthorize("hasAuthority('approval:flow:manage')")
    public ApiResponse<Map<String, Object>> nextVersion(@PathVariable long id, @RequestBody TemplateInput input,
                                                         @AuthenticationPrincipal AuthenticatedUser user) {
        return ok(workflows.nextVersion(id, input, user));
    }
    @GetMapping("/api/v1/approval-flows/{id}")
    @PreAuthorize("hasAuthority('approval:flow:manage')")
    public ApiResponse<Map<String, Object>> flow(@PathVariable long id) {
        return ok(workflows.flow(id));
    }
    @PostMapping("/api/v1/approval-flows/{id}/publish")
    @PreAuthorize("hasAuthority('approval:flow:manage')")
    public ApiResponse<Map<String, Object>> publish(@PathVariable long id,
                                                     @AuthenticationPrincipal AuthenticatedUser user) {
        return ok(workflows.publish(id, user));
    }
    @GetMapping("/api/v1/approvals")
    public ApiResponse<PageResponse<Map<String, Object>>> list(@RequestParam(defaultValue = "TODO") String scope,
            @RequestParam(defaultValue = "1") int pageNo, @RequestParam(defaultValue = "20") int pageSize,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ok(workflows.list(scope, pageNo, pageSize, user));
    }
    @GetMapping("/api/v1/approvals/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable long id,
                                                    @AuthenticationPrincipal AuthenticatedUser user) {
        return ok(workflows.detail(id, user));
    }
    @GetMapping("/api/v1/approvals/{id}/returnable-steps")
    @PreAuthorize("hasAuthority('approval:return')")
    public ApiResponse<List<Map<String, Object>>> returnable(@PathVariable long id,
                                                               @AuthenticationPrincipal AuthenticatedUser user) {
        return ok(workflows.returnableSteps(id, user));
    }
    @PostMapping("/api/v1/approvals")
    @PreAuthorize("hasAuthority('approval:create')")
    public ApiResponse<Map<String, Object>> create(@RequestBody Application input,
                                                    @AuthenticationPrincipal AuthenticatedUser user) {
        return ok(workflows.create(input, user));
    }
    @PostMapping("/api/v1/approvals/{id}/approve")
    @PreAuthorize("hasAuthority('approval:process')")
    public ApiResponse<Map<String, Object>> approve(@PathVariable long id, @RequestBody Action input,
                                                     @AuthenticationPrincipal AuthenticatedUser user) {
        return ok(workflows.decide(id, "APPROVE", input, user));
    }
    @PostMapping("/api/v1/approvals/{id}/return-step")
    @PreAuthorize("hasAuthority('approval:return')")
    public ApiResponse<Map<String, Object>> returnStep(@PathVariable long id, @RequestBody Action input,
                                                        @AuthenticationPrincipal AuthenticatedUser user) {
        return ok(workflows.decide(id, "RETURN_STEP", input, user));
    }
    @PostMapping("/api/v1/approvals/{id}/return-applicant")
    @PreAuthorize("hasAuthority('approval:return')")
    public ApiResponse<Map<String, Object>> returnApplicant(@PathVariable long id, @RequestBody Action input,
                                                             @AuthenticationPrincipal AuthenticatedUser user) {
        return ok(workflows.decide(id, "RETURN_APPLICANT", input, user));
    }
    @PostMapping("/api/v1/approvals/{id}/resubmit")
    @PreAuthorize("hasAuthority('approval:create')")
    public ApiResponse<Map<String, Object>> resubmit(@PathVariable long id, @RequestBody Resubmission input,
                                                      @AuthenticationPrincipal AuthenticatedUser user) {
        return ok(workflows.resubmit(id, input, user));
    }
    @GetMapping("/api/v1/expense-applications/options")
    @PreAuthorize("hasAuthority('approval:create')")
    public ApiResponse<Map<String, Object>> expenseOptions(@AuthenticationPrincipal AuthenticatedUser user) {
        return ok(workflows.expenseOptions(user));
    }
    @GetMapping("/api/v1/expense-applications/task-candidates")
    @PreAuthorize("hasAuthority('approval:create')")
    public ApiResponse<List<Map<String, Object>>> candidates(@RequestParam long driverId,
                                                               @AuthenticationPrincipal AuthenticatedUser user) {
        return ok(workflows.taskCandidates(driverId, user));
    }
    @GetMapping("/api/v1/expense-applications/daily-template")
    @PreAuthorize("hasAuthority('approval:create')")
    public ResponseEntity<byte[]> dailyTemplate(@AuthenticationPrincipal AuthenticatedUser user) {
        workflows.expenseOptions(user);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=daily-expense-template.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(workbook.template());
    }
    @PostMapping(value = "/api/v1/expense-applications/daily-preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('approval:create')")
    public ApiResponse<DailyExpenseWorkbook.Preview> preview(@RequestParam MultipartFile file,
                                                               @AuthenticationPrincipal AuthenticatedUser user) {
        workflows.expenseOptions(user);
        return ok(workbook.preview(file));
    }
    @PostMapping(value = "/api/v1/expense-applications/daily-submit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('approval:create')")
    public ApiResponse<Map<String, Object>> submitDaily(@RequestParam MultipartFile file,
            @RequestParam long templateId, @RequestParam String requestKey,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ok(workflows.create(new Application(templateId, "DAILY", requestKey,
                workbook.confirmedItems(file), null, null, null), user));
    }
    private <T> ApiResponse<T> ok(T value) { return ApiResponse.success(value, RequestIdHolder.get()); }
}
