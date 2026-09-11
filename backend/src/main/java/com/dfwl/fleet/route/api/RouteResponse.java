package com.dfwl.fleet.route.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record RouteResponse(
        Long id,
        String routeNo,
        String externalRouteNo,
        String tripSequence,
        LocalDate businessDate,
        Long customerId,
        Long productId,
        String direction,
        String loadingPlace,
        String unloadingPlace,
        BigDecimal taxUnitPrice,
        Long assignedDriverId,
        String status,
        Long departureDriverId,
        Long departureVehicleId,
        Long departureTrailerId,
        LocalDateTime departureTime,
        LocalDateTime unloadTime,
        BigDecimal grossWeight,
        BigDecimal tareWeight,
        BigDecimal netWeight,
        BigDecimal loadStandardThreshold,
        Boolean loadStandardMet
) {
}
