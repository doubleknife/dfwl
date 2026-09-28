package com.dfwl.fleet.report.repository;

import com.dfwl.fleet.common.api.PageResponse;
import com.dfwl.fleet.common.error.BusinessException;
import com.dfwl.fleet.common.error.ErrorCode;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ReportRepository {

    private static final String OPERATING_SELECT = """
            SELECT r.id AS routeId, r.route_no AS routeNo, r.business_date AS businessDate,
                   r.status, r.loading_place AS loadingPlace, r.unloading_place AS unloadingPlace,
                   w.net_weight AS netWeight, w.load_standard_met AS loadStandardMet,
                   r.tax_unit_price AS taxUnitPrice,
                   COALESCE(w.net_weight * r.tax_unit_price, 0) AS factualIncome,
                   COALESCE(x.routeExpense, 0) AS attributedExpense,
                   COALESCE(s.salaryAmount, 0) AS participantSalary,
                   COALESCE(s.participantCount, 0) AS participantCount,
                   COALESCE(s.vehicleCount, 0) AS vehicleCount,
                   CASE WHEN r.status = 'COMPLETED' THEN COALESCE(w.net_weight * r.tax_unit_price, 0) ELSE 0 END AS incomeAmount,
                   CASE WHEN r.status = 'COMPLETED' THEN COALESCE(x.routeExpense, 0) + COALESCE(s.salaryAmount, 0) ELSE 0 END AS expenseTotal,
                   CASE WHEN r.status = 'COMPLETED' THEN COALESCE(w.net_weight * r.tax_unit_price, 0)
                         - COALESCE(x.routeExpense, 0) - COALESCE(s.salaryAmount, 0) ELSE 0 END AS profitAmount
            FROM route_task r
            LEFT JOIN route_weight_version w ON w.id = r.effective_weight_version_id
            LEFT JOIN (
                SELECT route_id, SUM(amount) AS routeExpense FROM expense_entry
                WHERE attribution_type = 'ROUTE' AND status IN ('ACTIVE', 'REVERSED', 'REVERSAL')
                  AND deleted_at IS NULL GROUP BY route_id
            ) x ON x.route_id = r.id
            LEFT JOIN (
                SELECT route_id, SUM(COALESCE(salary_amount, 0)) AS salaryAmount,
                       COUNT(*) AS participantCount,
                       COUNT(DISTINCT CASE WHEN driver_type_snapshot = 'INTERNAL' THEN company_vehicle_id END) AS vehicleCount
                FROM route_participant WHERE status NOT IN ('CANCELLED', 'LEGACY_ENDED') GROUP BY route_id
            ) s ON s.route_id = r.id
            WHERE r.deleted_at IS NULL AND r.status IN ('COMPLETED', 'VOIDED')
            """;

    private static final String EFFECTIVE_EXPENSE_STATUS = "e.status IN ('ACTIVE', 'REVERSED', 'REVERSAL')";
    private static final String EFFECTIVE_ROUTE_STATUS = "r.status = 'COMPLETED' AND r.deleted_at IS NULL";

    private final JdbcTemplate jdbcTemplate;

    public ReportRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> dashboard() {
        BigDecimal income = income();
        BigDecimal routeExpense = routeExpense();
        BigDecimal dailyExpense = dailyExpense();
        BigDecimal unassignedExpense = companyExpense().subtract(routeExpense).subtract(dailyExpense);
        BigDecimal driverSalary = driverSalary();
        BigDecimal routeProfit = income.subtract(routeExpense).subtract(driverSalary);
        BigDecimal companyProfit = income.subtract(companyExpense()).subtract(driverSalary);
        boolean salaryComplete = scalar("""
                SELECT COUNT(*)
                FROM route_task r JOIN route_participant p ON p.route_id = r.id
                WHERE r.status = 'COMPLETED' AND r.deleted_at IS NULL
                  AND p.status NOT IN ('CANCELLED', 'LEGACY_ENDED') AND p.salary_amount IS NULL
                """) == 0;
        return Map.ofEntries(
                Map.entry("routeCount", scalar("SELECT COUNT(*) FROM route_task WHERE deleted_at IS NULL")),
                Map.entry("completedRouteCount", scalar("SELECT COUNT(*) FROM route_task WHERE status = 'COMPLETED' AND deleted_at IS NULL")),
                Map.entry("incomeAmount", income),
                Map.entry("routeExpense", routeExpense),
                Map.entry("dailyExpense", dailyExpense),
                Map.entry("unassignedExpense", unassignedExpense),
                Map.entry("companyExpense", companyExpense()),
                Map.entry("driverSalary", driverSalary),
                Map.entry("routeProfit", routeProfit),
                Map.entry("totalProfit", companyProfit),
                Map.entry("companyTotalProfit", companyProfit),
                Map.entry("salaryComplete", salaryComplete),
                Map.entry("salaryMissing", !salaryComplete),
                Map.entry("month", monthSummary()),
                Map.entry("year", yearSummary()),
                Map.entry("monthlyTrend", monthlyTrend()));
    }

    public List<Map<String, Object>> profit() {
        return jdbcTemplate.queryForList("""
                SELECT r.id AS routeId,
                       r.route_no AS routeNo,
                       r.business_date AS businessDate,
                       c.customer_name AS customerName,
                       p.product_name AS productName,
                       r.departure_driver_id AS driverId,
                       d.name AS driverName,
                       r.departure_vehicle_id AS vehicleId,
                       v.plate_no AS plateNo,
                       r.loading_place AS loadingPlace,
                       r.unloading_place AS unloadingPlace,
                       COALESCE(w.net_weight, 0) AS actualWeight,
                       r.tax_unit_price AS taxUnitPrice,
                       COALESCE(w.net_weight * r.tax_unit_price, 0) AS incomeAmount,
                       COALESCE(exp.electricAmount, 0) AS electricAmount,
                       COALESCE(exp.electricQuantity, 0) AS electricQuantity,
                       COALESCE(exp.tempElectricAmount, 0) AS tempElectricAmount,
                       COALESCE(exp.tempElectricQuantity, 0) AS tempElectricQuantity,
                       COALESCE(exp.carWashAmount, 0) AS carWashAmount,
                       COALESCE(exp.waterAmount, 0) AS waterAmount,
                       COALESCE(exp.penaltyAmount, 0) AS penaltyAmount,
                       COALESCE(exp.repairAmount, 0) AS repairAmount,
                       COALESCE(exp.routeExpense, 0) AS routeExpense,
                       COALESCE(s.salaryAmount, 0) AS driverSalary,
                       CASE WHEN s.missingCount = 0 THEN 1 ELSE 0 END AS salaryComplete,
                       COALESCE(w.net_weight * r.tax_unit_price, 0) AS incomeTotal,
                       COALESCE(exp.routeExpense, 0) + COALESCE(s.salaryAmount, 0) AS expenseTotal,
                       COALESCE(w.net_weight * r.tax_unit_price, 0) - COALESCE(exp.routeExpense, 0)
                         - COALESCE(s.salaryAmount, 0) AS profitAmount
                FROM route_task r
                LEFT JOIN customer c ON c.id = r.customer_id
                LEFT JOIN product p ON p.id = r.product_id
                LEFT JOIN driver d ON d.id = r.departure_driver_id
                LEFT JOIN vehicle v ON v.id = r.departure_vehicle_id
                LEFT JOIN route_weight_version w ON w.id = r.effective_weight_version_id
                LEFT JOIN (
                    SELECT route_id, SUM(COALESCE(salary_amount, 0)) AS salaryAmount,
                           SUM(CASE WHEN salary_amount IS NULL THEN 1 ELSE 0 END) AS missingCount
                    FROM route_participant
                    WHERE status NOT IN ('CANCELLED', 'LEGACY_ENDED')
                    GROUP BY route_id
                ) s ON s.route_id = r.id
                LEFT JOIN (
                    SELECT e.route_id,
                           SUM(e.amount) AS routeExpense,
                           SUM(CASE WHEN e.expense_type = 'ELECTRIC' THEN e.amount ELSE 0 END) AS electricAmount,
                           SUM(CASE WHEN e.expense_type = 'ELECTRIC' THEN COALESCE(ed.quantity, 0) ELSE 0 END) AS electricQuantity,
                           SUM(CASE WHEN e.expense_type = 'TEMP_ELECTRIC' THEN e.amount ELSE 0 END) AS tempElectricAmount,
                           SUM(CASE WHEN e.expense_type = 'TEMP_ELECTRIC' THEN COALESCE(ed.quantity, 0) ELSE 0 END) AS tempElectricQuantity,
                           SUM(CASE WHEN e.expense_type = 'CAR_WASH' THEN e.amount ELSE 0 END) AS carWashAmount,
                           SUM(CASE WHEN e.expense_type = 'WATER' THEN e.amount ELSE 0 END) AS waterAmount,
                           SUM(CASE WHEN e.expense_type = 'PENALTY' THEN e.amount ELSE 0 END) AS penaltyAmount,
                           SUM(CASE WHEN e.expense_type = 'REPAIR' THEN e.amount ELSE 0 END) AS repairAmount
                    FROM expense_entry e
                    LEFT JOIN expense_energy_detail ed ON ed.expense_id = e.id
                    WHERE e.attribution_type = 'ROUTE'
                      AND e.status IN ('ACTIVE', 'REVERSED', 'REVERSAL')
                      AND e.deleted_at IS NULL
                    GROUP BY e.route_id
                ) exp ON exp.route_id = r.id
                WHERE r.status = 'COMPLETED' AND r.deleted_at IS NULL
                ORDER BY r.business_date DESC, r.id DESC
                """);
    }

    public PageResponse<Map<String, Object>> completedRoutes(int pageNo, int pageSize, String status) {
        int safePage = Math.max(1, pageNo);
        int safeSize = Math.min(100, Math.max(1, pageSize));
        if (status != null && !status.equals("COMPLETED") && !status.equals("VOIDED"))
            throw new BusinessException(ErrorCode.SYS_002);
        Long total = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM route_task r WHERE r.deleted_at IS NULL
                  AND r.status IN ('COMPLETED', 'VOIDED') AND (? IS NULL OR r.status = ?)
                """, Long.class, status, status);
        List<Map<String, Object>> records = jdbcTemplate.queryForList(
                OPERATING_SELECT + " AND (? IS NULL OR r.status = ?) ORDER BY r.business_date DESC, r.id DESC LIMIT ? OFFSET ?",
                status, status, safeSize, (safePage - 1) * safeSize);
        return new PageResponse<>(safePage, safeSize, total == null ? 0 : total, records);
    }

    public Map<String, Object> routeArchive(long routeId, int expensePageNo, int expensePageSize) {
        List<Map<String, Object>> summaries = jdbcTemplate.queryForList(OPERATING_SELECT + " AND r.id = ?", routeId);
        if (summaries.isEmpty()) throw new BusinessException(ErrorCode.ROUTE_001);
        int safePage = Math.max(1, expensePageNo);
        int safeSize = Math.min(100, Math.max(1, expensePageSize));
        Long total = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM expense_entry WHERE route_id = ? AND attribution_type = 'ROUTE'
                  AND status IN ('ACTIVE', 'REVERSED', 'REVERSAL') AND deleted_at IS NULL
                """, Long.class, routeId);
        List<Map<String, Object>> expenses = jdbcTemplate.queryForList("""
                SELECT e.id, e.expense_no AS expenseNo, e.expense_type AS expenseType,
                       e.vehicle_id AS vehicleId, v.plate_no AS plateNo,
                       e.source_type AS sourceType, e.attribution_type AS attributionType,
                       e.approval_instance_id AS sourceApprovalId,
                       e.import_row_id AS sourceImportRowId, e.amount, e.status, e.business_date AS businessDate
                FROM expense_entry e JOIN vehicle v ON v.id = e.vehicle_id
                WHERE e.route_id = ? AND e.attribution_type = 'ROUTE'
                  AND e.status IN ('ACTIVE', 'REVERSED', 'REVERSAL') AND e.deleted_at IS NULL
                ORDER BY e.business_date DESC, e.id DESC LIMIT ? OFFSET ?
                """, routeId, safeSize, (safePage - 1) * safeSize);
        List<Map<String, Object>> expenseSummary = jdbcTemplate.queryForList("""
                SELECT e.expense_type AS expenseType, e.vehicle_id AS vehicleId,
                       v.plate_no AS plateNo, e.source_type AS sourceType,
                       COUNT(*) AS expenseCount, COALESCE(SUM(e.amount), 0) AS amount
                FROM expense_entry e JOIN vehicle v ON v.id = e.vehicle_id
                WHERE e.route_id = ? AND e.attribution_type = 'ROUTE'
                  AND e.status IN ('ACTIVE', 'REVERSED', 'REVERSAL') AND e.deleted_at IS NULL
                GROUP BY e.expense_type, e.vehicle_id, v.plate_no, e.source_type
                ORDER BY e.expense_type, e.vehicle_id, e.source_type
                """, routeId);
        List<Map<String, Object>> participants = jdbcTemplate.queryForList("""
                SELECT p.id AS participantId, p.sequence_no AS sequenceNo, p.driver_id AS driverId,
                       d.name AS driverName, p.driver_type_snapshot AS driverType,
                       p.company_vehicle_id AS companyVehicleId, v.plate_no AS companyPlateNo,
                       p.outsourced_plate_no AS outsourcedPlateNo,
                       p.salary_amount AS salaryAmount, p.status, p.assigned_at AS assignedAt,
                       p.departed_at AS departedAt, p.ended_at AS endedAt
                FROM route_participant p LEFT JOIN driver d ON d.id = p.driver_id
                LEFT JOIN vehicle v ON v.id = p.company_vehicle_id
                WHERE p.route_id = ? ORDER BY p.sequence_no
                """, routeId);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("summary", summaries.get(0));
        result.put("participants", participants);
        result.put("expenseSummary", expenseSummary);
        result.put("expenses", new PageResponse<>(safePage, safeSize, total == null ? 0 : total, expenses));
        return result;
    }

    public List<Map<String, Object>> vehicleExpense() {
        return jdbcTemplate.queryForList("""
                SELECT v.id AS vehicleId,
                       v.plate_no AS plateNo,
                       COALESCE(SUM(CASE WHEN e.expense_type = 'PENALTY' THEN e.amount ELSE 0 END), 0) AS penaltyAmount,
                       COALESCE(SUM(CASE WHEN e.expense_type = 'CAR_WASH' THEN e.amount ELSE 0 END), 0) AS carWashAmount,
                       COALESCE(SUM(CASE WHEN e.expense_type = 'WATER' THEN e.amount ELSE 0 END), 0) AS waterAmount,
                       COALESCE(SUM(CASE WHEN e.expense_type = 'REPAIR' THEN e.amount ELSE 0 END), 0) AS repairAmount,
                       COALESCE(SUM(CASE WHEN e.expense_type IN ('ELECTRIC', 'GAS', 'TEMP_ELECTRIC') THEN e.amount ELSE 0 END), 0) AS energyAmount,
                       COALESCE(routeStats.routeCount, 0) AS routeCount,
                       COALESCE(SUM(e.amount), 0) AS amount
                FROM vehicle v
                LEFT JOIN expense_entry e ON e.vehicle_id = v.id
                  AND e.status IN ('ACTIVE', 'PENDING_ATTRIBUTION', 'REVERSED', 'REVERSAL') AND e.deleted_at IS NULL
                LEFT JOIN (
                    SELECT p.company_vehicle_id AS vehicle_id, COUNT(DISTINCT r.id) AS routeCount
                    FROM route_task r JOIN route_participant p ON p.route_id = r.id
                    WHERE r.status = 'COMPLETED' AND r.deleted_at IS NULL
                      AND p.driver_type_snapshot = 'INTERNAL' AND p.status NOT IN ('CANCELLED', 'LEGACY_ENDED')
                    GROUP BY p.company_vehicle_id
                ) routeStats ON routeStats.vehicle_id = v.id
                GROUP BY v.id, v.plate_no, routeStats.routeCount
                ORDER BY amount DESC, v.id
                """);
    }

    public List<Map<String, Object>> driverExpense() {
        return jdbcTemplate.queryForList("""
                SELECT d.id AS driverId,
                       d.name AS driverName,
                       COALESCE(routeStats.routeCount, 0) AS routeCount,
                       COALESCE(routeStats.mileageKm, 0) AS mileageKm,
                       COALESCE(SUM(CASE WHEN e.expense_type = 'PENALTY' THEN e.amount ELSE 0 END), 0) AS penaltyAmount,
                       COALESCE(SUM(CASE WHEN e.expense_type = 'CAR_WASH' THEN e.amount ELSE 0 END), 0) AS carWashAmount,
                       COALESCE(SUM(CASE WHEN e.expense_type = 'WATER' THEN e.amount ELSE 0 END), 0) AS waterAmount,
                       COALESCE(SUM(CASE WHEN e.expense_type = 'REPAIR' THEN e.amount ELSE 0 END), 0) AS repairAmount,
                       COALESCE(routeStats.driverSalary, 0) AS driverSalary,
                       CASE WHEN COALESCE(routeStats.missingSalaryCount, 0) = 0 THEN 1 ELSE 0 END AS salaryComplete,
                       COALESCE(SUM(e.amount), 0) AS amount
                FROM driver d
                LEFT JOIN expense_entry e ON e.driver_id = d.id
                  AND e.status IN ('ACTIVE', 'PENDING_ATTRIBUTION', 'REVERSED', 'REVERSAL') AND e.deleted_at IS NULL
                LEFT JOIN (
                    SELECT d.driver_id, COUNT(*) AS routeCount,
                           COALESCE(SUM(d.mileageKm), 0) AS mileageKm,
                           COALESCE(SUM(d.driverSalary), 0) AS driverSalary,
                           SUM(d.missingSalaryCount) AS missingSalaryCount
                    FROM (
                        SELECT p.driver_id, r.id AS route_id, MAX(r.mileage_km) AS mileageKm,
                               SUM(COALESCE(p.salary_amount, 0)) AS driverSalary,
                               SUM(CASE WHEN p.salary_amount IS NULL THEN 1 ELSE 0 END) AS missingSalaryCount
                        FROM route_task r JOIN route_participant p ON p.route_id = r.id
                        WHERE r.status = 'COMPLETED' AND r.deleted_at IS NULL
                          AND p.status NOT IN ('CANCELLED', 'LEGACY_ENDED')
                        GROUP BY p.driver_id, r.id
                    ) d GROUP BY d.driver_id
                ) routeStats ON routeStats.driver_id = d.id
                GROUP BY d.id, d.name, routeStats.routeCount, routeStats.mileageKm, routeStats.driverSalary, routeStats.missingSalaryCount
                ORDER BY routeCount DESC, d.id
                """);
    }

    public List<Map<String, Object>> attendance() {
        return jdbcTemplate.queryForList("""
                SELECT p.driver_id AS driverId,
                       d.name AS driverName,
                       r.business_date AS workDate,
                       COUNT(DISTINCT r.id) AS routeCount,
                       COUNT(DISTINCT r.business_date) AS workDays,
                       CASE WHEN COUNT(*) > 0 THEN 1 ELSE 0 END AS worked
                FROM route_task r
                JOIN route_participant p ON p.route_id = r.id
                  AND p.status NOT IN ('CANCELLED', 'LEGACY_ENDED')
                LEFT JOIN driver d ON d.id = p.driver_id
                WHERE p.driver_id IS NOT NULL
                  AND r.status IN ('IN_TRANSIT', 'COMPLETED')
                  AND r.deleted_at IS NULL
                GROUP BY p.driver_id, d.name, r.business_date
                ORDER BY r.business_date DESC, p.driver_id
                """);
    }

    public List<Map<String, Object>> energy() {
        return jdbcTemplate.queryForList("""
                SELECT e.vehicle_id AS vehicleId,
                       v.plate_no AS plateNo,
                       d.energy_type AS energyType,
                       COALESCE(SUM(d.quantity), 0) AS quantity,
                       COALESCE(SUM(e.amount), 0) AS amount,
                       COUNT(*) AS orderCount
                FROM expense_energy_detail d
                JOIN expense_entry e ON e.id = d.expense_id
                JOIN vehicle v ON v.id = e.vehicle_id
                WHERE e.status IN ('ACTIVE', 'PENDING_ATTRIBUTION', 'REVERSED', 'REVERSAL') AND e.deleted_at IS NULL
                GROUP BY e.vehicle_id, v.plate_no, d.energy_type
                ORDER BY v.plate_no, d.energy_type
                """);
    }

    private Map<String, Object> monthSummary() {
        return summary("DATE_FORMAT(r.business_date, '%Y-%m') = DATE_FORMAT(CURRENT_DATE, '%Y-%m')",
                "DATE_FORMAT(e.business_date, '%Y-%m') = DATE_FORMAT(CURRENT_DATE, '%Y-%m')");
    }

    private Map<String, Object> yearSummary() {
        return summary("YEAR(r.business_date) = YEAR(CURRENT_DATE)", "YEAR(e.business_date) = YEAR(CURRENT_DATE)");
    }

    private Map<String, Object> summary(String routeDatePredicate, String expenseDatePredicate) {
        BigDecimal income = amount("""
                SELECT COALESCE(SUM(w.net_weight * r.tax_unit_price), 0)
                FROM route_task r
                JOIN route_weight_version w ON w.id = r.effective_weight_version_id
                WHERE r.status = 'COMPLETED' AND r.deleted_at IS NULL AND %s
                """.formatted(routeDatePredicate));
        BigDecimal routeExpense = expenseAmount("ROUTE", expenseDatePredicate);
        BigDecimal dailyExpense = expenseAmount("DAILY", expenseDatePredicate);
        BigDecimal salary = amount("""
                SELECT COALESCE(SUM(p.salary_amount), 0)
                FROM route_participant p JOIN route_task r ON r.id = p.route_id
                WHERE p.status NOT IN ('CANCELLED', 'LEGACY_ENDED')
                  AND r.status = 'COMPLETED' AND r.deleted_at IS NULL AND %s
                """.formatted(routeDatePredicate));
        BigDecimal companyExpense = companyExpense(expenseDatePredicate);
        BigDecimal unassigned = companyExpense.subtract(routeExpense).subtract(dailyExpense);
        long routeCount = scalar("""
                SELECT COUNT(*)
                FROM route_task r
                WHERE r.status = 'COMPLETED' AND r.deleted_at IS NULL AND %s
                """.formatted(routeDatePredicate));
        return Map.ofEntries(
                Map.entry("incomeAmount", income), Map.entry("routeExpense", routeExpense),
                Map.entry("dailyExpense", dailyExpense), Map.entry("unassignedExpense", unassigned),
                Map.entry("companyExpense", companyExpense), Map.entry("driverSalary", salary),
                Map.entry("routeProfit", income.subtract(routeExpense).subtract(salary)),
                Map.entry("companyTotalProfit", income.subtract(companyExpense).subtract(salary)),
                Map.entry("routeCount", routeCount));
    }

    private List<Map<String, Object>> monthlyTrend() {
        return jdbcTemplate.queryForList("""
                SELECT m.monthValue AS monthLabel,
                       COALESCE(i.incomeAmount, 0) AS incomeAmount,
                       COALESCE(e.expenseAmount, 0) AS expenseAmount,
                       COALESCE(i.incomeAmount, 0) - COALESCE(i.salaryAmount, 0)
                         - COALESCE(e.expenseAmount, 0) AS profitAmount
                FROM (
                    SELECT DATE_FORMAT(business_date, '%Y-%m') AS monthValue FROM route_task WHERE deleted_at IS NULL
                    UNION
                    SELECT DATE_FORMAT(business_date, '%Y-%m') AS monthValue FROM expense_entry WHERE deleted_at IS NULL
                ) m
                LEFT JOIN (
                    SELECT DATE_FORMAT(r.business_date, '%Y-%m') AS monthValue,
                           SUM(w.net_weight * r.tax_unit_price) AS incomeAmount,
                           COALESCE(SUM((SELECT SUM(p.salary_amount) FROM route_participant p
                                  WHERE p.route_id = r.id AND p.status NOT IN ('CANCELLED', 'LEGACY_ENDED'))), 0) AS salaryAmount
                    FROM route_task r
                    JOIN route_weight_version w ON w.id = r.effective_weight_version_id
                    WHERE r.status = 'COMPLETED' AND r.deleted_at IS NULL
                    GROUP BY DATE_FORMAT(r.business_date, '%Y-%m')
                ) i ON i.monthValue = m.monthValue
                LEFT JOIN (
                    SELECT DATE_FORMAT(e.business_date, '%Y-%m') AS monthValue,
                           SUM(e.amount) AS expenseAmount
                    FROM expense_entry e
                    WHERE e.status IN ('ACTIVE', 'PENDING_ATTRIBUTION', 'REVERSED', 'REVERSAL') AND e.deleted_at IS NULL
                    GROUP BY DATE_FORMAT(e.business_date, '%Y-%m')
                ) e ON e.monthValue = m.monthValue
                ORDER BY m.monthValue
                """);
    }

    private BigDecimal totalProfit() {
        return income().subtract(companyExpense()).subtract(driverSalary());
    }

    public BigDecimal income() {
        return amount("""
                SELECT COALESCE(SUM(w.net_weight * r.tax_unit_price), 0)
                FROM route_task r
                JOIN route_weight_version w ON w.id = r.effective_weight_version_id
                WHERE r.status = 'COMPLETED' AND r.deleted_at IS NULL
                """);
    }

    public BigDecimal routeExpense() {
        return expenseAmount("ROUTE", "1 = 1");
    }

    public BigDecimal dailyExpense() {
        return expenseAmount("DAILY", "1 = 1");
    }

    private BigDecimal expenseAmount(String attributionType, String datePredicate) {
        if ("ROUTE".equals(attributionType)) return amount("""
                SELECT COALESCE(SUM(e.amount), 0)
                FROM expense_entry e JOIN route_task r ON r.id = e.route_id
                WHERE e.attribution_type = 'ROUTE' AND r.status = 'COMPLETED' AND r.deleted_at IS NULL
                  AND e.status IN ('ACTIVE', 'REVERSED', 'REVERSAL')
                  AND e.deleted_at IS NULL AND %s
                """.formatted(datePredicate));
        return amount("""
                SELECT COALESCE(SUM(e.amount), 0)
                FROM expense_entry e
                WHERE e.attribution_type = '%s'
                  AND e.status IN ('ACTIVE', 'REVERSED', 'REVERSAL')
                  AND e.deleted_at IS NULL
                  AND %s
                """.formatted(attributionType, datePredicate));
    }

    public BigDecimal companyExpense() {
        return companyExpense("1 = 1");
    }

    private BigDecimal companyExpense(String datePredicate) {
        return amount("""
                SELECT COALESCE(SUM(e.amount), 0) FROM expense_entry e
                WHERE e.status IN ('ACTIVE', 'PENDING_ATTRIBUTION', 'REVERSED', 'REVERSAL')
                  AND e.deleted_at IS NULL AND %s
                """.formatted(datePredicate));
    }

    public BigDecimal driverSalary() {
        return amount("""
                SELECT COALESCE(SUM(p.salary_amount), 0)
                FROM route_participant p JOIN route_task r ON r.id = p.route_id
                WHERE r.status = 'COMPLETED' AND r.deleted_at IS NULL
                  AND p.status NOT IN ('CANCELLED', 'LEGACY_ENDED')
                """);
    }

    private BigDecimal amount(String sql) {
        BigDecimal value = jdbcTemplate.queryForObject(sql, BigDecimal.class);
        return value == null ? BigDecimal.ZERO : value;
    }

    private long scalar(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }
}
