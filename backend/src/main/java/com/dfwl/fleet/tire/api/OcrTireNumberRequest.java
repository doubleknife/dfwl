package com.dfwl.fleet.tire.api;

import jakarta.validation.constraints.NotNull;

public record OcrTireNumberRequest(
        @NotNull Long attachmentId) {
}
