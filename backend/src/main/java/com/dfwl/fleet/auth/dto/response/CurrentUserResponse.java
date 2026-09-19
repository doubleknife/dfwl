package com.dfwl.fleet.auth.dto.response;

import com.dfwl.fleet.auth.domain.RoleSummary;
import java.util.Set;

public record CurrentUserResponse(
        Long id,
        String phone,
        RoleSummary role,
        Set<String> permissions,
        Long driverId,
        String driverType,
        String driverName,
        Long currentVehicleId,
        String currentPlateNo
) {
}
