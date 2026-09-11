package com.dfwl.fleet.expense.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record ExpenseResponse(
        Long id,
        String expenseNo,
        String expenseType,
        LocalDate businessDate,
        Long vehicleId,
        Long driverId,
        String attributionType,
        Long routeId,
        BigDecimal amount,
        String sourceType,
        String status,
        Long approvalInstanceId,
        Long importRowId,
        Long reversalOfId,
        String remark,
        EnergyDetail energyDetail,
        LocalDateTime createdAt
) {
    public record EnergyDetail(
            String energyType,
            Long stationId,
            String orderNo,
            LocalDateTime startTime,
            BigDecimal quantity,
            Long autoMatchedRouteId,
            String matchStatus
    ) {
    }
}
