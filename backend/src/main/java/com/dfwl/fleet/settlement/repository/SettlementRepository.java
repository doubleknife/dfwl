package com.dfwl.fleet.settlement.repository;

import com.dfwl.fleet.common.api.PageResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
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
                       v.unassigned_expense AS unassignedExpense,
                       v.driver_salary AS driverSalary, v.total_profit AS totalProfit,
                       v.generated_by AS generatedBy, u.phone AS generatedByPhone, v.generated_at AS generatedAt
                FROM settlement_version v
                JOIN settlement_month m ON m.id = v.settlement_month_id
                LEFT JOIN sys_user u ON u.id = v.generated_by
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
                       v.unassigned_expense AS unassignedExpense,
                       v.driver_salary AS driverSalary, v.total_profit AS totalProfit,
                       v.generated_by AS generatedBy, u.phone AS generatedByPhone, v.generated_at AS generatedAt
                FROM settlement_version v
                JOIN settlement_month m ON m.id = v.settlement_month_id
                LEFT JOIN sys_user u ON u.id = v.generated_by
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

    public PageResponse<Map<String, Object>> detailsPage(long versionId, int pageNo, int pageSize) {
        int safePage = Math.max(1, pageNo);
        int safeSize = Math.min(100, Math.max(1, pageSize));
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM settlement_detail WHERE settlement_version_id = ?", Long.class, versionId);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT id, settlement_version_id AS settlementVersionId, fact_type AS factType,
                       source_id AS sourceId, snapshot_json AS snapshotJson,
                       income_amount AS incomeAmount, expense_amount AS expenseAmount,
                       profit_amount AS profitAmount
                FROM settlement_detail WHERE settlement_version_id = ?
                ORDER BY fact_type, source_id LIMIT ? OFFSET ?
                """, versionId, safeSize, (safePage - 1) * safeSize);
        rows.forEach(row -> row.put("businessSummary", summary((String) row.get("snapshotJson"),
                String.valueOf(row.get("factType")))));
        return new PageResponse<>(safePage, safeSize, total == null ? 0 : total, rows);
    }

    public PageResponse<Map<String, Object>> diffPage(long versionId, int pageNo, int pageSize) {
        int safePage = Math.max(1, pageNo);
        int safeSize = Math.min(100, Math.max(1, pageSize));
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM settlement_diff WHERE settlement_version_id = ?", Long.class, versionId);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT id, settlement_version_id AS settlementVersionId, previous_version_id AS previousVersionId,
                       fact_type AS factType, source_id AS sourceId, change_type AS changeType,
                       before_snapshot_json AS beforeSnapshotJson, after_snapshot_json AS afterSnapshotJson,
                       income_delta AS incomeDelta, expense_delta AS expenseDelta, profit_delta AS profitDelta,
                       business_operator_id AS businessOperatorId, business_operated_at AS businessOperatedAt,
                       business_reason AS businessReason
                FROM settlement_diff WHERE settlement_version_id = ? ORDER BY id LIMIT ? OFFSET ?
                """, versionId, safeSize, (safePage - 1) * safeSize);
        rows.forEach(row -> {
            String type = String.valueOf(row.get("factType"));
            row.put("beforeSummary", summary((String) row.get("beforeSnapshotJson"), type));
            row.put("afterSummary", summary((String) row.get("afterSnapshotJson"), type));
        });
        return new PageResponse<>(safePage, safeSize, total == null ? 0 : total, rows);
    }

    private String summary(String raw, String type) {
        if (raw == null) return "—";
        JsonNode node = snapshot(raw);
        if ("ROUTE".equals(type)) return node.path("routeNo").asText("线路")
                + " · 净重 " + node.path("netWeight").asText("—")
                + " · 参与 " + node.path("participants").size() + " 人";
        return node.path("expenseNo").asText("费用") + " · "
                + node.path("expenseType").asText("—") + " · "
                + node.path("companyCategory").asText(node.path("attributionType").asText("—"));
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
                                                    daily_expense, unassigned_expense, driver_salary, total_profit, generated_by)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, new String[]{"id"});
            ps.setLong(1, monthId);
            ps.setInt(2, versionNo);
            ps.setBigDecimal(3, totals.income());
            ps.setBigDecimal(4, totals.routeExpense());
            ps.setBigDecimal(5, totals.dailyExpense());
            ps.setBigDecimal(6, totals.unassignedExpense());
            ps.setBigDecimal(7, totals.driverSalary());
            ps.setBigDecimal(8, totals.profit());
            ps.setLong(9, operatorId);
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
                       COALESCE(s.salaryAmount, 0) AS driverSalary,
                       CASE WHEN s.missingCount = 0 THEN 1 ELSE 0 END AS salaryComplete,
                       COALESCE(w.net_weight * r.tax_unit_price, 0) - COALESCE(e.routeExpense, 0)
                         - COALESCE(s.salaryAmount, 0) AS profitAmount
                FROM route_task r
                LEFT JOIN route_weight_version w ON w.id = r.effective_weight_version_id
                LEFT JOIN (
                    SELECT route_id, SUM(COALESCE(salary_amount, 0)) AS salaryAmount,
                           SUM(CASE WHEN salary_amount IS NULL THEN 1 ELSE 0 END) AS missingCount
                    FROM route_participant WHERE status NOT IN ('CANCELLED', 'LEGACY_ENDED')
                    GROUP BY route_id
                ) s ON s.route_id = r.id
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

        Map<Long, List<Map<String, Object>>> participants = new HashMap<>();
        jdbcTemplate.query("""
                SELECT p.route_id, p.id, p.sequence_no, p.driver_id, p.driver_type_snapshot,
                       p.company_vehicle_id, p.outsourced_plate_no, p.status, p.salary_amount
                FROM route_participant p JOIN route_task r ON r.id = p.route_id
                WHERE r.business_date >= ? AND r.business_date < ? AND r.status = 'COMPLETED'
                  AND r.deleted_at IS NULL ORDER BY p.route_id, p.sequence_no
                """, rs -> {
            Map<String, Object> item = new HashMap<>();
            item.put("participantId", rs.getLong("id"));
            item.put("sequenceNo", rs.getInt("sequence_no"));
            item.put("driverId", rs.getLong("driver_id"));
            item.put("driverType", rs.getString("driver_type_snapshot"));
            item.put("companyVehicleId", readLong(rs, "company_vehicle_id"));
            item.put("outsourcedPlateNo", rs.getString("outsourced_plate_no"));
            item.put("status", rs.getString("status"));
            item.put("salaryAmount", rs.getBigDecimal("salary_amount"));
            participants.computeIfAbsent(rs.getLong("route_id"), unused -> new java.util.ArrayList<>()).add(item);
        }, startDate, endDate);
        Map<Long, List<Map<String, Object>>> routeExpenses = new HashMap<>();
        jdbcTemplate.query("""
                SELECT e.route_id, e.id, e.expense_type, e.vehicle_id, e.amount, e.source_type,
                       e.approval_instance_id, e.status
                FROM expense_entry e JOIN route_task r ON r.id = e.route_id
                WHERE r.business_date >= ? AND r.business_date < ? AND r.status = 'COMPLETED'
                  AND r.deleted_at IS NULL AND e.attribution_type = 'ROUTE'
                  AND e.status IN ('ACTIVE', 'REVERSED', 'REVERSAL') AND e.deleted_at IS NULL
                ORDER BY e.route_id, e.id
                """, rs -> {
            Map<String, Object> item = new HashMap<>();
            item.put("expenseId", rs.getLong("id"));
            item.put("expenseType", rs.getString("expense_type"));
            item.put("vehicleId", rs.getLong("vehicle_id"));
            item.put("amount", rs.getBigDecimal("amount"));
            item.put("sourceType", rs.getString("source_type"));
            item.put("approvalInstanceId", readLong(rs, "approval_instance_id"));
            item.put("status", rs.getString("status"));
            routeExpenses.computeIfAbsent(rs.getLong("route_id"), unused -> new java.util.ArrayList<>()).add(item);
        }, startDate, endDate);
        for (Fact fact : routeFacts) {
            fact.snapshot().put("participants", participants.getOrDefault(fact.sourceId(), List.of()));
            fact.snapshot().put("expenses", routeExpenses.getOrDefault(fact.sourceId(), List.of()));
        }

        List<Fact> expenseFacts = jdbcTemplate.query("""
                SELECT e.id AS sourceId, e.expense_no AS expenseNo, e.expense_type AS expenseType,
                       e.business_date AS businessDate, e.vehicle_id AS vehicleId, e.driver_id AS driverId,
                       e.amount AS expenseAmount, e.created_by, e.created_at, e.updated_by, e.updated_at, e.reversal_of_id,
                       e.status, e.route_id, e.attribution_type, e.source_type, e.approval_instance_id,
                       a.created_at AS approvalCreatedAt, a.applicant_user_id AS applicantUserId,
                       a.completed_at AS approvedAt,
                       (SELECT act.operator_id FROM approval_action act
                        JOIN approval_task t ON t.id = act.task_id
                        WHERE t.approval_instance_id = a.id AND act.action_type = 'APPROVE'
                        ORDER BY act.operated_at DESC, act.id DESC LIMIT 1) AS finalApproverId,
                       CASE WHEN e.attribution_type = 'DAILY' THEN 'DAILY'
                            WHEN e.status = 'PENDING_ATTRIBUTION' OR e.route_id IS NULL
                              OR r.status <> 'COMPLETED' OR r.id IS NULL THEN 'UNASSIGNED'
                            ELSE 'ROUTE' END AS companyCategory
                FROM expense_entry e LEFT JOIN route_task r ON r.id = e.route_id
                LEFT JOIN approval_instance a ON a.id = e.approval_instance_id
                WHERE e.status IN ('ACTIVE', 'PENDING_ATTRIBUTION', 'REVERSED', 'REVERSAL')
                  AND e.business_date >= ? AND e.business_date < ? AND e.deleted_at IS NULL
                """, (rs, rowNum) -> {
            Map<String, Object> snapshot = new HashMap<>();
            snapshot.put("expenseNo", rs.getString("expenseNo"));
            snapshot.put("expenseType", rs.getString("expenseType"));
            snapshot.put("businessDate", rs.getDate("businessDate").toLocalDate().toString());
            snapshot.put("vehicleId", rs.getLong("vehicleId"));
            snapshot.put("driverId", readLong(rs, "driverId"));
            snapshot.put("status", rs.getString("status"));
            snapshot.put("routeId", readLong(rs, "route_id"));
            snapshot.put("attributionType", rs.getString("attribution_type"));
            snapshot.put("companyCategory", rs.getString("companyCategory"));
            snapshot.put("sourceType", rs.getString("source_type"));
            snapshot.put("approvalInstanceId", readLong(rs, "approval_instance_id"));
            if (rs.getString("source_type").equals("APPROVAL")) {
                snapshot.put("applicationDate", rs.getTimestamp("approvalCreatedAt") == null ? null
                        : rs.getTimestamp("approvalCreatedAt").toLocalDateTime().toLocalDate().toString());
                snapshot.put("applicantUserId", readLong(rs, "applicantUserId"));
                snapshot.put("finalApproverId", readLong(rs, "finalApproverId"));
                snapshot.put("approvedAt", rs.getTimestamp("approvedAt") == null ? null
                        : rs.getTimestamp("approvedAt").toLocalDateTime().toString());
            }
            snapshot.put("createdBy", readLong(rs, "created_by"));
            snapshot.put("createdAt", rs.getTimestamp("created_at").toLocalDateTime().toString());
            snapshot.put("updatedBy", readLong(rs, "updated_by"));
            snapshot.put("updatedAt", rs.getTimestamp("updated_at").toLocalDateTime().toString());
            snapshot.put("reversalOfId", readLong(rs, "reversal_of_id"));
            BigDecimal expenseAmount = rs.getBigDecimal("expenseAmount");
            return new Fact("EXPENSE", rs.getLong("sourceId"), snapshot,
                    BigDecimal.ZERO, expenseAmount, expenseAmount.negate(), BigDecimal.ZERO, BigDecimal.ZERO);
        }, startDate, endDate);

        routeFacts.addAll(expenseFacts);
        return routeFacts;
    }

    private Totals totals(List<Fact> facts) {
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal routeExpense = BigDecimal.ZERO;
        BigDecimal dailyExpense = BigDecimal.ZERO;
        BigDecimal unassignedExpense = BigDecimal.ZERO;
        BigDecimal driverSalary = BigDecimal.ZERO;
        for (Fact fact : facts) {
            if ("ROUTE".equals(fact.factType())) {
                income = income.add(fact.incomeAmount());
                driverSalary = driverSalary.add(fact.driverSalary());
            } else if ("EXPENSE".equals(fact.factType())) {
                String category = String.valueOf(fact.snapshot().get("companyCategory"));
                if ("DAILY".equals(category)) dailyExpense = dailyExpense.add(fact.expenseAmount());
                else if ("ROUTE".equals(category)) routeExpense = routeExpense.add(fact.expenseAmount());
                else unassignedExpense = unassignedExpense.add(fact.expenseAmount());
            }
        }
        return new Totals(income, routeExpense, dailyExpense, unassignedExpense, driverSalary,
                income.subtract(routeExpense).subtract(dailyExpense).subtract(unassignedExpense).subtract(driverSalary));
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
                        || !snapshot(afterJson).equals(snapshot(before.snapshotJson()))) {
                    BusinessChange change = businessChange(fact, before);
                    insertDiff(versionId, previousVersionId, fact.factType(), fact.sourceId(), change.type(),
                            before.snapshotJson(), afterJson, incomeDelta, expenseDelta, profitDelta, change);
                }
            }
        }
        for (DetailFact removed : previous.values()) {
            BusinessChange change = "ROUTE".equals(removed.factType())
                    ? routeStatusChange(removed.sourceId(), "VOID", "ROUTE_VOID") : null;
            if (change == null) change = new BusinessChange("DELETE", null, null, null);
            insertDiff(versionId, previousVersionId, removed.factType(), removed.sourceId(), change.type(),
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
        if ("DAILY_EXPENSE".equals(after.factType()) || "EXPENSE".equals(after.factType())) {
            if (after.snapshot().get("reversalOfId") != null || "REVERSAL".equals(after.snapshot().get("status"))) {
                return reversalChange(after.sourceId());
            }
            JsonNode previous = before == null ? null : snapshot(before.snapshotJson());
            if (previous != null && (!java.util.Objects.equals(stringValue(after.snapshot().get("routeId")),
                    previous.path("routeId").isNull() ? null : previous.path("routeId").asText())
                    || !java.util.Objects.equals(stringValue(after.snapshot().get("companyCategory")),
                    previous.path("companyCategory").asText(null)))) {
                BusinessChange attribution = expenseAttributionChange(after.sourceId());
                if (attribution != null) return attribution;
            }
            return new BusinessChange(before == null ? "ADD" : "MODIFY",
                    longValue(after.snapshot().get("updatedBy"), after.snapshot().get("createdBy")),
                    timeValue(after.snapshot().get("updatedAt"), after.snapshot().get("createdAt")),
                    null);
        }
        if ("ROUTE".equals(after.factType())) {
            if (before == null) {
                BusinessChange reactivated = routeStatusChange(after.sourceId(), "REACTIVATE", "ROUTE_REACTIVATE");
                if (reactivated != null) return reactivated;
            }
            Long weightVersionId = longValue(after.snapshot().get("effectiveWeightVersionId"));
            JsonNode previousSnapshot = before == null ? null : snapshot(before.snapshotJson());
            JsonNode previousWeight = previousSnapshot == null ? null : previousSnapshot.get("effectiveWeightVersionId");
            Long previousWeightVersionId = previousWeight == null || previousWeight.isNull() ? null : previousWeight.longValue();
            if (before != null && weightVersionId != null && !weightVersionId.equals(previousWeightVersionId)) {
                return new BusinessChange("WEIGHT_ADJUST", longValue(after.snapshot().get("weightOperatorId")),
                        timeValue(after.snapshot().get("weightOperationTime")), stringValue(after.snapshot().get("weightReason")));
            }
            if (before != null && !participantIdentity(after.snapshot().get("participants"))
                    .equals(participantIdentity(previousSnapshot.path("participants")))) {
                return new BusinessChange("ROUTE_TRANSFER", null, null, null);
            }
            boolean salaryChanged = false;
            if (before != null) {
                JsonNode previousSalaryNode = previousSnapshot.get("driverSalary");
                BigDecimal previousSalary = previousSalaryNode == null || previousSalaryNode.isNull()
                        ? BigDecimal.ZERO : previousSalaryNode.decimalValue();
                BigDecimal currentSalary = (BigDecimal) after.snapshot().get("driverSalary");
                salaryChanged = previousSalary.compareTo(currentSalary) != 0;
                BigDecimal previousRouteExpense = before.expenseAmount().subtract(previousSalary);
                BigDecimal currentRouteExpense = after.expenseAmount().subtract(currentSalary);
                if (salaryChanged && previousRouteExpense.compareTo(currentRouteExpense) == 0) {
                    return salaryChange(after.sourceId());
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
            if (salaryChanged) {
                return salaryChange(after.sourceId());
            }
            return new BusinessChange(before == null ? "ADD" : "MODIFY", null, null, null);
        }
        return new BusinessChange(before == null ? "ADD" : "MODIFY", null, null, null);
    }

    private JsonNode snapshot(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to read settlement snapshot", ex);
        }
    }

    private java.util.List<String> participantIdentity(Object value) {
        JsonNode nodes = objectMapper.valueToTree(value);
        java.util.List<String> identities = new java.util.ArrayList<>();
        if (nodes.isArray()) for (JsonNode node : nodes) identities.add(
                node.path("participantId").asText() + ":" + node.path("sequenceNo").asText()
                + ":" + node.path("driverId").asText() + ":" + node.path("driverType").asText()
                + ":" + node.path("companyVehicleId").asText() + ":" + node.path("status").asText());
        return identities;
    }

    private BusinessChange salaryChange(long routeId) {
        List<BusinessChange> legacySalary = jdbcTemplate.query("""
                SELECT operator_id, operation_time, reason FROM driver_salary_history
                WHERE route_id = ? ORDER BY operation_time DESC, id DESC LIMIT 1
                """, (rs, rowNum) -> new BusinessChange("SALARY_ADJUST", readLong(rs, "operator_id"),
                rs.getTimestamp("operation_time").toLocalDateTime(), rs.getString("reason")), routeId);
        List<BusinessChange> participantSalary = jdbcTemplate.query("""
                SELECT h.operator_id, h.operation_time, h.reason
                FROM route_participant_salary_history h
                JOIN route_participant p ON p.id = h.participant_id
                WHERE p.route_id = ?
                ORDER BY h.operation_time DESC, h.id DESC
                LIMIT 1
                """, (rs, rowNum) -> new BusinessChange("SALARY_ADJUST", readLong(rs, "operator_id"),
                rs.getTimestamp("operation_time").toLocalDateTime(),
                rs.getString("reason")), routeId);
        if (participantSalary.isEmpty()) return legacySalary.isEmpty()
                ? new BusinessChange("SALARY_ADJUST", null, null, null) : legacySalary.get(0);
        if (legacySalary.isEmpty()) return participantSalary.get(0);
        if (java.util.Objects.equals(legacySalary.get(0).operatorId(), participantSalary.get(0).operatorId())
                && java.time.Duration.between(legacySalary.get(0).operatedAt(), participantSalary.get(0).operatedAt()).abs().toSeconds() < 1)
            return legacySalary.get(0);
        return legacySalary.get(0).operatedAt().isAfter(participantSalary.get(0).operatedAt())
                ? legacySalary.get(0) : participantSalary.get(0);
    }

    private BusinessChange reassignmentChange(long routeId) {
        return jdbcTemplate.query("""
                SELECT operator_id, operation_time, reason
                FROM expense_attribution_history
                WHERE (before_route_id = ? OR after_route_id = ?)
                ORDER BY operation_time DESC
                LIMIT 1
                """, (rs, rowNum) -> new BusinessChange("EXPENSE_ATTRIBUTION_ADJUST", readLong(rs, "operator_id"),
                rs.getTimestamp("operation_time").toLocalDateTime(),
                rs.getString("reason")), routeId, routeId).stream().findFirst().orElse(null);
    }

    private BusinessChange expenseAttributionChange(long expenseId) {
        return jdbcTemplate.query("""
                SELECT operator_id, operation_time, reason FROM expense_attribution_history
                WHERE expense_id = ? ORDER BY operation_time DESC, id DESC LIMIT 1
                """, (rs, row) -> new BusinessChange("EXPENSE_ATTRIBUTION_ADJUST", readLong(rs, "operator_id"),
                rs.getTimestamp("operation_time").toLocalDateTime(), rs.getString("reason")), expenseId)
                .stream().findFirst().orElse(null);
    }

    private BusinessChange routeStatusChange(long routeId, String operation, String changeType) {
        return jdbcTemplate.query("""
                SELECT operator_id, operation_time, reason FROM route_status_history
                WHERE route_id = ? AND operation_type = ? ORDER BY operation_time DESC, id DESC LIMIT 1
                """, (rs, row) -> new BusinessChange(changeType, readLong(rs, "operator_id"),
                rs.getTimestamp("operation_time").toLocalDateTime(), rs.getString("reason")), routeId, operation)
                .stream().findFirst().orElse(null);
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
                          BigDecimal unassignedExpense, BigDecimal driverSalary, BigDecimal profit) {
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
