package com.dfwl.fleet.tire.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record OcrTireNumberRequest(
        @NotNull Long attachmentId,
        String ocrProvider,
        String rawResultJson,
        @NotBlank String recognizedText) {
}
