package com.dfwl.fleet.route.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

public record TaskSalariesConfirmRequest(@NotEmpty List<@Valid SalaryDecision> salaries,
                                         @NotBlank String reason, @NotBlank String requestKey) {
    public record SalaryDecision(@NotNull Long participantId, @NotNull BigDecimal amount,
                                 @NotNull Integer expectedVersion) {}
}
