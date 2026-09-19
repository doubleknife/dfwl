package com.dfwl.fleet.system.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RoleRequest(
        @NotBlank String roleCode,
        @NotBlank String roleName,
        @NotNull @Min(0) @Max(1) Integer status
) {
}
