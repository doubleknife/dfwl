package com.dfwl.fleet.finance.v3;

import com.dfwl.fleet.attachment.storage.StorageService;
import com.dfwl.fleet.attachment.storage.StoredFile;
import com.dfwl.fleet.common.error.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Generic four-column verification and the sole charging-to-ExpenseEntry write path. */
@Service
public class ChargingImportService {
    public record Header(int index, String label) {}
    public record CompanyCommand(String companyName, String sourceSystem, String orderNoType,
                                 Integer headerRowNo, List<Header> headers, Map<String, Integer> mapping) {}
    public record Order(int rowNo, String sheetName, String orderNo, String plateNo,
                        LocalDateTime chargedAt, BigDecimal amount) {}
    public record Confirmation(long companyId, String sheetName, int headerRowNo,
                               List<Header> headers, Map<String, Integer> mapping,
                               List<Order> orders, int count, int zeroCount, BigDecimal totalAmount) {}

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final StorageService storage;

    public ChargingImportService(JdbcTemplate jdbc, ObjectMapper json, StorageService storage) {
        this.jdbc = jdbc; this.json = json; this.storage = storage;
    }

    public List<Map<String, Object>> companies() {
        return jdbc.queryForList("""
                SELECT id,company_name,source_system,order_no_type,header_row_no,headers_json,mapping_json,
                  enabled,updated_at FROM charging_company ORDER BY company_name
                """);
    }

    @Transactional
    public Map<String, Object> saveCompany(Long id, CompanyCommand command, long actor) {
        if (command == null || blank(command.companyName()) || blank(command.sourceSystem()))
            throw invalid("请填写公司名称和来源系统标识");
        String name = command.companyName().trim(), source = command.sourceSystem().trim();
        if (!source.matches("[A-Za-z0-9_-]{2,64}")) throw invalid("来源系统标识只允许字母、数字、下划线和横线");
        String orderType = blank(command.orderNoType()) ? "ORDER_NO" : command.orderNoType().trim();
        if (!orderType.matches("[A-Za-z0-9_-]{2,64}")) throw invalid("订单编号类型不合法");
        if (command.headers() != null || command.mapping() != null) validateMapping(command);
        String headers = toJson(command.headers()), mapping = toJson(command.mapping());
        if (id == null) {
            if (command.headers() == null || command.mapping() == null)
                throw invalid("新增公司时请上传样本并完成四字段映射");
            long created = insert("""
                    INSERT INTO charging_company(company_name,source_system,order_no_type,header_row_no,
                      headers_json,mapping_json,updated_by) VALUES(?,?,?,?,?,?,?)
                    """, name, source, orderType, command.headerRowNo(), headers, mapping, actor);
            return company(created);
        }
        Map<String, Object> existing = company(id);
        if (!source.equals(existing.get("source_system"))) throw invalid("来源系统标识入账后不可修改");
        if (command.headers() == null || command.mapping() == null) {
            jdbc.update("UPDATE charging_company SET company_name=?,updated_by=? WHERE id=?", name, actor, id);
        } else {
            jdbc.update("""
                    UPDATE charging_company SET company_name=?,order_no_type=?,header_row_no=?,
                      headers_json=?,mapping_json=?,updated_by=? WHERE id=?
                    """, name, orderType, command.headerRowNo(), headers, mapping, actor, id);
        }
        return company(id);
    }

    public Map<String, Object> company(long id) {
        return jdbc.queryForList("""
                SELECT id,company_name,source_system,order_no_type,header_row_no,headers_json,mapping_json,
                  enabled,updated_at FROM charging_company WHERE id=?
                """, id).stream().findFirst().orElseThrow(() -> invalid("充电公司不存在"));
    }

    public Map<String, Object> batches(int page, int size) {
        if (page < 1 || size < 1 || size > 100) throw invalid("导入历史分页参数不合法");
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM charging_import_batch", Long.class);
        List<Map<String, Object>> items = jdbc.queryForList("""
                SELECT b.id,c.company_name,b.original_filename,b.source_system,b.file_hash,b.created_at,
                  b.confirmed_count,b.imported_count,b.zero_count,b.duplicate_count,b.confirmed_amount,b.imported_amount,
                  u.phone operator_phone
                FROM charging_import_batch b JOIN charging_company c ON c.id=b.company_id
                LEFT JOIN sys_user u ON u.id=b.created_by ORDER BY b.id DESC LIMIT ? OFFSET ?
                """, size, (page - 1) * size);
        return Map.of("items", items, "total", total, "page", page, "size", size);
    }

    public Map<String, Object> batch(long id) {
        Map<String, Object> batch = jdbc.queryForList("""
                SELECT b.*,c.company_name FROM charging_import_batch b
                JOIN charging_company c ON c.id=b.company_id WHERE b.id=?
                """, id).stream().findFirst().orElseThrow(() -> invalid("导入批次不存在"));
        return Map.of("batch", batch, "orders", jdbc.queryForList("""
                SELECT o.id,o.original_order_no,o.sheet_name,o.row_no,o.plate_no,o.charged_at,o.amount,
                  CASE WHEN o.expense_id IS NULL THEN '零元不入账' ELSE '已入账' END processing_status,
                  e.expense_no,t.task_no FROM charging_source_order o
                LEFT JOIN expense_entry e ON e.id=o.expense_id
                LEFT JOIN transport_task t ON t.id=e.current_task_id
                WHERE o.batch_id=? ORDER BY o.row_no
                """, id));
    }

    public Map<String, Object> sourceOrder(long expenseId) {
        return jdbc.queryForList("""
                SELECT o.original_order_no,o.order_no_type,o.sheet_name,o.row_no,o.plate_no,o.charged_at,
                  o.amount,b.id batch_id,b.original_filename,b.file_hash,b.source_system,c.company_name,
                  b.mapping_json,b.created_at
                FROM charging_source_order o JOIN charging_import_batch b ON b.id=o.batch_id
                JOIN charging_company c ON c.id=b.company_id WHERE o.expense_id=?
                """, expenseId).stream().findFirst().orElseThrow(() -> invalid("这笔费用没有充电原始订单"));
    }

    public Resource originalFile(long batchId) {
        String key = jdbc.queryForList("SELECT original_storage_key FROM charging_import_batch WHERE id=?", batchId)
                .stream().findFirst().map(row -> String.valueOf(row.get("original_storage_key")))
                .orElseThrow(() -> invalid("原始文件不存在"));
        return storage.load(key);
    }

    @Transactional
    public Map<String, Object> confirm(Confirmation input, MultipartFile file, long actor) {
        if (input == null || file == null || file.isEmpty() || file.getSize() > 10_000_000)
            throw invalid("请选择不超过 10 MB 的原始 Excel 文件");
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        if (!(filename.toLowerCase().endsWith(".xls") || filename.toLowerCase().endsWith(".xlsx")))
            throw invalid("只接收 .xls 或 .xlsx 原始文件");
        // Lock the source to serialize duplicate imports, including concurrent files for one company.
        Map<String, Object> company = jdbc.queryForList("SELECT * FROM charging_company WHERE id=? FOR UPDATE",
                input.companyId()).stream().findFirst().orElseThrow(() -> invalid("充电公司不存在"));
        if (((Number) company.get("enabled")).intValue() != 1) throw invalid("充电公司已停用");
        if (input.orders() == null || input.orders().isEmpty() || input.orders().size() > 20_000
                || input.count() != input.orders().size() || blank(input.sheetName()))
            throw invalid("预览订单数与确认明细不一致");
        if (input.headerRowNo() != ((Number) company.get("header_row_no")).intValue()
                || !sameJson(input.mapping(), company.get("mapping_json")))
            throw invalid("公司字段映射已经变化，请重新解析和预览");
        validateMapping(new CompanyCommand("x", "SOURCE", "ORDER_NO", input.headerRowNo(),
                input.headers(), input.mapping()));
        for (Order order : input.orders()) validateOrder(order, input.sheetName(), input.headerRowNo());
        byte[] bytes;
        try { bytes = file.getBytes(); } catch (IOException ex) { throw invalid("原始 Excel 读取失败"); }
        verifyOriginal(bytes, input);
        BigDecimal total = BigDecimal.ZERO;
        Map<String, BigDecimal> inFile = new HashMap<>();
        List<Order> uniqueOrders = new ArrayList<>();
        for (Order order : input.orders()) {
            validateOrder(order, input.sheetName(), input.headerRowNo());
            BigDecimal priorAmount = inFile.putIfAbsent(order.orderNo(), order.amount());
            if (priorAmount != null && priorAmount.compareTo(order.amount()) != 0)
                throw invalid("同一文件重复订单金额不一致：第 " + order.rowNo() + " 行");
            if (priorAmount == null) uniqueOrders.add(order);
            total = total.add(order.amount());
        }
        if (input.totalAmount() == null || total.compareTo(input.totalAmount()) != 0)
            throw invalid("预览金额与确认明细不一致");
        if (input.zeroCount() != input.orders().stream().filter(order -> order.amount().signum() == 0).count())
            throw invalid("预览零元订单数与确认明细不一致");
        String source = String.valueOf(company.get("source_system"));
        String orderType = String.valueOf(company.get("order_no_type"));
        List<Order> newOrders = new ArrayList<>();
        for (Order order : uniqueOrders) {
            List<BigDecimal> prior = jdbc.query("""
                    SELECT amount FROM charging_source_order
                    WHERE source_system=? AND order_no_type=? AND original_order_no=?
                    """, (rs, index) -> rs.getBigDecimal(1), source, orderType, order.orderNo());
            if (!prior.isEmpty()) {
                if (prior.getFirst().compareTo(order.amount()) != 0)
                    throw invalid("重复订单金额不一致：第 " + order.rowNo() + " 行");
            } else newOrders.add(order);
        }
        StoredFile stored;
        try { stored = storage.store(bytes, filename, "application/octet-stream"); }
        catch (IOException ex) { throw invalid("原始 Excel 保存失败"); }
        try {
            int zeroCount = (int) newOrders.stream().filter(order -> order.amount().signum() == 0).count();
            int importedCount = newOrders.size() - zeroCount;
            int duplicateCount = input.count() - newOrders.size();
            if (input.count() != importedCount + zeroCount + duplicateCount)
                throw invalid("确认数量对账失败");
            long batchId = insert("""
                    INSERT INTO charging_import_batch(company_id,source_system,original_filename,
                      original_storage_key,file_hash,sheet_name,mapping_json,confirmed_count,imported_count,
                      zero_count,duplicate_count,confirmed_amount,imported_amount,created_by)
                    VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """, input.companyId(), source, filename, stored.storageKey(), stored.sha256(),
                    input.sheetName(), toJson(input.mapping()), input.count(), importedCount, zeroCount,
                    duplicateCount, total, sum(newOrders), actor);
            for (Order order : newOrders) {
                Map<String, Object> vehicle = jdbc.queryForList("""
                        SELECT id,plate_no FROM vehicle WHERE plate_no=? AND deleted_at IS NULL
                        """, order.plateNo()).stream().findFirst()
                        .orElseThrow(() -> invalid("第 " + order.rowNo() + " 行公司车辆不存在：" + order.plateNo()));
                long vehicleId = ((Number) vehicle.get("id")).longValue();
                Long taskId = uniqueTask(vehicleId, order.chargedAt());
                long sourceOrderId = insert("""
                        INSERT INTO charging_source_order(batch_id,source_system,order_no_type,original_order_no,
                          sheet_name,row_no,plate_no,charged_at,amount)
                        VALUES(?,?,?,?,?,?,?,?,?)
                        """, batchId, source, orderType, order.orderNo(), order.sheetName(), order.rowNo(),
                        order.plateNo(), order.chargedAt(), order.amount());
                if (order.amount().signum() == 0) continue;
                long expenseId = insert("""
                        INSERT INTO expense_entry(expense_no,application_scope,current_task_id,vehicle_id,
                          expense_type,business_date,description,amount,source_type,source_record_key,
                          status,created_by)
                        VALUES(?,'DAILY',?,?,'TEMP_ELECTRIC',?, ?,?,'CHARGING',?,'ACTIVE',?)
                        """, "CE" + UUID.randomUUID().toString().replace("-", ""), taskId, vehicleId,
                        order.chargedAt().toLocalDate(), "充电订单 " + order.orderNo(), order.amount(),
                        "CHARGING_ORDER:" + sourceOrderId, actor);
                jdbc.update("UPDATE charging_source_order SET expense_id=? WHERE id=?", expenseId, sourceOrderId);
                if (taskId != null) jdbc.update("""
                        INSERT INTO expense_attribution_history(expense_id,previous_task_id,new_task_id,reason,operator_id)
                        VALUES(?,NULL,?,'充电时间与参与车辆唯一匹配',?)
                        """, expenseId, taskId, actor);
            }
            return batch(batchId);
        } catch (RuntimeException failure) {
            storage.delete(stored.storageKey());
            throw failure;
        }
    }

    private Long uniqueTask(long vehicleId, LocalDateTime time) {
        List<Long> matches = jdbc.query("""
                SELECT DISTINCT p.task_id FROM transport_participant p
                JOIN transport_task t ON t.id=p.task_id
                WHERE p.company_vehicle_id=? AND p.driver_type_snapshot='INTERNAL'
                  AND p.departed_at IS NOT NULL AND p.departed_at<=?
                  AND COALESCE(p.ended_at,t.completed_at,CURRENT_TIMESTAMP)>?
                  AND p.status<>'CANCELLED' AND t.deleted_at IS NULL AND t.status<>'CANCELLED'
                LIMIT 2
                """, (rs, index) -> rs.getLong(1), vehicleId, time, time);
        return matches.size() == 1 ? matches.getFirst() : null;
    }

    private void verifyOriginal(byte[] bytes, Confirmation input) {
        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            if (!sheet.getSheetName().equals(input.sheetName())) throw invalid("工作表与预览不一致");
            Row header = sheet.getRow(input.headerRowNo() - 1);
            var formatter = new org.apache.poi.ss.usermodel.DataFormatter();
            for (Header item : input.headers()) {
                // Unmapped decorative headers may be formatted differently by POI and SheetJS.
                if (!input.mapping().containsValue(item.index())) continue;
                if (header == null || !item.label().equals(formatter.formatCellValue(header.getCell(item.index()))))
                    throw invalid("表头已变化，请重新预览");
            }
            Set<Integer> submittedRows = new HashSet<>();
            for (Order order : input.orders()) if (!submittedRows.add(order.rowNo()))
                throw invalid("同一原始行号不能重复提交：第 " + order.rowNo() + " 行");
            int lastSubmittedRow = input.orders().stream().mapToInt(Order::rowNo).max().orElse(input.headerRowNo());
            for (int number = input.headerRowNo() + 1; number <= sheet.getLastRowNum() + 1; number++) {
                Row row = sheet.getRow(number - 1);
                if (row == null) continue;
                boolean blankRow = true, summary = false;
                for (Cell cell : row) {
                    String value = formatter.formatCellValue(cell).trim();
                    if (!value.isEmpty()) blankRow = false;
                    if (value.matches("^(合计|总计|汇总|总订单|订单总数|总金额).*")) summary = true;
                }
                boolean identityEmpty = List.of("orderNo", "plateNo", "chargedAt").stream()
                        .allMatch(field -> formatter.formatCellValue(row.getCell(input.mapping().get(field))).isBlank());
                boolean amountPresent = !formatter.formatCellValue(row.getCell(input.mapping().get("amount"))).isBlank();
                boolean trailingTotal = false;
                if (identityEmpty && amountPresent && number > lastSubmittedRow) {
                    try {
                        int currentRowNo = number;
                        Cell amountCell = row.getCell(input.mapping().get("amount"));
                        BigDecimal rowAmount = (amountCell.getCellType() == CellType.NUMERIC
                                || amountCell.getCellType() == CellType.FORMULA
                                && amountCell.getCachedFormulaResultType() == CellType.NUMERIC)
                                ? BigDecimal.valueOf(amountCell.getNumericCellValue())
                                : new BigDecimal(formatter.formatCellValue(amountCell).replaceAll("[￥¥,\\s]", ""));
                        BigDecimal preceding = input.orders().stream().filter(order -> order.rowNo() < currentRowNo)
                                .map(Order::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
                        trailingTotal = rowAmount.compareTo(preceding) == 0;
                    } catch (NumberFormatException ignored) { /* Keep the row as an error. */ }
                }
                if (!blankRow && !summary && !trailingTotal && (!identityEmpty || amountPresent)
                        && !submittedRows.contains(number))
                    throw invalid("第 " + number + " 行未纳入预览，请修正原文件或映射后重新解析");
            }
            for (Order order : input.orders()) {
                Row row = sheet.getRow(order.rowNo() - 1);
                if (row == null) throw invalid("第 " + order.rowNo() + " 行不存在");
                String actualOrder = formatter.formatCellValue(row.getCell(input.mapping().get("orderNo"))).trim();
                String actualPlate = formatter.formatCellValue(row.getCell(input.mapping().get("plateNo"))).trim();
                if (!actualOrder.equals(order.orderNo()) || !actualPlate.equals(order.plateNo()))
                    throw invalid("第 " + order.rowNo() + " 行订单或车牌与原始文件不一致");
                Cell amountCell = row.getCell(input.mapping().get("amount"));
                BigDecimal actualAmount = amountCell != null
                        && (amountCell.getCellType() == CellType.NUMERIC
                        || amountCell.getCellType() == CellType.FORMULA
                        && amountCell.getCachedFormulaResultType() == CellType.NUMERIC)
                        ? BigDecimal.valueOf(amountCell.getNumericCellValue())
                        : new BigDecimal(formatter.formatCellValue(amountCell).replaceAll("[￥¥,\\s]", ""));
                if (actualAmount.compareTo(order.amount()) != 0)
                    throw invalid("第 " + order.rowNo() + " 行金额与原始文件不一致");
                Cell timeCell = row.getCell(input.mapping().get("chargedAt"));
                LocalDateTime originalTime = null;
                if (timeCell != null && (timeCell.getCellType() == CellType.NUMERIC
                        || timeCell.getCellType() == CellType.FORMULA
                        && timeCell.getCachedFormulaResultType() == CellType.NUMERIC))
                    originalTime = DateUtil.getLocalDateTime(timeCell.getNumericCellValue()).withNano(0);
                else if (timeCell != null) {
                    String raw = formatter.formatCellValue(timeCell).trim().replace('/', '-').replace(' ', 'T');
                    if (raw.length() == 16) raw += ":00";
                    originalTime = LocalDateTime.parse(raw);
                }
                if (!order.chargedAt().equals(originalTime))
                    throw invalid("第 " + order.rowNo() + " 行充电时间与原始文件不一致：预览 "
                            + order.chargedAt() + "，原始 " + originalTime);
            }
        } catch (BusinessException ex) { throw ex; }
        catch (Exception ex) { throw invalid("原始 Excel 无法核验，请检查格式和字段映射"); }
    }

    private void validateOrder(Order order, String sheet, int headerRow) {
        if (order == null) throw invalid("预览包含空订单行");
        if (order.rowNo() <= headerRow || !sheet.equals(order.sheetName())
                || blank(order.orderNo()) || order.orderNo().length() > 128
                || blank(order.plateNo()) || order.plateNo().length() > 32
                || order.chargedAt() == null || order.amount() == null
                || order.amount().signum() < 0 || order.amount().scale() > 2
                || order.amount().precision() > 18) throw invalid("第 " + order.rowNo() + " 行订单字段不合法");
    }

    private void validateMapping(CompanyCommand command) {
        if (command.headerRowNo() == null || command.headerRowNo() < 1 || command.headerRowNo() > 100
                || command.headers() == null || command.headers().isEmpty() || command.mapping() == null)
            throw invalid("请确认表头行和四项字段映射");
        Set<Integer> indexes = new HashSet<>();
        for (Header header : command.headers()) {
            if (header.index() < 0 || header.index() > 500 || blank(header.label()) || !indexes.add(header.index()))
                throw invalid("表头配置不合法");
        }
        for (String field : List.of("orderNo", "plateNo", "chargedAt", "amount")) {
            if (!indexes.contains(command.mapping().get(field))) throw invalid("四项字段必须从表头下拉选择");
        }
        if (new HashSet<>(command.mapping().values()).size() != 4) throw invalid("四项字段不能使用同一列");
    }

    private BigDecimal sum(List<Order> orders) {
        return orders.stream().map(Order::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    private boolean sameJson(Object supplied, Object stored) {
        try { return json.readTree(toJson(supplied)).equals(json.readTree(String.valueOf(stored))); }
        catch (JsonProcessingException ex) { return false; }
    }
    private String toJson(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw invalid("字段映射格式不合法"); }
    }
    private long insert(String sql, Object... args) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, new String[]{"id"});
            for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
            return statement;
        }, keys);
        return keys.getKey().longValue();
    }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private BusinessException invalid(String message) { return new BusinessException("SYS_002", message); }
}
