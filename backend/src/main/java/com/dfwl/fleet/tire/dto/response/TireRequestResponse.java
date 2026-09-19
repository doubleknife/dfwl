package com.dfwl.fleet.tire.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record TireRequestResponse(
        long id,
        long driverId,
        long vehicleId,
        Long approvalInstanceId,
        String status,
        LocalDateTime createdAt,
        List<Item> items) {

    public record Item(
            long id,
            long tireId,
            String confirmedTireNo,
            Long ocrRecordId) {
    }
}
