package com.dfwl.fleet.finance.v3;

import com.dfwl.fleet.common.api.ApiResponse;
import com.dfwl.fleet.common.web.RequestIdHolder;
import com.dfwl.fleet.security.AuthenticatedUser;
import com.dfwl.fleet.security.CurrentUserService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import com.dfwl.fleet.security.FunctionAccess;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/charging")
public class ChargingImportController {
    private final ChargingImportService service;
    private final CurrentUserService users;

    public ChargingImportController(ChargingImportService service, CurrentUserService users) {
        this.service = service; this.users = users;
    }

    @GetMapping("/companies")
    @FunctionAccess(anyOf={"PAGE_CHARGING_COMPANIES", "PAGE_CHARGING_IMPORTS"})
    public ApiResponse<List<Map<String, Object>>> companies(@AuthenticationPrincipal AuthenticatedUser user) {
        users.denyAllDrivers(user);
        return ok(service.companies());
    }

    @PostMapping("/companies")
    @FunctionAccess(anyOf={"BUTTON_CHARGING_COMPANIES_MANAGE"})
    public ApiResponse<Map<String, Object>> createCompany(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody ChargingImportService.CompanyCommand body) {
        users.denyAllDrivers(user);
        return ok(service.saveCompany(null, body, user.id()));
    }

    @PutMapping("/companies/{id}")
    @FunctionAccess(anyOf={"BUTTON_CHARGING_COMPANIES_MANAGE"})
    public ApiResponse<Map<String, Object>> updateCompany(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long id, @RequestBody ChargingImportService.CompanyCommand body) {
        users.denyAllDrivers(user);
        return ok(service.saveCompany(id, body, user.id()));
    }

    @GetMapping("/imports")
    @FunctionAccess(anyOf={"PAGE_CHARGING_IMPORTS"})
    public ApiResponse<Map<String, Object>> history(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        users.denyAllDrivers(user);
        return ok(service.batches(page, size));
    }

    @GetMapping("/imports/{id}")
    @FunctionAccess(anyOf={"PAGE_CHARGING_IMPORTS"})
    public ApiResponse<Map<String, Object>> importDetail(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long id) {
        users.denyAllDrivers(user);
        return ok(service.batch(id));
    }

    @PostMapping(value = "/imports", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @FunctionAccess(anyOf={"BUTTON_CHARGING_IMPORTS_IMPORT"})
    public ApiResponse<Map<String, Object>> confirm(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestPart("file") MultipartFile file,
            @RequestPart("payload") ChargingImportService.Confirmation body) {
        users.denyAllDrivers(user);
        return ok(service.confirm(body, file, user.id()));
    }

    @GetMapping("/imports/{id}/original-file")
    @FunctionAccess(anyOf={"PAGE_CHARGING_IMPORTS"})
    public ResponseEntity<Resource> original(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long id) {
        users.denyAllDrivers(user);
        Map<String, Object> batch = (Map<String, Object>) service.batch(id).get("batch");
        Resource file = service.originalFile(id);
        String name = String.valueOf(batch.get("original_filename"));
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(name, StandardCharsets.UTF_8).build().toString())
                .body(file);
    }

    @GetMapping("/source-orders/by-expense/{expenseId}")
    @FunctionAccess(anyOf={"PAGE_CHARGING_IMPORTS", "PAGE_EXPENSE_LEDGER"})
    public ApiResponse<Map<String, Object>> sourceOrder(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long expenseId) {
        users.denyAllDrivers(user);
        return ok(service.sourceOrder(expenseId));
    }

    private <T> ApiResponse<T> ok(T value) { return ApiResponse.success(value, RequestIdHolder.get()); }
}
