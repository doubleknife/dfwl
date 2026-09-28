package com.dfwl.fleet.route.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record RoutePublishRequest(
        @NotNull @DecimalMin("0.00") BigDecimal initialSalary,
        String outsourcedPlateNo
) {}
