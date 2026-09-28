package com.dfwl.fleet.route.dto.request;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

public record UnloadRequest(
        @NotNull BigDecimal grossWeight,
        @NotNull BigDecimal tareWeight,
        List<Long> evidenceAttachmentIds
) {
    public UnloadRequest(BigDecimal grossWeight, BigDecimal tareWeight) {
        this(grossWeight, tareWeight, List.of());
    }
}
