package com.dfwl.fleet.route.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TaskCancelRequest(@NotBlank String reason, @NotNull Integer expectedVersion,
                                @NotBlank String requestKey) {}
