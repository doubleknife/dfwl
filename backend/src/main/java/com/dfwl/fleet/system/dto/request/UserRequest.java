package com.dfwl.fleet.system.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UserRequest(
        @NotBlank String phone,
        @NotBlank @jakarta.validation.constraints.Size(max = 128) String name,
        String password,
        @NotNull Long roleId,
        @NotNull @Min(0) @Max(1) Integer status
) {
    public UserRequest { name = name == null ? null : name.trim(); }
}
