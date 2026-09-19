package com.dfwl.fleet.expense.imports;

import com.dfwl.fleet.expense.domain.ExpenseType;
import com.dfwl.fleet.expense.repository.ExpenseRepository;
import com.dfwl.fleet.expense.repository.ExpenseRepository.ImportedExpense;
import com.dfwl.fleet.imports.spi.ImportBusinessHandler;
import com.dfwl.fleet.imports.spi.ImportBusinessType;
import com.dfwl.fleet.imports.spi.ImportCommitResult;
import com.dfwl.fleet.imports.spi.ImportRowContext;
import com.dfwl.fleet.imports.spi.ImportValidationResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Import-specific rules; imports owns the enclosing row transaction and import history. */
@Component
public class ExpenseImportHandler implements ImportBusinessHandler {
    private static final DateTimeFormatter NO_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private final ExpenseRepository repository;
    private final ObjectMapper objectMapper;

    public ExpenseImportHandler(ExpenseRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    public ImportBusinessType businessType() { return ImportBusinessType.EXPENSE; }

    @Override
    public ImportValidationResult resolveBusinessKey(ImportRowContext context) {
        try {
            String key = StringUtils.hasText(context.businessUniqueKey()) ? context.businessUniqueKey().trim()
                    : expenseBusinessKey(context.rowData());
            return new ImportValidationResult(true, key, null, null);
        } catch (ExpenseValidationException ex) { return ex.result(); }
    }

    @Override
    public ImportValidationResult validateDuplicate(ImportRowContext context) {
        // Existing imports check import history, not expense tables. Preserve database constraint
        // failures (IMPORT_DB_ERROR) instead of introducing a new preflight duplicate rule.
        return new ImportValidationResult(true, context.businessUniqueKey(), null, null);
    }

    @Override
    public ImportValidationResult validate(ImportRowContext context) {
        try {
            parseExpense(context.importRowId() == null ? 0 : context.importRowId(), context.rowData());
            return new ImportValidationResult(true, context.businessUniqueKey(), null, null);
        } catch (ExpenseValidationException ex) { return ex.result(); }
    }

    @Override
    public ImportCommitResult commit(ImportRowContext context) {
        try { return commitExpense(context); }
        catch (ExpenseValidationException ex) {
            return new ImportCommitResult(ImportCommitResult.Status.FAILURE, null, ex.key, ex.code, ex.getMessage());
        }
    }

    private ImportCommitResult commitExpense(ImportRowContext context) {
        ParsedExpense parsed = parseExpense(context.importRowId(), context.rowData());
        ImportedExpense expense = parsed.expense();
        Long matchedRoute = null;
        String matchStatus = null;
        if (parsed.energyDetail() != null) {
            matchStatus = "UNMATCHED";
            if (expense.routeId() != null) {
                matchedRoute = expense.routeId();
                matchStatus = "MANUAL";
            } else {
                Optional<Long> routeId = repository.matchRoute(expense.vehicleId(), parsed.energyDetail().startTime());
                if (routeId.isPresent()) {
                    matchedRoute = routeId.get();
                    matchStatus = "AUTO_MATCHED";
                    expense = new ImportedExpense(
                            expense.expenseNo(),
                            expense.expenseType(),
                            expense.businessDate(),
                            expense.vehicleId(),
                            expense.driverId(),
                            "ROUTE",
                            matchedRoute,
                            expense.amount(),
                            expense.importRowId(),
                            expense.remark());
                }
            }
        }
        long id = repository.insertImportedExpense(expense, context.operatorId());
        if (parsed.energyDetail() != null) {
            repository.insertEnergyDetail(id, parsed.energyDetail().energyType(), parsed.energyDetail().stationId(),
                    parsed.energyDetail().orderNo(), parsed.energyDetail().startTime(), parsed.energyDetail().quantity(),
                    matchedRoute, matchStatus);
        }
        if (parsed.penaltyDetail() != null) {
            repository.insertPenaltyDetail(id, parsed.penaltyDetail().detail(), parsed.penaltyDetail().deductPoints(),
                    parsed.penaltyDetail().penaltyNo(), parsed.penaltyDetail().driverId());
        }
        if (parsed.repairDetail() != null) {
            repository.insertRepairDetail(id, parsed.repairDetail().trailerId(), parsed.repairDetail().detail(),
                    parsed.repairDetail().receiptNo(), parsed.repairDetail().invoiceNo(), parsed.repairDetail().repairShop());
        }
        if (parsed.tollDetail() != null) {
            repository.insertTollDetail(id, parsed.tollDetail().trailerId(), parsed.tollDetail().detail(),
                    parsed.tollDetail().receiptNo());
        }
        return new ImportCommitResult(ImportCommitResult.Status.SUCCESS, id, context.businessUniqueKey(), null, null);
    }

    private ParsedExpense parseExpense(long rowId, Map<String, Object> raw) {
        String expenseType = text(raw, "expenseType", true).toUpperCase(Locale.ROOT);
        try {
            ExpenseType.valueOf(expenseType);
        } catch (IllegalArgumentException ex) {
            throw new ExpenseValidationException(null, "IMPORT_FORMAT_ERROR", "费用类型不合法");
        }
        LocalDate businessDate = date(raw, "businessDate", true);
        long vehicleId = longValue(raw, "vehicleId", true);
        if (!repository.importVehicleExists(vehicleId)) {
            throw new ExpenseValidationException(null, "IMPORT_REFERENCE_NOT_FOUND", "车辆不存在");
        }
        Long driverId = longValue(raw, "driverId", false);
        String attributionType = text(raw, "attributionType", true).toUpperCase(Locale.ROOT);
        Long routeId = longValue(raw, "routeId", false);
        if ("ROUTE".equals(attributionType)) {
            if (routeId == null && !isEnergy(expenseType)) {
                throw new ExpenseValidationException(null, "EXPENSE_003", "线路费用必须绑定线路");
            }
        } else if ("DAILY".equals(attributionType)) {
            if (routeId != null) {
                throw new ExpenseValidationException(null, "EXPENSE_003", "日常费用不能绑定线路");
            }
        } else {
            throw new ExpenseValidationException(null, "EXPENSE_003", "费用归属不合法");
        }
        if (routeId != null && !repository.routeExists(routeId)) {
            throw new ExpenseValidationException(null, "ROUTE_001", "线路不存在");
        }
        EnergyDetail energyDetail = null;
        if (isEnergy(expenseType)) {
            Map<String, Object> detail = nested(raw, "energyDetail");
            if (detail == null) {
                throw new ExpenseValidationException(null, "IMPORT_FORMAT_ERROR", "能源费用明细不能为空");
            }
            energyDetail = new EnergyDetail(
                    defaultText(detail, "energyType", expenseType),
                    longValue(detail, "stationId", false),
                    text(detail, "orderNo", true),
                    dateTime(detail, "startTime", true),
                    decimal(detail, "quantity", true));
        }
        return new ParsedExpense(
                new ImportedExpense(
                        defaultText(raw, "expenseNo", nextNo("EI")),
                        expenseType,
                        businessDate,
                        vehicleId,
                        driverId,
                        attributionType,
                        routeId,
                        decimal(raw, "amount", true),
                        rowId,
                        text(raw, "remark", false)),
                energyDetail,
                parsePenalty(raw),
                parseRepair(raw),
                parseToll(raw));
    }

    private PenaltyDetail parsePenalty(Map<String, Object> raw) {
        Map<String, Object> detail = nested(raw, "penaltyDetail");
        if (detail == null) {
            return null;
        }
        return new PenaltyDetail(text(detail, "detail", false), decimal(detail, "deductPoints", false),
                text(detail, "penaltyNo", false), longValue(detail, "driverId", false));
    }

    private RepairDetail parseRepair(Map<String, Object> raw) {
        Map<String, Object> detail = nested(raw, "repairDetail");
        if (detail == null) {
            return null;
        }
        return new RepairDetail(longValue(detail, "trailerId", false), text(detail, "detail", false),
                text(detail, "receiptNo", false), text(detail, "invoiceNo", false), text(detail, "repairShop", false));
    }

    private TollDetail parseToll(Map<String, Object> raw) {
        Map<String, Object> detail = nested(raw, "tollDetail");
        if (detail == null) {
            return null;
        }
        return new TollDetail(longValue(detail, "trailerId", false), text(detail, "detail", false),
                text(detail, "receiptNo", false));
    }

    private String expenseBusinessKey(Map<String, Object> raw) {
        String expenseNo = text(raw, "expenseNo", false);
        if (StringUtils.hasText(expenseNo)) {
            return "EXPENSE_NO|" + expenseNo;
        }

        String expenseType = text(raw, "expenseType", true).toUpperCase(Locale.ROOT);
        Map<String, Object> energyDetail = nested(raw, "energyDetail");
        if (isEnergy(expenseType) && energyDetail != null) {
            String orderNo = text(energyDetail, "orderNo", false);
            if (StringUtils.hasText(orderNo)) {
                Long stationId = longValue(energyDetail, "stationId", false);
                return "ENERGY|" + expenseType + "|" + stationId + "|" + orderNo;
            }
        }

        Map<String, Object> penaltyDetail = nested(raw, "penaltyDetail");
        if (penaltyDetail != null) {
            String penaltyNo = text(penaltyDetail, "penaltyNo", false);
            if (StringUtils.hasText(penaltyNo)) {
                return "PENALTY|" + penaltyNo;
            }
        }

        Map<String, Object> repairDetail = nested(raw, "repairDetail");
        if (repairDetail != null) {
            String invoiceNo = text(repairDetail, "invoiceNo", false);
            if (StringUtils.hasText(invoiceNo)) {
                return "REPAIR_INVOICE|" + invoiceNo;
            }
            String receiptNo = text(repairDetail, "receiptNo", false);
            if (StringUtils.hasText(receiptNo)) {
                return "REPAIR_RECEIPT|" + receiptNo;
            }
        }

        Map<String, Object> tollDetail = nested(raw, "tollDetail");
        if (tollDetail != null) {
            String receiptNo = text(tollDetail, "receiptNo", false);
            if (StringUtils.hasText(receiptNo)) {
                return "TOLL_RECEIPT|" + receiptNo;
            }
        }

        throw new ExpenseValidationException(null, "IMPORT_KEY_MISSING", "无天然唯一编号的费用必须提供businessUniqueKey");
    }

    private boolean isEnergy(String expenseType) {
        return "ELECTRIC".equals(expenseType) || "GAS".equals(expenseType) || "TEMP_ELECTRIC".equals(expenseType);
    }

    private String text(Map<String, Object> raw, String field, boolean required) {
        Object value = raw.get(field);
        if (value == null || !StringUtils.hasText(String.valueOf(value))) {
            if (required) {
                throw new ExpenseValidationException(null, "IMPORT_FORMAT_ERROR", field + "不能为空");
            }
            return null;
        }
        return String.valueOf(value).trim();
    }

    private String defaultText(Map<String, Object> raw, String field, String defaultValue) {
        String value = text(raw, field, false);
        return value == null ? defaultValue : value;
    }

    private Long longValue(Map<String, Object> raw, String field, boolean required) {
        Object value = raw.get(field);
        if (value == null || !StringUtils.hasText(String.valueOf(value))) {
            if (required) {
                throw new ExpenseValidationException(null, "IMPORT_FORMAT_ERROR", field + "不能为空");
            }
            return null;
        }
        try {
            return value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            throw new ExpenseValidationException(null, "IMPORT_FORMAT_ERROR", field + "格式错误");
        }
    }

    private BigDecimal decimal(Map<String, Object> raw, String field, boolean required) {
        Object value = raw.get(field);
        if (value == null || !StringUtils.hasText(String.valueOf(value))) {
            if (required) {
                throw new ExpenseValidationException(null, "IMPORT_FORMAT_ERROR", field + "不能为空");
            }
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException ex) {
            throw new ExpenseValidationException(null, "IMPORT_FORMAT_ERROR", field + "格式错误");
        }
    }

    private LocalDate date(Map<String, Object> raw, String field, boolean required) {
        String value = text(raw, field, required);
        return value == null ? null : LocalDate.parse(value);
    }

    private LocalDateTime dateTime(Map<String, Object> raw, String field, boolean required) {
        String value = text(raw, field, required);
        return value == null ? null : LocalDateTime.parse(value);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> nested(Map<String, Object> raw, String field) {
        Object value = raw.get(field);
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return objectMapper.convertValue(value, MAP_TYPE);
    }

    private String nextNo(String prefix) {
        return prefix + LocalDateTime.now().format(NO_TIME)
                + UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
    }

    private record ParsedExpense(
            ImportedExpense expense,
            EnergyDetail energyDetail,
            PenaltyDetail penaltyDetail,
            RepairDetail repairDetail,
            TollDetail tollDetail
    ) {
    }

    private record EnergyDetail(String energyType, Long stationId, String orderNo,
                                LocalDateTime startTime, BigDecimal quantity) {
    }

    private record PenaltyDetail(String detail, BigDecimal deductPoints, String penaltyNo, Long driverId) {
    }

    private record RepairDetail(Long trailerId, String detail, String receiptNo, String invoiceNo, String repairShop) {
    }

    private record TollDetail(Long trailerId, String detail, String receiptNo) {
    }

    private static final class ExpenseValidationException extends RuntimeException {
        private final String key;
        private final String code;
        private ExpenseValidationException(String key, String code, String message) {
            super(message); this.key = key; this.code = code;
        }
        private ImportValidationResult result() { return new ImportValidationResult(false, key, code, getMessage()); }
    }
}
