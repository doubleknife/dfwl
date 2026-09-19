package com.dfwl.fleet.tire.policy;

import com.dfwl.fleet.security.AuthenticatedUser;
import com.dfwl.fleet.security.CurrentDriverContext;
import com.dfwl.fleet.security.CurrentUserService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
public class TireAccessPolicy {

    private final CurrentUserService currentUserService;

    public TireAccessPolicy(CurrentUserService currentUserService) {
        this.currentUserService = currentUserService;
    }

    public void ensureDriverCanUseTireRequest(AuthenticatedUser user, long requestDriverId, long vehicleId) {
        if (!currentUserService.isDriverUser(user)) {
            return;
        }
        CurrentDriverContext driver = currentUserService.requireDriver(user);
        if (driver.outsourced()) {
            throw new AccessDeniedException("outsourced driver cannot request tires");
        }
        if (driver.driverId() != requestDriverId || driver.currentVehicleId() == null || driver.currentVehicleId() != vehicleId) {
            throw new AccessDeniedException("tire request is outside current driver scope");
        }
    }

    public void ensureDriverCanUseTireOcr(AuthenticatedUser user) {
        if (!currentUserService.isDriverUser(user)) {
            return;
        }
        CurrentDriverContext driver = currentUserService.requireDriver(user);
        if (driver.outsourced()) {
            throw new AccessDeniedException("outsourced driver is not allowed");
        }
    }
}
