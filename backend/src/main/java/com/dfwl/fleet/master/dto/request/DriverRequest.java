package com.dfwl.fleet.master.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DriverRequest(
        @NotBlank String driverType,
        @NotBlank String name,
        @NotBlank String phone,
        @NotBlank String drivingLicenseNo,
        @NotBlank String idCardNo,
        @NotNull Integer status,
        String initialPassword
) {
    public DriverRequest(String driverType, String name, String phone,
                         String drivingLicenseNo, String idCardNo, Integer status) {
        this(driverType, name, phone, drivingLicenseNo, idCardNo, status, null);
    }
}
