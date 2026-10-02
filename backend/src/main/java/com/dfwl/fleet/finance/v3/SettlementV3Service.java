package com.dfwl.fleet.finance.v3;

import com.dfwl.fleet.common.api.PageResponse;
import com.dfwl.fleet.common.error.BusinessException;
import com.dfwl.fleet.common.error.ErrorCode;
import com.dfwl.fleet.system.service.AuditWriteService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Immutable settlement versions generated from FinanceFactsService, never from a parallel formula. */
@Service
public class SettlementV3Service {
    private final JdbcTemplate jdbc;
    private final FinanceFactsService finance;
    private final AuditWriteService audit;
    private final ObjectMapper mapper;

    public SettlementV3Service(JdbcTemplate jdbc, FinanceFactsService finance,
                               AuditWriteService audit, ObjectMapper mapper) {
        this.jdbc = jdbc; this.finance = finance; this.audit = audit; this.mapper = mapper;
    }

    public List<Map<String, Object>> versions(String month) {
        finance.requireMonth(month);
        return jdbc.queryForList("""
                SELECT v.id,v.version_no,v.status,v.generated_at,v.generated_by,v.income_total,
                  v.expense_total,v.salary_total,v.information_fee_total,v.profit_total
                FROM settlement_version v JOIN settlement_month m ON m.id=v.month_id
                WHERE m.month_key=? ORDER BY v.version_no DESC
                """, month);
    }

    @Transactional
    public Map<String, Object> generate(String month, long actor) {
        YearMonth period = finance.requireMonth(month);
        jdbc.update("INSERT INTO settlement_month(month_key) VALUES(?) ON DUPLICATE KEY UPDATE id=id", month);
        Map<String, Object> monthRow = jdbc.queryForMap("SELECT * FROM settlement_month WHERE month_key=? FOR UPDATE", month);
        long monthId = num(monthRow.get("id"));
        int next = ((Number) monthRow.get("latest_version_no")).intValue() + 1;
        Map<String, Object> totals = finance.company(month);
        long versionId = insert("""
                INSERT INTO settlement_version(month_id,version_no,status,generated_by,income_total,
                  expense_total,salary_total,information_fee_total,profit_total)
                VALUES(?,?,'COMPLETED',?,?,?,?,?,?)
                """, monthId, next, actor, totals.get("incomeTotal"), totals.get("totalExpense"),
                totals.get("salaryTotal"), totals.get("informationFeeTotal"), totals.get("profitTotal"));
        Map<String, DetailFact> current = snapshot(period);
        for (DetailFact fact : current.values()) jdbc.update("""
                INSERT INTO settlement_detail(version_id,fact_type,fact_id,fact_version,fact_snapshot_json,
                  business_date,income_amount,expense_amount,salary_amount,information_fee_amount,profit_amount)
                VALUES(?,?,?,?,?,?,?,?,?,?,?)
                """, versionId, fact.type(), fact.id(), fact.version(), json(fact.snapshot()),
                fact.businessDate(), fact.income(), fact.expense(), fact.salary(), fact.information(), fact.profit());
        Long priorId = next == 1 ? null : jdbc.queryForObject("""
                SELECT id FROM settlement_version WHERE month_id=? AND version_no=?
                """, Long.class, monthId, next - 1);
        if (priorId != null) writeDiffs(versionId, priorId, current, readFacts(priorId));
        jdbc.update("UPDATE settlement_month SET latest_version_no=? WHERE id=?", next, monthId);
        audit.write("SETTLEMENT", "SETTLEMENT_VERSION", versionId, "GENERATE", null,
                Map.of("month", month, "version", next, "income", totals.get("incomeTotal"),
                        "expense", totals.get("totalExpense"), "profit", totals.get("profitTotal")), actor, null);
        return version(versionId);
    }

    public Map<String, Object> version(long id) {
        return jdbc.queryForList("""
                SELECT v.id,m.month_key,v.version_no,v.status,v.generated_at,v.generated_by,
                  v.income_total,v.expense_total,v.salary_total,v.information_fee_total,v.profit_total
                FROM settlement_version v JOIN settlement_month m ON m.id=v.month_id WHERE v.id=?
                """, id).stream().findFirst().orElseThrow(() -> new BusinessException(ErrorCode.DATA_001));
    }

    public PageResponse<Map<String, Object>> details(long id, int pageNo, int pageSize) {
        version(id);
        validatePage(pageNo, pageSize);
        long count = jdbc.queryForObject("SELECT COUNT(*) FROM settlement_detail WHERE version_id=?", Long.class, id);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT fact_type,fact_id,fact_snapshot_json,business_date,income_amount,expense_amount,
                  salary_amount,information_fee_amount,profit_amount
                FROM settlement_detail WHERE version_id=? ORDER BY fact_type,fact_id LIMIT ? OFFSET ?
                """, id, pageSize, (pageNo - 1) * pageSize);
        List<Map<String, Object>> business = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            Map<String, Object> snapshot = parse(row.get("fact_snapshot_json"));
            item.put("factType", "TASK".equals(row.get("fact_type")) ? "运输任务" : "正式费用");
            item.put("businessNo", "TASK".equals(row.get("fact_type"))
                    ? snapshot.get("task_no") : snapshot.get("expense_no"));
            item.put("businessDate", row.get("business_date"));
            item.put("snapshot", snapshot);
            item.put("income", row.get("income_amount"));
            item.put("expense", row.get("expense_amount"));
            item.put("profit", row.get("profit_amount"));
            business.add(item);
        }
        return new PageResponse<>(pageNo, pageSize, count, business);
    }

    @SuppressWarnings("unchecked")
    public PageResponse<Map<String, Object>> diffs(long id, int pageNo, int pageSize) {
        version(id);
        validatePage(pageNo, pageSize);
        long count = jdbc.queryForObject("SELECT COUNT(*) FROM settlement_diff WHERE version_id=?", Long.class, id);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT fact_type,fact_key,change_type,before_snapshot_json,after_snapshot_json,
                  income_delta,expense_delta,profit_delta,reason
                FROM settlement_diff WHERE version_id=? ORDER BY id LIMIT ? OFFSET ?
                """, id, pageSize, (pageNo - 1) * pageSize);
        List<Map<String, Object>> business = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> before = parse(row.get("before_snapshot_json"));
            Map<String, Object> after = parse(row.get("after_snapshot_json"));
            Map<String, Object> source = after.isEmpty() ? before : after;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("factType", "TASK".equals(row.get("fact_type")) ? "运输任务" : "正式费用");
            item.put("businessNo", "TASK".equals(row.get("fact_type"))
                    ? source.get("task_no") : source.get("expense_no"));
            item.put("changeType", changeName(String.valueOf(row.get("change_type"))));
            item.put("before", businessSummary(before));
            item.put("after", businessSummary(after));
            item.put("incomeDelta", row.get("income_delta"));
            item.put("expenseDelta", row.get("expense_delta"));
            item.put("profitDelta", row.get("profit_delta"));
            item.put("reason", row.get("reason"));
            Map<String, Object> action = source.get("diffEvidence") instanceof Map<?, ?> captured
                    ? new LinkedHashMap<>((Map<String, Object>) captured)
                    : changeEvidence(source, String.valueOf(row.get("change_type")));
            item.put("operator", action.getOrDefault("operator", source.get("changeOperator")));
            item.put("operatedAt", action.getOrDefault("time", source.get("changeTime")));
            item.put("approval", source.get("approval"));
            business.add(item);
        }
        return new PageResponse<>(pageNo, pageSize, count, business);
    }

    private Map<String, Object> businessSummary(Map<String, Object> snapshot) {
        if (snapshot.isEmpty()) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : List.of("net_weight", "actual_unit_price", "actual_information_fee",
                "salary_amount", "task_expense_amount", "income_amount", "total_expense_amount",
                "profit_amount", "amount", "current_task_id", "application_scope"))
            if (snapshot.containsKey(key)) result.put(key, snapshot.get(key));
        return result;
    }

    private Map<String, Object> evidence(Map<String, Object> row, String timeKey) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("reason", row.get("reason"));
        value.put("operator", row.get("operator_phone"));
        value.put("time", row.get(timeKey) == null ? null : row.get(timeKey).toString());
        return value;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> changeEvidence(Map<String, Object> snapshot, String type) {
        Object all = snapshot.get("changeEvidence");
        if (!(all instanceof Map<?, ?> evidence)) return Map.of();
        Object selected = evidence.get(type);
        return selected instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private Map<String, DetailFact> snapshot(YearMonth month) {
        String period = month.toString();
        LocalDate start = month.atDay(1);
        LocalDate end = month.plusMonths(1).atDay(1);
        List<Map<String, Object>> tasks = finance.settlementTasks(period);
        List<Map<String, Object>> expenses = jdbc.queryForList("""
                SELECT e.*,a.created_at approval_date,a.business_type approval_type,
                  applicant.phone applicant_phone,approver.phone approver_phone,
                  l.reason reversal_reason,attr.reason attribution_reason,
                  attr.operated_at attribution_time,operator.phone attribution_operator
                FROM expense_entry e
                LEFT JOIN approval_instance a ON a.id=e.approval_instance_id
                LEFT JOIN sys_user applicant ON applicant.id=a.applicant_user_id
                LEFT JOIN approval_action action ON action.id=(SELECT MAX(ac.id) FROM approval_action ac
                  JOIN approval_task atask ON atask.id=ac.task_id
                  WHERE atask.instance_id=a.id AND ac.action_type='APPROVE')
                LEFT JOIN sys_user approver ON approver.id=action.operator_id
                LEFT JOIN expense_reversal_link l ON l.reversal_expense_id=e.id
                LEFT JOIN expense_attribution_history attr ON attr.id=(SELECT MAX(ah.id)
                  FROM expense_attribution_history ah WHERE ah.expense_id=e.id)
                LEFT JOIN sys_user operator ON operator.id=attr.operator_id
                WHERE e.status='ACTIVE' AND e.deleted_at IS NULL
                  AND e.business_date>=? AND e.business_date<? ORDER BY e.id
                """, start, end);
        Map<String, DetailFact> result = new LinkedHashMap<>();
        for (Map<String, Object> task : tasks) {
            long taskId = num(task.get("task_id"));
            Map<String, Object> snapshot = new LinkedHashMap<>(task);
            snapshot.put("participants", jdbc.queryForList("""
                    SELECT p.id,p.sequence_no,p.driver_id,p.driver_type_snapshot,p.company_vehicle_id,
                      p.vehicle_plate_snapshot,p.trailer_plate_snapshot,p.outsourced_plate_no,p.status,
                      p.salary_amount,p.departed_at,p.ended_at,d.name driver_name
                    FROM transport_participant p JOIN driver d ON d.id=p.driver_id
                    WHERE p.task_id=? ORDER BY p.sequence_no
                    """, taskId));
            snapshot.put("assignedExpenses", jdbc.queryForList("""
                    SELECT id,expense_no,amount,expense_type,business_date,application_scope,
                      current_task_id,source_type,approval_instance_id FROM expense_entry
                    WHERE current_task_id=? AND status='ACTIVE' AND deleted_at IS NULL ORDER BY id
                    """, taskId));
            Map<String, Object> latest = jdbc.queryForList("""
                    SELECT h.amount_kind,h.reason,h.operated_at,u.phone operator_phone FROM task_amount_history h
                    LEFT JOIN sys_user u ON u.id=h.operator_id WHERE h.task_id=? ORDER BY h.id DESC LIMIT 1
                    """, taskId).stream().findFirst().orElse(null);
            Map<String, Object> weight = jdbc.queryForList("""
                    SELECT w.reason,w.operation_time,u.phone operator_phone FROM task_weight_version w
                    LEFT JOIN sys_user u ON u.id=w.operator_id WHERE w.id=?
                    """, task.get("effective_weight_version_id")).stream().findFirst().orElse(null);
            Map<String, Object> change = weight != null && weight.get("reason") != null ? weight : latest;
            Map<String, Object> changeEvidence = new LinkedHashMap<>();
            if (weight != null && weight.get("reason") != null)
                changeEvidence.put("WEIGHT_ADJUST", evidence(weight, "operation_time"));
            for (String kind : List.of("UNIT_PRICE", "INFORMATION_FEE")) {
                List<Map<String, Object>> amountHistory = jdbc.queryForList("""
                        SELECT h.reason,h.operated_at,u.phone operator_phone FROM task_amount_history h
                        LEFT JOIN sys_user u ON u.id=h.operator_id
                        WHERE h.task_id=? AND h.amount_kind=? ORDER BY h.id DESC LIMIT 1
                        """, taskId, kind);
                if (!amountHistory.isEmpty()) changeEvidence.put(kind + "_ADJUST", evidence(amountHistory.get(0), "operated_at"));
            }
            List<Map<String, Object>> salaryHistory = jdbc.queryForList("""
                    SELECT h.reason,h.operated_at,u.phone operator_phone
                    FROM participant_salary_history h JOIN transport_participant p ON p.id=h.participant_id
                    LEFT JOIN sys_user u ON u.id=h.operator_id
                    WHERE p.task_id=? ORDER BY h.id DESC LIMIT 1
                    """, taskId);
            if (!salaryHistory.isEmpty()) changeEvidence.put("SALARY_ADJUST", evidence(salaryHistory.get(0), "operated_at"));
            List<Map<String, Object>> attribution = jdbc.queryForList("""
                    SELECT h.reason,h.operated_at,u.phone operator_phone FROM expense_attribution_history h
                    LEFT JOIN sys_user u ON u.id=h.operator_id
                    WHERE h.new_task_id=? OR h.previous_task_id=? ORDER BY h.id DESC LIMIT 1
                    """, taskId, taskId);
            if (!attribution.isEmpty()) changeEvidence.put("TASK_EXPENSE_ADJUST", evidence(attribution.get(0), "operated_at"));
            snapshot.put("changeEvidence", changeEvidence);
            if (change != null) {
                snapshot.put("changeReason", change.get("reason"));
                snapshot.put("changeOperator", change.get("operator_phone"));
                snapshot.put("changeTime", change.getOrDefault("operated_at", change.get("operation_time")));
            }
            boolean incomeMonth = inPeriod(task.get("completed_at"), start, end);
            boolean costMonth = inPeriod(task.get("business_date"), start, end);
            BigDecimal income = incomeMonth ? money(task.get("income_amount")) : BigDecimal.ZERO;
            BigDecimal info = costMonth ? money(task.get("information_fee_amount")) : BigDecimal.ZERO;
            BigDecimal salary = costMonth ? money(task.get("salary_amount")) : BigDecimal.ZERO;
            result.put("TASK:" + taskId, new DetailFact("TASK", taskId,
                    ((Number) task.get("task_version")).intValue(), snapshot,
                    date(task.get("business_date")), income, info.add(salary), salary, info,
                    income.subtract(info).subtract(salary)));
        }
        for (Map<String, Object> expense : expenses) {
            long id = num(expense.get("id"));
            Map<String, Object> snapshot = new LinkedHashMap<>(expense);
            if (expense.get("approval_instance_id") != null) snapshot.put("approval", Map.of(
                    "date", expense.get("approval_date") == null ? "" : expense.get("approval_date").toString(),
                    "type", Objects.toString(expense.get("approval_type"), ""),
                    "amount", Objects.toString(expense.get("amount"), ""),
                    "applicant", Objects.toString(expense.get("applicant_phone"), ""),
                    "approver", Objects.toString(expense.get("approver_phone"), "")));
            snapshot.put("changeReason", Objects.toString(expense.get("reversal_reason"),
                    Objects.toString(expense.get("attribution_reason"), "正式费用新增")));
            snapshot.put("changeOperator", expense.get("attribution_operator"));
            snapshot.put("changeTime", expense.get("attribution_time"));
            BigDecimal amount = money(expense.get("amount"));
            result.put("EXPENSE:" + id, new DetailFact("EXPENSE", id,
                    ((Number) expense.get("version")).intValue(), snapshot,
                    date(expense.get("business_date")), BigDecimal.ZERO, amount,
                    BigDecimal.ZERO, BigDecimal.ZERO, amount.negate()));
        }
        return result;
    }

    private void writeDiffs(long versionId, long priorId, Map<String, DetailFact> current,
                            Map<String, DetailFact> previous) {
        Set<String> keys = new TreeSet<>();
        keys.addAll(current.keySet()); keys.addAll(previous.keySet());
        for (String key : keys) {
            DetailFact after = current.get(key);
            DetailFact before = previous.get(key);
            if (after != null && before != null
                    && normalize(parse(json(after.snapshot()))).equals(normalize(before.snapshot()))) continue;
            String type = after == null ? "REMOVED" : before == null ? "ADDED" : classify(before, after);
            Map<String, Object> source = after == null ? before.snapshot() : after.snapshot();
            Map<String, Object> action = "TASK_EXPENSE_ADJUST".equals(type)
                    ? taskExpenseEvidence(before.snapshot(), after.snapshot()) : changeEvidence(source, type);
            String reason = Objects.toString(action.get("reason"),
                    "EXPENSE".equals(after == null ? before.type() : after.type())
                            ? Objects.toString(source.get("changeReason"), changeName(type)) : changeName(type));
            Map<String, Object> capturedAfter = after == null ? null : new LinkedHashMap<>(after.snapshot());
            if (capturedAfter != null) capturedAfter.put("diffEvidence", action);
            jdbc.update("""
                    INSERT INTO settlement_diff(version_id,prior_version_id,fact_type,fact_key,change_type,
                      before_snapshot_json,after_snapshot_json,income_delta,expense_delta,profit_delta,reason)
                    VALUES(?,?,?,?,?,?,?,?,?,?,?)
                    """, versionId, priorId, after == null ? before.type() : after.type(), key, type,
                    before == null ? null : json(before.snapshot()), after == null ? null : json(capturedAfter),
                    amount(after, DetailFact::income).subtract(amount(before, DetailFact::income)),
                    amount(after, DetailFact::expense).subtract(amount(before, DetailFact::expense)),
                    amount(after, DetailFact::profit).subtract(amount(before, DetailFact::profit)), reason);
        }
    }

    private Map<String, Object> taskExpenseEvidence(Map<String, Object> before, Map<String, Object> after) {
        Map<Long, Object> oldAmounts = expenseAmounts(before.get("assignedExpenses"));
        Map<Long, Object> newAmounts = expenseAmounts(after.get("assignedExpenses"));
        Set<Long> ids = new TreeSet<>(); ids.addAll(oldAmounts.keySet()); ids.addAll(newAmounts.keySet());
        for (Long id : ids) {
            if (Objects.equals(normalize(oldAmounts.get(id)), normalize(newAmounts.get(id)))) continue;
            List<Map<String, Object>> attribution = jdbc.queryForList("""
                    SELECT h.reason,h.operated_at,u.phone operator_phone
                    FROM expense_attribution_history h LEFT JOIN sys_user u ON u.id=h.operator_id
                    WHERE h.expense_id=? ORDER BY h.id DESC LIMIT 1
                    """, id);
            if (!attribution.isEmpty()) return evidence(attribution.get(0), "operated_at");
            List<Map<String, Object>> reversal = jdbc.queryForList("""
                    SELECT l.reason,l.created_at,u.phone operator_phone
                    FROM expense_reversal_link l LEFT JOIN sys_user u ON u.id=l.operator_id
                    WHERE l.reversal_expense_id=? ORDER BY l.id DESC LIMIT 1
                    """, id);
            if (!reversal.isEmpty()) return evidence(reversal.get(0), "created_at");
        }
        return changeEvidence(after, "TASK_EXPENSE_ADJUST");
    }

    private Map<Long, Object> expenseAmounts(Object value) {
        Map<Long, Object> amounts = new LinkedHashMap<>();
        if (value instanceof List<?> list) for (Object item : list) {
            if (item instanceof Map<?, ?> row && row.get("id") instanceof Number id)
                amounts.put(id.longValue(), row.get("amount"));
        }
        return amounts;
    }

    private String classify(DetailFact before, DetailFact after) {
        if ("EXPENSE".equals(after.type())) {
            if (!Objects.equals(before.snapshot().get("current_task_id"), after.snapshot().get("current_task_id")))
                return "EXPENSE_ATTRIBUTION_ADJUST";
            return "EXPENSE_FACT_ADJUST";
        }
        List<String> changes = new ArrayList<>();
        for (String[] pair : new String[][]{{"net_weight","WEIGHT_ADJUST"},
                {"actual_unit_price","UNIT_PRICE_ADJUST"},
                {"actual_information_fee","INFORMATION_FEE_ADJUST"},
                {"salary_amount","SALARY_ADJUST"},
                {"task_expense_amount","TASK_EXPENSE_ADJUST"}})
            if (!Objects.equals(normalize(before.snapshot().get(pair[0])), normalize(after.snapshot().get(pair[0]))))
                changes.add(pair[1]);
        return changes.size() == 1 ? changes.get(0) : "MULTIPLE_FACT_CHANGES";
    }

    private Map<String, DetailFact> readFacts(long versionId) {
        Map<String, DetailFact> result = new HashMap<>();
        for (Map<String, Object> row : jdbc.queryForList("SELECT * FROM settlement_detail WHERE version_id=?", versionId)) {
            String type = String.valueOf(row.get("fact_type"));
            long id = num(row.get("fact_id"));
            result.put(type + ":" + id, new DetailFact(type, id, ((Number) row.get("fact_version")).intValue(),
                    parse(row.get("fact_snapshot_json")), date(row.get("business_date")),
                    money(row.get("income_amount")), money(row.get("expense_amount")),
                    money(row.get("salary_amount")), money(row.get("information_fee_amount")),
                    money(row.get("profit_amount"))));
        }
        return result;
    }

    private record DetailFact(String type, long id, int version, Map<String, Object> snapshot,
                              LocalDate businessDate, BigDecimal income, BigDecimal expense,
                              BigDecimal salary, BigDecimal information, BigDecimal profit) {}

    private BigDecimal amount(DetailFact fact, java.util.function.Function<DetailFact, BigDecimal> accessor) {
        return fact == null ? BigDecimal.ZERO : accessor.apply(fact);
    }
    private boolean inPeriod(Object value, LocalDate start, LocalDate end) {
        if (value == null) return false;
        LocalDate date = value instanceof LocalDate local ? local
                : value instanceof java.sql.Date sqlDate ? sqlDate.toLocalDate()
                : value instanceof java.time.LocalDateTime dateTime ? dateTime.toLocalDate()
                : java.sql.Timestamp.valueOf(value.toString()).toLocalDateTime().toLocalDate();
        return !date.isBefore(start) && date.isBefore(end);
    }
    private long num(Object value) { return ((Number) value).longValue(); }
    private BigDecimal money(Object value) { return value == null ? BigDecimal.ZERO : new BigDecimal(value.toString()); }
    private LocalDate date(Object value) {
        if (value instanceof LocalDate local) return local;
        if (value instanceof java.sql.Date sql) return sql.toLocalDate();
        return LocalDate.parse(value.toString());
    }
    private Object normalize(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return new BigDecimal(number.toString()).stripTrailingZeros().toPlainString();
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sorted = new java.util.TreeMap<>();
            map.forEach((key, item) -> sorted.put(key.toString(), normalize(item)));
            return sorted;
        }
        if (value instanceof List<?> list) return list.stream().map(this::normalize).toList();
        return value instanceof java.util.Date date ? Long.toString(date.getTime()) : value.toString();
    }
    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("月结快照序列化失败", ex); }
    }
    private Map<String, Object> parse(Object raw) {
        if (raw == null) return Map.of();
        try { return mapper.readValue(raw.toString(), new TypeReference<LinkedHashMap<String, Object>>() {}); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("月结快照读取失败", ex); }
    }
    private String changeName(String type) {
        return switch (type) {
            case "ADDED" -> "新增事实"; case "REMOVED" -> "事实移出本月";
            case "WEIGHT_ADJUST" -> "重量纠错"; case "UNIT_PRICE_ADJUST" -> "单价调整";
            case "INFORMATION_FEE_ADJUST" -> "信息费调整"; case "SALARY_ADJUST" -> "司机工资调整";
            case "TASK_EXPENSE_ADJUST" -> "任务费用变化";
            case "EXPENSE_ATTRIBUTION_ADJUST" -> "费用归属调整";
            case "EXPENSE_FACT_ADJUST" -> "费用事实变化";
            default -> "多项事实变化";
        };
    }
    private long insert(String sql, Object... values) {
        KeyHolder holder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, new String[]{"id"});
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            return statement;
        }, holder);
        return holder.getKey().longValue();
    }
    private void validatePage(int pageNo, int pageSize) {
        if (pageNo < 1 || pageSize < 1 || pageSize > 200) throw new BusinessException(ErrorCode.SYS_002);
    }
}
