package com.dfwl.fleet.approval.spi;

import com.dfwl.fleet.security.AuthenticatedUser;

/** Expense-owned, read-only scope validation for an approval application. */
public interface ApprovalExpenseResourcePolicy {

    void ensureSupportedExpenseType(String expenseType);

    void ensureCanSubmit(AuthenticatedUser user, Long vehicleId, Long driverId,
                         String attributionType, Long routeId);
}
