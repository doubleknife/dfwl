package com.dfwl.fleet.master.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ProductRequest(
        String productCode,
        @NotBlank String productName,
        @NotNull Integer status,
        String remark
) {
}
