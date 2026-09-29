package com.dfwl.fleet.route.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record DispatchBatchRequest(
        @NotNull LocalDate businessDate,
        @NotEmpty List<@Valid Row> rows
) {
    public record Row(
            @NotBlank String templateStableCode,
            @NotNull Long driverId,
            String outsourcedPlateNo,
            BigDecimal actualUnitPrice,
            BigDecimal actualInformationFee,
            BigDecimal initialSalary,
            String remark
    ) {}
}
