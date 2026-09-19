package com.dfwl.fleet.master.dto.request;

import jakarta.validation.constraints.NotNull;

public record BindVehicleRequest(
        @NotNull Long vehicleId
) {
}
