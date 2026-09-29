package com.dfwl.fleet.route.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

public record TaskWeightAdjustmentRequest(@NotNull BigDecimal grossWeight, @NotNull BigDecimal tareWeight,
                                          @NotBlank String reason, List<Long> evidenceAttachmentIds,
                                          @NotNull Integer expectedVersion) {}
