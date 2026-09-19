package com.dfwl.fleet.master.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record DriverRequest(
        @NotBlank String driverType,
        @NotBlank String name,
        @NotBlank String phone,
        String vehicleLicenseNo,
        String drivingLicenseNo,
        String idCardNo,
        LocalDate birthDate,
        @NotNull Integer status
) {
}
