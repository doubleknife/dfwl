package com.dfwl.fleet.master.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ProductRequest(
        @NotBlank String name,
        @NotNull Boolean enabled,
        Integer expectedVersion
) {
}
