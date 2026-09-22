package com.dfwl.fleet.settlement.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
public class SettlementRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public SettlementRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> versions(String yearMonth) {
        return jdbcTemplate.queryForList("""
                SELECT v.id, m.`year_month` AS yearMonth, v.version_no AS versionNo, v.total_income AS totalIncome,
                       v.route_expense AS routeExpense, v.daily_expense AS dailyExpense,
                       v.driver_salary AS driverSalary, v.total_profit AS totalProfit,
                       v.generated_by AS generatedBy, v.generated_at AS generatedAt
                FROM settlement_version v
                JOIN settlement_month m ON m.id = v.settlement_month_id
                WHERE m.`year_month` = ?
                ORDER BY v.version_no DESC
                """, yearMonth);
    }

    public Optional<Long> versionExists(long versionId) {
        return jdbcTemplate.query("SELECT id FROM settlement_version WHERE id = ?",
                (rs, rowNum) -> rs.getLong("id"), versionId).stream().findFirst();
    }

    public Map<String, Object> version(long versionId) {
        return jdbcTemplate.queryForMap("""
                SELECT v.id, m.`year_month` AS yearMonth, v.version_no AS versionNo, v.total_income AS totalIncome,
                       v.route_expense AS routeExpense, v.daily_expense AS dailyExpense,
                       v.driver_salary AS driverSalary, v.total_profit AS totalProfit,
                       v.generated_by AS generatedBy, v.generated_at AS generatedAt
                FROM settlement_version v
                JOIN settlement_month m ON m.id = v.settlement_month_id
                WHERE v.id = ?
                """, versionId);
    }

    public List<Map<String, Object>> details(long versionId) {
        return jdbcTemplate.queryForList("""
                SELECT id, settlement_version_id AS settlementVersionId, fact_type AS factType, source_id AS sourceId,
                       snapshot_json AS snapshotJson, income_amount AS incomeAmount, expense_amount AS expenseAmount,
                       profit_amount AS profitAmount
                FROM settlement_detail
                WHERE settlement_version_id = ?
                ORDER BY fact_type, source_id
                """, versionId);
    }

    public List<Map<String, Object>> diff(long versionId) {
        return jdbcTemplate.queryForList("""
                SELECT id, settlement_version_id AS settlementVersionId, previous_version_id AS previousVersionId,
                       fact_type AS factType, source_id AS sourceId, change_type AS changeType,
                       before_snapshot_json AS beforeSnapshotJson, after_snapshot_json AS afterSnapshotJson,
                       income_delta AS incomeDelta, expense_delta AS expenseDelta, profit_delta AS profitDelta,
                       business_operator_id AS businessOperatorId, business_operated_at AS businessOperatedAt,
                       business_reason AS businessReason
                FROM settlement_diff
                WHERE settlement_version_id = ?
                ORDER BY id
                """, versionId);
    }

    public long generate(String yearMonth, LocalDate startDate, LocalDate endDate, long operatorId) {
        long monthId = ensureMonth(yearMonth);
        int nextVersion = reserveNextVersion(monthId);
        List<Fact> facts = currentFacts(startDate, endDate);
        Totals totals = totals(facts);
        long versionId = createVersion(monthId, nextVersion, totals, operatorId);
        facts.forEach(fact -> insertDetail(versionId, fact));
        Long previousVersionId = previousVersionId(monthId, nextVersion).orElse(null);
        insertDiffs(versionId, previousVersionId, facts);
        return versionId;
    }

    private long ensureMonth(String yearMonth) {
        try {
            jdbcTemplate.update("""
                    INSERT INTO settlement_month (`year_month`, latest_version_no)
                    SELECT ?, 0
                    WHERE NOT EXISTS (SELECT 1 FROM settlement_month WHERE `year_month` = ?)
                    """, yearMonth, yearMonth);
        } catch (DataIntegrityViolationException ignored) {
            // Another transaction created the same month concurrently; read it below.
        }
        Long id = jdbcTemplate.queryForObject("SELECT id FROM settlement_month WHERE `year_month` = ?", Long.class, yearMonth);
        return id == null ? 0 : id;
    }

    private int latestVersionNo(long monthId) {
        Integer value = jdbcTemplate.queryForObject("SELECT latest_version_no FROM settlement_month WHERE id = ?", Integer.class, monthId);
        return value == null ? 0 : value;
    }

    private int reserveNextVersion(long monthId) {
        jdbcTemplate.update("UPDATE settlement_month SET latest_version_no = latest_version_no + 1 WHERE id = ?", monthId);
        return latestVersionNo(monthId);
    }

    private Optional<Long> previousVersionId(long monthId, int nextVersion) {
        if (nextVersion <= 1) {
            return Optional.empty();
        }
        return jdbcTemplate.query("""
                SELECT id FROM settlement_version
                WHERE settlement_month_id = ? AND version_no = ?
                """, (rs, rowNum) -> rs.getLong("id"), monthId, nextVersion - 1).stream().findFirst();
    }

    private long createVersion(long monthId, int versionNo, Totals totals, long operatorId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO settlement_version (settlement_month_id, version_no, total_income, route_expense,
                                                    daily_expense, driver_salary, total_profit, generated_by)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, new String[]{"id"});
            ps.setLong(1, monthId);
            ps.setInt(2, versionNo);
            ps.setBigDecimal(3, totals.income());
            ps.setBigDecimal(4, totals.routeExpense());
            ps.setBigDecimal(5, totals.dailyExpense());
            ps.setBigDecimal(6, totals.driverSalary());
            ps.setBigDecimal(7, totals.profit());
            ps.setLong(8, operatorId);
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    private List<Fact> currentFacts(LocalDate startDate, LocalDate endDate) {
        List<Fact> routeFacts = jdbcTemplate.query("""
                SELECT r.id AS sourceId,
                       r.route_no AS routeNo,
                       r.business_date AS businessDate,
                       r.customer_id AS customerId,
                       r.product_id AS productId,
                       r.departure_driver_id AS driverId,
                       r.departure_vehicle_id AS vehicleId,
                       r.mileage_km AS mileageKm,
                       r.tax_unit_price AS taxUnitPrice,
                       r.effective_weight_version_id AS effectiveWeightVersionId,
                       w.net_weight AS netWeight,
                       w.operator_id AS weightOperatorId,
                       w.operation_time AS weightOperationTime,
                       w.reason AS weightReason,
                       COALESCE(w.net_weight * r.tax_unit_price, 0) AS incomeAmount,
                       COALESCE(e.routeExpense, 0) AS expenseAmount,
                       COALESCE(e.reversalCount, 0) AS reversalCount,
                       COALESCE(r.driver_salary, 0) AS driverSalary,
                       CASE WHEN r.driver_salary IS NULL THEN 0 ELSE 1 END AS salaryComplete,
                       COALESCE(w.net_weight * r.tax_unit_price, 0) - COALESCE(e.routeExpense, 0)
                         - COALESCE(r.driver_salary, 0) AS profitAmount
                FROM route_task r
                LEFT JOIN route_weight_version w ON w.id = r.effective_weight_version_id
                LEFT JOIN (
                    SELECT route_id,
                           SUM(amount) AS routeExpense,
                           SUM(CASE WHEN status = 'REVERSAL' THEN 1 ELSE 0 END) AS reversalCount
                    FROM expense_entry
                    WHERE attribution_type = 'ROUTE'
                      AND status IN ('ACTIVE', 'REVERSED', 'REVERSAL')
                      AND deleted_at IS NULL
                    GROUP BY route_id
                ) e ON e.route_id = r.id
                WHERE r.status = 'COMPLETED'
                  AND r.business_date >= ?
                  AND r.business_date < ?
                  AND r.deleted_at IS NULL
                """, (rs, rowNum) -> {
            Map<String, Object> snapshot = new HashMap<>();
            snapshot.put("routeNo", rs.getString("routeNo"));
            snapshot.put("businessDate", rs.getDate("businessDate").toLocalDate().toString());
            snapshot.put("customerId", readLong(rs, "customerId"));
            snapshot.put("productId", readLong(rs, "productId"));
            snapshot.put("driverId", readLong(rs, "driverId"));
            snapshot.put("vehicleId", readLong(rs, "vehicleId"));
            snapshot.put("mileageKm", rs.getBigDecimal("mileageKm"));
            snapshot.put("taxUnitPrice", rs.getBigDecimal("taxUnitPrice"));
            snapshot.put("effectiveWeightVersionId", readLong(rs, "effectiveWeightVersionId"));
            snapshot.put("netWeight", rs.getBigDecimal("netWeight"));
            snapshot.put("driverSalary", rs.getBigDecimal("driverSalary"));
            snapshot.put("reversalCount", rs.getInt("reversalCount"));
            snapshot.put("salaryComplete", rs.getInt("salaryComplete") == 1);
            snapshot.put("weightOperatorId", readLong(rs, "weightOperatorId"));
            snapshot.put("weightOperationTime", rs.getTimestamp("weightOperationTime") == null ? null : rs.getTimestamp("weightOperationTime").toLocalDateTime().toString());
            snapshot.put("weightReason", rs.getString("weightReason"));
            return new Fact("ROUTE", rs.getLong("sourceId"), snapshot,
                    rs.getBigDecimal("incomeAmount"),
                    rs.getBigDecimal("expenseAmount").add(rs.getBigDecimal("driverSalary")),
                    rs.getBigDecimal("profitAmount"),
                    BigDecimal.ZERO,
                    rs.getBigDecimal("driverSalary"));
        }, startDate, endDate);

        List<Fact> dailyExpenseFacts = jdbcTemplate.query("""
                SELECT id AS sourceId, expense_no AS expenseNo, expense_type AS expenseType,
                       business_date AS businessDate, vehicle_id AS vehicleId, driver_id AS driverId,
                       amount AS expenseAmount, created_by, created_at, updated_by, updated_at, reversal_of_id,
                       status, route_id
                FROM expense_entry
                WHERE attribution_type = 'DAILY'
                  AND status IN ('ACTIVE', 'REVERSED', 'REVERSAL')
                  AND business_date >= ?
                  AND business_date < ?
                  AND deleted_at IS NULL
                """, (rs, rowNum) -> {
            Map<String, Object> snapshot = new HashMap<>();
            snapshot.put("expenseNo", rs.getString("expenseNo"));
            snapshot.put("expenseType", rs.getString("expenseType"));
            snapshot.put("businessDate", rs.getDate("businessDate").toLocalDate().toString());
            snapshot.put("vehicleId", rs.getLong("vehicleId"));
            snapshot.put("driverId", readLong(rs, "driverId"));
            snapshot.put("status", rs.getString("status"));
            snapshot.put("routeId", readLong(rs, "route_id"));
            snapshot.put("createdBy", readLong(rs, "created_by"));
            snapshot.put("createdAt", rs.getTimestamp("created_at").toLocalDateTime().toString());
            snapshot.put("updatedBy", readLong(rs, "updated_by"));
            snapshot.put("updatedAt", rs.getTimestamp("updated_at").toLocalDateTime().toString());
            snapshot.put("reversalOfId", readLong(rs, "reversal_of_id"));
            BigDecimal expenseAmount = rs.getBigDecimal("expenseAmount");
            return new Fact("DAILY_EXPENSE", rs.getLong("sourceId"), snapshot,
                    BigDecimal.ZERO, expenseAmount, expenseAmount.negate(), expenseAmount, BigDecimal.ZERO);
        }, startDate, endDate);

        routeFacts.addAll(dailyExpenseFacts);
        return routeFacts;
    }

    private Totals totals(List<Fact> facts) {
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal routeExpense = BigDecimal.ZERO;
        BigDecimal dailyExpense = BigDecimal.ZERO;
        BigDecimal driverSalary = BigDecimal.ZERO;
        BigDecimal profit = BigDecimal.ZERO;
        for (Fact fact : facts) {
            income = income.add(fact.incomeAmount());
            if ("ROUTE".equals(fact.factType())) {
                routeExpense = routeExpense.add(fact.expenseAmount().subtract(fact.driverSalary()));
            }
            dailyExpense = dailyExpense.add(fact.dailyExpense());
            driverSalary = driverSalary.add(fact.driverSalary());
            profit = profit.add(fact.profitAmount());
        }
        return new Totals(income, routeExpense, dailyExpense, driverSalary, profit);
    }

    private void insertDetail(long versionId, Fact fact) {
        jdbcTemplate.update("""
                INSERT INTO settlement_detail (settlement_version_id, fact_type, source_id, snapshot_json,
                                               income_amount, expense_amount, profit_amount)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, versionId, fact.factType(), fact.sourceId(), json(fact.snapshot()), fact.incomeAmount(),
                fact.expenseAmount(), fact.profitAmount());
    }

    private void insertDiffs(long versionId, Long previousVersionId, List<Fact> currentFacts) {
        Map<String, Fact> current = toFactMap(currentFacts);
        Map<String, DetailFact> previous = previousVersionId == null ? new HashMap<>() : previousDetails(previousVersionId);

        for (Fact fact : current.values()) {
            DetailFact before = previous.remove(fact.key());
            if (before == null) {
                BusinessChange change = businessChange(fact, null);
                insertDiff(versionId, previousVersionId, fact.factType(), fact.sourceId(), change.type(),
                        null, json(fact.snapshot()), fact.incomeAmount(), fact.expenseAmount(), fact.profitAmount(), change);
            } else {
                BigDecimal incomeDelta = fact.incomeAmount().subtract(before.incomeAmount());
                BigDecimal expenseDelta = fact.expenseAmount().subtract(before.expenseAmount());
                BigDecimal profitDelta = fact.profitAmount().subtract(before.profitAmount());
                String afterJson = json(fact.snapshot());
                if (incomeDelta.signum() != 0 || expenseDelta.signum() != 0 || profitDelta.signum() != 0
                        || !afterJson.equals(before.snapshotJson())) {
                    BusinessChange change = businessChange(fact, before);
                    insertDiff(versionId, previousVersionId, fact.factType(), fact.sourceId(), change.type(),
                            before.snapshotJson(), afterJson, incomeDelta, expenseDelta, profitDelta, change);
                }
            }
        }
        for (DetailFact removed : previous.values()) {
            BusinessChange change = new BusinessChange("DELETE", null, null, null);
            insertDiff(versionId, previousVersionId, removed.factType(), removed.sourceId(), "DELETE",
                    removed.snapshotJson(), null, removed.incomeAmount().negate(),
                    removed.expenseAmount().negate(), removed.profitAmount().negate(), change);
        }
    }

    private Map<String, Fact> toFactMap(List<Fact> facts) {
        Map<String, Fact> map = new HashMap<>();
        facts.forEach(fact -> map.put(fact.key(), fact));
        return map;
    }

    private Map<String, DetailFact> previousDetails(long previousVersionId) {
        Map<String, DetailFact> facts = new HashMap<>();
        jdbcTemplate.query("""
                SELECT fact_type, source_id, snapshot_json, income_amount, expense_amount, profit_amount
                FROM settlement_detail
                WHERE settlement_version_id = ?
                """, rs -> {
            DetailFact fact = new DetailFact(
                    rs.getString("fact_type"),
                    rs.getLong("source_id"),
                    rs.getString("snapshot_json"),
                    rs.getBigDecimal("income_amount"),
                    rs.getBigDecimal("expense_amount"),
                    rs.getBigDecimal("profit_amount"));
            facts.put(fact.key(), fact);
        }, previousVersionId);
        return facts;
    }

    private void insertDiff(long versionId, Long previousVersionId, String factType, long sourceId, String changeType,
                            String beforeJson, String afterJson, BigDecimal incomeDelta, BigDecimal expenseDelta,
                            BigDecimal profitDelta, BusinessChange change) {
        jdbcTemplate.update("""
                INSERT INTO settlement_diff (settlement_version_id, previous_version_id, fact_type, source_id,
                                             change_type, before_snapshot_json, after_snapshot_json,
                                             income_delta, expense_delta, profit_delta,
                                             business_operator_id, business_operated_at, business_reason)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, versionId, previousVersionId, factType, sourceId, changeType, beforeJson, afterJson,
                incomeDelta, expenseDelta, profitDelta, change.operatorId(), change.operatedAt(), change.reason());
    }

    private BusinessChange businessChange(Fact after, DetailFact before) {
        if ("DAILY_EXPENSE".equals(after.factType())) {
            if (after.snapshot().get("reversalOfId") != null || "REVERSAL".equals(after.snapshot().get("status"))) {
                return reversalChange(after.sourceId());
            }
            return new BusinessChange(before == null ? "ADD" : "MODIFY",
                    longValue(after.snapshot().get("updatedBy"), after.snapshot().get("createdBy")),
                    timeValue(after.snapshot().get("updatedAt"), after.snapshot().get("createdAt")),
                    null);
        }
        if ("ROUTE".equals(after.factType())) {
            Long weightVersionId = longValue(after.snapshot().get("effectiveWeightVersionId"));
            if (before != null && before.snapshotJson() != null && before.snapshotJson().contains("\"effectiveWeightVersionId\"")) {
                String marker = "\"effectiveWeightVersionId\":" + weightVersionId;
                if (weightVersionId != null && !before.snapshotJson().contains(marker)) {
                    return new BusinessChange("WEIGHT_ADJUST", longValue(after.snapshot().get("weightOperatorId")),
                            timeValue(after.snapshot().get("weightOperationTime")), stringValue(after.snapshot().get("weightReason")));
                }
            }
            BusinessChange reassignment = reassignmentChange(after.sourceId());
            if (reassignment != null) {
                return reassignment;
            }
            BusinessChange reversal = routeReversalChange(after.sourceId());
            if (reversal != null) {
                return reversal;
            }
            BusinessChange salary = salaryChange(after.sourceId());
            if (salary != null) {
                return salary;
            }
            return new BusinessChange(before == null ? "ADD" : "MODIFY", null, null, null);
        }
        return new BusinessChange(before == null ? "ADD" : "MODIFY", null, null, null);
    }

    private BusinessChange salaryChange(long routeId) {
        return jdbcTemplate.query("""
                SELECT operator_id, operation_time, reason
                FROM driver_salary_history
                WHERE route_id = ?
                ORDER BY operation_time DESC, id DESC
                LIMIT 1
                """, (rs, rowNum) -> new BusinessChange("MODIFY", readLong(rs, "operator_id"),
                rs.getTimestamp("operation_time").toLocalDateTime(),
                rs.getString("reason")), routeId).stream().findFirst().orElse(null);
    }

    private BusinessChange reassignmentChange(long routeId) {
        return jdbcTemplate.query("""
                SELECT operator_id, operation_time, reason
                FROM expense_attribution_history
                WHERE (before_route_id = ? OR after_route_id = ?)
                ORDER BY operation_time DESC
                LIMIT 1
                """, (rs, rowNum) -> new BusinessChange("REASSIGN", readLong(rs, "operator_id"),
                rs.getTimestamp("operation_time").toLocalDateTime(),
                rs.getString("reason")), routeId, routeId).stream().findFirst().orElse(null);
    }

    private BusinessChange routeReversalChange(long routeId) {
        return jdbcTemplate.query("""
                SELECT l.operator_id, l.created_at, l.reason
                FROM expense_reversal_link l
                JOIN expense_entry e ON e.id = l.reversal_expense_id
                WHERE e.route_id = ?
                ORDER BY l.created_at DESC
                LIMIT 1
                """, (rs, rowNum) -> new BusinessChange("REVERSAL", readLong(rs, "operator_id"),
                rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getString("reason")), routeId).stream().findFirst().orElse(null);
    }

    private BusinessChange reversalChange(long reversalExpenseId) {
        return jdbcTemplate.query("""
                SELECT operator_id, created_at, reason
                FROM expense_reversal_link
                WHERE reversal_expense_id = ?
                ORDER BY created_at DESC
                LIMIT 1
                """, (rs, rowNum) -> new BusinessChange("REVERSAL", readLong(rs, "operator_id"),
                rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getString("reason")), reversalExpenseId).stream().findFirst()
                .orElse(new BusinessChange("REVERSAL", null, null, null));
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize settlement snapshot", ex);
        }
    }

    private Long readLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private Long longValue(Object value, Object fallback) {
        Long result = longValue(value);
        return result == null ? longValue(fallback) : result;
    }

    private Long longValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }

    private LocalDateTime timeValue(Object value, Object fallback) {
        LocalDateTime result = timeValue(value);
        return result == null ? timeValue(fallback) : result;
    }

    private LocalDateTime timeValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime time) {
            return time;
        }
        return LocalDateTime.parse(value.toString());
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private record Totals(BigDecimal income, BigDecimal routeExpense, BigDecimal dailyExpense,
                          BigDecimal driverSalary, BigDecimal profit) {
    }

    private record Fact(String factType, long sourceId, Map<String, Object> snapshot, BigDecimal incomeAmount,
                        BigDecimal expenseAmount, BigDecimal profitAmount, BigDecimal dailyExpense,
                        BigDecimal driverSalary) {
        String key() {
            return factType + ":" + sourceId;
        }
    }

    private record DetailFact(String factType, long sourceId, String snapshotJson, BigDecimal incomeAmount,
                              BigDecimal expenseAmount, BigDecimal profitAmount) {
        String key() {
            return factType + ":" + sourceId;
        }
    }

    private record BusinessChange(String type, Long operatorId, LocalDateTime operatedAt, String reason) {
    }
}
