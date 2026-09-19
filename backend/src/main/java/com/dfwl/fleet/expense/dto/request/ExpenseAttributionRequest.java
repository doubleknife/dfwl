package com.dfwl.fleet.expense.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ExpenseAttributionRequest(
        @NotBlank String attributionType,
        Long routeId,
        @NotBlank String reason
) {
}
