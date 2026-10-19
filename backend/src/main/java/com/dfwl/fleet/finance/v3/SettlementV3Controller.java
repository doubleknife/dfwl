package com.dfwl.fleet.finance.v3;

import com.dfwl.fleet.common.api.ApiResponse;
import com.dfwl.fleet.common.api.PageResponse;
import com.dfwl.fleet.common.web.RequestIdHolder;
import com.dfwl.fleet.security.AuthenticatedUser;
import com.dfwl.fleet.security.CurrentUserService;
import java.util.List;
import java.util.Map;
import com.dfwl.fleet.security.FunctionAccess;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SettlementV3Controller {
    private final SettlementV3Service settlement;
    private final CurrentUserService users;
    public SettlementV3Controller(SettlementV3Service settlement, CurrentUserService users) {
        this.settlement = settlement; this.users = users;
    }
    @GetMapping("/api/v1/settlements/{month}/versions")
    @FunctionAccess(anyOf={"PAGE_SETTLEMENTS"})
    public ApiResponse<List<Map<String, Object>>> versions(@AuthenticationPrincipal AuthenticatedUser user,
                                                              @PathVariable String month) {
        users.denyAllDrivers(user);
        return ok(settlement.versions(month));
    }
    @PostMapping("/api/v1/settlements/{month}/versions")
    @FunctionAccess(anyOf={"BUTTON_SETTLEMENTS_GENERATE"})
    public ApiResponse<Map<String, Object>> generate(@AuthenticationPrincipal AuthenticatedUser user,
                                                      @PathVariable String month) {
        users.denyAllDrivers(user);
        return ok(settlement.generate(month, user.id()));
    }
    @GetMapping("/api/v1/settlements/versions/{id}")
    @FunctionAccess(anyOf={"PAGE_SETTLEMENTS"})
    public ApiResponse<Map<String, Object>> version(@AuthenticationPrincipal AuthenticatedUser user,
                                                     @PathVariable long id) {
        users.denyAllDrivers(user);
        return ok(settlement.version(id));
    }
    @GetMapping("/api/v1/settlements/versions/{id}/details")
    @FunctionAccess(anyOf={"PAGE_SETTLEMENTS"})
    public ApiResponse<PageResponse<Map<String, Object>>> details(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long id,@RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        users.denyAllDrivers(user);
        return ok(settlement.details(id,pageNo,pageSize));
    }
    @GetMapping("/api/v1/settlements/versions/{id}/diff")
    @FunctionAccess(anyOf={"PAGE_SETTLEMENTS"})
    public ApiResponse<PageResponse<Map<String, Object>>> diff(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long id,@RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        users.denyAllDrivers(user);
        return ok(settlement.diffs(id,pageNo,pageSize));
    }
    private <T> ApiResponse<T> ok(T result) { return ApiResponse.success(result, RequestIdHolder.get()); }
}
