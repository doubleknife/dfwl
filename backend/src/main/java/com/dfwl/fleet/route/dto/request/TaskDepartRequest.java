package com.dfwl.fleet.route.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TaskDepartRequest(@NotBlank String requestKey, @NotNull Integer expectedVersion) {}
