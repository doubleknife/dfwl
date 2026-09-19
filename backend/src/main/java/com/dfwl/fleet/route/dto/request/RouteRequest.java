package com.dfwl.fleet.route.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record RouteRequest(
        @NotNull LocalDate businessDate,
        @NotNull Long customerId,
        @NotNull Long productId,
        @NotBlank String direction,
        @NotBlank String loadingPlace,
        @NotBlank String unloadingPlace,
        Long assignedDriverId,
        @NotNull BigDecimal taxUnitPrice,
        String externalRouteNo,
        String tripSequence
) {
}
