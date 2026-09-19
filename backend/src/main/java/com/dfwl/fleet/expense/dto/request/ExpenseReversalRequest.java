package com.dfwl.fleet.expense.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ExpenseReversalRequest(
        @NotBlank String reason
) {
}
