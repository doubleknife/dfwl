package com.dfwl.fleet.master.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TrailerRequest(
        @NotBlank String trailerNo,
        String plateNo,
        @NotNull Boolean insuranceComplete,
        @NotNull Integer status
) {
}
