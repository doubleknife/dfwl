package com.dfwl.fleet.salary.service;

import com.dfwl.fleet.common.error.BusinessException;
import com.dfwl.fleet.common.error.ErrorCode;
import com.dfwl.fleet.common.api.PageResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Derived only from actual Participant departures and their frozen vehicle/trailer facts. */
@Service
public class MonthlyOperationsService {
    private static final int EXPORT_LIMIT = 20_000;
    private final JdbcTemplate jdbc;

    public MonthlyOperationsService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record TaskFact(long taskId, String taskNo, LocalDateTime departedAt,
                           String templateName, String customerName) {}
    public record DayFact(int day, int count, List<TaskFact> tasks) {}
    public record MonthlyRow(String rowKey, Long driverId, String driverName, String plate,
                             String trailerPlate, List<DayFact> days, int monthlyTotal) {}
    public record MonthPage(String month, int daysInMonth, int pageNo, int pageSize,
                            long total, long monthTotal, List<MonthlyRow> records) {}
    public record DriverOption(long id, String label, String detail) {}

    public PageResponse<DriverOption> attendanceDrivers(String search, int pageNo, int pageSize) {
        checkPage(pageNo, pageSize);
        if (search != null && search.length() > 100) throw new BusinessException(ErrorCode.SYS_002);
        String clause = " WHERE driver_type='INTERNAL' AND deleted_at IS NULL";
        List<Object> args = new ArrayList<>();
        if (search != null && !search.isBlank()) {
            clause += " AND (name LIKE ? OR phone LIKE ?)";
            String pattern = "%" + search.trim() + "%";
            args.add(pattern); args.add(pattern);
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM driver" + clause, Long.class, args.toArray());
        args.add(pageSize); args.add((pageNo - 1) * pageSize);
        List<DriverOption> rows = jdbc.query("SELECT id,name,phone FROM driver" + clause
                + " ORDER BY name,id LIMIT ? OFFSET ?",
                (rs, ignored) -> new DriverOption(rs.getLong("id"), rs.getString("name"),
                        rs.getString("phone")), args.toArray());
        return new PageResponse<>(pageNo, pageSize, total == null ? 0 : total, rows);
    }

    public MonthPage attendance(String month, Long driverId, int pageNo, int pageSize) {
        YearMonth period = period(month);
        checkPage(pageNo, pageSize);
        Map<String, MutableRow> grouped = new LinkedHashMap<>();
        jdbc.query("SELECT id,name FROM driver WHERE driver_type='INTERNAL' AND deleted_at IS NULL"
                + (driverId == null ? "" : " AND id = ?") + " ORDER BY name,id",
                rs -> {
                    long id = rs.getLong("id");
                    grouped.put("D:" + id, new MutableRow("D:" + id, id, rs.getString("name"), null, null));
                }, driverId == null ? new Object[]{} : new Object[]{driverId});
        for (Event event : events(period)) {
            if (!"INTERNAL".equals(event.driverType()) || driverId != null && event.driverId() != driverId) continue;
            String key = "D:" + event.driverId();
            grouped.computeIfAbsent(key, ignored -> new MutableRow(key, event.driverId(), event.driverName(), null, null))
                    .add(event);
        }
        return page(period, grouped.values().stream().sorted(Comparator.comparing(MutableRow::driverName)
                .thenComparing(MutableRow::key)).toList(), pageNo, pageSize, true);
    }

    public MonthPage ledger(String month, boolean outsourced, int pageNo, int pageSize) {
        YearMonth period = period(month);
        checkPage(pageNo, pageSize);
        Map<String, MutableRow> grouped = new LinkedHashMap<>();
        for (Event event : events(period)) {
            if (outsourced != "OUTSOURCED".equals(event.driverType())) continue;
            String plate = outsourced ? event.outsourcedPlate() : event.vehiclePlate();
            String trailer = outsourced ? null : event.trailerPlate();
            // A changed binding creates a separate, clearly labelled historical row.
            String key = (outsourced ? "O:" : "I:") + (outsourced ? plate : event.vehicleId())
                    + ":" + event.driverId() + ":" + (trailer == null ? "" : trailer);
            grouped.computeIfAbsent(key, ignored -> new MutableRow(key, event.driverId(), event.driverName(),
                    plate, trailer)).add(event);
        }
        return page(period, grouped.values().stream().sorted(Comparator.comparing(MutableRow::plate,
                        Comparator.nullsFirst(String::compareTo))
                .thenComparing(MutableRow::driverName).thenComparing(MutableRow::key)).toList(),
                pageNo, pageSize, false);
    }

    @Transactional(readOnly = true)
    public byte[] exportAttendance(String month, Long driverId) {
        MonthPage data = attendance(month, driverId, 1, 100);
        return export(data, true, "自有司机出勤", () -> allAttendance(period(month), driverId));
    }

    @Transactional(readOnly = true)
    public byte[] exportLedger(String month, boolean outsourced) {
        MonthPage data = ledger(month, outsourced, 1, 100);
        return export(data, false, outsourced ? "外协车辆月账" : "自有车辆月账",
                () -> allLedger(period(month), outsourced));
    }

    private List<MutableRow> allAttendance(YearMonth month, Long driverId) {
        // Reuse the same grouping used by the page, without using the current page as the export source.
        MonthPage first = attendance(month.toString(), driverId, 1, 100);
        List<MonthlyRow> rows = allPages(first, page -> attendance(month.toString(), driverId, page, 100));
        return rows.stream().map(MutableRow::copy).toList();
    }

    private List<MutableRow> allLedger(YearMonth month, boolean outsourced) {
        MonthPage first = ledger(month.toString(), outsourced, 1, 100);
        List<MonthlyRow> rows = allPages(first, page -> ledger(month.toString(), outsourced, page, 100));
        return rows.stream().map(MutableRow::copy).toList();
    }

    private List<MonthlyRow> allPages(MonthPage first, java.util.function.IntFunction<MonthPage> read) {
        if (first.total() > EXPORT_LIMIT)
            throw new BusinessException("MONTH_EXPORT_TOO_LARGE", "当前月表超过两万行，请缩小范围后导出");
        List<MonthlyRow> rows = new ArrayList<>(first.records());
        long pages = (first.total() + first.pageSize() - 1) / first.pageSize();
        for (int page = 2; page <= pages; page++) rows.addAll(read.apply(page).records());
        if (rows.size() != first.total())
            throw new BusinessException("MONTH_EXPORT_CHANGED", "导出时月度数据已变化，请重试");
        return rows;
    }

    private byte[] export(MonthPage data, boolean attendance, String title,
                          java.util.function.Supplier<List<MutableRow>> source) {
        List<MutableRow> rows = source.get();
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet(title);
            var header = sheet.createRow(0);
            int dayStart = attendance ? 2 : 3;
            header.createCell(0).setCellValue(attendance ? "司机" : "车牌");
            header.createCell(1).setCellValue(attendance ? "司机类型" : "执行司机");
            if (!attendance) header.createCell(2).setCellValue("当时车挂");
            for (int day = 1; day <= 31; day++) header.createCell(dayStart + day - 1).setCellValue(day + "日");
            header.createCell(dayStart + 31).setCellValue(attendance ? "出勤天数" : "发车次数合计");
            int rowNo = 1;
            for (MutableRow row : rows) {
                var line = sheet.createRow(rowNo++);
                line.createCell(0).setCellValue(attendance ? row.driverName() : row.plate());
                line.createCell(1).setCellValue(attendance ? "自有司机" : row.driverName());
                if (!attendance) line.createCell(2).setCellValue(row.trailerPlate() == null ? "" : row.trailerPlate());
                int total = 0;
                for (int day = 1; day <= 31; day++) {
                    int count = day <= data.daysInMonth() ? row.events(day).size() : 0;
                    int shown = attendance ? Math.min(count, 1) : count;
                    if (shown != 0) line.createCell(dayStart + day - 1).setCellValue(shown);
                    total += shown;
                }
                line.createCell(dayStart + 31).setCellValue(total);
            }
            sheet.createFreezePane(dayStart, 1);
            sheet.setColumnWidth(0, 24 * 256);
            sheet.setColumnWidth(1, 20 * 256);
            if (!attendance) sheet.setColumnWidth(2, 19 * 256);
            for (int col = dayStart; col < dayStart + 32; col++) sheet.setColumnWidth(col, 8 * 256);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) { throw new IllegalStateException(ex); }
    }

    private MonthPage page(YearMonth month, List<MutableRow> source, int pageNo, int pageSize,
                           boolean attendance) {
        long monthTotal = source.stream().mapToInt(row -> row.total(attendance)).sum();
        int start = Math.min((pageNo - 1) * pageSize, source.size());
        int end = Math.min(start + pageSize, source.size());
        List<MonthlyRow> rows = source.subList(start, end).stream().map(row -> row.freeze(month, attendance)).toList();
        return new MonthPage(month.toString(), month.lengthOfMonth(), pageNo, pageSize,
                source.size(), monthTotal, rows);
    }

    private List<Event> events(YearMonth month) {
        return jdbc.query("""
                SELECT p.driver_id,p.driver_type_snapshot,p.company_vehicle_id,p.vehicle_plate_snapshot,
                       p.trailer_plate_snapshot,p.outsourced_plate_no,p.departed_at,d.name AS driver_name,
                       t.id AS task_id,t.task_no,t.template_name_snapshot,t.customer_name_snapshot
                FROM transport_participant p JOIN transport_task t ON t.id=p.task_id
                JOIN driver d ON d.id=p.driver_id
                WHERE p.departed_at >= ? AND p.departed_at < ?
                  AND p.status <> 'CANCELLED' AND t.status <> 'CANCELLED' AND t.deleted_at IS NULL
                ORDER BY p.departed_at,p.id
                """, this::mapEvent, month.atDay(1).atStartOfDay(), month.plusMonths(1).atDay(1).atStartOfDay());
    }

    private Event mapEvent(ResultSet rs, int ignored) throws SQLException {
        long vehicle = rs.getLong("company_vehicle_id");
        Long vehicleId = rs.wasNull() ? null : vehicle;
        return new Event(rs.getLong("driver_id"), rs.getString("driver_name"),
                rs.getString("driver_type_snapshot"), vehicleId, rs.getString("vehicle_plate_snapshot"),
                rs.getString("trailer_plate_snapshot"), rs.getString("outsourced_plate_no"),
                rs.getTimestamp("departed_at").toLocalDateTime(), rs.getLong("task_id"),
                rs.getString("task_no"), rs.getString("template_name_snapshot"),
                rs.getString("customer_name_snapshot"));
    }

    private YearMonth period(String month) {
        try { return YearMonth.parse(month); }
        catch (DateTimeParseException | NullPointerException ex) { throw new BusinessException(ErrorCode.SYS_002); }
    }

    private void checkPage(int pageNo, int pageSize) {
        if (pageNo < 1 || pageSize < 1 || pageSize > 100) throw new BusinessException(ErrorCode.SYS_002);
    }

    private record Event(long driverId, String driverName, String driverType, Long vehicleId,
                         String vehiclePlate, String trailerPlate, String outsourcedPlate,
                         LocalDateTime departedAt, long taskId, String taskNo,
                         String templateName, String customerName) {
        TaskFact task() { return new TaskFact(taskId, taskNo, departedAt, templateName, customerName); }
    }

    private static final class MutableRow {
        private final String key;
        private final long driverId;
        private final String driverName;
        private final String plate;
        private final String trailerPlate;
        private final Map<Integer, List<TaskFact>> byDay = new LinkedHashMap<>();

        MutableRow(String key, long driverId, String driverName, String plate, String trailerPlate) {
            this.key = key; this.driverId = driverId; this.driverName = driverName;
            this.plate = plate; this.trailerPlate = trailerPlate;
        }
        static MutableRow copy(MonthlyRow source) {
            MutableRow row = new MutableRow(source.rowKey(), source.driverId(), source.driverName(),
                    source.plate(), source.trailerPlate());
            for (DayFact day : source.days()) row.byDay.put(day.day(), day.tasks());
            return row;
        }
        String key() { return key; }
        String driverName() { return driverName; }
        String plate() { return plate; }
        String trailerPlate() { return trailerPlate; }
        List<TaskFact> events(int day) { return byDay.getOrDefault(day, List.of()); }
        void add(Event event) { byDay.computeIfAbsent(event.departedAt().getDayOfMonth(), ignored -> new ArrayList<>())
                .add(event.task()); }
        int total(boolean attendance) {
            return byDay.values().stream().mapToInt(value -> attendance ? Math.min(1, value.size()) : value.size()).sum();
        }
        MonthlyRow freeze(YearMonth month, boolean attendance) {
            List<DayFact> days = new ArrayList<>();
            for (int day = 1; day <= month.lengthOfMonth(); day++) {
                List<TaskFact> tasks = List.copyOf(events(day));
                days.add(new DayFact(day, attendance ? Math.min(tasks.size(), 1) : tasks.size(), tasks));
            }
            return new MonthlyRow(key, driverId, driverName, plate, trailerPlate,
                    List.copyOf(days), total(attendance));
        }
    }
}
