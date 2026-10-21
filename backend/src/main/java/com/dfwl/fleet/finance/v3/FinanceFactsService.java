package com.dfwl.fleet.finance.v3;

import com.dfwl.fleet.common.api.PageResponse;
import com.dfwl.fleet.common.error.BusinessException;
import com.dfwl.fleet.common.error.ErrorCode;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** The only live task-finance projection. Ledger, reports and settlement consume these same facts. */
@Service
public class FinanceFactsService {
    private static final String FACTS = """
            WITH participant_salary AS (
              SELECT task_id, SUM(salary_amount) salary_amount
              FROM transport_participant WHERE status <> 'CANCELLED' GROUP BY task_id
            ), assigned_expense AS (
              SELECT current_task_id task_id, SUM(amount) expense_amount
              FROM expense_entry WHERE current_task_id IS NOT NULL AND status='ACTIVE'
                AND deleted_at IS NULL GROUP BY current_task_id
            ), task_components AS (
              SELECT t.id task_id, t.task_no, t.template_id, t.template_code_snapshot,
                t.template_name_snapshot, t.customer_name_snapshot, t.product_name_snapshot,
                t.loading_place_snapshot, t.unloading_place_snapshot, t.business_date,
                t.completed_at, t.status, t.actual_unit_price, t.actual_information_fee,
                t.version task_version,
                t.effective_weight_version_id, w.version_no weight_version,
                w.gross_weight, w.tare_weight, w.net_weight,
                CASE WHEN t.status='COMPLETED' THEN
                  ROUND(COALESCE(w.net_weight,0)*t.actual_unit_price,2) ELSE 0 END income_amount,
                CASE WHEN t.status='COMPLETED' THEN t.actual_information_fee ELSE 0 END information_fee_amount,
                CASE WHEN t.status='COMPLETED' THEN COALESCE(ps.salary_amount,0) ELSE 0 END salary_amount,
                COALESCE(ae.expense_amount,0) task_expense_amount
              FROM transport_task t
              LEFT JOIN task_weight_version w ON w.id=t.effective_weight_version_id
              LEFT JOIN participant_salary ps ON ps.task_id=t.id
              LEFT JOIN assigned_expense ae ON ae.task_id=t.id
              WHERE t.deleted_at IS NULL
            ), task_finance AS (
              SELECT c.*, c.information_fee_amount+c.salary_amount+c.task_expense_amount total_expense_amount,
                c.income_amount-c.information_fee_amount-c.salary_amount-c.task_expense_amount profit_amount
              FROM task_components c
            )
            """;
    private final JdbcTemplate jdbc;

    public FinanceFactsService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Map<String, Object> task(long id) {
        return jdbc.queryForList(FACTS + "SELECT * FROM task_finance WHERE task_id=?", id)
                .stream().findFirst().orElseThrow(() -> new BusinessException(ErrorCode.DATA_001));
    }

    public List<Map<String, Object>> tasksByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        if (ids.size() > 200 || ids.stream().anyMatch(id -> id == null || id <= 0))
            throw new BusinessException(ErrorCode.SYS_002);
        String markers = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        return jdbc.queryForList(FACTS + "SELECT * FROM task_finance WHERE task_id IN (" + markers
                + ") ORDER BY business_date DESC,task_id DESC", ids.toArray());
    }

    public PageResponse<Map<String, Object>> tasks(String month, Long templateId, int pageNo, int pageSize) {
        validatePage(pageNo, pageSize);
        List<Object> args = new ArrayList<>();
        String where = taskWhere(month, templateId, args);
        long count = jdbc.queryForObject(FACTS + "SELECT COUNT(*) FROM task_finance WHERE " + where,
                Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(pageSize); pageArgs.add((pageNo - 1) * pageSize);
        return new PageResponse<>(pageNo, pageSize, count,
                jdbc.queryForList(FACTS + "SELECT * FROM task_finance WHERE " + where
                        + " ORDER BY completed_at DESC,task_id DESC LIMIT ? OFFSET ?", pageArgs.toArray()));
    }

    public PageResponse<Map<String, Object>> templates(String month, int pageNo, int pageSize) {
        validatePage(pageNo, pageSize);
        List<Object> args = new ArrayList<>();
        String where = taskWhere(month, null, args);
        String grouped = FACTS + """
                SELECT template_id,MAX(template_code_snapshot) template_code,
                  MAX(template_name_snapshot) template_name, MAX(customer_name_snapshot) customer_name,
                  MAX(product_name_snapshot) product_name,COUNT(*) task_count,
                  COALESCE(SUM(net_weight),0) weight_total,COALESCE(SUM(income_amount),0) income_total,
                  COALESCE(SUM(information_fee_amount),0) information_fee_total,
                  COALESCE(SUM(salary_amount),0) salary_total,
                  COALESCE(SUM(task_expense_amount),0) task_expense_total,
                  COALESCE(SUM(total_expense_amount),0) expense_total,
                  COALESCE(SUM(profit_amount),0) profit_total
                FROM task_finance WHERE
                """ + where + " GROUP BY template_id";
        long count = jdbc.queryForObject(FACTS + "SELECT COUNT(DISTINCT template_id) FROM task_finance WHERE "
                + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(pageSize); pageArgs.add((pageNo - 1) * pageSize);
        return new PageResponse<>(pageNo, pageSize, count, jdbc.queryForList(grouped
                + " ORDER BY template_id DESC LIMIT ? OFFSET ?", pageArgs.toArray()));
    }

    public Map<String, Object> company(String month) {
        YearMonth period = requireMonth(month);
        var start = period.atDay(1);
        var end = period.plusMonths(1).atDay(1);
        Map<String, Object> taskTotals = jdbc.queryForMap(FACTS + """
                SELECT COALESCE(SUM(CASE WHEN completed_at>=? AND completed_at<? THEN income_amount ELSE 0 END),0) income_total,
                  COALESCE(SUM(CASE WHEN business_date>=? AND business_date<? THEN information_fee_amount ELSE 0 END),0) information_fee_total,
                  COALESCE(SUM(CASE WHEN business_date>=? AND business_date<? THEN salary_amount ELSE 0 END),0) salary_total,
                  COALESCE(SUM(CASE WHEN completed_at>=? AND completed_at<? THEN 1 ELSE 0 END),0) completed_task_count
                FROM task_finance WHERE status='COMPLETED'
                """, start, end, start, end, start, end, start, end);
        BigDecimal expense = jdbc.queryForObject("""
                SELECT COALESCE(SUM(amount),0) FROM expense_entry
                WHERE status='ACTIVE' AND deleted_at IS NULL AND business_date>=? AND business_date<?
                """, BigDecimal.class, start, end);
        BigDecimal unassigned = jdbc.queryForObject("""
                SELECT COALESCE(SUM(amount),0) FROM expense_entry
                WHERE status='ACTIVE' AND deleted_at IS NULL AND current_task_id IS NULL
                  AND business_date>=? AND business_date<?
                """, BigDecimal.class, start, end);
        BigDecimal income = decimal(taskTotals.get("income_total"));
        BigDecimal information = decimal(taskTotals.get("information_fee_total"));
        BigDecimal salary = decimal(taskTotals.get("salary_total"));
        BigDecimal total = information.add(salary).add(expense);
        return Map.of("month", month, "incomeTotal", income, "informationFeeTotal", information,
                "salaryTotal", salary, "expenseEntryTotal", expense, "unassignedExpenseTotal", unassigned,
                "totalExpense", total, "profitTotal", income.subtract(total),
                "completedTaskCount", taskTotals.get("completed_task_count"));
    }

    public Map<String, Object> dashboard(String month) {
        Map<String, Object> result = new java.util.LinkedHashMap<>(company(month));
        result.put("pendingApprovalCount", jdbc.queryForObject(
                "SELECT COUNT(*) FROM approval_instance WHERE status='PENDING'", Long.class));
        result.put("unassignedExpenseCount", jdbc.queryForObject("""
                SELECT COUNT(*) FROM expense_entry WHERE status='ACTIVE' AND deleted_at IS NULL
                  AND current_task_id IS NULL
                """, Long.class));
        return result;
    }

    /** Completed task facts contributing revenue and/or task costs to this accounting month. */
    public List<Map<String, Object>> settlementTasks(String month) {
        YearMonth period = requireMonth(month);
        var start = period.atDay(1);
        var end = period.plusMonths(1).atDay(1);
        return jdbc.queryForList(FACTS + """
                SELECT * FROM task_finance WHERE status='COMPLETED'
                  AND ((completed_at>=? AND completed_at<?) OR (business_date>=? AND business_date<?))
                ORDER BY task_id
                """, start, end, start, end);
    }

    public List<Map<String, Object>> taskExpenses(long taskId) {
        task(taskId);
        return jdbc.queryForList("""
                SELECT e.id,e.expense_no,e.business_date,e.expense_type,e.application_scope,e.amount,
                  e.description,e.receipt_no,e.invoice_no,e.remark,e.station_name,e.source_type,
                  e.approval_instance_id,e.approval_item_no,a.approval_no,
                  e.current_task_id,e.status,e.vehicle_id,v.plate_no vehicle_plate,d.name driver_name,
                  CASE WHEN e.source_type='REVERSAL' THEN '冲销记录'
                       WHEN e.application_scope='TASK' AND NOT EXISTS
                            (SELECT 1 FROM expense_attribution_history h WHERE h.expense_id=e.id)
                         THEN '申请时直接归属' ELSE '后续人工归属' END attribution_method,
                  CASE WHEN e.source_type='REVERSAL' THEN '冲销记录'
                       WHEN rl.id IS NOT NULL THEN '已冲销' ELSE '有效' END display_status
                FROM expense_entry e JOIN vehicle v ON v.id=e.vehicle_id
                LEFT JOIN driver d ON d.id=e.driver_id
                LEFT JOIN approval_instance a ON a.id=e.approval_instance_id
                LEFT JOIN expense_reversal_link rl ON rl.original_expense_id=e.id
                WHERE e.current_task_id=? AND e.status='ACTIVE' AND e.deleted_at IS NULL
                ORDER BY e.business_date DESC,e.id DESC
                """, taskId);
    }

    public List<Map<String, Object>> taskParticipants(long taskId) {
        task(taskId);
        return jdbc.queryForList("""
                SELECT p.sequence_no,p.driver_type_snapshot,p.vehicle_plate_snapshot,p.trailer_plate_snapshot,
                  p.outsourced_plate_no,p.salary_amount,p.departed_at,p.ended_at,p.status,d.name driver_name
                FROM transport_participant p JOIN driver d ON d.id=p.driver_id
                WHERE p.task_id=? ORDER BY p.sequence_no
                """, taskId);
    }

    public List<Map<String, Object>> taskExpenseTypes(long taskId) {
        task(taskId);
        return jdbc.queryForList("""
                SELECT expense_type,COUNT(*) entry_count,COALESCE(SUM(amount),0) amount_total
                FROM expense_entry WHERE current_task_id=? AND status='ACTIVE' AND deleted_at IS NULL
                GROUP BY expense_type ORDER BY expense_type
                """, taskId);
    }

    private String taskWhere(String month, Long templateId, List<Object> args) {
        StringBuilder where = new StringBuilder("status='COMPLETED'");
        if (month != null && !month.isBlank()) {
            YearMonth period = requireMonth(month);
            where.append(" AND completed_at>=? AND completed_at<?");
            args.add(period.atDay(1)); args.add(period.plusMonths(1).atDay(1));
        }
        if (templateId != null) { where.append(" AND template_id=?"); args.add(templateId); }
        return where.toString();
    }

    public YearMonth requireMonth(String value) {
        try { return YearMonth.parse(value); }
        catch (DateTimeParseException | NullPointerException ex) { throw new BusinessException(ErrorCode.SYS_002); }
    }

    private void validatePage(int pageNo, int pageSize) {
        if (pageNo < 1 || pageSize < 1 || pageSize > 200) throw new BusinessException(ErrorCode.SYS_002);
    }

    private BigDecimal decimal(Object value) {
        return value instanceof BigDecimal number ? number : new BigDecimal(value.toString());
    }
}
