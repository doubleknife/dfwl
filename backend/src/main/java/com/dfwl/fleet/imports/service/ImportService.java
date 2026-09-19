package com.dfwl.fleet.imports.service;

import com.dfwl.fleet.attachment.service.AttachmentService;
import com.dfwl.fleet.common.api.PageResponse;
import com.dfwl.fleet.common.error.BusinessException;
import com.dfwl.fleet.common.error.ErrorCode;
import com.dfwl.fleet.imports.dto.request.ImportPreviewRequest;
import com.dfwl.fleet.imports.dto.response.ImportRowResponse;
import com.dfwl.fleet.imports.dto.response.ImportTaskResponse;
import com.dfwl.fleet.imports.repository.ImportRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ImportService {

    private static final DateTimeFormatter BATCH_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final ImportRepository repository;
    private final ObjectMapper objectMapper;
    private final ImportRowCommitService rowCommitService;
    private final ImportFileParser fileParser;
    private final AttachmentService attachmentService;

    public ImportService(ImportRepository repository, ObjectMapper objectMapper, ImportRowCommitService rowCommitService,
                         ImportFileParser fileParser, AttachmentService attachmentService) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.rowCommitService = rowCommitService;
        this.fileParser = fileParser;
        this.attachmentService = attachmentService;
    }

    @Transactional
    public ImportTaskResponse preview(ImportPreviewRequest request, long operatorId) {
        String businessType = normalizeBusinessType(request.businessType());
        if (!repository.importFileExists(request.originalFileId())) {
            throw new BusinessException(ErrorCode.ATTACHMENT_001);
        }
        List<ImportPreviewRequest.ImportRowRequest> rows = fileParser.parse(
                businessType, request.templateId(), request.originalFileId(), operatorId);
        Set<String> keysInBatch = new HashSet<>();
        int successCount = 0;
        int failureCount = 0;
        long taskId = repository.createTask(
                nextBatchNo(),
                businessType,
                request.templateId(),
                request.originalFileId(),
                rows.size(),
                0,
                0,
                operatorId);

        for (ImportPreviewRequest.ImportRowRequest row : rows) {
            RowPreparation preparation = rowCommitService.preparePreview(businessType, row, keysInBatch);
            ValidationResult validation = preparation.validation();
            if (validation.success()) {
                successCount++;
            } else {
                failureCount++;
            }
            repository.insertRow(
                    taskId,
                    row.rowNo(),
                    toJson(row.rawData()),
                    validation.success() ? toJson(row.rawData()) : null,
                    validation.success() ? "SUCCESS" : "FAILURE",
                    validation.errorCode(),
                    validation.errorMessage(),
                    businessType,
                    preparation.businessUniqueKey());
        }
        attachmentService.bindImportFile(request.originalFileId(), taskId, operatorId);
        repository.updatePreviewCounts(taskId, successCount, failureCount);
        return repository.findTask(taskId, true).orElseThrow(() -> new BusinessException(ErrorCode.DATA_001));
    }

    @Transactional
    public ImportTaskResponse commit(long id, long operatorId) {
        ImportTaskResponse task = repository.findTask(id, true)
                .orElseThrow(() -> new BusinessException(ErrorCode.DATA_001));
        if ("COMMITTED".equals(task.status())) {
            return task;
        }
        if (!"PREVIEWED".equals(task.status())) {
            return task;
        }
        if (!repository.beginCommit(id)) {
            return repository.findTask(id, true).orElseThrow(() -> new BusinessException(ErrorCode.DATA_001));
        }
        int successCount = 0;
        int unpublishedCount = 0;
        int failureCount = 0;
        Set<String> keysInBatch = new HashSet<>();
        for (ImportRowResponse row : task.rows()) {
            RowCommitResult result;
            if (row.finalStatus() == null) {
                try {
                    result = rowCommitService.commitRow(task.businessType(), row, keysInBatch, operatorId);
                } catch (RuntimeException ex) {
                    rowCommitService.markFailure(row.id(), task.businessType(), row.businessUniqueKey(),
                            "IMPORT_DB_ERROR", "数据库写入失败");
                    result = new RowCommitResult("FAILURE");
                }
            } else {
                result = new RowCommitResult(row.finalStatus());
            }
            if ("SUCCESS".equals(result.finalStatus())) {
                successCount++;
            } else if ("UNPUBLISHED".equals(result.finalStatus())) {
                unpublishedCount++;
            } else {
                failureCount++;
            }
        }
        repository.markCommitted(id, successCount, unpublishedCount, failureCount);
        return repository.findTask(id, true).orElseThrow(() -> new BusinessException(ErrorCode.DATA_001));
    }

    public PageResponse<ImportTaskResponse> list(int pageNo, int pageSize) {
        return repository.listTasks(pageNo, Math.min(Math.max(pageSize, 1), 200));
    }

    public String exportFailures(long id) {
        ImportTaskResponse task = repository.findTask(id, true)
                .orElseThrow(() -> new BusinessException(ErrorCode.DATA_001));
        StringBuilder csv = new StringBuilder("rowNo,errorCode,errorMessage,rawData\n");
        for (ImportRowResponse row : task.rows()) {
            String code = row.finalErrorCode() == null ? row.previewErrorCode() : row.finalErrorCode();
            if (StringUtils.hasText(code)) {
                String message = row.finalErrorMessage() == null ? row.previewErrorMessage() : row.finalErrorMessage();
                csv.append(row.rowNo()).append(',')
                        .append(escape(code)).append(',')
                        .append(escape(message)).append(',')
                        .append(escape(row.rawDataJson())).append('\n');
            }
        }
        return csv.toString();
    }

    private String normalizeBusinessType(String businessType) {
        if (!StringUtils.hasText(businessType)) {
            throw new BusinessException(ErrorCode.SYS_002);
        }
        String normalized = businessType.trim().toUpperCase();
        ImportBusinessType parsed;
        try {
            parsed = ImportBusinessType.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.SYS_002);
        }
        if (!EnumSet.of(ImportBusinessType.ROUTE, ImportBusinessType.TIRE, ImportBusinessType.EXPENSE, ImportBusinessType.SALARY).contains(parsed)) {
            throw new BusinessException(ErrorCode.SYS_002);
        }
        return normalized;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.SYS_003);
        }
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private String nextBatchNo() {
        return "IMP" + LocalDateTime.now().format(BATCH_TIME) + UUID.randomUUID().toString().substring(0, 6);
    }

    public enum ImportBusinessType {
        ROUTE,
        TIRE,
        EXPENSE,
        SALARY
    }

    public record ValidationResult(boolean success, String errorCode, String errorMessage) {
    }

    public record RowPreparation(String businessUniqueKey, ValidationResult validation) {
    }

    public record RowCommitResult(String finalStatus) {
    }
}
