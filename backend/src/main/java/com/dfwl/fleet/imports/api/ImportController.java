package com.dfwl.fleet.imports.api;

import com.dfwl.fleet.common.api.ApiResponse;
import com.dfwl.fleet.common.api.PageResponse;
import com.dfwl.fleet.common.web.RequestIdHolder;
import com.dfwl.fleet.imports.service.ImportService;
import com.dfwl.fleet.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/api/v1/imports")
public class ImportController {

    private final ImportService importService;

    public ImportController(ImportService importService) {
        this.importService = importService;
    }

    @PostMapping("/preview")
    @PreAuthorize("hasAuthority('import:preview')")
    public ApiResponse<ImportTaskResponse> preview(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody ImportPreviewRequest request) {
        return ApiResponse.success(importService.preview(request, user.id()), RequestIdHolder.get());
    }

    @PostMapping("/{id}/commit")
    @PreAuthorize("hasAuthority('import:commit')")
    public ApiResponse<ImportTaskResponse> commit(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long id) {
        return ApiResponse.success(importService.commit(id, user.id()), RequestIdHolder.get());
    }

    @GetMapping
    @PreAuthorize("hasAuthority('import:history')")
    public ApiResponse<PageResponse<ImportTaskResponse>> list(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success(importService.list(pageNo, pageSize), RequestIdHolder.get());
    }

    @GetMapping("/{id}/failures/export")
    @PreAuthorize("hasAuthority('import:failure:export')")
    public ResponseEntity<String> exportFailures(@PathVariable long id) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=import-failures-" + id + ".csv")
                .contentType(MediaType.valueOf("text/csv;charset=UTF-8"))
                .body(importService.exportFailures(id));
    }
}
