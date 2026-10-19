package com.dfwl.fleet.system.controller;

import com.dfwl.fleet.common.api.ApiResponse;
import com.dfwl.fleet.common.web.RequestIdHolder;
import com.dfwl.fleet.security.FunctionAccess;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
public class SystemController {

    @GetMapping("/ping")
    @FunctionAccess(self=true)
    public ApiResponse<Map<String, String>> ping() {
        return ApiResponse.success(Map.of("status", "UP"), RequestIdHolder.get());
    }
}

