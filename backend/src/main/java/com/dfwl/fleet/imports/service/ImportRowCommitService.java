package com.dfwl.fleet.imports.service;

import com.dfwl.fleet.common.domain.ExpenseType;
import com.dfwl.fleet.imports.api.ImportPreviewRequest;
import com.dfwl.fleet.imports.api.ImportRowResponse;
import com.dfwl.fleet.imports.repository.ImportRepository;
import com.dfwl.fleet.imports.repository.ImportRepository.BindingSnapshot;
import com.dfwl.fleet.imports.repository.ImportRepository.ImportedExpense;
import com.dfwl.fleet.imports.repository.ImportRepository.ImportedRoute;
import com.dfwl.fleet.imports.repository.ImportRepository.ImportedTire;
import com.dfwl.fleet.salary.api.DriverSalaryResponse;
import com.dfwl.fleet.salary.service.DriverSalaryService;
import com.dfwl.fleet.imports.service.ImportService.RowCommitResult;
import com.dfwl.fleet.imports.service.ImportService.RowPreparation;
import com.dfwl.fleet.imports.service.ImportService.ValidationResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ImportRowCommitService {

    private static final DateTimeFormatter NO_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ImportRepository repository;
    private final ObjectMapper objectMapper;
    private final DriverSalaryService salaryService;

    public ImportRowCommitService(ImportRepository repository, ObjectMapper objectMapper, DriverSalaryService salaryService) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.salaryService = salaryService;
    }

    public RowPreparation preparePreview(String businessType, ImportPreviewRequest.ImportRowRequest row, Set<String> keysInBatch) {
        try {
            Map<String, Object> raw = row.rawData();
            String key = businessKey(businessType, raw, row.businessUniqueKey());
            ValidationResult duplicateValidation = validateDuplicate(businessType, key, keysInBatch);
            if (!duplicateValidation.success()) {
                return new RowPreparation(key, duplicateValidation);
            }
            validateStructure(businessType, raw);
            return new RowPreparation(key, new ValidationResult(true, null, null));
        } catch (ImportValidationException ex) {
            return new RowPreparation(ex.businessUniqueKey(), new ValidationResult(false, ex.code(), ex.getMessage()));
        } catch (RuntimeException ex) {
            return new RowPreparation(row.businessUniqueKey(), new ValidationResult(false, "IMPORT_FORMAT_ERROR", "导入行格式错误"));
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RowCommitResult commitRow(String businessType, ImportRowResponse row, Set<String> keysInBatch, long operatorId) {
        try {
            Map<String, Object> raw = objectMapper.readValue(row.rawDataJson(), MAP_TYPE);
            String key = businessKey(businessType, raw, row.businessUniqueKey());
            ValidationResult duplicateValidation = validateDuplicate(businessType, key, keysInBatch);
            if (!duplicateValidation.success()) {
                repository.markRowFinal(row.id(), "FAILURE", duplicateValidation.errorCode(), duplicateValidation.errorMessage(),
                        businessType, null, key);
                return new RowCommitResult("FAILURE");
            }
            RowCommitResult result = switch (businessType) {
                case "ROUTE" -> commitRoute(row.id(), key, raw, operatorId);
                case "TIRE" -> commitTire(row.id(), key, raw);
                case "EXPENSE" -> commitExpense(row.id(), key, raw, operatorId);
                case "SALARY" -> commitSalary(row.id(), key, raw, operatorId);
                default -> throw new ImportValidationException(key, "IMPORT_UNSUPPORTED_TYPE", "不支持的导入类型");
            };
            return result;
        } catch (ImportValidationException ex) {
            repository.markRowFinal(row.id(), "FAILURE", ex.code(), ex.getMessage(), businessType, null, ex.businessUniqueKey());
            return new RowCommitResult("FAILURE");
        } catch (DataAccessException ex) {
            throw ex;
        } catch (RuntimeException | JsonProcessingException ex) {
            repository.markRowFinal(row.id(), "FAILURE", "IMPORT_FORMAT_ERROR", "导入行格式错误", businessType, null, row.businessUniqueKey());
            return new RowCommitResult("FAILURE");
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailure(long rowId, String businessType, String businessUniqueKey, String errorCode, String errorMessage) {
        repository.markRowFinal(rowId, "FAILURE", errorCode, errorMessage, businessType, null, businessUniqueKey);
    }

    private RowCommitResult commitRoute(long rowId, String key, Map<String, Object> raw, long operatorId) {
        if (repository.findRouteByBusinessKey(key).isPresent()) {
            repository.markRowFinal(rowId, "FAILURE", "IMPORT_DUPLICATE_EXISTING", "业务数据已存在", "ROUTE", null, key);
            return new RowCommitResult("FAILURE");
        }
        ImportedRoute route = parseRoute(key, raw);
        String finalStatus = route.status().equals("PUBLISHED") ? "SUCCESS" : "UNPUBLISHED";
        long id = repository.insertRoute(route, operatorId);
        repository.insertRouteStatusHistory(id, route.status(), operatorId);
        repository.markRowFinal(rowId, finalStatus, null, null, "ROUTE", id, key);
        return new RowCommitResult(finalStatus);
    }

    private RowCommitResult commitTire(long rowId, String key, Map<String, Object> raw) {
        if (repository.findTireByNo(key).isPresent()) {
            repository.markRowFinal(rowId, "FAILURE", "IMPORT_DUPLICATE_EXISTING", "业务数据已存在", "TIRE", null, key);
            return new RowCommitResult("FAILURE");
        }
        ImportedTire tire = parseTire(raw);
        long id = repository.insertTire(tire);
        if ("CLAIMED".equals(tire.status())) {
            repository.insertTireClaim(id, tire.driverId(), tire.vehicleId(), tire.installTime());
        }
        repository.markRowFinal(rowId, "SUCCESS", null, null, "TIRE", id, key);
        return new RowCommitResult("SUCCESS");
    }

    private RowCommitResult commitExpense(long rowId, String key, Map<String, Object> raw, long operatorId) {
        if (repository.findImportedBusiness("EXPENSE", key).isPresent()) {
            repository.markRowFinal(rowId, "FAILURE", "IMPORT_DUPLICATE_EXISTING", "业务数据已存在", "EXPENSE", null, key);
            return new RowCommitResult("FAILURE");
        }
        ParsedExpense parsed = parseExpense(rowId, raw);
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
        long id = repository.insertExpense(expense, operatorId);
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
        repository.markRowFinal(rowId, "SUCCESS", null, null, "EXPENSE", id, key);
        return new RowCommitResult("SUCCESS");
    }

    private RowCommitResult commitSalary(long rowId, String key, Map<String, Object> raw, long operatorId) {
        ParsedSalary salary = parseSalary(key, raw);
        if (repository.salaryExistsForRoute(salary.routeId())) {
            repository.markRowFinal(rowId, "FAILURE", "IMPORT_DUPLICATE_EXISTING", "业务数据已存在", "SALARY", null, key);
            return new RowCommitResult("FAILURE");
        }
        DriverSalaryResponse response = salaryService.upsertImported(
                salary.routeId(), salary.driverId(), salary.salaryAmount(), rowId, operatorId);
        repository.markRowFinal(rowId, "SUCCESS", null, null, "SALARY", response.id(), key);
        return new RowCommitResult("SUCCESS");
    }

    private void validateStructure(String businessType, Map<String, Object> raw) {
        switch (businessType) {
            case "ROUTE" -> parseRoute(businessKey("ROUTE", raw, null), raw);
            case "TIRE" -> parseTire(raw);
            case "EXPENSE" -> parseExpense(0, raw);
            case "SALARY" -> parseSalary(businessKey("SALARY", raw, null), raw);
            default -> throw new ImportValidationException(null, "IMPORT_UNSUPPORTED_TYPE", "不支持的导入类型");
        }
    }

    private ImportedRoute parseRoute(String key, Map<String, Object> raw) {
        LocalDate businessDate = date(raw, "businessDate", true);
        long customerId = longValue(raw, "customerId", true);
        long productId = longValue(raw, "productId", true);
        String direction = text(raw, "direction", true);
        String loadingPlace = text(raw, "loadingPlace", true);
        String unloadingPlace = text(raw, "unloadingPlace", true);
        BigDecimal taxUnitPrice = decimal(raw, "taxUnitPrice", true);
        Long driverId = longValue(raw, "assignedDriverId", false);
        if (driverId == null) {
            driverId = longValue(raw, "driverId", false);
        }
        Long vehicleId = longValue(raw, "vehicleId", false);
        if (vehicleId == null) {
            vehicleId = longValue(raw, "importVehicleId", false);
        }
        if (!"OUTBOUND".equals(direction) && !"RETURN".equals(direction)) {
            throw new ImportValidationException(key, "IMPORT_FORMAT_ERROR", "线路方向不合法");
        }
        if (!repository.customerExists(customerId) || !repository.productExists(productId)) {
            throw new ImportValidationException(key, "IMPORT_REFERENCE_NOT_FOUND", "客户或产品不存在");
        }
        String status = "UNPUBLISHED";
        if (driverId != null) {
            BindingSnapshot binding = repository.driverBinding(driverId)
                    .orElseThrow(() -> new ImportValidationException(key, "ROUTE_003", "司机车辆校验失败"));
            if (vehicleId != null && !vehicleId.equals(binding.vehicleId())) {
                throw new ImportValidationException(key, "ROUTE_003", "导入车辆与司机当前绑定车辆不匹配");
            }
            if (bindingValid(binding)) {
                status = "PUBLISHED";
                vehicleId = binding.vehicleId();
            }
        } else if (vehicleId != null && !repository.vehicleExists(vehicleId)) {
            throw new ImportValidationException(key, "IMPORT_REFERENCE_NOT_FOUND", "车辆不存在");
        }
        return new ImportedRoute(
                defaultText(raw, "routeNo", nextNo("R")),
                text(raw, "externalRouteNo", false),
                text(raw, "tripSequence", false),
                key,
                businessDate,
                customerId,
                productId,
                direction,
                loadingPlace,
                unloadingPlace,
                decimalDefault(raw, "carryingFee", "0.00"),
                decimal(raw, "mileageKm", false),
                taxUnitPrice,
                decimalDefault(raw, "infoFee", "0.00"),
                driverId,
                vehicleId,
                decimal(raw, "driverSalary", false),
                text(raw, "salarySource", false),
                status);
    }

    private boolean bindingValid(BindingSnapshot binding) {
        boolean validDriver = binding.driverStatus() == 1;
        boolean validVehicle = binding.vehicleId() != null
                && binding.vehicleStatus() != null
                && binding.vehicleStatus() == 1
                && binding.vehicleInsuranceComplete() != null
                && binding.vehicleInsuranceComplete() == 1;
        boolean validTrailer = binding.trailerId() == null
                || (binding.trailerStatus() != null
                && binding.trailerStatus() == 1
                && binding.trailerInsuranceComplete() != null
                && binding.trailerInsuranceComplete() == 1);
        return validDriver && validVehicle && validTrailer;
    }

    private ImportedTire parseTire(Map<String, Object> raw) {
        String tireNo = text(raw, "tireNo", true);
        boolean used = bool(raw, "used", false);
        String status = defaultText(raw, "status", used ? "CLAIMED" : "IN_STOCK").toUpperCase(Locale.ROOT);
        Long vehicleId = longValue(raw, "vehicleId", false);
        Long driverId = longValue(raw, "driverId", false);
        LocalDateTime installTime = dateTime(raw, "installTime", false);
        if (!"IN_STOCK".equals(status) && !"APPROVAL_RESERVED".equals(status) && !"CLAIMED".equals(status)) {
            throw new ImportValidationException(tireNo, "IMPORT_FORMAT_ERROR", "轮胎状态不合法");
        }
        if ("CLAIMED".equals(status) && (vehicleId == null || driverId == null || installTime == null)) {
            throw new ImportValidationException(tireNo, "IMPORT_FORMAT_ERROR", "历史已使用轮胎必须提供司机、车辆和安装时间");
        }
        if (vehicleId != null && !repository.vehicleExists(vehicleId)) {
            throw new ImportValidationException(tireNo, "IMPORT_REFERENCE_NOT_FOUND", "车辆不存在");
        }
        if (driverId != null && !repository.driverExists(driverId)) {
            throw new ImportValidationException(tireNo, "IMPORT_REFERENCE_NOT_FOUND", "司机不存在");
        }
        return new ImportedTire(
                tireNo,
                text(raw, "barcode", false),
                dateTime(raw, "arrivalTime", false),
                text(raw, "description", false),
                status,
                installTime,
                vehicleId,
                driverId);
    }

    private ParsedExpense parseExpense(long rowId, Map<String, Object> raw) {
        String expenseType = text(raw, "expenseType", true).toUpperCase(Locale.ROOT);
        try {
            ExpenseType.valueOf(expenseType);
        } catch (IllegalArgumentException ex) {
            throw new ImportValidationException(null, "IMPORT_FORMAT_ERROR", "费用类型不合法");
        }
        LocalDate businessDate = date(raw, "businessDate", true);
        long vehicleId = longValue(raw, "vehicleId", true);
        if (!repository.vehicleExists(vehicleId)) {
            throw new ImportValidationException(null, "IMPORT_REFERENCE_NOT_FOUND", "车辆不存在");
        }
        Long driverId = longValue(raw, "driverId", false);
        String attributionType = text(raw, "attributionType", true).toUpperCase(Locale.ROOT);
        Long routeId = longValue(raw, "routeId", false);
        if ("ROUTE".equals(attributionType)) {
            if (routeId == null && !isEnergy(expenseType)) {
                throw new ImportValidationException(null, "EXPENSE_003", "线路费用必须绑定线路");
            }
        } else if ("DAILY".equals(attributionType)) {
            if (routeId != null) {
                throw new ImportValidationException(null, "EXPENSE_003", "日常费用不能绑定线路");
            }
        } else {
            throw new ImportValidationException(null, "EXPENSE_003", "费用归属不合法");
        }
        if (routeId != null && !repository.routeExists(routeId)) {
            throw new ImportValidationException(null, "ROUTE_001", "线路不存在");
        }
        EnergyDetail energyDetail = null;
        if (isEnergy(expenseType)) {
            Map<String, Object> detail = nested(raw, "energyDetail");
            if (detail == null) {
                throw new ImportValidationException(null, "IMPORT_FORMAT_ERROR", "能源费用明细不能为空");
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

    private ParsedSalary parseSalary(String key, Map<String, Object> raw) {
        long routeId = longValue(raw, "routeId", true);
        long driverId = longValue(raw, "driverId", true);
        BigDecimal amount = decimal(raw, "salaryAmount", false);
        if (amount == null) {
            amount = decimal(raw, "amount", true);
        }
        if (amount.signum() <= 0) {
            throw new ImportValidationException(key, "IMPORT_FORMAT_ERROR", "工资金额必须大于0");
        }
        ImportRepository.RouteSalarySnapshot route = repository.routeSalary(routeId)
                .orElseThrow(() -> new ImportValidationException(key, "ROUTE_001", "线路不存在"));
        if (route.driverId() == null || route.driverId() != driverId) {
            throw new ImportValidationException(key, "ROUTE_003", "线路司机不匹配");
        }
        return new ParsedSalary(routeId, driverId, amount);
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

    private ValidationResult validateDuplicate(String businessType, String key, Set<String> keysInBatch) {
        if (!StringUtils.hasText(key)) {
            return new ValidationResult(false, "IMPORT_KEY_MISSING", "业务唯一键不能为空");
        }
        if (!keysInBatch.add(key)) {
            return new ValidationResult(false, "IMPORT_DUPLICATE_IN_BATCH", "导入批次内重复");
        }
        boolean exists = switch (businessType) {
            case "ROUTE" -> repository.findRouteByBusinessKey(key).isPresent();
            case "TIRE" -> repository.findTireByNo(key).isPresent();
            case "EXPENSE" -> repository.findImportedBusiness("EXPENSE", key).isPresent();
            case "SALARY" -> salaryDuplicate(rawRouteIdFromKey(key), key);
            default -> repository.committedKeyExists(businessType, key);
        };
        if (exists || repository.committedKeyExists(businessType, key)) {
            return new ValidationResult(false, "IMPORT_DUPLICATE_EXISTING", "业务数据已存在");
        }
        return new ValidationResult(true, null, null);
    }

    private String businessKey(String businessType, Map<String, Object> raw, String explicitKey) {
        if (StringUtils.hasText(explicitKey)) {
            return explicitKey.trim();
        }
        return switch (businessType) {
            case "ROUTE" -> longValue(raw, "customerId", true) + "|" + date(raw, "businessDate", true)
                    + "|" + text(raw, "direction", true) + "|" + text(raw, "loadingPlace", true)
                    + "|" + text(raw, "unloadingPlace", true);
            case "TIRE" -> text(raw, "tireNo", true);
            case "EXPENSE" -> expenseBusinessKey(raw);
            case "SALARY" -> "SALARY|" + longValue(raw, "routeId", true);
            default -> explicitKey;
        };
    }

    private boolean salaryDuplicate(Long routeId, String key) {
        return routeId != null && repository.salaryExistsForRoute(routeId)
                || repository.findImportedBusiness("SALARY", key).isPresent();
    }

    private Long rawRouteIdFromKey(String key) {
        if (key == null || !key.startsWith("SALARY|")) {
            return null;
        }
        try {
            return Long.parseLong(key.substring("SALARY|".length()));
        } catch (NumberFormatException ex) {
            return null;
        }
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

        throw new ImportValidationException(null, "IMPORT_KEY_MISSING", "无天然唯一编号的费用必须提供businessUniqueKey");
    }

    private boolean isEnergy(String expenseType) {
        return "ELECTRIC".equals(expenseType) || "GAS".equals(expenseType) || "TEMP_ELECTRIC".equals(expenseType);
    }

    private String text(Map<String, Object> raw, String field, boolean required) {
        Object value = raw.get(field);
        if (value == null || !StringUtils.hasText(String.valueOf(value))) {
            if (required) {
                throw new ImportValidationException(null, "IMPORT_FORMAT_ERROR", field + "不能为空");
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
                throw new ImportValidationException(null, "IMPORT_FORMAT_ERROR", field + "不能为空");
            }
            return null;
        }
        try {
            return value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            throw new ImportValidationException(null, "IMPORT_FORMAT_ERROR", field + "格式错误");
        }
    }

    private BigDecimal decimal(Map<String, Object> raw, String field, boolean required) {
        Object value = raw.get(field);
        if (value == null || !StringUtils.hasText(String.valueOf(value))) {
            if (required) {
                throw new ImportValidationException(null, "IMPORT_FORMAT_ERROR", field + "不能为空");
            }
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException ex) {
            throw new ImportValidationException(null, "IMPORT_FORMAT_ERROR", field + "格式错误");
        }
    }

    private BigDecimal decimalDefault(Map<String, Object> raw, String field, String defaultValue) {
        BigDecimal value = decimal(raw, field, false);
        return value == null ? new BigDecimal(defaultValue) : value;
    }

    private LocalDate date(Map<String, Object> raw, String field, boolean required) {
        String value = text(raw, field, required);
        return value == null ? null : LocalDate.parse(value);
    }

    private LocalDateTime dateTime(Map<String, Object> raw, String field, boolean required) {
        String value = text(raw, field, required);
        return value == null ? null : LocalDateTime.parse(value);
    }

    private boolean bool(Map<String, Object> raw, String field, boolean defaultValue) {
        Object value = raw.get(field);
        return value == null ? defaultValue : Boolean.parseBoolean(String.valueOf(value));
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

    private record ParsedSalary(long routeId, long driverId, BigDecimal salaryAmount) {
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

    private static class ImportValidationException extends RuntimeException {
        private final String businessUniqueKey;
        private final String code;

        ImportValidationException(String businessUniqueKey, String code, String message) {
            super(message);
            this.businessUniqueKey = businessUniqueKey;
            this.code = code;
        }

        String businessUniqueKey() {
            return businessUniqueKey;
        }

        String code() {
            return code;
        }
    }
}
