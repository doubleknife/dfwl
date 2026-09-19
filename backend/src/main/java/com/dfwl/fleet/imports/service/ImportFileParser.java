package com.dfwl.fleet.imports.service;

import com.dfwl.fleet.attachment.domain.FileAttachment;
import com.dfwl.fleet.attachment.repository.FileAttachmentRepository;
import com.dfwl.fleet.attachment.storage.StorageService;
import com.dfwl.fleet.common.error.BusinessException;
import com.dfwl.fleet.common.error.ErrorCode;
import com.dfwl.fleet.imports.dto.request.ImportPreviewRequest;
import com.dfwl.fleet.imports.repository.ImportRepository;
import com.dfwl.fleet.imports.repository.ImportRepository.FieldMappingRecord;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ImportFileParser {

    private static final int MAX_ROWS = 10_000;

    private final FileAttachmentRepository attachmentRepository;
    private final StorageService storageService;
    private final ImportRepository importRepository;

    public ImportFileParser(FileAttachmentRepository attachmentRepository, StorageService storageService,
                            ImportRepository importRepository) {
        this.attachmentRepository = attachmentRepository;
        this.storageService = storageService;
        this.importRepository = importRepository;
    }

    public List<ImportPreviewRequest.ImportRowRequest> parse(String businessType, Long templateId,
                                                             long originalFileId, long operatorId) {
        FileAttachment attachment = attachmentRepository.find(originalFileId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ATTACHMENT_001));
        validateAttachment(attachment, operatorId);
        List<FieldMappingRecord> mappings = loadMappings(businessType, templateId);
        String filename = attachment.originalFilename() == null ? "" : attachment.originalFilename().toLowerCase(Locale.ROOT);
        Resource resource = storageService.load(attachment.storageKey());
        try (InputStream input = resource.getInputStream()) {
            if (filename.endsWith(".xlsx") || filename.endsWith(".xls")) {
                return parseWorkbook(input, mappings);
            }
            if (filename.endsWith(".csv")) {
                return parseCsv(input, mappings);
            }
            throw new BusinessException(ErrorCode.SYS_002.name(), "不支持的导入文件格式");
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.SYS_002.name(), "导入文件损坏或无法解析");
        }
    }

    private void validateAttachment(FileAttachment attachment, long operatorId) {
        if (!"IMPORT_FILE".equals(attachment.purpose())) {
            throw new BusinessException(ErrorCode.SYS_002.name(), "导入文件 purpose 必须为 IMPORT_FILE");
        }
        boolean ownerAllowed = "IMPORT".equals(attachment.ownerType()) && attachment.ownerId() == 0
                && attachment.uploadedBy() == operatorId;
        if (!ownerAllowed && attachment.uploadedBy() != operatorId) {
            throw new BusinessException(ErrorCode.AUTH_003);
        }
    }

    private List<FieldMappingRecord> loadMappings(String businessType, Long templateId) {
        if (templateId == null) {
            return List.of();
        }
        importRepository.findTemplate(templateId, businessType)
                .orElseThrow(() -> new BusinessException(ErrorCode.SYS_002.name(), "导入模板不存在或已停用"));
        List<FieldMappingRecord> mappings = importRepository.fieldMappings(templateId);
        if (mappings.isEmpty()) {
            throw new BusinessException(ErrorCode.SYS_002.name(), "导入模板字段映射不能为空");
        }
        return mappings;
    }

    private List<ImportPreviewRequest.ImportRowRequest> parseWorkbook(InputStream input, List<FieldMappingRecord> mappings)
            throws IOException {
        try (Workbook workbook = WorkbookFactory.create(input)) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new BusinessException(ErrorCode.SYS_002.name(), "导入文件为空");
            }
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) {
                throw new BusinessException(ErrorCode.SYS_002.name(), "导入文件缺少表头");
            }
            DataFormatter formatter = new DataFormatter(Locale.CHINA);
            HeaderMapping headerMapping = headerMapping(headers(headerRow, formatter), mappings);
            List<ImportPreviewRequest.ImportRowRequest> rows = new ArrayList<>();
            for (int i = sheet.getFirstRowNum() + 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || rowIsBlank(row, formatter)) {
                    continue;
                }
                rows.add(new ImportPreviewRequest.ImportRowRequest(i + 1, values(row, headerMapping, formatter), null));
                ensureRowLimit(rows);
            }
            ensureHasRows(rows);
            return rows;
        }
    }

    private List<ImportPreviewRequest.ImportRowRequest> parseCsv(InputStream input, List<FieldMappingRecord> mappings)
            throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setTrim(false)
                     .setIgnoreEmptyLines(true)
                     .build()
                     .parse(reader)) {
            HeaderMapping headerMapping = headerMapping(parser.getHeaderNames(), mappings);
            List<ImportPreviewRequest.ImportRowRequest> rows = new ArrayList<>();
            for (CSVRecord record : parser) {
                Map<String, Object> value = new LinkedHashMap<>();
                for (ColumnMapping mapping : headerMapping.columns()) {
                    String cell = mapping.index() < record.size() ? record.get(mapping.index()) : null;
                    putNested(value, mapping.targetField(), normalizeText(cell));
                }
                rows.add(new ImportPreviewRequest.ImportRowRequest((int) record.getRecordNumber() + 1, value, null));
                ensureRowLimit(rows);
            }
            ensureHasRows(rows);
            return rows;
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.SYS_002.name(), "导入文件缺少表头或表头重复");
        }
    }

    private List<String> headers(Row headerRow, DataFormatter formatter) {
        List<String> headers = new ArrayList<>();
        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            headers.add(normalizeText(formatter.formatCellValue(headerRow.getCell(i))));
        }
        return headers;
    }

    private HeaderMapping headerMapping(List<String> headers, List<FieldMappingRecord> mappings) {
        if (headers == null || headers.isEmpty() || headers.stream().noneMatch(StringUtils::hasText)) {
            throw new BusinessException(ErrorCode.SYS_002.name(), "导入文件缺少表头");
        }
        Map<String, Integer> headerIndexes = new HashMap<>();
        Set<String> duplicates = new HashSet<>();
        for (int i = 0; i < headers.size(); i++) {
            String header = normalizeHeader(headers.get(i));
            if (!StringUtils.hasText(header)) {
                continue;
            }
            if (headerIndexes.putIfAbsent(header, i) != null) {
                duplicates.add(header);
            }
        }
        if (!duplicates.isEmpty()) {
            throw new BusinessException(ErrorCode.SYS_002.name(), "导入文件存在重复表头");
        }

        List<ColumnMapping> columns = new ArrayList<>();
        if (mappings.isEmpty()) {
            for (int i = 0; i < headers.size(); i++) {
                String header = normalizeText(headers.get(i));
                if (StringUtils.hasText(header)) {
                    columns.add(new ColumnMapping(i, header));
                }
            }
            return new HeaderMapping(columns);
        }

        for (FieldMappingRecord mapping : mappings) {
            Integer index = headerIndexes.get(normalizeHeader(mapping.sourceColumn()));
            if (index == null) {
                if (mapping.required()) {
                    throw new BusinessException(ErrorCode.SYS_002.name(), "导入文件缺少必填列: " + mapping.sourceColumn());
                }
                continue;
            }
            columns.add(new ColumnMapping(index, mapping.targetField()));
        }
        if (columns.isEmpty()) {
            throw new BusinessException(ErrorCode.SYS_002.name(), "导入文件表头与模板不匹配");
        }
        return new HeaderMapping(columns);
    }

    private boolean rowIsBlank(Row row, DataFormatter formatter) {
        for (int i = row.getFirstCellNum(); i < row.getLastCellNum(); i++) {
            if (StringUtils.hasText(formatter.formatCellValue(row.getCell(i)))) {
                return false;
            }
        }
        return true;
    }

    private Map<String, Object> values(Row row, HeaderMapping headerMapping, DataFormatter formatter) {
        Map<String, Object> value = new LinkedHashMap<>();
        for (ColumnMapping mapping : headerMapping.columns()) {
            Cell cell = row.getCell(mapping.index());
            putNested(value, mapping.targetField(), normalizeCell(cell, mapping.targetField(), formatter));
        }
        return value;
    }

    private Object normalizeCell(Cell cell, String targetField, DataFormatter formatter) {
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            LocalDateTime value = cell.getDateCellValue().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
            if (targetField.toLowerCase(Locale.ROOT).contains("time")) {
                return value.toString();
            }
            return value.toLocalDate().toString();
        }
        if (cell.getCellType() == CellType.NUMERIC && numericField(targetField)) {
            return BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
        }
        return normalizeText(formatter.formatCellValue(cell));
    }

    private boolean numericField(String targetField) {
        String field = targetField.toLowerCase(Locale.ROOT);
        return field.endsWith("id")
                || field.contains("amount")
                || field.contains("weight")
                || field.contains("price")
                || field.contains("fee")
                || field.contains("quantity")
                || field.contains("mileage")
                || field.contains("percent")
                || field.contains("points");
    }

    @SuppressWarnings("unchecked")
    private void putNested(Map<String, Object> target, String path, Object value) {
        String[] parts = path.split("\\.");
        Map<String, Object> current = target;
        for (int i = 0; i < parts.length - 1; i++) {
            current = (Map<String, Object>) current.computeIfAbsent(parts[i], ignored -> new LinkedHashMap<>());
        }
        current.put(parts[parts.length - 1], value);
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String text = value.strip();
        return text.isEmpty() ? null : text;
    }

    private String normalizeHeader(String value) {
        String text = normalizeText(value);
        return text == null ? null : text.toLowerCase(Locale.ROOT);
    }

    private void ensureHasRows(List<ImportPreviewRequest.ImportRowRequest> rows) {
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.SYS_002.name(), "导入文件没有数据行");
        }
    }

    private void ensureRowLimit(List<ImportPreviewRequest.ImportRowRequest> rows) {
        if (rows.size() > MAX_ROWS) {
            throw new BusinessException(ErrorCode.SYS_002.name(), "导入文件超过最大行数限制");
        }
    }

    private record HeaderMapping(List<ColumnMapping> columns) {
    }

    private record ColumnMapping(int index, String targetField) {
    }
}
