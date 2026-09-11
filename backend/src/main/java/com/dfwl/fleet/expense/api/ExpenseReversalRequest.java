package com.dfwl.fleet.expense.api;

import jakarta.validation.constraints.NotBlank;

public record ExpenseReversalRequest(
        @NotBlank String reason
) {
}
