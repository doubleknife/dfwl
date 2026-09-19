package com.dfwl.fleet.route.dto.request;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record UnloadRequest(
        @NotNull BigDecimal grossWeight,
        @NotNull BigDecimal tareWeight
) {
}
