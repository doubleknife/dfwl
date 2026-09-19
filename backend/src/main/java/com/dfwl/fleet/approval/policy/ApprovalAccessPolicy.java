package com.dfwl.fleet.approval.policy;

import com.dfwl.fleet.security.AuthenticatedUser;
import com.dfwl.fleet.security.CurrentUserService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
public class ApprovalAccessPolicy {

    private final CurrentUserService currentUserService;

    public ApprovalAccessPolicy(CurrentUserService currentUserService) {
        this.currentUserService = currentUserService;
    }

    public void ensureCanSubmit(AuthenticatedUser user, String approvalType, String businessType) {
        if ("EXPENSE".equalsIgnoreCase(approvalType) || "EXPENSE".equalsIgnoreCase(businessType)) {
            currentUserService.denyAllDrivers(user);
        } else if (currentUserService.isDriverUser(user) && currentUserService.requireDriver(user).outsourced()) {
            throw new AccessDeniedException("outsourced driver is not allowed");
        }
    }

    public void ensureCanProcess(AuthenticatedUser user) {
        currentUserService.denyAllDrivers(user);
    }

    public void ensureCanManageFlows(AuthenticatedUser user) {
        currentUserService.denyAllDrivers(user);
    }
}
