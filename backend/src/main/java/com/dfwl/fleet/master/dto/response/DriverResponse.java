package com.dfwl.fleet.master.dto.response;

import java.time.LocalDate;

public record DriverResponse(
        Long id,
        Long userId,
        String driverType,
        String name,
        String phone,
        String drivingLicenseNo,
        String idCardNo,
        LocalDate birthDate,
        Integer age,
        Integer status,
        Long currentVehicleId,
        String currentVehiclePlateNo,
        String currentTrailerPlateNo
) {
}
