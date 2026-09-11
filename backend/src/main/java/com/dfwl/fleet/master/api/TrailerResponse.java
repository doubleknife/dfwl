package com.dfwl.fleet.master.api;

public record TrailerResponse(
        Long id,
        String trailerNo,
        String plateNo,
        Boolean insuranceComplete,
        Integer status,
        Long currentVehicleId
) {
}
