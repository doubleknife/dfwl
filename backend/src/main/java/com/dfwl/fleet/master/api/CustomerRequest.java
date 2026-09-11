package com.dfwl.fleet.master.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CustomerRequest(
        String customerCode,
        @NotBlank String customerName,
        @NotNull Integer status,
        String remark
) {
}
