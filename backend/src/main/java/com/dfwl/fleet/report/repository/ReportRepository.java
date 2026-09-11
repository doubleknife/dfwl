package com.dfwl.fleet.report.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ReportRepository {

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
        BigDecimal driverSalary = driverSalary();
        BigDecimal routeProfit = income.subtract(routeExpense).subtract(driverSalary);
        BigDecimal companyProfit = routeProfit.subtract(dailyExpense);
        boolean salaryComplete = scalar("""
                SELECT COUNT(*)
                FROM route_task
                WHERE status = 'COMPLETED' AND deleted_at IS NULL AND driver_salary IS NULL
                """) == 0;
        return Map.ofEntries(
                Map.entry("routeCount", scalar("SELECT COUNT(*) FROM route_task WHERE deleted_at IS NULL")),
                Map.entry("completedRouteCount", scalar("SELECT COUNT(*) FROM route_task WHERE status = 'COMPLETED' AND deleted_at IS NULL")),
                Map.entry("incomeAmount", income),
                Map.entry("routeExpense", routeExpense),
                Map.entry("dailyExpense", dailyExpense),
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
                       COALESCE(r.driver_salary, 0) AS driverSalary,
                       CASE WHEN r.driver_salary IS NULL THEN 0 ELSE 1 END AS salaryComplete,
                       COALESCE(w.net_weight * r.tax_unit_price, 0) AS incomeTotal,
                       COALESCE(exp.routeExpense, 0) + COALESCE(r.driver_salary, 0) AS expenseTotal,
                       COALESCE(w.net_weight * r.tax_unit_price, 0) - COALESCE(exp.routeExpense, 0)
                         - COALESCE(r.driver_salary, 0) AS profitAmount
                FROM route_task r
                LEFT JOIN customer c ON c.id = r.customer_id
                LEFT JOIN product p ON p.id = r.product_id
                LEFT JOIN driver d ON d.id = r.departure_driver_id
                LEFT JOIN vehicle v ON v.id = r.departure_vehicle_id
                LEFT JOIN route_weight_version w ON w.id = r.effective_weight_version_id
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
                  AND e.status IN ('ACTIVE', 'REVERSED', 'REVERSAL') AND e.deleted_at IS NULL
                LEFT JOIN (
                    SELECT departure_vehicle_id AS vehicle_id, COUNT(*) AS routeCount
                    FROM route_task
                    WHERE status = 'COMPLETED' AND deleted_at IS NULL
                    GROUP BY departure_vehicle_id
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
                  AND e.status IN ('ACTIVE', 'REVERSED', 'REVERSAL') AND e.deleted_at IS NULL
                LEFT JOIN (
                    SELECT departure_driver_id AS driver_id,
                           COUNT(*) AS routeCount,
                           COALESCE(SUM(mileage_km), 0) AS mileageKm,
                           COALESCE(SUM(driver_salary), 0) AS driverSalary,
                           SUM(CASE WHEN driver_salary IS NULL THEN 1 ELSE 0 END) AS missingSalaryCount
                    FROM route_task
                    WHERE status = 'COMPLETED' AND deleted_at IS NULL
                    GROUP BY departure_driver_id
                ) routeStats ON routeStats.driver_id = d.id
                GROUP BY d.id, d.name, routeStats.routeCount, routeStats.mileageKm, routeStats.driverSalary, routeStats.missingSalaryCount
                ORDER BY routeCount DESC, d.id
                """);
    }

    public List<Map<String, Object>> attendance() {
        return jdbcTemplate.queryForList("""
                SELECT r.departure_driver_id AS driverId,
                       d.name AS driverName,
                       r.business_date AS workDate,
                       COUNT(*) AS routeCount,
                       COUNT(DISTINCT r.business_date) AS workDays,
                       CASE WHEN COUNT(*) > 0 THEN 1 ELSE 0 END AS worked
                FROM route_task r
                LEFT JOIN driver d ON d.id = r.departure_driver_id
                WHERE r.departure_driver_id IS NOT NULL
                  AND r.status IN ('IN_TRANSIT', 'COMPLETED')
                  AND r.deleted_at IS NULL
                GROUP BY r.departure_driver_id, d.name, r.business_date
                ORDER BY r.business_date DESC, r.departure_driver_id
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
                WHERE e.status IN ('ACTIVE', 'REVERSED', 'REVERSAL') AND e.deleted_at IS NULL
                GROUP BY e.vehicle_id, v.plate_no, d.energy_type
                ORDER BY v.plate_no, d.energy_type
                """);
    }

    private Map<String, Object> monthSummary() {
        return summary("FORMATDATETIME(r.business_date, 'yyyy-MM') = FORMATDATETIME(CURRENT_DATE, 'yyyy-MM')",
                "FORMATDATETIME(e.business_date, 'yyyy-MM') = FORMATDATETIME(CURRENT_DATE, 'yyyy-MM')");
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
                SELECT COALESCE(SUM(r.driver_salary), 0)
                FROM route_task r
                WHERE r.status = 'COMPLETED' AND r.deleted_at IS NULL AND %s
                """.formatted(routeDatePredicate));
        long routeCount = scalar("""
                SELECT COUNT(*)
                FROM route_task r
                WHERE r.status = 'COMPLETED' AND r.deleted_at IS NULL AND %s
                """.formatted(routeDatePredicate));
        return Map.of(
                "incomeAmount", income,
                "routeExpense", routeExpense,
                "dailyExpense", dailyExpense,
                "driverSalary", salary,
                "routeProfit", income.subtract(routeExpense).subtract(salary),
                "companyTotalProfit", income.subtract(routeExpense).subtract(dailyExpense).subtract(salary),
                "routeCount", routeCount);
    }

    private List<Map<String, Object>> monthlyTrend() {
        return jdbcTemplate.queryForList("""
                SELECT m.monthValue AS monthLabel,
                       COALESCE(i.incomeAmount, 0) AS incomeAmount,
                       COALESCE(e.expenseAmount, 0) AS expenseAmount,
                       COALESCE(i.incomeAmount, 0) - COALESCE(e.expenseAmount, 0) AS profitAmount
                FROM (
                    SELECT FORMATDATETIME(business_date, 'yyyy-MM') AS monthValue FROM route_task WHERE deleted_at IS NULL
                    UNION
                    SELECT FORMATDATETIME(business_date, 'yyyy-MM') AS monthValue FROM expense_entry WHERE deleted_at IS NULL
                ) m
                LEFT JOIN (
                    SELECT FORMATDATETIME(r.business_date, 'yyyy-MM') AS monthValue,
                           SUM(w.net_weight * r.tax_unit_price) - COALESCE(SUM(r.driver_salary), 0) AS incomeAmount
                    FROM route_task r
                    JOIN route_weight_version w ON w.id = r.effective_weight_version_id
                    WHERE r.status = 'COMPLETED' AND r.deleted_at IS NULL
                    GROUP BY FORMATDATETIME(r.business_date, 'yyyy-MM')
                ) i ON i.monthValue = m.monthValue
                LEFT JOIN (
                    SELECT FORMATDATETIME(e.business_date, 'yyyy-MM') AS monthValue,
                           SUM(e.amount) AS expenseAmount
                    FROM expense_entry e
                    WHERE e.status IN ('ACTIVE', 'REVERSED', 'REVERSAL') AND e.deleted_at IS NULL
                    GROUP BY FORMATDATETIME(e.business_date, 'yyyy-MM')
                ) e ON e.monthValue = m.monthValue
                ORDER BY m.monthValue
                """);
    }

    private BigDecimal totalProfit() {
        return income().subtract(routeExpense()).subtract(dailyExpense()).subtract(driverSalary());
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
        return amount("""
                SELECT COALESCE(SUM(e.amount), 0)
                FROM expense_entry e
                WHERE e.attribution_type = '%s'
                  AND e.status IN ('ACTIVE', 'REVERSED', 'REVERSAL')
                  AND e.deleted_at IS NULL
                  AND %s
                """.formatted(attributionType, datePredicate));
    }

    public BigDecimal driverSalary() {
        return amount("""
                SELECT COALESCE(SUM(driver_salary), 0)
                FROM route_task
                WHERE status = 'COMPLETED' AND deleted_at IS NULL
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
