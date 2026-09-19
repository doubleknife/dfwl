package com.dfwl.fleet.tire.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record TireRequestCreateRequest(
        @NotNull Long driverId,
        @NotNull Long vehicleId,
        @NotEmpty List<Item> items) {

    public record Item(
            @NotNull Long tireId,
            @NotBlank String confirmedTireNo,
            Long ocrRecordId) {
    }
}
