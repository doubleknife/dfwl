package com.dfwl.fleet.expense.service;

import com.dfwl.fleet.approval.spi.ApprovalBusinessContext;
import com.dfwl.fleet.approval.spi.ApprovalSubmissionContext;
import com.dfwl.fleet.approval.spi.ApprovalBusinessHandler;
import com.dfwl.fleet.expense.domain.ExpenseStatus;
import com.dfwl.fleet.expense.domain.ExpenseType;
import com.dfwl.fleet.common.error.BusinessException;
import com.dfwl.fleet.common.error.ErrorCode;
import com.dfwl.fleet.expense.dto.request.ExpenseRequest;
import com.dfwl.fleet.expense.repository.ExpenseRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ExpenseApprovalBusinessHandler implements ApprovalBusinessHandler {

    private static final DateTimeFormatter EXPENSE_NO_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final ExpenseRepository repository;
    private final ObjectMapper objectMapper;

    public ExpenseApprovalBusinessHandler(ExpenseRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(ApprovalBusinessContext approval) {
        return "EXPENSE".equals(approval.approvalType()) || "EXPENSE".equals(approval.businessType());
    }

    @Override
    public void onApproved(ApprovalBusinessContext approval, ApprovalSubmissionContext submission, long operatorId) {
        JsonNode snapshot = readSnapshot(submission);
        JsonNode items = snapshot.path("items");
        boolean legacy = !items.isArray();
        if (legacy) {
            // Historical submissions predate the unified item snapshot.
            items = objectMapper.createArrayNode().add(snapshot);
        }
        if (items.isEmpty()) throw new BusinessException(ErrorCode.SYS_002);
        Map<String, ExpenseRepository.ApprovalExpenseRow> existing = new HashMap<>();
        for (var row : repository.approvalItems(approval.approvalInstanceId())) {
            if (row.itemNo() == null || existing.put(row.itemNo(), row) != null) {
                throw new BusinessException(ErrorCode.SYS_003.name(), "审批费用明细归属不完整");
            }
        }
        HashSet<String> expected = new HashSet<>();
        for (JsonNode item : items) {
            String itemNo = legacy ? "1" : text(item, "itemNo", true);
            if (!expected.add(itemNo)) throw new BusinessException(ErrorCode.SYS_002);
            ExpenseRequest submitted = buildRequest(approval.approvalInstanceId(), snapshot, item);
            ExpenseRequest request = resolveApprovedRequest(submitted);
            validate(request);
            var row = existing.get(itemNo);
            if (row != null) {
                ensureSame(row, request, item);
                continue;
            }
            long expenseId = repository.create(nextExpenseNo(), request,
                    "ROUTE".equals(request.attributionType()) && request.routeId() == null
                            ? ExpenseStatus.PENDING_ATTRIBUTION.name() : ExpenseStatus.ACTIVE.name(),
                    request.routeId(), operatorId, itemNo);
            insertDetail(expenseId, request, item, submitted.routeId() == null && request.routeId() != null);
        }
        if (!expected.containsAll(existing.keySet())) {
            throw new BusinessException(ErrorCode.SYS_003.name(), "审批费用明细与提交快照不一致");
        }
    }

    private void ensureSame(ExpenseRepository.ApprovalExpenseRow row, ExpenseRequest request, JsonNode item) {
        if (row.deleted() || !row.expenseType().equals(request.expenseType())
                || !row.businessDate().equals(request.businessDate())
                || row.vehicleId() != request.vehicleId()
                || !Objects.equals(row.driverId(), request.driverId())
                || !row.attributionType().equals(request.attributionType())
                || !Objects.equals(row.routeId(), request.routeId())
                || row.amount().compareTo(request.amount()) != 0
                || row.energyDetail() != (request.energyDetail() != null)
                || row.penaltyDetail() != item.hasNonNull("penaltyDetail")
                || row.repairDetail() != item.hasNonNull("repairDetail")
                || row.tollDetail() != item.hasNonNull("tollDetail")) {
            throw new BusinessException(ErrorCode.SYS_003.name(), "审批费用明细与提交快照不一致: itemNo=" + row.itemNo());
        }
    }

    private ExpenseRequest buildRequest(long approvalInstanceId, JsonNode snapshot, JsonNode item) {
        return new ExpenseRequest(
                text(snapshot, "expenseType", true).toUpperCase(Locale.ROOT),
                LocalDate.parse(text(item, "businessDate", true)),
                longValue(item, "vehicleId", true),
                longValue(item, "driverId", false),
                text(snapshot, "attributionType", true).toUpperCase(Locale.ROOT),
                longValue(snapshot, "routeId", false),
                decimal(item, "amount", true),
                "APPROVAL",
                approvalInstanceId,
                null,
                text(item, "remark", false),
                energyDetail(item));
    }

    private ExpenseRequest resolveApprovedRequest(ExpenseRequest request) {
        if (!"ROUTE".equals(request.attributionType()) || request.routeId() != null
                || request.energyDetail() == null) return request;
        var match = repository.matchRoute(request.vehicleId(), request.energyDetail().startTime());
        if (match.isEmpty()) return request;
        return new ExpenseRequest(request.expenseType(), request.businessDate(), request.vehicleId(),
                request.driverId(), request.attributionType(), match.get(), request.amount(),
                request.sourceType(), request.approvalInstanceId(), request.importRowId(),
                request.remark(), request.energyDetail());
    }

    private ExpenseRequest.EnergyDetailRequest energyDetail(JsonNode snapshot) {
        JsonNode detail = snapshot.get("energyDetail");
        if (detail == null || detail.isNull()) {
            return null;
        }
        return new ExpenseRequest.EnergyDetailRequest(
                text(detail, "energyType", true).toUpperCase(Locale.ROOT),
                longValue(detail, "stationId", false),
                text(detail, "orderNo", true),
                LocalDateTime.parse(text(detail, "startTime", true)),
                decimal(detail, "quantity", true));
    }

    private void validate(ExpenseRequest request) {
        try {
            ExpenseType.valueOf(request.expenseType());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.SYS_002);
        }
        if (!repository.vehicleExists(request.vehicleId())) {
            throw new BusinessException(ErrorCode.DATA_001);
        }
        if (request.driverId() != null && !repository.driverExists(request.driverId())) {
            throw new BusinessException(ErrorCode.DATA_001);
        }
        if ("ROUTE".equals(request.attributionType())) {
            if (request.routeId() == null && !isEnergy(request.expenseType())) {
                throw new BusinessException(ErrorCode.EXPENSE_003);
            }
            if (request.routeId() != null && !repository.routeExists(request.routeId())) {
                throw new BusinessException(ErrorCode.ROUTE_001);
            }
            if (request.routeId() != null) repository.lockRoute(request.routeId());
            if (request.routeId() != null && !repository.eligibleRouteVehicle(request.routeId(), request.vehicleId())) {
                throw new BusinessException(ErrorCode.EXPENSE_003);
            }
        } else if ("DAILY".equals(request.attributionType())) {
            if (request.routeId() != null) {
                throw new BusinessException(ErrorCode.EXPENSE_003);
            }
        } else {
            throw new BusinessException(ErrorCode.EXPENSE_003);
        }
        if (isEnergy(request.expenseType()) && request.energyDetail() == null) {
            throw new BusinessException(ErrorCode.SYS_002);
        }
    }

    private void insertDetail(long expenseId, ExpenseRequest request, JsonNode snapshot, boolean autoMatched) {
        if (request.energyDetail() != null) {
            repository.insertEnergyDetail(expenseId, request.energyDetail(), request.routeId(),
                    autoMatched ? "AUTO_MATCHED" : request.routeId() == null ? "UNMATCHED" : "MANUAL");
        }
        JsonNode penalty = snapshot.get("penaltyDetail");
        if (penalty != null && !penalty.isNull()) {
            repository.insertPenaltyDetail(expenseId, text(penalty, "detail", false),
                    decimal(penalty, "deductPoints", false), text(penalty, "penaltyNo", false),
                    longValue(penalty, "driverId", false));
        }
        JsonNode repair = snapshot.get("repairDetail");
        if (repair != null && !repair.isNull()) {
            repository.insertRepairDetail(expenseId, longValue(repair, "trailerId", false),
                    text(repair, "detail", false), text(repair, "receiptNo", false),
                    text(repair, "invoiceNo", false), text(repair, "repairShop", false));
        }
        JsonNode toll = snapshot.get("tollDetail");
        if (toll != null && !toll.isNull()) {
            repository.insertTollDetail(expenseId, longValue(toll, "trailerId", false),
                    text(toll, "detail", false), text(toll, "receiptNo", false));
        }
    }

    private JsonNode readSnapshot(ApprovalSubmissionContext submission) {
        try {
            return objectMapper.readerFor(JsonNode.class)
                    .with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                    .readValue(submission.businessSnapshotJson());
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.SYS_003);
        }
    }

    private boolean isEnergy(String expenseType) {
        return "ELECTRIC".equals(expenseType) || "GAS".equals(expenseType) || "TEMP_ELECTRIC".equals(expenseType);
    }

    private String text(JsonNode node, String field, boolean required) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            if (required) {
                throw new BusinessException(ErrorCode.SYS_002);
            }
            return null;
        }
        return value.asText().trim();
    }

    private Long longValue(JsonNode node, String field, boolean required) {
        String value = text(node, field, required);
        return value == null ? null : Long.parseLong(value);
    }

    private BigDecimal decimal(JsonNode node, String field, boolean required) {
        String value = text(node, field, required);
        return value == null ? null : new BigDecimal(value);
    }

    private String nextExpenseNo() {
        return "EA" + LocalDateTime.now().format(EXPENSE_NO_TIME)
                + UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
    }
}
