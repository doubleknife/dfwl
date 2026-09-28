package com.dfwl.fleet.expense.service;

import com.dfwl.fleet.common.api.PageResponse;
import com.dfwl.fleet.expense.domain.ExpenseStatus;
import com.dfwl.fleet.expense.domain.ExpenseType;
import com.dfwl.fleet.common.error.BusinessException;
import com.dfwl.fleet.common.error.ErrorCode;
import com.dfwl.fleet.expense.dto.response.ExpenseAttributionHistoryResponse;
import com.dfwl.fleet.expense.dto.request.ExpenseAttributionRequest;
import com.dfwl.fleet.expense.dto.request.ExpenseRequest;
import com.dfwl.fleet.expense.dto.response.ExpenseResponse;
import com.dfwl.fleet.expense.dto.request.ExpenseReversalRequest;
import com.dfwl.fleet.expense.repository.ExpenseRepository;
import com.dfwl.fleet.system.service.AuditWriteService;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
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
    private final AuditWriteService audit;

    public ExpenseService(ExpenseRepository repository, AuditWriteService audit) {
        this.repository = repository;
        this.audit = audit;
    }

    public PageResponse<ExpenseResponse> list(int pageNo, int pageSize) {
        return list(pageNo, pageSize, null, null, null, null, null, null, null);
    }

    public PageResponse<ExpenseResponse> list(int pageNo, int pageSize, Long routeId,
            java.time.LocalDate businessDate, String expenseType, String attributionType,
            String status, Long vehicleId, String keyword) {
        return repository.list(pageNo, Math.min(Math.max(pageSize, 1), 200), routeId,
                businessDate, expenseType, attributionType, status, vehicleId, keyword);
    }

    public java.util.List<Map<String, Object>> eligibleRouteVehicles(long routeId) {
        if (!repository.routeExists(routeId)) throw new BusinessException(ErrorCode.ROUTE_001);
        return repository.eligibleRouteVehicles(routeId);
    }

    public ExpenseResponse find(long id) {
        return repository.find(id).orElseThrow(() -> new BusinessException(ErrorCode.EXPENSE_001));
    }

    @Transactional
    public ExpenseResponse create(ExpenseRequest request, long operatorId) {
        validateRequest(request);
        MatchResult match = resolveRoute(request);
        ExpenseRequest effective = applyAutoMatch(request, match);
        validateRequest(effective);
        long id = repository.create(nextExpenseNo("E"), effective, statusFor(effective.attributionType(), effective.routeId()), effective.routeId(), operatorId);
        if (effective.energyDetail() != null) {
            repository.insertEnergyDetail(id, effective.energyDetail(), match.autoMatchedRouteId(), match.matchStatus());
        }
        return find(id);
    }

    @Transactional
    public ExpenseResponse update(long id, ExpenseRequest request, long operatorId) {
        ExpenseResponse current = repository.lockExpense(id).orElseThrow(() -> new BusinessException(ErrorCode.EXPENSE_001));
        ensureEditable(current);
        validateRequest(request);
        MatchResult match = resolveRoute(request);
        ExpenseRequest effective = applyAutoMatch(request, match);
        validateRequest(effective);
        repository.update(id, effective, effective.routeId(), operatorId);
        repository.replaceEnergyDetail(id, effective.energyDetail(), match.autoMatchedRouteId(), match.matchStatus());
        return find(id);
    }

    @Transactional
    public void delete(long id, long operatorId) {
        ExpenseResponse current = repository.lockExpense(id).orElseThrow(() -> new BusinessException(ErrorCode.EXPENSE_001));
        ensureEditable(current);
        repository.delete(id, operatorId);
    }

    @Transactional
    public ExpenseResponse adjustAttribution(long id, ExpenseAttributionRequest request, long operatorId) {
        ExpenseResponse current = repository.lockExpense(id).orElseThrow(() -> new BusinessException(ErrorCode.EXPENSE_001));
        if (ExpenseStatus.REVERSED.name().equals(current.status()) || ExpenseStatus.REVERSAL.name().equals(current.status())) {
            throw new BusinessException(ErrorCode.EXPENSE_002);
        }
        validateAttribution(request.attributionType(), request.routeId());
        if (request.routeId() != null) repository.lockRoute(request.routeId());
        if (request.routeId() != null && !repository.eligibleRouteVehicle(request.routeId(), current.vehicleId())) {
            throw new BusinessException(ErrorCode.EXPENSE_003);
        }
        String nextStatus = statusFor(request.attributionType(), request.routeId());
        if (Objects.equals(current.attributionType(), request.attributionType())
                && Objects.equals(current.routeId(), request.routeId())
                && Objects.equals(current.status(), nextStatus)) return current;
        if (!repository.updateAttribution(id, request.attributionType(), request.routeId(), operatorId)) {
            throw new BusinessException(ErrorCode.EXPENSE_002);
        }
        repository.insertAttributionHistory(
                id,
                current.attributionType(),
                current.routeId(),
                current.status(),
                request.attributionType(),
                request.routeId(),
                nextStatus,
                operatorId,
                request.reason());
        audit.write("EXPENSE", "EXPENSE", id, "ATTRIBUTION_ADJUST",
                attributionSnapshot(current.attributionType(), current.routeId(), current.status()),
                attributionSnapshot(request.attributionType(), request.routeId(), nextStatus), operatorId, request.reason());
        return find(id);
    }

    public java.util.List<ExpenseAttributionHistoryResponse> attributionHistory(long id) {
        find(id);
        return repository.attributionHistory(id);
    }

    @Transactional
    public ExpenseResponse reverse(long id, ExpenseReversalRequest request, long operatorId) {
        ExpenseResponse original = repository.lockExpense(id).orElseThrow(() -> new BusinessException(ErrorCode.EXPENSE_001));
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

    public ExpenseResponse reversalRecord(long id) {
        find(id);
        return repository.findReversalByOriginalId(id).orElse(null);
    }

    private void ensureEditable(ExpenseResponse current) {
        if (!ExpenseStatus.ACTIVE.name().equals(current.status())
                && !ExpenseStatus.PENDING_ATTRIBUTION.name().equals(current.status())) {
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
        validateAttribution(request.attributionType(), request.routeId());
        if (request.routeId() != null && !repository.routeExists(request.routeId())) {
            throw new BusinessException(ErrorCode.ROUTE_001);
        }
        if (request.routeId() != null) repository.lockRoute(request.routeId());
        if (request.routeId() != null && !repository.eligibleRouteVehicle(request.routeId(), request.vehicleId())) {
            throw new BusinessException(ErrorCode.EXPENSE_003);
        }
        if (isEnergy(request.expenseType()) && request.energyDetail() == null) {
            throw new BusinessException(ErrorCode.SYS_002);
        }
    }

    private void validateAttribution(String attributionType, Long routeId) {
        if (ATTRIBUTION_ROUTE.equals(attributionType)) {
            // A route expense without a route is an explicit, company-level pending fact.
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
        var candidates = repository.matchRoutes(request.vehicleId(), request.energyDetail().startTime());
        if (candidates.size() == 1) {
            return new MatchResult(candidates.get(0), candidates.get(0), "AUTO_MATCHED");
        }
        return new MatchResult(request.routeId(), null, candidates.isEmpty() ? "UNMATCHED" : "AMBIGUOUS");
    }

    private ExpenseRequest applyAutoMatch(ExpenseRequest request, MatchResult match) {
        if (!isEnergy(request.expenseType()) || (ATTRIBUTION_ROUTE.equals(request.attributionType()) && request.routeId() != null)) {
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

    private String statusFor(String attributionType, Long routeId) {
        return ATTRIBUTION_ROUTE.equals(attributionType) && routeId == null
                ? ExpenseStatus.PENDING_ATTRIBUTION.name() : ExpenseStatus.ACTIVE.name();
    }

    private Map<String, Object> attributionSnapshot(String type, Long routeId, String status) {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("attributionType", type);
        snapshot.put("routeId", routeId);
        snapshot.put("status", status);
        return snapshot;
    }

    private String nextExpenseNo(String prefix) {
        return prefix + LocalDateTime.now().format(EXPENSE_NO_TIME)
                + UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
    }

    private record MatchResult(Long routeId, Long autoMatchedRouteId, String matchStatus) {
    }
}
