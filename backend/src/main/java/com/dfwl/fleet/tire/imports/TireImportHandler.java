package com.dfwl.fleet.tire.imports;

import com.dfwl.fleet.imports.spi.ImportBusinessHandler;
import com.dfwl.fleet.imports.spi.ImportBusinessType;
import com.dfwl.fleet.imports.spi.ImportCommitResult;
import com.dfwl.fleet.imports.spi.ImportRowContext;
import com.dfwl.fleet.imports.spi.ImportValidationResult;
import com.dfwl.fleet.tire.repository.TireRepository;
import com.dfwl.fleet.tire.repository.TireRepository.ImportedTire;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Historical imports bypass online requests/approval; imports owns the enclosing row transaction. */
@Component
public class TireImportHandler implements ImportBusinessHandler {
    private final TireRepository repository;

    public TireImportHandler(TireRepository repository) { this.repository = repository; }

    @Override
    public ImportBusinessType businessType() { return ImportBusinessType.TIRE; }

    @Override
    public ImportValidationResult resolveBusinessKey(ImportRowContext context) {
        try {
            String key = StringUtils.hasText(context.businessUniqueKey()) ? context.businessUniqueKey().trim()
                    : text(context.rowData(), "tireNo", true);
            return new ImportValidationResult(true, key, null, null);
        } catch (TireValidationException ex) { return ex.result(); }
    }

    @Override
    public ImportValidationResult validateDuplicate(ImportRowContext context) {
        boolean duplicate = repository.findTireByNo(context.businessUniqueKey()).isPresent();
        return new ImportValidationResult(!duplicate, context.businessUniqueKey(),
                duplicate ? "IMPORT_DUPLICATE_EXISTING" : null, duplicate ? "业务数据已存在" : null);
    }

    @Override
    public ImportValidationResult validate(ImportRowContext context) {
        try {
            parseTire(context.rowData());
            return new ImportValidationResult(true, context.businessUniqueKey(), null, null);
        } catch (TireValidationException ex) { return ex.result(); }
    }

    @Override
    public ImportCommitResult commit(ImportRowContext context) {
        // Preserve commit's additional duplicate check before the same live validation used by preview.
        ImportValidationResult duplicate = validateDuplicate(context);
        if (!duplicate.success()) return failure(duplicate);
        ImportedTire tire;
        try { tire = parseTire(context.rowData()); }
        catch (TireValidationException ex) { return failure(ex.result()); }
        long id = repository.insertImportedTire(tire);
        if ("CLAIMED".equals(tire.status())) {
            repository.insertImportedClaim(id, tire.driverId(), tire.vehicleId(), tire.installTime());
        }
        return new ImportCommitResult(ImportCommitResult.Status.SUCCESS, id, context.businessUniqueKey(), null, null);
    }

    private ImportCommitResult failure(ImportValidationResult result) {
        return new ImportCommitResult(ImportCommitResult.Status.FAILURE, null, result.businessUniqueKey(),
                result.errorCode(), result.errorMessage());
    }

    private ImportedTire parseTire(Map<String, Object> raw) {
        String tireNo = text(raw, "tireNo", true);
        boolean used = bool(raw, "used", false);
        String status = defaultText(raw, "status", used ? "CLAIMED" : "IN_STOCK").toUpperCase(Locale.ROOT);
        Long vehicleId = longValue(raw, "vehicleId", false);
        Long driverId = longValue(raw, "driverId", false);
        LocalDateTime installTime = dateTime(raw, "installTime", false);
        if (!"IN_STOCK".equals(status) && !"APPROVAL_RESERVED".equals(status) && !"CLAIMED".equals(status)) {
            throw new TireValidationException(tireNo, "IMPORT_FORMAT_ERROR", "轮胎状态不合法");
        }
        if ("CLAIMED".equals(status) && (vehicleId == null || driverId == null || installTime == null)) {
            throw new TireValidationException(tireNo, "IMPORT_FORMAT_ERROR", "历史已使用轮胎必须提供司机、车辆和安装时间");
        }
        if (vehicleId != null && !repository.importVehicleExists(vehicleId)) {
            throw new TireValidationException(tireNo, "IMPORT_REFERENCE_NOT_FOUND", "车辆不存在");
        }
        if (driverId != null && !repository.importDriverExists(driverId)) {
            throw new TireValidationException(tireNo, "IMPORT_REFERENCE_NOT_FOUND", "司机不存在");
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

    private String text(Map<String, Object> raw, String field, boolean required) {
        Object value = raw.get(field);
        if (value == null || !StringUtils.hasText(String.valueOf(value))) {
            if (required) {
                throw new TireValidationException(null, "IMPORT_FORMAT_ERROR", field + "不能为空");
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
                throw new TireValidationException(null, "IMPORT_FORMAT_ERROR", field + "不能为空");
            }
            return null;
        }
        try {
            return value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            throw new TireValidationException(null, "IMPORT_FORMAT_ERROR", field + "格式错误");
        }
    }

    private LocalDateTime dateTime(Map<String, Object> raw, String field, boolean required) {
        String value = text(raw, field, required);
        return value == null ? null : LocalDateTime.parse(value);
    }

    private boolean bool(Map<String, Object> raw, String field, boolean defaultValue) {
        Object value = raw.get(field);
        return value == null ? defaultValue : Boolean.parseBoolean(String.valueOf(value));
    }

    private static final class TireValidationException extends RuntimeException {
        private final String key;
        private final String code;
        private TireValidationException(String key, String code, String message) {
            super(message);
            this.key = key;
            this.code = code;
        }
        private ImportValidationResult result() { return new ImportValidationResult(false, key, code, getMessage()); }
    }
}
