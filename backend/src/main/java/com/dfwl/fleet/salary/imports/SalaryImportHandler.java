package com.dfwl.fleet.salary.imports;

import com.dfwl.fleet.imports.spi.ImportBusinessHandler;
import com.dfwl.fleet.imports.spi.ImportBusinessType;
import com.dfwl.fleet.imports.spi.ImportCommitResult;
import com.dfwl.fleet.imports.spi.ImportRowContext;
import com.dfwl.fleet.imports.spi.ImportValidationResult;
import com.dfwl.fleet.salary.repository.DriverSalaryRepository;
import com.dfwl.fleet.salary.service.DriverSalaryService;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Salary rules only. The imports caller owns the row transaction and final row result. */
@Component
public class SalaryImportHandler implements ImportBusinessHandler {
    private final DriverSalaryRepository repository;
    private final DriverSalaryService salaryService;

    public SalaryImportHandler(DriverSalaryRepository repository, DriverSalaryService salaryService) {
        this.repository = repository;
        this.salaryService = salaryService;
    }

    @Override
    public ImportBusinessType businessType() { return ImportBusinessType.SALARY; }

    @Override
    public ImportValidationResult resolveBusinessKey(ImportRowContext context) {
        try {
            return new ImportValidationResult(true, key(context.rowData(), context.businessUniqueKey()), null, null);
        } catch (SalaryValidationException ex) {
            return ex.result();
        }
    }

    @Override
    public ImportValidationResult validateDuplicate(ImportRowContext context) {
        // Preserve the legacy key-based preview check, including explicit non-SALARY keys.
        Long routeId = routeIdFromKey(context.businessUniqueKey());
        return routeId != null && repository.salaryExistsForRoute(routeId)
                ? duplicate(context.businessUniqueKey())
                : new ImportValidationResult(true, context.businessUniqueKey(), null, null);
    }

    @Override
    public ImportValidationResult validate(ImportRowContext context) {
        try {
            // Legacy preview structure validation derives its own key; commit uses the resolved key.
            String validationKey = context.importRowId() == null ? key(context.rowData(), null) : context.businessUniqueKey();
            ParsedSalary salary = fields(validationKey, context.rowData());
            var route = repository.findImportRoute(salary.routeId())
                    .orElseThrow(() -> new SalaryValidationException(validationKey, "ROUTE_001", "线路不存在"));
            if (route.driverId() == null || route.driverId() != salary.driverId()) {
                throw new SalaryValidationException(validationKey, "ROUTE_003", "线路司机不匹配");
            }
            return new ImportValidationResult(true, context.businessUniqueKey(), null, null);
        } catch (SalaryValidationException ex) {
            return ex.result();
        }
    }

    @Override
    public ImportCommitResult commit(ImportRowContext context) {
        ImportValidationResult validation = validate(context);
        if (!validation.success()) return failure(validation);
        ParsedSalary salary = fields(context.businessUniqueKey(), context.rowData());
        // The original commit additionally checks the actual route, even for an explicit key.
        if (repository.salaryExistsForRoute(salary.routeId())) return failure(duplicate(context.businessUniqueKey()));
        var response = salaryService.upsertImported(salary.routeId(), salary.driverId(), salary.amount(),
                context.importRowId(), context.operatorId());
        return new ImportCommitResult(ImportCommitResult.Status.SUCCESS, response.id(), context.businessUniqueKey(), null, null);
    }

    private ImportCommitResult failure(ImportValidationResult result) {
        return new ImportCommitResult(ImportCommitResult.Status.FAILURE, null, result.businessUniqueKey(),
                result.errorCode(), result.errorMessage());
    }

    private ImportValidationResult duplicate(String key) {
        return new ImportValidationResult(false, key, "IMPORT_DUPLICATE_EXISTING", "业务数据已存在");
    }

    private String key(Map<String, Object> raw, String explicitKey) {
        return StringUtils.hasText(explicitKey) ? explicitKey.trim() : "SALARY|" + longValue(raw, "routeId");
    }

    private Long routeIdFromKey(String key) {
        if (key == null || !key.startsWith("SALARY|")) return null;
        try { return Long.parseLong(key.substring("SALARY|".length())); }
        catch (NumberFormatException ex) { return null; }
    }

    private ParsedSalary fields(String key, Map<String, Object> raw) {
        long routeId = longValue(raw, "routeId");
        long driverId = longValue(raw, "driverId");
        BigDecimal amount = decimal(raw, "salaryAmount", false);
        if (amount == null) amount = decimal(raw, "amount", true);
        if (amount.signum() <= 0) throw new SalaryValidationException(key, "IMPORT_FORMAT_ERROR", "工资金额必须大于0");
        return new ParsedSalary(routeId, driverId, amount);
    }

    private long longValue(Map<String, Object> raw, String field) {
        Object value = raw.get(field);
        if (value == null || !StringUtils.hasText(String.valueOf(value))) {
            throw new SalaryValidationException(null, "IMPORT_FORMAT_ERROR", field + "不能为空");
        }
        try { return value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value)); }
        catch (NumberFormatException ex) { throw new SalaryValidationException(null, "IMPORT_FORMAT_ERROR", field + "格式错误"); }
    }

    private BigDecimal decimal(Map<String, Object> raw, String field, boolean required) {
        Object value = raw.get(field);
        if (value == null || !StringUtils.hasText(String.valueOf(value))) {
            if (required) throw new SalaryValidationException(null, "IMPORT_FORMAT_ERROR", field + "不能为空");
            return null;
        }
        try { return new BigDecimal(String.valueOf(value)); }
        catch (NumberFormatException ex) { throw new SalaryValidationException(null, "IMPORT_FORMAT_ERROR", field + "格式错误"); }
    }

    private record ParsedSalary(long routeId, long driverId, BigDecimal amount) { }

    private static final class SalaryValidationException extends RuntimeException {
        private final String key;
        private final String code;
        private SalaryValidationException(String key, String code, String message) {
            super(message);
            this.key = key;
            this.code = code;
        }
        private ImportValidationResult result() { return new ImportValidationResult(false, key, code, getMessage()); }
    }
}
