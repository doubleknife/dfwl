package com.dfwl.fleet.route.controller;

import com.dfwl.fleet.common.api.ApiResponse;
import com.dfwl.fleet.common.api.PageResponse;
import com.dfwl.fleet.common.web.RequestIdHolder;
import com.dfwl.fleet.route.service.TaskImportService;
import com.dfwl.fleet.route.service.TaskImportService.ImportDetail;
import com.dfwl.fleet.route.service.TaskImportService.ImportSummary;
import com.dfwl.fleet.security.AuthenticatedUser;
import com.dfwl.fleet.security.CurrentUserService;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import com.dfwl.fleet.security.FunctionAccess;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/task-imports")
public class TaskImportController {
    private final TaskImportService service;
    private final CurrentUserService users;

    public TaskImportController(TaskImportService service, CurrentUserService users) {
        this.service = service;
        this.users = users;
    }

    @GetMapping("/template")
    @FunctionAccess(anyOf={"PAGE_TASK_IMPORTS"})
    public ResponseEntity<byte[]> template(@AuthenticationPrincipal AuthenticatedUser user) {
        users.denyAllDrivers(user);
        byte[] content = service.template();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .contentLength(content.length)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("运输任务固定模板.xlsx", StandardCharsets.UTF_8).build().toString())
                .body(content);
    }

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @FunctionAccess(anyOf={"BUTTON_TASK_IMPORTS_SUBMIT"})
    public ApiResponse<ImportDetail> preview(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam String requestKey, @RequestPart("file") MultipartFile file) {
        users.denyAllDrivers(user);
        return ApiResponse.success(service.preview(file, requestKey, user), RequestIdHolder.get());
    }

    @PostMapping(value = "/confirm", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @FunctionAccess(anyOf={"BUTTON_TASK_IMPORTS_SUBMIT"})
    public ApiResponse<ImportDetail> confirm(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam String requestKey, @RequestPart("file") MultipartFile file) {
        users.denyAllDrivers(user);
        return ApiResponse.success(service.confirm(file, requestKey, user), RequestIdHolder.get());
    }

    @GetMapping
    @FunctionAccess(anyOf={"PAGE_TASK_IMPORTS"})
    public ApiResponse<PageResponse<ImportSummary>> history(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "1") int pageNo, @RequestParam(defaultValue = "20") int pageSize) {
        users.denyAllDrivers(user);
        return ApiResponse.success(service.history(pageNo, pageSize), RequestIdHolder.get());
    }

    @GetMapping("/{id}")
    @FunctionAccess(anyOf={"PAGE_TASK_IMPORTS"})
    public ApiResponse<ImportDetail> detail(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id) {
        users.denyAllDrivers(user);
        return ApiResponse.success(service.detail(id), RequestIdHolder.get());
    }

    @GetMapping("/{id}/rows")
    @FunctionAccess(anyOf={"PAGE_TASK_IMPORTS"})
    public ApiResponse<java.util.List<TaskImportService.ImportRowView>> rows(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id) {
        users.denyAllDrivers(user);
        return ApiResponse.success(service.detail(id).rows(), RequestIdHolder.get());
    }
}
