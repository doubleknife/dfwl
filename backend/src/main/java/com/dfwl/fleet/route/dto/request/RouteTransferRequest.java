package com.dfwl.fleet.route.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

public record RouteTransferRequest(
        @NotNull Long expectedCurrentParticipantId,
        @NotNull Integer expectedRouteVersion,
        @NotNull Long newDriverId,
        String outsourcedPlateNo,
        @NotNull @DecimalMin("0.00") BigDecimal newSalary,
        @NotBlank String reason,
        @NotNull List<@Valid SalaryDecision> salaries
) {
    public record SalaryDecision(@NotNull Long participantId,
                                 @NotNull @DecimalMin("0.00") BigDecimal amount) {}
}
