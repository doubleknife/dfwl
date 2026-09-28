package com.dfwl.fleet.route.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record ParticipantSalaryAdjustmentRequest(
        @NotNull @DecimalMin("0.00") BigDecimal amount,
        @NotNull Integer expectedParticipantVersion,
        @NotBlank String reason
) {}
