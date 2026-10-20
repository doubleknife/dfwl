package com.dfwl.fleet.master.service;

import com.dfwl.fleet.common.error.BusinessException;
import com.dfwl.fleet.master.dto.request.DriverRequest;
import com.dfwl.fleet.master.dto.request.VehicleRequest;
import com.dfwl.fleet.master.validation.MasterDataInputValidator;
import com.dfwl.fleet.master.validation.MasterDataInputValidator.Issue;
import com.dfwl.fleet.system.service.AuditWriteService;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Stateless preview and transactional confirmation of the fixed ten-column initial-data template. */
@Service
public class DriverVehicleInitialImportService {
    public static final List<String> HEADERS = List.of("车牌号", "驾驶员姓名", "电话", "行车证号", "驾驶证号", "身份证号",
            "车辆保险状态(是否齐全)", "最大载重（吨）", "达标吨数（吨）", "能源类型");
    private static final int MAX_ROWS = 500;
    private static final String INITIAL_PASSWORD = "123456";

    public record PreviewRow(int rowNo, String plateNo, String driverName, String phone,
                             List<Issue> errors) { }
    public record Preview(int total, int passed, int failed, List<PreviewRow> rows) { }
    public record Confirmation(int imported) { }
    private record Entry(int rowNo, String[] values, DriverRequest driver, VehicleRequest vehicle,
                         List<Issue> parseErrors) { }
    private record Checked(Preview preview, List<Entry> entries) { }

    private final MasterDataInputValidator validator;
    private final MasterDataService masterData;
    private final AuditWriteService audit;

    public DriverVehicleInitialImportService(MasterDataInputValidator validator, MasterDataService masterData,
                                             AuditWriteService audit) {
        this.validator = validator;
        this.masterData = masterData;
        this.audit = audit;
    }

    public byte[] template() {
        try (Workbook book = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = book.createSheet("自有司机车辆");
            Row header = sheet.createRow(0);
            var headStyle = book.createCellStyle();
            var font = book.createFont(); font.setBold(true); headStyle.setFont(font);
            var textStyle = book.createCellStyle(); textStyle.setDataFormat(book.createDataFormat().getFormat("@"));
            var tonsStyle = book.createCellStyle(); tonsStyle.setDataFormat(book.createDataFormat().getFormat("0.000"));
            for (int index = 0; index < HEADERS.size(); index++) {
                header.createCell(index).setCellValue(HEADERS.get(index));
                header.getCell(index).setCellStyle(headStyle);
                sheet.setColumnWidth(index, index == 6 ? 8500 : 5600);
                sheet.setDefaultColumnStyle(index, index == 7 || index == 8 ? tonsStyle : textStyle);
            }
            addList(sheet, 6, "是", "否");
            addList(sheet, 9, "电", "气");
            Sheet instructions = book.createSheet("填写说明");
            String[] notes = {"只能导入未录入的自有司机与公司车辆；任一行错误则整批不得确认。",
                    "请保留第一工作表的十列表头和顺序，不填写序号、年龄或其他辅助列。",
                    "电话、行车证号、驾驶证号、身份证号均按文本填写，避免丢失前导零。",
                    "车辆保险状态仅填是或否；能源类型仅填电或气。",
                    "最大载重与达标吨数均大于 0，最多三位小数，达标吨数不得超过最大载重。",
                    "系统统一创建启用的自有司机、公司车及司机账号，并记录正式绑定历史。"};
            for (int i = 0; i < notes.length; i++) instructions.createRow(i).createCell(0).setCellValue(notes[i]);
            instructions.setColumnWidth(0, 29000);
            book.write(output);
            return output.toByteArray();
        } catch (IOException ex) { throw new IllegalStateException("司机车辆模板生成失败", ex); }
    }

    private void addList(Sheet sheet, int column, String... values) {
        var helper = sheet.getDataValidationHelper();
        DataValidationConstraint constraint = helper.createExplicitListConstraint(values);
        var validation = helper.createValidation(constraint, new CellRangeAddressList(1, MAX_ROWS, column, column));
        validation.setShowErrorBox(true);
        sheet.addValidationData(validation);
    }

    public Preview preview(MultipartFile file) { return check(file).preview(); }

    @Transactional
    public Confirmation confirm(MultipartFile file, long actorId) {
        Checked checked = check(file); // Always parse and query live database again.
        if (checked.preview().failed() > 0) {
            PreviewRow first = checked.preview().rows().stream().filter(row -> !row.errors().isEmpty()).findFirst().orElseThrow();
            Issue issue = first.errors().get(0);
            throw new BusinessException("DATA_002", "第 " + first.rowNo() + " 行" + issue.field() + "：" + issue.reason()
                    + "；整批未导入，请修正后重新预览");
        }
        for (Entry entry : checked.entries()) {
            try {
                long driverId = masterData.createDriverWithAccount(entry.driver(), actorId).id();
                long vehicleId = masterData.createVehicle(entry.vehicle()).id();
                masterData.bindDriverVehicle(driverId, vehicleId, actorId);
                audit.write("MASTER", "DRIVER_VEHICLE_IMPORT", driverId, "INITIAL_IMPORT", null,
                        Map.of("vehicleId", vehicleId, "excelRow", entry.rowNo()), actorId, null);
            } catch (BusinessException ex) {
                throw new BusinessException(ex.code(), "第 " + entry.rowNo() + " 行：" + ex.getMessage() + "；整批已回滚");
            }
        }
        return new Confirmation(checked.entries().size());
    }

    private Checked check(MultipartFile file) {
        List<Entry> entries = parse(file);
        var existing = validator.existing(entries.stream().map(Entry::driver).toList(),
                entries.stream().map(Entry::vehicle).toList());
        List<PreviewRow> rows = new ArrayList<>();
        Map<String, Integer> seen = new HashMap<>();
        int passed = 0;
        for (Entry entry : entries) {
            List<Issue> errors = new ArrayList<>(entry.parseErrors());
            errors.addAll(validator.driver(entry.driver(), existing, true));
            errors.addAll(validator.vehicle(entry.vehicle(), existing));
            String[] fields = {"车牌号", "电话", "行车证号", "驾驶证号", "身份证号"};
            int[] columns = {0, 2, 3, 4, 5};
            for (int i = 0; i < fields.length; i++) {
                String value = entry.values()[columns[i]];
                if (value.isBlank()) continue;
                String key = fields[i] + ":" + (columns[i] == 5 ? value.toUpperCase() : value);
                Integer previous = seen.putIfAbsent(key, entry.rowNo());
                if (previous != null) errors.add(new Issue(fields[i], "与 Excel 第 " + previous + " 行重复"));
            }
            if (errors.isEmpty()) passed++;
            rows.add(new PreviewRow(entry.rowNo(), entry.values()[0], entry.values()[1], mask(entry.values()[2]),
                    List.copyOf(errors)));
        }
        return new Checked(new Preview(rows.size(), passed, rows.size() - passed, rows), entries);
    }

    private List<Entry> parse(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > 5_000_000L
                || file.getOriginalFilename() == null || !file.getOriginalFilename().toLowerCase().endsWith(".xlsx"))
            throw invalid("请上传不超过 5 MB 的系统 .xlsx 模板");
        List<Entry> entries = new ArrayList<>();
        try (Workbook book = new XSSFWorkbook(new ByteArrayInputStream(file.getBytes()))) {
            Sheet sheet = book.getSheetAt(0);
            DataFormatter format = new DataFormatter();
            Row header = sheet.getRow(0);
            if (header == null || header.getLastCellNum() != HEADERS.size()) throw invalid("请使用系统模板");
            for (int i = 0; i < HEADERS.size(); i++)
                if (!HEADERS.get(i).equals(format.formatCellValue(header.getCell(i))))
                    throw invalid("请使用系统模板");
            if (sheet.getLastRowNum() > MAX_ROWS) throw invalid("每批最多 500 行，请拆分后重新上传");
            for (int index = 1; index <= sheet.getLastRowNum(); index++) {
                Row row = sheet.getRow(index);
                if (row == null) continue;
                String[] values = new String[HEADERS.size()];
                List<Issue> parseErrors = new ArrayList<>();
                boolean nonEmpty = false;
                for (int column = 0; column < values.length; column++) {
                    Cell cell = row.getCell(column);
                    if (cell != null && cell.getCellType() == CellType.FORMULA)
                        parseErrors.add(new Issue(HEADERS.get(column), "不接受公式，请填写固定值"));
                    values[column] = cell == null || cell.getCellType() == CellType.FORMULA ? ""
                            : (column == 7 || column == 8) && cell.getCellType() == CellType.NUMERIC
                                    ? BigDecimal.valueOf(cell.getNumericCellValue()).toPlainString()
                                    : format.formatCellValue(cell).trim();
                    nonEmpty |= !values[column].isBlank();
                }
                if (!nonEmpty && parseErrors.isEmpty()) continue;
                if (row.getLastCellNum() > HEADERS.size())
                    parseErrors.add(new Issue("表格", "存在十列以外的内容"));
                Boolean insurance = "是".equals(values[6]) ? Boolean.TRUE
                        : "否".equals(values[6]) ? Boolean.FALSE : null;
                if (insurance == null && !values[6].isBlank())
                    parseErrors.add(new Issue(HEADERS.get(6), "只允许是或否"));
                String energy = "电".equals(values[9]) ? "ELECTRIC" : "气".equals(values[9]) ? "GAS" : null;
                if (!values[9].isBlank() && energy == null)
                    parseErrors.add(new Issue(HEADERS.get(9), "只允许电或气"));
                BigDecimal maxLoad = tons(values[7], HEADERS.get(7), parseErrors);
                BigDecimal standard = tons(values[8], HEADERS.get(8), parseErrors);
                var driver = new DriverRequest("INTERNAL", values[1], values[2], values[4], values[5], 1, INITIAL_PASSWORD);
                var vehicle = new VehicleRequest(values[0], values[3], insurance, energy, maxLoad,
                        "FIXED", null, standard, 1);
                entries.add(new Entry(index + 1, values, driver, vehicle, parseErrors));
            }
        } catch (BusinessException ex) { throw ex; }
        catch (IOException | org.apache.poi.ooxml.POIXMLException | IllegalArgumentException ex) {
            throw invalid("文件不是有效的系统十列 .xlsx 模板");
        }
        if (entries.isEmpty()) throw invalid("Excel 没有可导入的数据行");
        return entries;
    }

    private BigDecimal tons(String value, String field, List<Issue> errors) {
        if (value.isBlank()) return null;
        try { return new BigDecimal(value); }
        catch (NumberFormatException ex) { errors.add(new Issue(field, "请填写有效数字")); return null; }
    }
    private String mask(String phone) {
        return phone.length() == 11 ? phone.substring(0, 3) + "****" + phone.substring(7) : "";
    }
    private BusinessException invalid(String message) { return new BusinessException("SYS_002", message); }
}
