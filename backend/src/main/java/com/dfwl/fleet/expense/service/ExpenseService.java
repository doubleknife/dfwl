package com.dfwl.fleet.expense.service;

import com.dfwl.fleet.common.api.PageResponse;
import com.dfwl.fleet.common.domain.ExpenseStatus;
import com.dfwl.fleet.common.domain.ExpenseType;
import com.dfwl.fleet.common.error.BusinessException;
import com.dfwl.fleet.common.error.ErrorCode;
import com.dfwl.fleet.expense.api.ExpenseAttributionHistoryResponse;
import com.dfwl.fleet.expense.api.ExpenseAttributionRequest;
import com.dfwl.fleet.expense.api.ExpenseRequest;
import com.dfwl.fleet.expense.api.ExpenseResponse;
import com.dfwl.fleet.expense.api.ExpenseReversalRequest;
import com.dfwl.fleet.expense.repository.ExpenseRepository;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExpenseService {

    private static final DateTimeFormatter EXPENSE_NO_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final String ATTRIBUTION_ROUTE = "ROUTE";
    private static final String ATTRIBUTION_DAILY = "DAILY";

    private final ExpenseRepository repository;

    public ExpenseService(ExpenseRepository repository) {
        this.repository = repository;
    }

    public PageResponse<ExpenseResponse> list(int pageNo, int pageSize) {
        return repository.list(pageNo, Math.min(Math.max(pageSize, 1), 200));
    }

    public ExpenseResponse find(long id) {
        return repository.find(id).orElseThrow(() -> new BusinessException(ErrorCode.EXPENSE_001));
    }

    @Transactional
    public ExpenseResponse create(ExpenseRequest request, long operatorId) {
        validateRequest(request);
        MatchResult match = resolveRoute(request);
        ExpenseRequest effective = applyAutoMatch(request, match);
        long id = repository.create(nextExpenseNo("E"), effective, ExpenseStatus.ACTIVE.name(), effective.routeId(), operatorId);
        if (effective.energyDetail() != null) {
            repository.insertEnergyDetail(id, effective.energyDetail(), match.autoMatchedRouteId(), match.matchStatus());
        }
        return find(id);
    }

    @Transactional
    public ExpenseResponse update(long id, ExpenseRequest request, long operatorId) {
        ExpenseResponse current = find(id);
        ensureEditable(current);
        validateRequest(request);
        MatchResult match = resolveRoute(request);
        ExpenseRequest effective = applyAutoMatch(request, match);
        repository.update(id, effective, effective.routeId(), operatorId);
        repository.replaceEnergyDetail(id, effective.energyDetail(), match.autoMatchedRouteId(), match.matchStatus());
        return find(id);
    }

    @Transactional
    public void delete(long id, long operatorId) {
        ExpenseResponse current = find(id);
        ensureEditable(current);
        repository.delete(id, operatorId);
    }

    @Transactional
    public ExpenseResponse adjustAttribution(long id, ExpenseAttributionRequest request, long operatorId) {
        ExpenseResponse current = find(id);
        if (ExpenseStatus.REVERSED.name().equals(current.status()) || ExpenseStatus.REVERSAL.name().equals(current.status())) {
            throw new BusinessException(ErrorCode.EXPENSE_002);
        }
        validateAttribution(request.attributionType(), request.routeId(), false);
        repository.updateAttribution(id, request.attributionType(), request.routeId(), operatorId);
        repository.insertAttributionHistory(
                id,
                current.attributionType(),
                current.routeId(),
                current.status(),
                request.attributionType(),
                request.routeId(),
                ExpenseStatus.ACTIVE.name(),
                operatorId,
                request.reason());
        return find(id);
    }

    public java.util.List<ExpenseAttributionHistoryResponse> attributionHistory(long id) {
        find(id);
        return repository.attributionHistory(id);
    }

    @Transactional
    public ExpenseResponse reverse(long id, ExpenseReversalRequest request, long operatorId) {
        ExpenseResponse original = find(id);
        if (ExpenseStatus.REVERSED.name().equals(original.status())) {
            return repository.findReversalByOriginalId(id)
                    .orElseThrow(() -> new BusinessException(ErrorCode.EXPENSE_005));
        }
        if (ExpenseStatus.REVERSAL.name().equals(original.status())) {
            throw new BusinessException(ErrorCode.EXPENSE_005);
        }
        if (!repository.markReversed(id, operatorId)) {
            Optional<ExpenseResponse> existing = repository.findReversalByOriginalId(id);
            if (existing.isPresent()) {
                return existing.get();
            }
            throw new BusinessException(ErrorCode.EXPENSE_005);
        }
        long reversalId = repository.createReversal(nextExpenseNo("RV"), original, operatorId);
        try {
            repository.insertReversalLink(id, reversalId, request.reason(), operatorId);
        } catch (DataIntegrityViolationException ex) {
            return repository.findReversalByOriginalId(id).orElseThrow(() -> new BusinessException(ErrorCode.EXPENSE_005));
        }
        return find(reversalId);
    }

    public Object approvalChain(long id) {
        ExpenseResponse expense = find(id);
        return java.util.Map.of(
                "expenseId", expense.id(),
                "approvalInstanceId", expense.approvalInstanceId() == null ? "" : expense.approvalInstanceId());
    }

    private void ensureEditable(ExpenseResponse current) {
        if (!ExpenseStatus.ACTIVE.name().equals(current.status())) {
            throw new BusinessException(ErrorCode.EXPENSE_002);
        }
        if ("APPROVAL".equals(current.sourceType()) || current.approvalInstanceId() != null) {
            throw new BusinessException(ErrorCode.EXPENSE_004);
        }
    }

    private void validateRequest(ExpenseRequest request) {
        try {
            ExpenseType.valueOf(request.expenseType());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.SYS_002);
        }
        if (!repository.vehicleExists(request.vehicleId())) {
            throw new BusinessException(ErrorCode.DATA_001);
        }
        validateAttribution(request.attributionType(), request.routeId(), isEnergy(request.expenseType()));
        if (request.routeId() != null && !repository.routeExists(request.routeId())) {
            throw new BusinessException(ErrorCode.ROUTE_001);
        }
        if (isEnergy(request.expenseType()) && request.energyDetail() == null) {
            throw new BusinessException(ErrorCode.SYS_002);
        }
    }

    private void validateAttribution(String attributionType, Long routeId, boolean allowAutoMatch) {
        if (ATTRIBUTION_ROUTE.equals(attributionType)) {
            if (routeId == null && !allowAutoMatch) {
                throw new BusinessException(ErrorCode.EXPENSE_003);
            }
        } else if (ATTRIBUTION_DAILY.equals(attributionType)) {
            if (routeId != null) {
                throw new BusinessException(ErrorCode.EXPENSE_003);
            }
        } else {
            throw new BusinessException(ErrorCode.EXPENSE_003);
        }
    }

    private MatchResult resolveRoute(ExpenseRequest request) {
        if (!isEnergy(request.expenseType()) || request.energyDetail() == null) {
            return new MatchResult(request.routeId(), null, null);
        }
        if (ATTRIBUTION_ROUTE.equals(request.attributionType()) && request.routeId() != null) {
            return new MatchResult(request.routeId(), request.routeId(), "MANUAL");
        }
        Optional<Long> routeId = repository.matchRoute(request.vehicleId(), request.energyDetail().startTime());
        if (routeId.isPresent()) {
            return new MatchResult(routeId.get(), routeId.get(), "AUTO_MATCHED");
        }
        return new MatchResult(request.routeId(), null, "UNMATCHED");
    }

    private ExpenseRequest applyAutoMatch(ExpenseRequest request, MatchResult match) {
        if (match.autoMatchedRouteId() == null) {
            return request;
        }
        return new ExpenseRequest(
                request.expenseType(),
                request.businessDate(),
                request.vehicleId(),
                request.driverId(),
                ATTRIBUTION_ROUTE,
                match.autoMatchedRouteId(),
                request.amount(),
                request.sourceType(),
                request.approvalInstanceId(),
                request.importRowId(),
                request.remark(),
                request.energyDetail());
    }

    private boolean isEnergy(String expenseType) {
        return ExpenseType.ELECTRIC.name().equals(expenseType)
                || ExpenseType.GAS.name().equals(expenseType)
                || ExpenseType.TEMP_ELECTRIC.name().equals(expenseType);
    }

    private String nextExpenseNo(String prefix) {
        return prefix + LocalDateTime.now().format(EXPENSE_NO_TIME)
                + UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
    }

    private record MatchResult(Long routeId, Long autoMatchedRouteId, String matchStatus) {
    }
}
