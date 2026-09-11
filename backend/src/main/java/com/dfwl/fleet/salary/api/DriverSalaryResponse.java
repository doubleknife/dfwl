package com.dfwl.fleet.salary.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record DriverSalaryResponse(
        long id,
        long routeId,
        String routeNo,
        long driverId,
        String driverName,
        String businessMonth,
        LocalDate businessDate,
        BigDecimal salaryAmount,
        String sourceType,
        Long importTaskId,
        Long importRowId,
        long createdBy,
        LocalDateTime createdAt,
        Long updatedBy,
        LocalDateTime updatedAt
) {
}
