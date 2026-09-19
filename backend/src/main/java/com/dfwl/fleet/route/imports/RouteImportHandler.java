package com.dfwl.fleet.route.imports;

import com.dfwl.fleet.imports.spi.ImportBusinessHandler;
import com.dfwl.fleet.imports.spi.ImportBusinessType;
import com.dfwl.fleet.imports.spi.ImportCommitResult;
import com.dfwl.fleet.imports.spi.ImportRowContext;
import com.dfwl.fleet.imports.spi.ImportValidationResult;
import com.dfwl.fleet.route.repository.RouteImportRepository;
import com.dfwl.fleet.route.repository.RouteImportRepository.BindingSnapshot;
import com.dfwl.fleet.route.repository.RouteImportRepository.ImportedRoute;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Route import rules; the enclosing imports row transaction owns all writes. */
@Component
public class RouteImportHandler implements ImportBusinessHandler {
    private static final DateTimeFormatter NO_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private final RouteImportRepository repository;
    public RouteImportHandler(RouteImportRepository repository) { this.repository = repository; }

    @Override
    public ImportBusinessType businessType() { return ImportBusinessType.ROUTE; }

    @Override
    public ImportValidationResult resolveBusinessKey(ImportRowContext context) {
        try {
            String key = StringUtils.hasText(context.businessUniqueKey()) ? context.businessUniqueKey().trim()
                    : naturalBusinessKey(context.rowData());
            return new ImportValidationResult(true, key, null, null);
        } catch (RouteValidationException ex) { return ex.result(); }
    }

    @Override
    public ImportValidationResult validateDuplicate(ImportRowContext context) {
        boolean duplicate = repository.findRouteByBusinessKey(context.businessUniqueKey()).isPresent();
        return new ImportValidationResult(!duplicate, context.businessUniqueKey(),
                duplicate ? "IMPORT_DUPLICATE_EXISTING" : null, duplicate ? "业务数据已存在" : null);
    }

    @Override
    public ImportValidationResult validate(ImportRowContext context) {
        try {
            // Preview previously recalculated the natural key for structural validation,
            // including its error key. Commit uses the resolved/persisted key instead.
            parseRoute(context.importRowId() == null ? naturalBusinessKey(context.rowData())
                    : context.businessUniqueKey(), context.rowData());
            return new ImportValidationResult(true, context.businessUniqueKey(), null, null);
        } catch (RouteValidationException ex) { return ex.result(); }
    }

    @Override
    public ImportCommitResult commit(ImportRowContext context) {
        // Preserve the original additional business duplicate check before parsing and insertion.
        ImportValidationResult duplicate = validateDuplicate(context);
        if (!duplicate.success()) return failure(duplicate);
        ImportedRoute route;
        try { route = parseRoute(context.businessUniqueKey(), context.rowData()); }
        catch (RouteValidationException ex) { return failure(ex.result()); }
        long id = repository.insertRoute(route, context.operatorId());
        repository.insertRouteStatusHistory(id, route.status(), context.operatorId());
        ImportCommitResult.Status status = route.status().equals("PUBLISHED")
                ? ImportCommitResult.Status.SUCCESS : ImportCommitResult.Status.UNPUBLISHED;
        return new ImportCommitResult(status, id, context.businessUniqueKey(), null, null);
    }

    private ImportCommitResult failure(ImportValidationResult result) {
        return new ImportCommitResult(ImportCommitResult.Status.FAILURE, null, result.businessUniqueKey(),
                result.errorCode(), result.errorMessage());
    }

    private String naturalBusinessKey(Map<String, Object> raw) {
        return longValue(raw, "customerId", true) + "|" + date(raw, "businessDate", true)
                + "|" + text(raw, "direction", true) + "|" + text(raw, "loadingPlace", true)
                + "|" + text(raw, "unloadingPlace", true);
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
            throw new RouteValidationException(key, "IMPORT_FORMAT_ERROR", "线路方向不合法");
        }
        if (!repository.customerExists(customerId) || !repository.productExists(productId)) {
            throw new RouteValidationException(key, "IMPORT_REFERENCE_NOT_FOUND", "客户或产品不存在");
        }
        String status = "UNPUBLISHED";
        if (driverId != null) {
            BindingSnapshot binding = repository.driverBinding(driverId)
                    .orElseThrow(() -> new RouteValidationException(key, "ROUTE_003", "司机车辆校验失败"));
            if (vehicleId != null && !vehicleId.equals(binding.vehicleId())) {
                throw new RouteValidationException(key, "ROUTE_003", "导入车辆与司机当前绑定车辆不匹配");
            }
            if (bindingValid(binding)) {
                status = "PUBLISHED";
                vehicleId = binding.vehicleId();
            }
        } else if (vehicleId != null && !repository.vehicleExists(vehicleId)) {
            throw new RouteValidationException(key, "IMPORT_REFERENCE_NOT_FOUND", "车辆不存在");
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

    private String text(Map<String, Object> raw, String field, boolean required) {
        Object value = raw.get(field);
        if (value == null || !StringUtils.hasText(String.valueOf(value))) {
            if (required) {
                throw new RouteValidationException(null, "IMPORT_FORMAT_ERROR", field + "不能为空");
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
                throw new RouteValidationException(null, "IMPORT_FORMAT_ERROR", field + "不能为空");
            }
            return null;
        }
        try {
            return value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            throw new RouteValidationException(null, "IMPORT_FORMAT_ERROR", field + "格式错误");
        }
    }

    private BigDecimal decimal(Map<String, Object> raw, String field, boolean required) {
        Object value = raw.get(field);
        if (value == null || !StringUtils.hasText(String.valueOf(value))) {
            if (required) {
                throw new RouteValidationException(null, "IMPORT_FORMAT_ERROR", field + "不能为空");
            }
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException ex) {
            throw new RouteValidationException(null, "IMPORT_FORMAT_ERROR", field + "格式错误");
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

    private String nextNo(String prefix) {
        return prefix + LocalDateTime.now().format(NO_TIME)
                + UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
    }

    private static final class RouteValidationException extends RuntimeException {
        private final String key;
        private final String code;
        private RouteValidationException(String key, String code, String message) {
            super(message); this.key = key; this.code = code;
        }
        private ImportValidationResult result() { return new ImportValidationResult(false, key, code, getMessage()); }
    }
}
