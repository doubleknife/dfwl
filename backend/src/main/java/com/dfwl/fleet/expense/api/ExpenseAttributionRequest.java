package com.dfwl.fleet.expense.api;

import jakarta.validation.constraints.NotBlank;

public record ExpenseAttributionRequest(
        @NotBlank String attributionType,
        Long routeId,
        @NotBlank String reason
) {
}
