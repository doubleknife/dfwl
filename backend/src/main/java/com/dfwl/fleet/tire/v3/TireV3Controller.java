package com.dfwl.fleet.tire.v3;

import com.dfwl.fleet.common.api.ApiResponse;
import com.dfwl.fleet.common.api.PageResponse;
import com.dfwl.fleet.common.web.RequestIdHolder;
import com.dfwl.fleet.security.AuthenticatedUser;
import com.dfwl.fleet.security.CurrentUserService;
import com.dfwl.fleet.tire.dto.request.OcrConfirmRequest;
import com.dfwl.fleet.tire.dto.request.OcrTireNumberRequest;
import com.dfwl.fleet.tire.dto.response.OcrRecordResponse;
import com.dfwl.fleet.tire.service.TireService;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import com.dfwl.fleet.security.FunctionAccess;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Final V3 tire API: inventory/import on Web, request creation only by a driver. */
@RestController
public class TireV3Controller {
    private final TireV3Service tires;
    private final TireService ocr;
    private final CurrentUserService users;
    public TireV3Controller(TireV3Service tires, TireService ocr, CurrentUserService users) {
        this.tires = tires; this.ocr = ocr; this.users = users;
    }
    @GetMapping("/api/v1/tires")
    @FunctionAccess(anyOf={"PAGE_TIRES"})
    public ApiResponse<PageResponse<Map<String, Object>>> list(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "1") int pageNo, @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String tireNo, @RequestParam(required = false) String status) {
        users.denyAllDrivers(user);
        return ok(tires.inventory(pageNo, pageSize, tireNo, status));
    }
    @GetMapping("/api/v1/tires/{id}")
    @FunctionAccess(anyOf={"PAGE_TIRES"})
    public ApiResponse<Map<String, Object>> detail(@PathVariable long id,
            @AuthenticationPrincipal AuthenticatedUser user) {
        users.denyAllDrivers(user);
        return ok(tires.detail(id));
    }
    @PatchMapping("/api/v1/tires/{id}")
    @FunctionAccess(anyOf={"BUTTON_TIRES_MANAGE"})
    public ApiResponse<Map<String, Object>> edit(@PathVariable long id,
            @RequestBody TireV3Service.TireEdit input, @AuthenticationPrincipal AuthenticatedUser user) {
        return ok(tires.edit(id, input, user));
    }
    @GetMapping("/api/v1/tires/{id}/history")
    @FunctionAccess(anyOf={"PAGE_TIRES"})
    public ApiResponse<java.util.List<Map<String, Object>>> history(@PathVariable long id,
            @AuthenticationPrincipal AuthenticatedUser user) {
        users.denyAllDrivers(user);
        return ok(tires.history(id));
    }
    @GetMapping("/api/v1/tire-imports/template")
    @FunctionAccess(anyOf={"PAGE_TIRE_IMPORTS"})
    public ResponseEntity<byte[]> template(@AuthenticationPrincipal AuthenticatedUser user) {
        users.denyAllDrivers(user);
        byte[] content = tires.template();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .contentLength(content.length)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("轮胎入库固定模板.xlsx", StandardCharsets.UTF_8).build().toString())
                .body(content);
    }
    @PostMapping(value = "/api/v1/tire-imports/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @FunctionAccess(anyOf={"BUTTON_TIRE_IMPORTS_SUBMIT"})
    public ApiResponse<Map<String, Object>> preview(@RequestPart MultipartFile file,
            @AuthenticationPrincipal AuthenticatedUser user) { return ok(tires.preview(file, user)); }
    @PostMapping(value = "/api/v1/tire-imports/confirm", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @FunctionAccess(anyOf={"BUTTON_TIRE_IMPORTS_SUBMIT"})
    public ApiResponse<Map<String, Object>> confirm(@RequestPart MultipartFile file, @RequestParam String requestKey,
            @AuthenticationPrincipal AuthenticatedUser user) { return ok(tires.confirm(file, requestKey, user)); }
    @GetMapping("/api/v1/tire-imports")
    @FunctionAccess(anyOf={"PAGE_TIRE_IMPORTS"})
    public ApiResponse<PageResponse<Map<String, Object>>> imports(@RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize, @AuthenticationPrincipal AuthenticatedUser user) {
        users.denyAllDrivers(user);
        return ok(tires.importHistory(pageNo, pageSize));
    }
    @GetMapping("/api/v1/tire-imports/{id}")
    @FunctionAccess(anyOf={"PAGE_TIRE_IMPORTS"})
    public ApiResponse<Map<String, Object>> importDetail(@PathVariable long id,
            @AuthenticationPrincipal AuthenticatedUser user) {
        users.denyAllDrivers(user);
        return ok(tires.importDetail(id));
    }
    @PostMapping("/api/v1/tire-requests/ocr")
    @FunctionAccess(anyOf={"BUTTON_MINI_TIRE_REQUEST_SUBMIT"})
    public ApiResponse<OcrRecordResponse> recognize(@RequestBody OcrTireNumberRequest input,
            @AuthenticationPrincipal AuthenticatedUser user) {
        requireTireRequester(user);
        return ok(ocr.recognize(input, user));
    }
    @PostMapping("/api/v1/tire-requests/ocr/{id}/confirm")
    @FunctionAccess(anyOf={"BUTTON_MINI_TIRE_REQUEST_SUBMIT"})
    public ApiResponse<OcrRecordResponse> confirmOcr(@PathVariable long id, @RequestBody OcrConfirmRequest input,
            @AuthenticationPrincipal AuthenticatedUser user) {
        requireTireRequester(user);
        return ok(tires.confirmOcr(id, input, user));
    }
    @GetMapping("/api/v1/tires/request-target")
    @FunctionAccess(anyOf={"PAGE_MINI_TIRE_REQUEST"})
    public ApiResponse<Map<String, Object>> requestTarget(@RequestParam String tireNumber,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ok(tires.requestTarget(tireNumber, user));
    }
    @PostMapping("/api/v1/tire-requests")
    @FunctionAccess(anyOf={"BUTTON_MINI_TIRE_REQUEST_SUBMIT"})
    public ApiResponse<Map<String, Object>> submit(@RequestBody TireV3Service.TireRequestInput input,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ok(tires.submitRequest(input, user));
    }
    @GetMapping("/api/v1/tire-requests/{id}")
    @FunctionAccess(anyOf={"PAGE_MINI_TIRE_REQUEST", "PAGE_APPROVALS_TODO", "PAGE_APPROVALS_HISTORY"})
    public ApiResponse<Map<String, Object>> requestDetail(@PathVariable long id,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ok(tires.requestDetail(id, user));
    }
    private void requireTireRequester(AuthenticatedUser user) {
        var driver = users.requireDriver(user);
        if (driver.outsourced() || driver.currentVehicleId() == null)
            throw new AccessDeniedException("仅有当前公司车辆的自有司机可以申请换胎");
    }
    private <T> ApiResponse<T> ok(T value) { return ApiResponse.success(value, RequestIdHolder.get()); }
}
