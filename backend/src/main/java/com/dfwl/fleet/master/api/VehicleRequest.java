package com.dfwl.fleet.master.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record VehicleRequest(
        @NotBlank String plateNo,
        @NotNull Boolean insuranceComplete,
        @NotBlank String energyType,
        @NotNull @DecimalMin("0.001") BigDecimal maxLoad,
        @NotBlank String loadStandardType,
        BigDecimal loadStandardPercent,
        BigDecimal loadStandardMinLoad,
        @NotNull Integer status
) {
}
