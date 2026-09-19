package com.dfwl.fleet.salary.service;

import com.dfwl.fleet.common.api.PageResponse;
import com.dfwl.fleet.common.error.BusinessException;
import com.dfwl.fleet.common.error.ErrorCode;
import com.dfwl.fleet.route.service.RouteSalaryService;
import com.dfwl.fleet.salary.dto.response.DriverSalaryHistoryResponse;
import com.dfwl.fleet.salary.dto.request.DriverSalaryRequest;
import com.dfwl.fleet.salary.dto.response.DriverSalaryResponse;
import com.dfwl.fleet.salary.repository.DriverSalaryRepository;
import com.dfwl.fleet.salary.repository.DriverSalaryRepository.RouteSalaryContext;
import com.dfwl.fleet.salary.repository.DriverSalaryRepository.SalaryEntry;
import com.dfwl.fleet.security.AuthenticatedUser;
import com.dfwl.fleet.security.CurrentDriverContext;
import com.dfwl.fleet.security.CurrentUserService;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class DriverSalaryService {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

    private final DriverSalaryRepository repository;
    private final CurrentUserService currentUserService;
    private final RouteSalaryService routeSalaryService;

    public DriverSalaryService(DriverSalaryRepository repository, CurrentUserService currentUserService,
                               RouteSalaryService routeSalaryService) {
        this.repository = repository;
        this.currentUserService = currentUserService;
        this.routeSalaryService = routeSalaryService;
    }

    @Transactional
    public DriverSalaryResponse upsertManual(DriverSalaryRequest request, long operatorId) {
        if (request.salaryAmount() == null || request.salaryAmount().signum() <= 0) {
            throw new BusinessException(ErrorCode.SYS_002);
        }
        return applySalary(request.routeId(), request.driverId(), request.salaryAmount(), "MANUAL",
                null, null, operatorId, request.reason(), false);
    }

    @Transactional
    public DriverSalaryResponse upsertImported(long routeId, long driverId, BigDecimal amount, long importRowId, long operatorId) {
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessException(ErrorCode.SYS_002);
        }
        Long taskId = repository.importTaskIdForRow(importRowId).orElse(null);
        return applySalary(routeId, driverId, amount, "IMPORT", taskId, importRowId, operatorId, "工资导入", true);
    }

    @Transactional
    public DriverSalaryResponse applySalary(long routeId, long driverId, BigDecimal amount, String sourceType,
                                            Long importTaskId, Long importRowId, long operatorId, String reason,
                                            boolean failIfExists) {
        RouteSalaryContext route = repository.findRouteContext(routeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROUTE_001));
        if (route.driverId() == null || route.driverId() != driverId) {
            throw new BusinessException(ErrorCode.ROUTE_003);
        }
        SalaryEntry before = repository.findEntryByRouteForUpdate(routeId).orElse(null);
        if (before != null && failIfExists) {
            throw new BusinessException("SALARY_001", "线路工资已存在");
        }
        String businessMonth = route.businessDate().format(MONTH);
        long entryId;
        if (before == null) {
            entryId = repository.insertEntry(routeId, driverId, businessMonth, route.businessDate(), amount,
                    sourceType, importTaskId, importRowId, operatorId);
            repository.insertHistory(entryId, routeId, driverId, null, amount, null, sourceType,
                    operatorId, reason, importTaskId, importRowId);
        } else {
            entryId = before.id();
            repository.updateEntry(entryId, driverId, businessMonth, route.businessDate(), amount,
                    sourceType, importTaskId, importRowId, operatorId);
            repository.insertHistory(entryId, routeId, driverId, before.amount(), amount, before.sourceType(), sourceType,
                    operatorId, StringUtils.hasText(reason) ? reason : "工资调整", importTaskId, importRowId);
        }
        routeSalaryService.updateSalary(routeId, amount, sourceType, operatorId);
        return repository.findByRoute(routeId).orElseThrow(() -> new BusinessException(ErrorCode.DATA_001));
    }

    public PageResponse<DriverSalaryResponse> list(Long driverId, String businessMonth, int pageNo, int pageSize) {
        return repository.list(driverId, businessMonth, pageNo, Math.min(Math.max(pageSize, 1), 200));
    }

    public List<DriverSalaryHistoryResponse> history(long routeId) {
        return repository.history(routeId);
    }

    public PageResponse<DriverSalaryResponse> mySalary(AuthenticatedUser user, String businessMonth, int pageNo, int pageSize) {
        CurrentDriverContext driver = currentUserService.requireDriver(user);
        if (driver.outsourced()) {
            throw new AccessDeniedException("outsourced driver cannot view salary");
        }
        return repository.list(driver.driverId(), businessMonth, pageNo, Math.min(Math.max(pageSize, 1), 200));
    }

    public PageResponse<DriverSalaryResponse> salaryForDriver(AuthenticatedUser user, long driverId, String businessMonth,
                                                              int pageNo, int pageSize) {
        if (currentUserService.isDriverUser(user)) {
            CurrentDriverContext driver = currentUserService.requireDriver(user);
            if (driver.outsourced()) {
                throw new AccessDeniedException("outsourced driver cannot view salary");
            }
            if (driver.driverId() != driverId) {
                throw new AccessDeniedException("salary is outside current driver scope");
            }
        }
        return repository.list(driverId, businessMonth, pageNo, Math.min(Math.max(pageSize, 1), 200));
    }
}
