package com.dfwl.fleet.expense.policy;

import com.dfwl.fleet.approval.spi.ApprovalExpenseResourcePolicy;
import com.dfwl.fleet.common.error.BusinessException;
import com.dfwl.fleet.common.error.ErrorCode;
import com.dfwl.fleet.expense.domain.ExpenseType;
import com.dfwl.fleet.expense.repository.ExpenseRepository;
import com.dfwl.fleet.master.policy.VehicleAccessPolicy;
import com.dfwl.fleet.route.repository.TransportTaskRepository;
import com.dfwl.fleet.security.AuthenticatedUser;
import com.dfwl.fleet.security.CurrentDriverContext;
import com.dfwl.fleet.security.CurrentUserService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.DateTimeException;
import java.util.Map;
import java.util.Set;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/** Read-only resource checks for an expense application before it becomes an approval. */
@Component
public class ExpenseApplicationPolicy implements ApprovalExpenseResourcePolicy {

    private final ExpenseRepository expenses;
    private final VehicleAccessPolicy vehicles;
    private final TransportTaskRepository tasks;
    private final CurrentUserService currentUsers;

    public ExpenseApplicationPolicy(ExpenseRepository expenses, VehicleAccessPolicy vehicles,
                                    TransportTaskRepository tasks, CurrentUserService currentUsers) {
        this.expenses = expenses;
        this.vehicles = vehicles;
        this.tasks = tasks;
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
        if (driverId != null && !expenses.driverExists(driverId)) {
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
        if (routeId != null && tasks.findForDriver(routeId, driver.driverId()).isEmpty()) {
            throw new AccessDeniedException("task is outside current driver scope");
        }
    }

    @Override
    public void ensureValidDetails(String expenseType, Map<String, Object> item) {
        for (String key : new String[]{"energyDetail", "penaltyDetail", "repairDetail", "tollDetail"}) {
            if (item.get(key) != null && !(item.get(key) instanceof Map<?, ?>)) {
                throw new BusinessException(ErrorCode.SYS_002);
            }
        }
        if (item.get("energyDetail") instanceof Map<?, ?> energy) {
            ensureKeys(energy, Set.of("energyType", "stationId", "orderNo", "startTime", "quantity"));
            try {
                if (blank(energy.get("energyType")) || blank(energy.get("orderNo"))
                        || new BigDecimal(String.valueOf(energy.get("quantity"))).signum() <= 0) {
                    throw new BusinessException(ErrorCode.SYS_002);
                }
                textLength(energy.get("energyType"), 16);
                textLength(energy.get("orderNo"), 128);
                BigDecimal quantity = new BigDecimal(String.valueOf(energy.get("quantity")));
                if (quantity.scale() > 3 || quantity.precision() > 18) throw new BusinessException(ErrorCode.SYS_002);
                LocalDateTime.parse(String.valueOf(energy.get("startTime")));
                if (energy.get("stationId") != null) Long.parseLong(String.valueOf(energy.get("stationId")));
            } catch (IllegalArgumentException | DateTimeException ex) {
                throw new BusinessException(ErrorCode.SYS_002);
            }
        } else if ("ELECTRIC".equals(expenseType) || "GAS".equals(expenseType)
                || "TEMP_ELECTRIC".equals(expenseType)) {
            throw new BusinessException(ErrorCode.SYS_002);
        }
        for (String key : new String[]{"penaltyDetail", "repairDetail", "tollDetail"}) {
            if (item.get(key) instanceof Map<?, ?> detail) {
                if ("penaltyDetail".equals(key)) {
                    ensureKeys(detail, Set.of("detail", "deductPoints", "penaltyNo", "driverId"));
                    textLength(detail.get("detail"), 1000);
                    textLength(detail.get("penaltyNo"), 128);
                } else if ("repairDetail".equals(key)) {
                    ensureKeys(detail, Set.of("trailerId", "detail", "receiptNo", "invoiceNo", "repairShop"));
                    textLength(detail.get("detail"), 1000);
                    textLength(detail.get("receiptNo"), 128);
                    textLength(detail.get("invoiceNo"), 128);
                    textLength(detail.get("repairShop"), 255);
                } else {
                    ensureKeys(detail, Set.of("trailerId", "detail", "receiptNo"));
                    textLength(detail.get("detail"), 1000);
                    textLength(detail.get("receiptNo"), 128);
                }
                for (String id : new String[]{"driverId", "trailerId"}) {
                    if (detail.get(id) != null) {
                        try { Long.parseLong(String.valueOf(detail.get(id))); }
                        catch (NumberFormatException ex) { throw new BusinessException(ErrorCode.SYS_002); }
                    }
                }
                if (detail.get("deductPoints") != null) {
                    try {
                        BigDecimal points = new BigDecimal(String.valueOf(detail.get("deductPoints")));
                        if (points.scale() > 4 || points.precision() > 8) throw new BusinessException(ErrorCode.SYS_002);
                    }
                    catch (NumberFormatException ex) { throw new BusinessException(ErrorCode.SYS_002); }
                }
            }
        }
    }

    private boolean blank(Object value) {
        return value == null || value.toString().isBlank();
    }

    private void textLength(Object value, int max) {
        if (value != null && value.toString().length() > max) throw new BusinessException(ErrorCode.SYS_002);
    }

    private void ensureKeys(Map<?, ?> values, Set<String> allowed) {
        if (values.keySet().stream().anyMatch(key -> !allowed.contains(key))) {
            throw new BusinessException(ErrorCode.SYS_002);
        }
    }
}
