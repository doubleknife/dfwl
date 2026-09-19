package com.dfwl.fleet.master.dto.response;

public record TrailerResponse(
        Long id,
        String trailerNo,
        String plateNo,
        Boolean insuranceComplete,
        Integer status,
        Long currentVehicleId
) {
}
