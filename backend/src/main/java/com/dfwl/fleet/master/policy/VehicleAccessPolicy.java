package com.dfwl.fleet.master.policy;

import com.dfwl.fleet.master.repository.MasterDataRepository;
import com.dfwl.fleet.security.AuthenticatedUser;
import com.dfwl.fleet.security.CurrentDriverContext;
import com.dfwl.fleet.security.CurrentUserService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
public class VehicleAccessPolicy {

    private final MasterDataRepository repository;
    private final CurrentUserService currentUserService;

    public VehicleAccessPolicy(MasterDataRepository repository, CurrentUserService currentUserService) {
        this.repository = repository;
        this.currentUserService = currentUserService;
    }

    public void ensureDriverCanViewVehicle(AuthenticatedUser user, long vehicleId) {
        if (!currentUserService.isDriverUser(user)) {
            return;
        }
        CurrentDriverContext driver = currentUserService.requireDriver(user);
        if (!repository.canDriverViewVehicle(vehicleId, driver.driverId())) {
            throw new AccessDeniedException("vehicle is outside current driver scope");
        }
    }
}
