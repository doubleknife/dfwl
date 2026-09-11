package com.dfwl.fleet.auth.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateUserStatusRequest(
        @NotNull @Min(0) @Max(1) Integer status
) {
}
