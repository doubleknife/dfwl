package com.dfwl.fleet.route.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DispatchConfirmRequest(
        @NotBlank String previewId,
        @NotBlank String requestKey,
        @NotNull @Valid DispatchBatchRequest batch
) {}
