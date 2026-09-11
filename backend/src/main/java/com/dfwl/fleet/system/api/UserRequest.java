package com.dfwl.fleet.system.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UserRequest(
        @NotBlank String phone,
        String password,
        @NotNull Long roleId,
        @NotNull @Min(0) @Max(1) Integer status
) {
}
