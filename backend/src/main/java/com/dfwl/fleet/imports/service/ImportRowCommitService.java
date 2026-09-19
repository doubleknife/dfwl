package com.dfwl.fleet.imports.service;

import com.dfwl.fleet.imports.dto.request.ImportPreviewRequest;
import com.dfwl.fleet.imports.dto.response.ImportRowResponse;
import com.dfwl.fleet.imports.repository.ImportRepository;
import com.dfwl.fleet.imports.service.ImportService.RowCommitResult;
import com.dfwl.fleet.imports.service.ImportService.RowPreparation;
import com.dfwl.fleet.imports.service.ImportService.ValidationResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.EnumMap;
import com.dfwl.fleet.imports.spi.ImportBusinessHandler;
import com.dfwl.fleet.imports.spi.ImportBusinessType;
import com.dfwl.fleet.imports.spi.ImportRowContext;
import com.dfwl.fleet.imports.spi.ImportValidationResult;
import com.dfwl.fleet.imports.spi.ImportCommitResult;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ImportRowCommitService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ImportRepository repository;
    private final ObjectMapper objectMapper;
    private final Map<ImportBusinessType, ImportBusinessHandler> handlers;

    public ImportRowCommitService(ImportRepository repository, ObjectMapper objectMapper, List<ImportBusinessHandler> businessHandlers) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.handlers = new EnumMap<>(ImportBusinessType.class);
        for (ImportBusinessHandler handler : businessHandlers) {
            if (handlers.putIfAbsent(handler.businessType(), handler) != null) {
                throw new IllegalStateException("Duplicate import handler: " + handler.businessType());
            }
        }
        for (ImportBusinessType required : List.of(ImportBusinessType.SALARY, ImportBusinessType.TIRE, ImportBusinessType.EXPENSE, ImportBusinessType.ROUTE)) {
            if (!handlers.containsKey(required)) throw new IllegalStateException("Missing import handler: " + required);
        }
    }

    public RowPreparation preparePreview(String businessType, ImportPreviewRequest.ImportRowRequest row, Set<String> keysInBatch) {
        try {
            ImportBusinessHandler handler = handlerFor(businessType);
            return handler == null ? unsupportedType(businessType, row.businessUniqueKey(), keysInBatch, true)
                    : prepareHandledPreview(handler, row.rawData(), row.businessUniqueKey(), keysInBatch);
        } catch (RuntimeException ex) {
            return new RowPreparation(row.businessUniqueKey(), new ValidationResult(false, "IMPORT_FORMAT_ERROR", "导入行格式错误"));
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RowCommitResult commitRow(String businessType, ImportRowResponse row, Set<String> keysInBatch, long operatorId) {
        try {
            Map<String, Object> raw = objectMapper.readValue(row.rawDataJson(), MAP_TYPE);
            ImportBusinessHandler handler = handlerFor(businessType);
            if (handler != null) return commitHandledRow(handler, row, raw, keysInBatch, operatorId);
            RowPreparation unsupported = unsupportedType(businessType, row.businessUniqueKey(), keysInBatch, false);
            repository.markRowFinal(row.id(), "FAILURE", unsupported.validation().errorCode(),
                    unsupported.validation().errorMessage(), businessType, null, unsupported.businessUniqueKey());
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

    private ImportBusinessHandler handlerFor(String businessType) {
        if (businessType == null) return null;
        try { return handlers.get(ImportBusinessType.valueOf(businessType)); }
        catch (IllegalArgumentException ex) { return null; }
    }

    /** Preserve legacy invalid-type error priority without retaining any domain fallback. */
    private RowPreparation unsupportedType(String businessType, String explicitKey, Set<String> keysInBatch, boolean preview) {
        if (!StringUtils.hasText(explicitKey)) java.util.Objects.requireNonNull(businessType);
        String key = StringUtils.hasText(explicitKey) ? explicitKey.trim() : explicitKey;
        if (!StringUtils.hasText(key)) return new RowPreparation(key, new ValidationResult(false, "IMPORT_KEY_MISSING", "业务唯一键不能为空"));
        if (!keysInBatch.add(key)) return new RowPreparation(key, new ValidationResult(false, "IMPORT_DUPLICATE_IN_BATCH", "导入批次内重复"));
        java.util.Objects.requireNonNull(businessType);
        // Keep the existing invalid-type history checks and their ordering.
        if (repository.committedKeyExists(businessType, key) || repository.committedKeyExists(businessType, key)) {
            return new RowPreparation(key, new ValidationResult(false, "IMPORT_DUPLICATE_EXISTING", "业务数据已存在"));
        }
        return new RowPreparation(preview ? null : key, new ValidationResult(false, "IMPORT_UNSUPPORTED_TYPE", "不支持的导入类型"));
    }

    private RowPreparation prepareHandledPreview(ImportBusinessHandler handler, Map<String, Object> raw,
                                                  String explicitKey, Set<String> keysInBatch) {
        ImportValidationResult key = handler.resolveBusinessKey(new ImportRowContext(null, raw, explicitKey, 0));
        if (!key.success()) return preparation(key);
        ImportRowContext context = new ImportRowContext(null, raw, key.businessUniqueKey(), 0);
        ImportValidationResult duplicate = validateHandledDuplicate(handler, context, keysInBatch);
        return preparation(duplicate.success() ? handler.validate(context) : duplicate);
    }

    private RowPreparation preparation(ImportValidationResult result) {
        return new RowPreparation(result.businessUniqueKey(),
                new ValidationResult(result.success(), result.errorCode(), result.errorMessage()));
    }

    private RowCommitResult commitHandledRow(ImportBusinessHandler handler, ImportRowResponse row,
                                             Map<String, Object> raw, Set<String> keysInBatch, long operatorId) {
        ImportValidationResult validation = handler.resolveBusinessKey(
                new ImportRowContext(row.id(), raw, row.businessUniqueKey(), operatorId));
        ImportRowContext context = new ImportRowContext(row.id(), raw, validation.businessUniqueKey(), operatorId);
        if (validation.success()) validation = validateHandledDuplicate(handler, context, keysInBatch);
        // Preserve Expense's existing second import-history check immediately before business writes.
        // This is import coordination; the handler must not access ImportRepository.
        if (validation.success() && handler.businessType() == ImportBusinessType.EXPENSE
                && repository.findImportedBusiness(handler.businessType().name(), context.businessUniqueKey()).isPresent()) {
            validation = new ImportValidationResult(false, context.businessUniqueKey(),
                    "IMPORT_DUPLICATE_EXISTING", "业务数据已存在");
        }
        ImportCommitResult result = validation.success() ? handler.commit(context)
                : new ImportCommitResult(ImportCommitResult.Status.FAILURE, null, validation.businessUniqueKey(),
                        validation.errorCode(), validation.errorMessage());
        repository.markRowFinal(row.id(), result.finalStatus().name(), result.errorCode(), result.errorMessage(),
                handler.businessType().name(), result.businessId(), result.businessUniqueKey());
        return new RowCommitResult(result.finalStatus().name());
    }

    private ImportValidationResult validateHandledDuplicate(ImportBusinessHandler handler, ImportRowContext context,
                                                            Set<String> keysInBatch) {
        String key = context.businessUniqueKey();
        if (!StringUtils.hasText(key)) return new ImportValidationResult(false, key, "IMPORT_KEY_MISSING", "业务唯一键不能为空");
        if (!keysInBatch.add(key)) return new ImportValidationResult(false, key, "IMPORT_DUPLICATE_IN_BATCH", "导入批次内重复");
        ImportValidationResult domain = handler.validateDuplicate(context);
        if (!domain.success()) return domain;
        String type = handler.businessType().name();
        if ((handler.businessType() != ImportBusinessType.ROUTE && repository.findImportedBusiness(type, key).isPresent())
                || repository.committedKeyExists(type, key)) {
            return new ImportValidationResult(false, key, "IMPORT_DUPLICATE_EXISTING", "业务数据已存在");
        }
        return new ImportValidationResult(true, key, null, null);
    }

}
