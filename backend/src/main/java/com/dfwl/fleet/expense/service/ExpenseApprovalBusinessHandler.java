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
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
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
        if (repository.findByApprovalInstanceId(approval.approvalInstanceId()).isPresent()) {
            return;
        }
        JsonNode snapshot = readSnapshot(submission);
        ExpenseRequest request = buildRequest(approval.approvalInstanceId(), snapshot);
        validate(request);
        Long routeId = request.routeId();
        long expenseId = repository.create(nextExpenseNo(), request, ExpenseStatus.ACTIVE.name(), routeId, operatorId);
        insertDetail(expenseId, request, snapshot);
    }

    private ExpenseRequest buildRequest(long approvalInstanceId, JsonNode snapshot) {
        return new ExpenseRequest(
                text(snapshot, "expenseType", true).toUpperCase(Locale.ROOT),
                LocalDate.parse(text(snapshot, "businessDate", true)),
                longValue(snapshot, "vehicleId", true),
                longValue(snapshot, "driverId", false),
                text(snapshot, "attributionType", true).toUpperCase(Locale.ROOT),
                longValue(snapshot, "routeId", false),
                decimal(snapshot, "amount", true),
                "APPROVAL",
                approvalInstanceId,
                null,
                text(snapshot, "remark", false),
                energyDetail(snapshot));
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
        if ("ROUTE".equals(request.attributionType())) {
            if (request.routeId() == null) {
                throw new BusinessException(ErrorCode.EXPENSE_003);
            }
            if (!repository.routeExists(request.routeId())) {
                throw new BusinessException(ErrorCode.ROUTE_001);
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

    private void insertDetail(long expenseId, ExpenseRequest request, JsonNode snapshot) {
        if (request.energyDetail() != null) {
            repository.insertEnergyDetail(expenseId, request.energyDetail(), request.routeId(), "MANUAL");
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
            return objectMapper.readTree(submission.businessSnapshotJson());
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
