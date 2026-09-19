package com.dfwl.fleet.route.policy;

import com.dfwl.fleet.route.repository.RouteRepository;
import com.dfwl.fleet.security.AuthenticatedUser;
import com.dfwl.fleet.security.CurrentDriverContext;
import com.dfwl.fleet.security.CurrentUserService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
public class RouteAccessPolicy {

    private final RouteRepository repository;
    private final CurrentUserService currentUserService;

    public RouteAccessPolicy(RouteRepository repository, CurrentUserService currentUserService) {
        this.repository = repository;
        this.currentUserService = currentUserService;
    }

    public void ensureDriverCanViewRoute(AuthenticatedUser user, long routeId) {
        if (!currentUserService.isDriverUser(user)) {
            return;
        }
        CurrentDriverContext driver = currentUserService.requireDriver(user);
        if (!repository.canDriverViewRoute(routeId, driver.driverId())) {
            throw new AccessDeniedException("route is outside current driver scope");
        }
    }

    public void ensureDriverCanDepart(AuthenticatedUser user, long routeId) {
        if (!currentUserService.isDriverUser(user)) {
            return;
        }
        CurrentDriverContext driver = currentUserService.requireDriver(user);
        if (!repository.canDriverDepartRoute(routeId, driver.driverId())) {
            throw new AccessDeniedException("route is outside current driver departure scope");
        }
    }

    public void ensureDriverCanUnload(AuthenticatedUser user, long routeId) {
        if (!currentUserService.isDriverUser(user)) {
            return;
        }
        CurrentDriverContext driver = currentUserService.requireDriver(user);
        if (!repository.canDriverUnloadRoute(routeId, driver.driverId())) {
            throw new AccessDeniedException("route is outside current driver unload scope");
        }
    }

}
