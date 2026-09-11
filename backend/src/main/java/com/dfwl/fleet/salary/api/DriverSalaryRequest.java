package com.dfwl.fleet.salary.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record DriverSalaryRequest(
        @NotNull Long routeId,
        @NotNull Long driverId,
        @NotNull @DecimalMin(value = "0.00", inclusive = false) BigDecimal salaryAmount,
        String reason
) {
}
