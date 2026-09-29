package com.dfwl.fleet.route.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

public record TaskTransferRequest(@NotNull Long newDriverId, String outsourcedPlateNo,
                                  @NotEmpty List<@Valid SalaryDecision> allParticipantSalaries,
                                  @NotNull BigDecimal newSalary, @NotBlank String reason,
                                  @NotNull Integer expectedVersion, @NotNull Long expectedCurrentParticipantId) {
    public record SalaryDecision(@NotNull Long participantId, @NotNull BigDecimal amount) {}
}
