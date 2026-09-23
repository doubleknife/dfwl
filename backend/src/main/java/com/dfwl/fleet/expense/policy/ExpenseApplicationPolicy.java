package com.dfwl.fleet.expense.policy;

import com.dfwl.fleet.approval.spi.ApprovalExpenseResourcePolicy;
import com.dfwl.fleet.common.error.BusinessException;
import com.dfwl.fleet.common.error.ErrorCode;
import com.dfwl.fleet.expense.domain.ExpenseType;
import com.dfwl.fleet.expense.repository.ExpenseRepository;
import com.dfwl.fleet.master.policy.VehicleAccessPolicy;
import com.dfwl.fleet.route.policy.RouteAccessPolicy;
import com.dfwl.fleet.security.AuthenticatedUser;
import com.dfwl.fleet.security.CurrentDriverContext;
import com.dfwl.fleet.security.CurrentUserService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/** Read-only resource checks for an expense application before it becomes an approval. */
@Component
public class ExpenseApplicationPolicy implements ApprovalExpenseResourcePolicy {

    private final ExpenseRepository expenses;
    private final VehicleAccessPolicy vehicles;
    private final RouteAccessPolicy routes;
    private final CurrentUserService currentUsers;

    public ExpenseApplicationPolicy(ExpenseRepository expenses, VehicleAccessPolicy vehicles,
                                    RouteAccessPolicy routes, CurrentUserService currentUsers) {
        this.expenses = expenses;
        this.vehicles = vehicles;
        this.routes = routes;
        this.currentUsers = currentUsers;
    }

    @Override
    public void ensureSupportedExpenseType(String expenseType) {
        try {
            ExpenseType.valueOf(expenseType);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new BusinessException(ErrorCode.APPROVAL_002);
        }
    }

    @Override
    public void ensureCanSubmit(AuthenticatedUser user, Long vehicleId, Long driverId,
                                String attributionType, Long routeId) {
        if (vehicleId == null || !expenses.vehicleExists(vehicleId)) {
            throw new BusinessException(ErrorCode.DATA_001);
        }
        if ("ROUTE".equals(attributionType)) {
            if (routeId == null) throw new BusinessException(ErrorCode.EXPENSE_003);
            if (!expenses.routeExists(routeId)) throw new BusinessException(ErrorCode.ROUTE_001);
        } else if (!"DAILY".equals(attributionType) || routeId != null) {
            throw new BusinessException(ErrorCode.EXPENSE_003);
        }
        if (!currentUsers.isDriverUser(user)) return;
        CurrentDriverContext driver = currentUsers.requireDriver(user);
        if (driver.outsourced()) throw new AccessDeniedException("outsourced driver is not allowed");
        if (driverId != null && driverId != driver.driverId()) {
            throw new AccessDeniedException("expense driver is outside current driver scope");
        }
        vehicles.ensureDriverCanViewVehicle(user, vehicleId);
        if (routeId != null) routes.ensureDriverCanViewRoute(user, routeId);
    }
}
