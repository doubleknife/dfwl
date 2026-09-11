package com.dfwl.fleet.master.api;

import java.time.LocalDate;

public record DriverResponse(
        Long id,
        Long userId,
        String driverType,
        String name,
        String phone,
        String vehicleLicenseNo,
        String drivingLicenseNo,
        String idCardNo,
        LocalDate birthDate,
        Integer status,
        Long currentVehicleId
) {
}
