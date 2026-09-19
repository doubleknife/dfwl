package com.dfwl.fleet.master.dto.response;

import java.math.BigDecimal;

public record VehicleResponse(
        Long id,
        String plateNo,
        Boolean insuranceComplete,
        String energyType,
        BigDecimal maxLoad,
        String loadStandardType,
        BigDecimal loadStandardPercent,
        BigDecimal loadStandardMinLoad,
        Integer status,
        Long currentDriverId,
        Long currentTrailerId
) {
}
