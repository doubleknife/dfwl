package com.dfwl.fleet.tire.dto.request;

import jakarta.validation.constraints.NotNull;

public record OcrTireNumberRequest(
        @NotNull Long attachmentId) {
}
