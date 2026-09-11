package com.dfwl.fleet.route.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record DriverRouteResponse(
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
    public static DriverRouteResponse from(RouteResponse route) {
        return new DriverRouteResponse(
                route.id(),
                route.routeNo(),
                route.externalRouteNo(),
                route.tripSequence(),
                route.businessDate(),
                route.customerId(),
                route.productId(),
                route.direction(),
                route.loadingPlace(),
                route.unloadingPlace(),
                route.assignedDriverId(),
                route.status(),
                route.departureDriverId(),
                route.departureVehicleId(),
                route.departureTrailerId(),
                route.departureTime(),
                route.unloadTime(),
                route.grossWeight(),
                route.tareWeight(),
                route.netWeight(),
                route.loadStandardThreshold(),
                route.loadStandardMet());
    }
}
