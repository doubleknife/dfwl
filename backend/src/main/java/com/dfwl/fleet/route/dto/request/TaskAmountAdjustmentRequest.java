package com.dfwl.fleet.route.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record TaskAmountAdjustmentRequest(
        @NotNull Integer expectedVersion,
        @NotBlank String kind,
        @NotNull BigDecimal newAmount,
        @NotBlank String reason
) {}
