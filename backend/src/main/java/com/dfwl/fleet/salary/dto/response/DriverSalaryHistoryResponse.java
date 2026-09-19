package com.dfwl.fleet.salary.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DriverSalaryHistoryResponse(
        long id,
        Long salaryEntryId,
        long routeId,
        long driverId,
        BigDecimal beforeAmount,
        BigDecimal afterAmount,
        String beforeSourceType,
        String afterSourceType,
        long operatorId,
        LocalDateTime operationTime,
        String reason,
        Long importTaskId,
        Long importRowId
) {
}
