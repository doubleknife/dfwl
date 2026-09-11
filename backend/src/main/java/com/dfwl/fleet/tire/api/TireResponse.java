package com.dfwl.fleet.tire.api;

import java.time.LocalDateTime;

public record TireResponse(
        long id,
        String tireNo,
        String barcode,
        String status,
        String dataSource,
        Long vehicleId,
        Long driverId,
        LocalDateTime installTime,
        LocalDateTime createdAt) {
}
