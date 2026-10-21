package com.dfwl.fleet.finance.v3;

import com.dfwl.fleet.common.api.PageResponse;
import com.dfwl.fleet.common.error.BusinessException;
import com.dfwl.fleet.common.error.ErrorCode;
import com.dfwl.fleet.system.service.AuditWriteService;
import com.dfwl.fleet.security.TransactionalAuthorizationGuard;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Approved ExpenseEntry is the sole writable ordinary-expense fact. */
@Service
public class ExpenseLedgerService {
    private static final int EXPORT_LIMIT = 20_000;
    private static final String FROM = """
            FROM expense_entry e JOIN vehicle v ON v.id=e.vehicle_id
            LEFT JOIN driver d ON d.id=e.driver_id
            LEFT JOIN transport_task t ON t.id=e.current_task_id
            LEFT JOIN approval_instance a ON a.id=e.approval_instance_id
            LEFT JOIN approval_template atpl ON atpl.id=a.template_id
            LEFT JOIN expense_reversal_link rl ON rl.original_expense_id=e.id
            """;
    private static final String FIELDS = """
            e.id,e.expense_no,e.business_date,e.expense_type,e.application_scope,
            e.current_task_id,t.task_no,e.vehicle_id,v.plate_no vehicle_plate,
            e.driver_id,d.name driver_name,e.amount,e.description,e.receipt_no,e.invoice_no,
            e.remark,e.station_name,e.trailer_no,e.source_type,e.source_record_key,e.status,e.version,
            e.approval_instance_id,e.approval_item_no,a.approval_no,a.created_at approval_started_at,
            atpl.name approval_name,a.request_key approval_batch_key,
            CASE WHEN e.source_type='REVERSAL' THEN '冲销记录'
              WHEN rl.id IS NOT NULL THEN '已冲销' ELSE '有效' END display_status,
            CASE WHEN e.current_task_id IS NULL THEN '未归属'
              WHEN e.application_scope='TASK' AND NOT EXISTS
                   (SELECT 1 FROM expense_attribution_history ah WHERE ah.expense_id=e.id)
                THEN '申请时直接归属' ELSE '后续人工归属' END attribution_method
            """;
    private final JdbcTemplate jdbc;
    private final FinanceFactsService finance;
    private final AuditWriteService audit;
    private final TransactionalAuthorizationGuard authorization;

    public ExpenseLedgerService(JdbcTemplate jdbc, FinanceFactsService finance, AuditWriteService audit,
                                TransactionalAuthorizationGuard authorization) {
        this.jdbc = jdbc; this.finance = finance; this.audit = audit; this.authorization = authorization;
    }

    public record Filter(String month, String expenseType, String applicationScope, Long driverId,
                         Long vehicleId, Boolean bound, Long taskId, String sourceType, String status) {}
    public record Attribution(Long targetTaskId, String reason, Integer expectedVersion) {}
    public record Reversal(String reason, String requestKey) {}

    public PageResponse<Map<String, Object>> options(String kind, String search, Long vehicleId, int pageNo) {
        if (pageNo < 1) throw invalid("分页参数不合法");
        String term = "%" + (search == null ? "" : search.trim()) + "%";
        String from;
        String fields;
        switch (kind) {
              case "CATEGORY" -> {
                  from = " FROM expense_category WHERE enabled=1 AND category_name LIKE ?";
                  fields = "category_code id,category_name label,category_code detail";
              }
            case "DRIVER" -> {
                from = " FROM driver WHERE deleted_at IS NULL AND (name LIKE ? OR phone LIKE ?)";
                fields = "id,name label,phone detail";
            }
            case "VEHICLE" -> {
                from = " FROM vehicle WHERE deleted_at IS NULL AND plate_no LIKE ?";
                fields = "id,plate_no label,plate_no detail";
            }
            case "TASK" -> {
                from = " FROM transport_task WHERE deleted_at IS NULL AND status<>'CANCELLED' AND (task_no LIKE ? OR template_name_snapshot LIKE ?)";
                fields = "id,task_no label,template_name_snapshot detail";
            }
            default -> throw invalid("筛选对象不合法");
        }
          Object[] args = "VEHICLE".equals(kind) || "CATEGORY".equals(kind) ? new Object[]{term} : new Object[]{term, term};
        if ("TASK".equals(kind) && vehicleId != null) {
            from += " AND EXISTS(SELECT 1 FROM transport_participant p WHERE p.task_id=transport_task.id "
                    + "AND p.driver_type_snapshot='INTERNAL' AND p.company_vehicle_id=? AND p.status<>'CANCELLED')";
            args = new Object[]{term, term, vehicleId};
        }
        long total = jdbc.queryForObject("SELECT COUNT(*)" + from, Long.class, args);
        Object[] pageArgs = java.util.Arrays.copyOf(args, args.length + 2);
        pageArgs[args.length] = 100;
        pageArgs[args.length + 1] = (pageNo - 1) * 100;
        return new PageResponse<>(pageNo, 100, total,
                jdbc.queryForList("SELECT " + fields + from + " ORDER BY id DESC LIMIT ? OFFSET ?", pageArgs));
    }

    public PageResponse<Map<String, Object>> list(Filter filter, int pageNo, int pageSize) {
        if (pageNo < 1 || pageSize < 1 || pageSize > 200) throw invalid("分页参数不合法");
        List<Object> args = new ArrayList<>();
        String where = where(filter, args);
        long total = jdbc.queryForObject("SELECT COUNT(*) " + FROM + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(pageSize); pageArgs.add((pageNo - 1) * pageSize);
        return new PageResponse<>(pageNo, pageSize, total, jdbc.queryForList("SELECT " + FIELDS + FROM + where
                + " ORDER BY e.business_date DESC,e.id DESC LIMIT ? OFFSET ?", pageArgs.toArray()));
    }

    public Map<String, Object> summary(Filter filter) {
        List<Object> args = new ArrayList<>();
        String where = where(filter, args);
        return jdbc.queryForMap("""
                SELECT COUNT(*) entry_count,COALESCE(SUM(e.amount),0) amount_total,
                  COALESCE(SUM(CASE WHEN e.current_task_id IS NULL THEN e.amount ELSE 0 END),0) unassigned_total
                """ + FROM + where, args.toArray());
    }

    public Map<String, Object> detail(long id) {
        Map<String, Object> entry = jdbc.queryForList("SELECT " + FIELDS + FROM + " WHERE e.id=?", id)
                .stream().findFirst().orElseThrow(() -> new BusinessException(ErrorCode.EXPENSE_001));
        List<Map<String, Object>> history = jdbc.queryForList("""
                SELECT h.previous_task_id,prior.task_no previous_task_no,h.new_task_id,next.task_no new_task_no,
                  h.reason,h.operator_id,u.phone operator_phone,h.operated_at
                FROM expense_attribution_history h
                LEFT JOIN transport_task prior ON prior.id=h.previous_task_id
                LEFT JOIN transport_task next ON next.id=h.new_task_id
                LEFT JOIN sys_user u ON u.id=h.operator_id
                WHERE h.expense_id=? ORDER BY h.id
                """, id);
        List<Map<String, Object>> reversal = jdbc.queryForList("""
                SELECT l.original_expense_id,l.reversal_expense_id,original.expense_no original_expense_no,
                  reversed.expense_no reversal_expense_no,l.reason,l.operator_id,u.phone operator_phone,l.created_at
                FROM expense_reversal_link l
                JOIN expense_entry original ON original.id=l.original_expense_id
                JOIN expense_entry reversed ON reversed.id=l.reversal_expense_id
                LEFT JOIN sys_user u ON u.id=l.operator_id
                WHERE l.original_expense_id=? OR l.reversal_expense_id=?
                """, id, id);
        List<Map<String, Object>> auditTrail = jdbc.queryForList("""
                SELECT operation_type,operator_id,operation_time,reason
                FROM audit_log WHERE business_type='EXPENSE' AND business_id=? ORDER BY id
                """, id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("entry", entry);
        result.put("attributionHistory", history);
        result.put("reversal", reversal);
        result.put("audit", auditTrail);
        return result;
    }

    @Transactional
    public Map<String, Object> attribute(long id, Attribution command, long actor) {
        authorization.require(actor, "BUTTON_EXPENSE_UNASSIGNED_ATTRIBUTE");
        if (command == null || command.reason() == null || command.reason().isBlank()
                || command.expectedVersion() == null) throw invalid("请填写归属原因并刷新版本");
        Map<String, Object> expense = lock(id);
        requireAdjustable(expense);
        int version = number(expense.get("version")).intValue();
        if (version != command.expectedVersion()) throw invalid("费用已由他人修改，请刷新后重试");
        Long before = expense.get("current_task_id") == null ? null : number(expense.get("current_task_id")).longValue();
        Long after = command.targetTaskId();
        if (after == null && "TASK".equals(expense.get("application_scope")))
            throw new BusinessException(ErrorCode.EXPENSE_003);
        if (java.util.Objects.equals(before, after)) throw invalid("目标任务与当前归属相同");
        if (after != null) {
            long match = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM transport_task t JOIN transport_participant p ON p.task_id=t.id
                    WHERE t.id=? AND t.deleted_at IS NULL AND t.status<>'CANCELLED'
                      AND p.status<>'CANCELLED' AND p.driver_type_snapshot='INTERNAL'
                      AND p.company_vehicle_id=?
                    """, Long.class, after, expense.get("vehicle_id"));
            if (match == 0) throw new BusinessException(ErrorCode.EXPENSE_003);
        }
        Long chargingDriverId = null;
        if (after != null && "CHARGING".equals(expense.get("source_type"))) {
            chargingDriverId = jdbc.queryForObject("""
                    SELECT p.driver_id FROM transport_participant p
                    JOIN transport_task t ON t.id=p.task_id
                    LEFT JOIN charging_source_order o ON o.expense_id=?
                    WHERE p.task_id=? AND p.company_vehicle_id=?
                      AND p.driver_type_snapshot='INTERNAL' AND p.status<>'CANCELLED'
                    ORDER BY CASE WHEN o.charged_at>=p.departed_at
                      AND o.charged_at<COALESCE(p.ended_at,t.completed_at,CURRENT_TIMESTAMP)
                      THEN 0 ELSE 1 END, p.is_current DESC,p.sequence_no DESC
                    LIMIT 1
                    """, Long.class, id, after, expense.get("vehicle_id"));
        }
        int changed = jdbc.update("""
                UPDATE expense_entry SET current_task_id=?,
                  driver_id=CASE WHEN source_type='CHARGING' THEN ? ELSE driver_id END,
                  expense_type=CASE WHEN source_type='CHARGING' THEN ? ELSE expense_type END,
                  version=version+1,updated_by=?
                WHERE id=? AND version=? AND status='ACTIVE'
                """, after, chargingDriverId, after == null ? "TEMP_ELECTRIC" : "ELECTRIC",
                actor, id, version);
        if (changed != 1) throw invalid("费用已由他人修改，请刷新后重试");
        jdbc.update("""
                INSERT INTO expense_attribution_history(expense_id,previous_task_id,new_task_id,reason,operator_id)
                VALUES(?,?,?,?,?)
                """, id, before, after, command.reason().trim(), actor);
        Map<String, Object> auditBefore = new LinkedHashMap<>();
        auditBefore.put("version", version);
        Map<String, Object> auditAfter = new LinkedHashMap<>();
        auditAfter.put("version", version + 1);
        auditAfter.put("target", after == null ? "UNASSIGNED" : after);
        if ("CHARGING".equals(expense.get("source_type"))) {
            auditBefore.put("expenseType", expense.get("expense_type"));
            auditBefore.put("driverId", expense.get("driver_id"));
            auditAfter.put("expenseType", after == null ? "TEMP_ELECTRIC" : "ELECTRIC");
            auditAfter.put("driverId", chargingDriverId);
        }
        audit.write("EXPENSE", "EXPENSE", id, "ATTRIBUTION", auditBefore, auditAfter,
                actor, command.reason().trim());
        return detail(id);
    }

    @Transactional
    public Map<String, Object> reverse(long id, Reversal command, long actor) {
        authorization.require(actor, "BUTTON_EXPENSE_LEDGER_REVERSE");
        if (command == null || command.reason() == null || command.reason().isBlank()
                || command.requestKey() == null || command.requestKey().isBlank())
            throw invalid("请填写冲销原因和请求标识");
        Map<String, Object> original = lock(id);
        requireAdjustable(original);
        if (((BigDecimal) original.get("amount")).signum() <= 0) throw new BusinessException(ErrorCode.EXPENSE_002);
        long existing = jdbc.queryForObject("SELECT COUNT(*) FROM expense_reversal_link WHERE original_expense_id=?",
                Long.class, id);
        if (existing > 0) throw new BusinessException(ErrorCode.EXPENSE_005);
        BigDecimal amount = (BigDecimal) original.get("amount");
        String no = "RV" + UUID.randomUUID().toString().replace("-", "");
        KeyHolder holder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO expense_entry(expense_no,application_scope,current_task_id,vehicle_id,driver_id,
                      expense_type,business_date,description,amount,receipt_no,invoice_no,remark,station_name,trailer_no,
                      source_type,source_record_key,status,created_by)
                    VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,'REVERSAL',?,'ACTIVE',?)
                    """, new String[]{"id"});
            Object[] values = {no, original.get("application_scope"), original.get("current_task_id"),
                    original.get("vehicle_id"), original.get("driver_id"), original.get("expense_type"),
                    original.get("business_date"), original.get("description"), amount.negate(),
                    original.get("receipt_no"), original.get("invoice_no"), original.get("remark"),
                    original.get("station_name"), original.get("trailer_no"), "REVERSAL:" + id, actor};
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            return statement;
        }, holder);
        long reversalId = holder.getKey().longValue();
        jdbc.update("""
                INSERT INTO expense_reversal_link(original_expense_id,reversal_expense_id,reason,operator_id)
                VALUES(?,?,?,?)
                """, id, reversalId, command.reason().trim(), actor);
        audit.write("EXPENSE", "EXPENSE", id, "REVERSE", Map.of("amount", amount),
                Map.of("reversalExpenseId", reversalId, "negativeAmount", amount.negate()), actor,
                command.reason().trim());
        return detail(id);
    }

    public byte[] export(Filter filter) {
        List<Object> args = new ArrayList<>();
        String where = where(filter, args);
        long count = jdbc.queryForObject("SELECT COUNT(*) " + FROM + where, Long.class, args.toArray());
        if (count > EXPORT_LIMIT) throw invalid("结果超过 20000 条，请缩小筛选范围");
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT " + FIELDS + FROM + where
                + " ORDER BY e.business_date DESC,e.id DESC", args.toArray());
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("费用台账");
            String[] headings = {"日期", "费用编号", "费用类型", "申请属性", "司机", "公司车牌", "车挂号", "当前任务",
                    "明细", "金额", "来源", "状态", "归属方式", "票据号", "发票号", "站点名称", "备注"};
            var header = sheet.createRow(0);
            for (int i = 0; i < headings.length; i++) header.createCell(i).setCellValue(headings[i]);
            int number = 1;
            for (Map<String, Object> row : rows) {
                var excel = sheet.createRow(number++);
                Object[] values = {row.get("business_date"),row.get("expense_no"),typeName(row.get("expense_type")),
                        scopeName(row.get("application_scope")),row.get("driver_name"),row.get("vehicle_plate"),
                        row.get("trailer_no"),row.get("task_no"),row.get("description"),row.get("amount"),sourceName(row.get("source_type")),
                        row.get("display_status"),row.get("attribution_method"),row.get("receipt_no"),
                        row.get("invoice_no"),row.get("station_name"),row.get("remark")};
                for (int i = 0; i < values.length; i++) excel.createCell(i).setCellValue(
                        values[i] == null ? "" : values[i].toString());
            }
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException ex) { throw new IllegalStateException("费用台账 Excel 生成失败", ex); }
    }

    private Map<String, Object> lock(long id) {
        return jdbc.queryForList("SELECT * FROM expense_entry WHERE id=? FOR UPDATE", id)
                .stream().findFirst().orElseThrow(() -> new BusinessException(ErrorCode.EXPENSE_001));
    }
    private void requireAdjustable(Map<String, Object> entry) {
        if (!"ACTIVE".equals(entry.get("status")) || "REVERSAL".equals(entry.get("source_type")))
            throw new BusinessException(ErrorCode.EXPENSE_002);
        long reversed = jdbc.queryForObject("SELECT COUNT(*) FROM expense_reversal_link WHERE original_expense_id=?",
                Long.class, entry.get("id"));
        if (reversed > 0) throw new BusinessException(ErrorCode.EXPENSE_005);
    }

    private String where(Filter filter, List<Object> args) {
        StringBuilder clause = new StringBuilder(" WHERE e.deleted_at IS NULL AND e.status='ACTIVE'");
        if (filter == null) return clause.toString();
        if (filter.month() != null && !filter.month().isBlank()) {
            YearMonth month = finance.requireMonth(filter.month());
            clause.append(" AND e.business_date>=? AND e.business_date<?");
            args.add(month.atDay(1)); args.add(month.plusMonths(1).atDay(1));
        }
        if (filter.expenseType() != null && !filter.expenseType().isBlank()) {
            clause.append(" AND e.expense_type=?"); args.add(filter.expenseType());
        }
        if (filter.applicationScope() != null && !filter.applicationScope().isBlank()) {
            if (!List.of("DAILY", "TASK").contains(filter.applicationScope())) throw invalid("申请属性不合法");
            clause.append(" AND e.application_scope=?"); args.add(filter.applicationScope());
        }
        if (filter.driverId() != null) { clause.append(" AND e.driver_id=?"); args.add(filter.driverId()); }
        if (filter.vehicleId() != null) { clause.append(" AND e.vehicle_id=?"); args.add(filter.vehicleId()); }
        if (filter.bound() != null) clause.append(filter.bound() ? " AND e.current_task_id IS NOT NULL"
                : " AND e.current_task_id IS NULL");
        if (filter.taskId() != null) { clause.append(" AND e.current_task_id=?"); args.add(filter.taskId()); }
        if (filter.sourceType() != null && !filter.sourceType().isBlank()) {
            if (!List.of("APPROVAL", "REVERSAL", "CHARGING").contains(filter.sourceType()))
                throw invalid("来源不合法");
            clause.append(" AND e.source_type=?"); args.add(filter.sourceType());
        }
        if (filter.status() != null && !filter.status().isBlank()) {
            switch (filter.status()) {
                case "ACTIVE" -> clause.append(" AND e.source_type<>'REVERSAL' AND rl.id IS NULL");
                case "REVERSED" -> clause.append(" AND rl.id IS NOT NULL");
                case "REVERSAL" -> clause.append(" AND e.source_type='REVERSAL'");
                default -> throw invalid("费用状态不合法");
            }
        }
        return clause.toString();
    }
    private String typeName(Object value) {
        return switch (String.valueOf(value)) {
            case "REPAIR" -> "维修费"; case "PENALTY" -> "罚款"; case "WATER" -> "水费";
            case "TEMP_ELECTRIC" -> "临时电费"; case "TEMP_GAS" -> "临时气费";
            case "TOLL" -> "过路费"; default -> "其他费用";
        };
    }
    private String scopeName(Object value) { return "TASK".equals(value) ? "任务费用" : "日常费用"; }
    private String sourceName(Object value) {
        return switch (String.valueOf(value)) {
            case "APPROVAL" -> "审批通过"; case "REVERSAL" -> "费用冲销";
            case "CHARGING" -> "充电公司"; default -> "其他来源";
        };
    }
    private Number number(Object value) { return (Number) value; }
    private BusinessException invalid(String message) { return new BusinessException("SYS_002", message); }
}
