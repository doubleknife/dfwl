package com.dfwl.fleet.expense.api;

import java.time.LocalDateTime;

public record ExpenseAttributionHistoryResponse(
        Long id,
        Long expenseId,
        String beforeAttributionType,
        Long beforeRouteId,
        String beforeStatus,
        String afterAttributionType,
        Long afterRouteId,
        String afterStatus,
        Long operatorId,
        LocalDateTime operationTime,
        String reason
) {
}
