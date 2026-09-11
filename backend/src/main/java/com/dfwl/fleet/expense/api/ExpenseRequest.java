package com.dfwl.fleet.expense.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record ExpenseRequest(
        @NotBlank String expenseType,
        @NotNull LocalDate businessDate,
        @NotNull Long vehicleId,
        Long driverId,
        @NotBlank String attributionType,
        Long routeId,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        @NotBlank String sourceType,
        Long approvalInstanceId,
        Long importRowId,
        String remark,
        EnergyDetailRequest energyDetail
) {
    public record EnergyDetailRequest(
            String energyType,
            Long stationId,
            @NotBlank String orderNo,
            @NotNull LocalDateTime startTime,
            @NotNull @DecimalMin("0.001") BigDecimal quantity
    ) {
    }
}
