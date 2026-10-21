package com.dfwl.fleet.salary.service;

import com.dfwl.fleet.common.api.PageResponse;
import com.dfwl.fleet.common.error.BusinessException;
import com.dfwl.fleet.common.error.ErrorCode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only projections of the single TransportParticipant salary fact. */
@Service
public class SalaryLedgerService {
    private static final int EXPORT_LIMIT = 20_000;
    private final JdbcTemplate jdbc;

    public SalaryLedgerService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record Filter(String month, Long driverId, String driverType, Long templateId,
                         Long taskId, Boolean transferOnly) {}

    public record Row(long participantId, long taskId, String taskNo, LocalDate businessDate,
                      String templateName, String customerName, String productName,
                      long driverId, String driverName, String driverType, int sequenceNo,
                      String sourceType, String vehiclePlate, String trailerPlate,
                      BigDecimal salaryAmount, boolean transferTask, BigDecimal taskSalaryTotal,
                      String status, LocalDateTime departedAt, LocalDateTime endedAt, int version) {}

    public record Summary(BigDecimal salaryTotal, BigDecimal internalSalary, BigDecimal outsourcedSalary,
                          long participantCount, long taskCount, long transferTaskCount) {}

    public record DriverSummary(long driverId, String driverName, String driverType,
                                long participantCount, long taskCount, long transferTaskCount,
                                BigDecimal salaryTotal) {}
    public record FilterOption(long id, String label, String detail) {}

    public PageResponse<FilterOption> options(String kind, String search, String month, int pageNo, int pageSize) {
        validatePage(pageNo, pageSize);
        if (search != null && search.length() > 100) throw new BusinessException(ErrorCode.SYS_002);
        String idExpression;
        String labelExpression;
        String detailExpression;
        String searchExpression;
        switch (kind) {
            case "DRIVER" -> {
                idExpression = "d.id"; labelExpression = "MAX(d.name)"; detailExpression = "MAX(d.phone)";
                searchExpression = "(d.name LIKE ? OR d.phone LIKE ?)";
            }
            case "TEMPLATE" -> {
                idExpression = "t.template_id"; labelExpression = "MAX(t.template_name_snapshot)";
                detailExpression = "MAX(t.customer_name_snapshot)";
                searchExpression = "(t.template_name_snapshot LIKE ? OR t.customer_name_snapshot LIKE ?)";
            }
            case "TASK" -> {
                idExpression = "t.id"; labelExpression = "MAX(t.task_no)";
                detailExpression = "MAX(t.template_name_snapshot)";
                searchExpression = "(t.task_no LIKE ? OR t.template_name_snapshot LIKE ?)";
            }
            default -> throw new BusinessException(ErrorCode.SYS_002);
        }
        StringBuilder clause = new StringBuilder(" WHERE t.deleted_at IS NULL AND t.status = 'COMPLETED'"
                + " AND p.status <> 'CANCELLED'");
        List<Object> args = new ArrayList<>();
        if (month != null && !month.isBlank()) {
            YearMonth period;
            try { period = YearMonth.parse(month); }
            catch (DateTimeParseException ex) { throw new BusinessException(ErrorCode.SYS_002); }
            clause.append(" AND t.business_date >= ? AND t.business_date < ?");
            args.add(period.atDay(1)); args.add(period.plusMonths(1).atDay(1));
        }
        if (search != null && !search.isBlank()) {
            clause.append(" AND ").append(searchExpression);
            String pattern = "%" + search.trim() + "%";
            args.add(pattern); args.add(pattern);
        }
        Long total = jdbc.queryForObject("SELECT COUNT(DISTINCT " + idExpression + ")" + from()
                + clause, Long.class, args.toArray());
        List<Object> paged = new ArrayList<>(args);
        paged.add(pageSize); paged.add((pageNo - 1) * pageSize);
        List<FilterOption> rows = jdbc.query("SELECT " + idExpression + " AS id, "
                + labelExpression + " AS label, " + detailExpression + " AS detail" + from()
                + clause + " GROUP BY " + idExpression + " ORDER BY id DESC LIMIT ? OFFSET ?",
                (rs, ignored) -> new FilterOption(rs.getLong("id"), rs.getString("label"),
                        rs.getString("detail")), paged.toArray());
        return new PageResponse<>(pageNo, pageSize, total == null ? 0 : total, rows);
    }

    public PageResponse<Row> list(Filter filter, int pageNo, int pageSize) {
        validatePage(pageNo, pageSize);
        Clause clause = where(filter);
        Long total = jdbc.queryForObject("SELECT COUNT(*) " + from() + clause.sql(), Long.class, clause.args().toArray());
        List<Object> args = new ArrayList<>(clause.args());
        args.add(pageSize);
        args.add((pageNo - 1) * pageSize);
        List<Row> rows = jdbc.query(select() + from() + clause.sql()
                + " ORDER BY t.business_date DESC, t.id DESC, p.sequence_no LIMIT ? OFFSET ?",
                this::map, args.toArray());
        return new PageResponse<>(pageNo, pageSize, total == null ? 0 : total, rows);
    }

    public Summary summary(Filter filter) {
        Clause clause = where(filter);
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(p.salary_amount),0) AS salary_total,
                       COALESCE(SUM(CASE WHEN p.driver_type_snapshot='INTERNAL' THEN p.salary_amount ELSE 0 END),0) AS internal_salary,
                       COALESCE(SUM(CASE WHEN p.driver_type_snapshot='OUTSOURCED' THEN p.salary_amount ELSE 0 END),0) AS outsourced_salary,
                       COUNT(*) AS participant_count, COUNT(DISTINCT t.id) AS task_count,
                       COUNT(DISTINCT CASE WHEN EXISTS (SELECT 1 FROM transport_participant transfer_participant
                           WHERE transfer_participant.task_id=t.id AND transfer_participant.source_type='TRANSFER')
                           THEN t.id ELSE NULL END) AS transfer_task_count
                """ + from() + clause.sql(), (rs, ignored) -> new Summary(rs.getBigDecimal("salary_total"),
                rs.getBigDecimal("internal_salary"), rs.getBigDecimal("outsourced_salary"),
                rs.getLong("participant_count"), rs.getLong("task_count"), rs.getLong("transfer_task_count")),
                clause.args().toArray());
    }

    public PageResponse<DriverSummary> drivers(Filter filter, int pageNo, int pageSize) {
        validatePage(pageNo, pageSize);
        Clause clause = where(filter);
        Long total = jdbc.queryForObject("SELECT COUNT(DISTINCT p.driver_id) " + from() + clause.sql(),
                Long.class, clause.args().toArray());
        List<Object> args = new ArrayList<>(clause.args());
        args.add(pageSize);
        args.add((pageNo - 1) * pageSize);
        List<DriverSummary> rows = jdbc.query("""
                SELECT p.driver_id, d.name AS driver_name, p.driver_type_snapshot,
                       COUNT(*) AS participant_count, COUNT(DISTINCT t.id) AS task_count,
                       COUNT(DISTINCT CASE WHEN EXISTS (SELECT 1 FROM transport_participant transfer_participant
                           WHERE transfer_participant.task_id=t.id AND transfer_participant.source_type='TRANSFER')
                           THEN t.id ELSE NULL END) AS transfer_task_count,
                       COALESCE(SUM(p.salary_amount),0) AS salary_total
                """ + from() + clause.sql() + " GROUP BY p.driver_id,d.name,p.driver_type_snapshot"
                + " ORDER BY salary_total DESC,p.driver_id LIMIT ? OFFSET ?",
                (rs, ignored) -> new DriverSummary(rs.getLong("driver_id"), rs.getString("driver_name"),
                        rs.getString("driver_type_snapshot"), rs.getLong("participant_count"),
                        rs.getLong("task_count"), rs.getLong("transfer_task_count"),
                        rs.getBigDecimal("salary_total")), args.toArray());
        return new PageResponse<>(pageNo, pageSize, total == null ? 0 : total, rows);
    }

    @Transactional(readOnly = true)
    public byte[] export(Filter filter) {
        Clause clause = where(filter);
        Long count = jdbc.queryForObject("SELECT COUNT(*) " + from() + clause.sql(), Long.class, clause.args().toArray());
        if (count != null && count > EXPORT_LIMIT)
            throw new BusinessException("SALARY_EXPORT_TOO_LARGE", "当前筛选超过两万条，请缩小月份或司机范围后导出");
        List<Row> rows = jdbc.query(select() + from() + clause.sql()
                + " ORDER BY t.business_date DESC,t.id DESC,p.sequence_no", this::map, clause.args().toArray());
        try (XSSFWorkbook book = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var sheet = book.createSheet("工资台账");
            String[] headers = {"业务日期","任务号","线路模板","客户","货物","司机","司机类型","参与顺序",
                    "参与身份","当时车牌","当时车挂","核定工资（元）","转派任务","任务工资合计（元）","任务状态"};
            var header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);
            CellStyle moneyStyle = book.createCellStyle();
            DataFormat fmt = book.createDataFormat();
            moneyStyle.setDataFormat(fmt.getFormat("#,##0.00"));
            int index = 1;
            for (Row item : rows) {
                var line = sheet.createRow(index++);
                line.createCell(0).setCellValue(item.businessDate().toString());
                line.createCell(1).setCellValue(item.taskNo());
                line.createCell(2).setCellValue(item.templateName());
                line.createCell(3).setCellValue(item.customerName());
                line.createCell(4).setCellValue(item.productName());
                line.createCell(5).setCellValue(item.driverName());
                line.createCell(6).setCellValue(typeName(item.driverType()));
                line.createCell(7).setCellValue(item.sequenceNo());
                line.createCell(8).setCellValue(item.sequenceNo() == 1 ? "初始司机" : "第" + (item.sequenceNo() - 1) + "次接手");
                line.createCell(9).setCellValue(item.vehiclePlate() == null ? "" : item.vehiclePlate());
                line.createCell(10).setCellValue(item.trailerPlate() == null ? "" : item.trailerPlate());
                line.createCell(11).setCellValue(item.salaryAmount().doubleValue());
                line.getCell(11).setCellStyle(moneyStyle);
                line.createCell(12).setCellValue(item.transferTask() ? "转派" : "普通");
                line.createCell(13).setCellValue(item.taskSalaryTotal().doubleValue());
                line.getCell(13).setCellStyle(moneyStyle);
                line.createCell(14).setCellValue(statusName(item.status()));
            }
            sheet.createFreezePane(0, 1);
            for (int i = 0; i < headers.length; i++) sheet.setColumnWidth(i, (i == 1 ? 29 : 19) * 256);
            book.write(out);
            return out.toByteArray();
        } catch (IOException ex) { throw new IllegalStateException(ex); }
    }

    private Clause where(Filter filter) {
        if (filter == null) filter = new Filter(null, null, null, null, null, null);
        StringBuilder sql = new StringBuilder(" WHERE t.deleted_at IS NULL AND t.status = 'COMPLETED'"
                + " AND p.status <> 'CANCELLED'");
        List<Object> args = new ArrayList<>();
        if (filter.month() != null && !filter.month().isBlank()) {
            YearMonth month;
            try { month = YearMonth.parse(filter.month()); }
            catch (DateTimeParseException ex) { throw new BusinessException(ErrorCode.SYS_002); }
            sql.append(" AND t.business_date >= ? AND t.business_date < ?");
            args.add(month.atDay(1)); args.add(month.plusMonths(1).atDay(1));
        }
        if (filter.driverId() != null) { sql.append(" AND p.driver_id = ?"); args.add(filter.driverId()); }
        if (filter.driverType() != null && !filter.driverType().isBlank()) {
            if (!List.of("INTERNAL", "OUTSOURCED").contains(filter.driverType()))
                throw new BusinessException(ErrorCode.SYS_002);
            sql.append(" AND p.driver_type_snapshot = ?"); args.add(filter.driverType());
        }
        if (filter.templateId() != null) { sql.append(" AND t.template_id = ?"); args.add(filter.templateId()); }
        if (filter.taskId() != null) { sql.append(" AND t.id = ?"); args.add(filter.taskId()); }
        if (filter.transferOnly() != null) {
            sql.append(filter.transferOnly() ? " AND " : " AND NOT ").append("EXISTS (SELECT 1 FROM transport_participant"
                    + " transfer_participant WHERE transfer_participant.task_id=t.id AND transfer_participant.source_type='TRANSFER')");
        }
        return new Clause(sql.toString(), args);
    }

    private String select() { return """
            SELECT p.id AS participant_id,p.sequence_no,p.driver_id,d.name AS driver_name,
                   p.driver_type_snapshot,p.salary_amount,p.source_type,p.vehicle_plate_snapshot,
                   p.trailer_plate_snapshot,p.outsourced_plate_no,p.departed_at,p.ended_at,p.version,
                   t.id AS task_id,t.task_no,t.business_date,t.status,t.template_name_snapshot,
                   t.customer_name_snapshot,t.product_name_snapshot,
                   (SELECT COALESCE(SUM(all_p.salary_amount),0) FROM transport_participant all_p
                       WHERE all_p.task_id=t.id AND all_p.status<>'CANCELLED') AS task_salary_total,
                   CASE WHEN EXISTS (SELECT 1 FROM transport_participant transfer_participant
                       WHERE transfer_participant.task_id=t.id AND transfer_participant.source_type='TRANSFER')
                       THEN 1 ELSE 0 END AS transfer_task
            """; }

    private String from() { return " FROM transport_participant p JOIN transport_task t ON t.id=p.task_id"
            + " JOIN driver d ON d.id=p.driver_id"; }

    private Row map(ResultSet rs, int ignored) throws SQLException {
        String type = rs.getString("driver_type_snapshot");
        return new Row(rs.getLong("participant_id"), rs.getLong("task_id"), rs.getString("task_no"),
                rs.getDate("business_date").toLocalDate(), rs.getString("template_name_snapshot"),
                rs.getString("customer_name_snapshot"), rs.getString("product_name_snapshot"),
                rs.getLong("driver_id"), rs.getString("driver_name"), type, rs.getInt("sequence_no"),
                rs.getString("source_type"), "OUTSOURCED".equals(type) ? rs.getString("outsourced_plate_no")
                        : rs.getString("vehicle_plate_snapshot"), rs.getString("trailer_plate_snapshot"),
                rs.getBigDecimal("salary_amount"), rs.getBoolean("transfer_task"),
                rs.getBigDecimal("task_salary_total"), rs.getString("status"),
                timestamp(rs, "departed_at"), timestamp(rs, "ended_at"), rs.getInt("version"));
    }

    private LocalDateTime timestamp(ResultSet rs, String name) throws SQLException {
        var value = rs.getTimestamp(name);
        return value == null ? null : value.toLocalDateTime();
    }

    private void validatePage(int pageNo, int pageSize) {
        if (pageNo < 1 || pageSize < 1 || pageSize > 100) throw new BusinessException(ErrorCode.SYS_002);
    }

    private String typeName(String type) { return "OUTSOURCED".equals(type) ? "外协司机" : "自有司机"; }
    private String statusName(String status) {
        return switch (status) {
            case "PUBLISHED" -> "待发车";
            case "IN_TRANSIT" -> "运输中";
            case "TRANSFER_PENDING" -> "待接手发车";
            case "COMPLETED" -> "已完成";
            case "CANCELLED" -> "已取消";
            default -> "状态异常";
        };
    }
    private record Clause(String sql, List<Object> args) {}
}
