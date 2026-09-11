package com.dfwl.fleet.master.api;

import jakarta.validation.constraints.NotNull;

public record BindTrailerVehicleRequest(
        @NotNull Long vehicleId
) {
}
