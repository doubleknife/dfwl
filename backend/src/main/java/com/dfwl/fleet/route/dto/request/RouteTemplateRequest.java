package com.dfwl.fleet.route.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Defaults belong to the template; published tasks retain their own copied amounts. */
public record RouteTemplateRequest(
        @NotBlank @Size(max = 64) String stableCode,
        @NotBlank @Size(max = 128) String name,
        @NotBlank @Size(max = 128) String customerName,
        @NotNull Long productId,
        @NotBlank @Size(max = 255) String loadingPlace,
        @NotBlank @Size(max = 255) String unloadingPlace,
        @NotNull @DecimalMin("0.00") BigDecimal defaultUnitPrice,
        @NotNull @DecimalMin("0.00") BigDecimal defaultInformationFee,
        @NotNull @DecimalMin("0.00") BigDecimal defaultDriverSalary,
        @Size(max = 500) String remark,
        Integer expectedVersion
) {}
