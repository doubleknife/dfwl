package com.dfwl.fleet.approval.v3;

import com.dfwl.fleet.common.error.BusinessException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** Fixed DAILY template used only to fill the editable application draft. */
@Service
public class DailyExpenseWorkbook {
    private static final List<String> HEADERS = List.of(
            "业务日期", "公司车牌", "车挂号", "司机手机号", "金额", "明细", "票据号", "发票号", "备注", "站点名称");
    private final JdbcTemplate jdbc;
    public DailyExpenseWorkbook(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record PreviewRow(int rowNo, String businessDate, String plateNo, String trailerNo, String driverPhone,
                             String amount, String description, String receiptNo, String invoiceNo,
                             String remark, String stationName, List<String> errors) {}
    public record Preview(List<PreviewRow> rows, BigDecimal total, int errorCount) {}

    public byte[] template() {
        try (Workbook book = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = book.createSheet("日常费用");
            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.size(); i++) {
                header.createCell(i).setCellValue(HEADERS.get(i));
                sheet.setColumnWidth(i, i < 4 ? 5400 : 7200);
            }
            book.write(out);
            return out.toByteArray();
        } catch (IOException ex) { throw new IllegalStateException("费用模板生成失败", ex); }
    }

    public Preview preview(MultipartFile file) {
        List<PreviewRow> rows = new ArrayList<>();
        BigDecimal sum = BigDecimal.ZERO;
        int errors = 0;
        try (Workbook book = new XSSFWorkbook(file.getInputStream())) {
            if (book.getNumberOfSheets() != 1) throw invalid("只接受系统下载的单工作表模板");
            Sheet sheet = book.getSheetAt(0);
            Row header = sheet.getRow(0);
            if (header == null || header.getLastCellNum() != HEADERS.size())
                throw invalid("费用 Excel 表头与系统模板不一致");
            DataFormatter formatter = new DataFormatter();
            for (int i = 0; i < HEADERS.size(); i++)
                if (!HEADERS.get(i).equals(formatter.formatCellValue(header.getCell(i))))
                    throw invalid("费用 Excel 表头与系统模板不一致");
            if (sheet.getLastRowNum() > 500) throw invalid("单批最多 500 条费用");
            for (int index = 1; index <= sheet.getLastRowNum(); index++) {
                Row row = sheet.getRow(index);
                if (row == null || empty(row, formatter)) continue;
                String[] values = new String[HEADERS.size()];
                for (int i = 0; i < values.length; i++)
                    values[i] = formatter.formatCellValue(row.getCell(i)).trim();
                if (row.getCell(0) != null && row.getCell(0).getCellType() == CellType.NUMERIC
                        && DateUtil.isCellDateFormatted(row.getCell(0)))
                    values[0] = row.getCell(0).getLocalDateTimeCellValue().toLocalDate().toString();
                List<String> problems = new ArrayList<>();
                try { LocalDate.parse(values[0]); }
                catch (RuntimeException ex) { problems.add("业务日期请填写 YYYY-MM-DD"); }
                if (values[1].isBlank()) problems.add("公司车牌不能为空");
                if (values[3].isBlank()) problems.add("司机手机号不能为空");
                if (values[2].length() > 64) problems.add("车挂号不能超过 64 字");
                try {
                    BigDecimal amount = new BigDecimal(values[4]);
                    if (amount.signum() <= 0 || amount.scale() > 2) problems.add("金额必须大于 0 且最多两位小数");
                    else sum = sum.add(amount);
                } catch (RuntimeException ex) { problems.add("金额格式不正确"); }
                rows.add(new PreviewRow(index + 1, values[0], values[1], values[2], values[3], values[4],
                        values[5], values[6], values[7], values[8], values[9], problems));
            }
        } catch (IOException | RuntimeException ex) {
            if (ex instanceof BusinessException business) throw business;
            throw invalid("文件不是有效的系统固定费用 Excel");
        }
        if (rows.isEmpty()) throw invalid("Excel 没有费用明细");
        Map<String, Long> vehicles = lookupAll("vehicle", "plate_no",
                rows.stream().map(PreviewRow::plateNo).collect(Collectors.toSet()));
        Map<String, Long> drivers = lookupAll("driver", "phone",
                rows.stream().map(PreviewRow::driverPhone).collect(Collectors.toSet()));
        Map<String, Long> trailers = lookupAll("trailer", "trailer_no",
                rows.stream().map(PreviewRow::trailerNo).collect(Collectors.toSet()), false);
        List<PreviewRow> checked = new ArrayList<>();
        for (PreviewRow row : rows) {
            List<String> problems = new ArrayList<>(row.errors());
            if (!row.plateNo().isBlank() && !vehicles.containsKey(row.plateNo()))
                problems.add("公司车牌不存在或已停用");
            if (!row.driverPhone().isBlank() && !drivers.containsKey(row.driverPhone()))
                problems.add("司机不存在或已停用");
            if (!row.trailerNo().isBlank() && !trailers.containsKey(row.trailerNo()))
                problems.add("车挂号不存在");
            if (!problems.isEmpty()) errors++;
            checked.add(new PreviewRow(row.rowNo(), row.businessDate(), row.plateNo(), row.trailerNo(), row.driverPhone(),
                    row.amount(), row.description(), row.receiptNo(), row.invoiceNo(), row.remark(),
                    row.stationName(), problems));
        }
        return new Preview(checked, sum, errors);
    }

    private Map<String, Long> lookupAll(String table, String column, Set<String> values) {
        return lookupAll(table, column, values, true);
    }
    private Map<String, Long> lookupAll(String table, String column, Set<String> values, boolean activeOnly) {
        List<String> keys = values.stream().filter(Objects::nonNull).filter(value -> !value.isBlank()).toList();
        if (keys.isEmpty()) return Map.of();
        String placeholders = String.join(",", java.util.Collections.nCopies(keys.size(), "?"));
        List<Map<String, Object>> found = jdbc.queryForList("SELECT id," + column + " businessKey FROM "
                + table + " WHERE " + column + " IN (" + placeholders
                + ") AND deleted_at IS NULL" + (activeOnly ? " AND status=1" : ""), keys.toArray());
        Map<String, Long> result = new LinkedHashMap<>();
        for (Map<String, Object> row : found)
            result.put((String) row.get("businessKey"), ((Number) row.get("id")).longValue());
        return result;
    }
    private boolean empty(Row row, DataFormatter formatter) {
        for (int i = 0; i < HEADERS.size(); i++)
            if (!formatter.formatCellValue(row.getCell(i)).trim().isBlank()) return false;
        return true;
    }
    private BusinessException invalid(String message) { return new BusinessException("SYS_002", message); }
}
