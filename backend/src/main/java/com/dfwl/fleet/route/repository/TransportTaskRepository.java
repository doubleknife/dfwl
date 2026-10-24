package com.dfwl.fleet.route.repository;

import com.dfwl.fleet.common.api.PageResponse;
import com.dfwl.fleet.route.dto.response.DriverTaskResponse;
import com.dfwl.fleet.route.dto.response.TaskWeightVersionResponse;
import com.dfwl.fleet.route.dto.response.TransportParticipantResponse;
import com.dfwl.fleet.route.dto.response.TransportTaskResponse;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TransportTaskRepository {
    private static final String BASE_SELECT = """
            SELECT t.*, d.name AS current_driver_name,
                   CASE WHEN p.driver_type_snapshot = 'OUTSOURCED' THEN p.outsourced_plate_no
                        ELSE p.vehicle_plate_snapshot END AS current_plate,
                   w.gross_weight, w.tare_weight, w.net_weight, w.load_standard_met
            FROM transport_task t
            LEFT JOIN transport_participant p ON p.id = t.current_participant_id
            LEFT JOIN driver d ON d.id = p.driver_id
            LEFT JOIN task_weight_version w ON w.id = t.effective_weight_version_id
            WHERE t.deleted_at IS NULL
            """;
    private final JdbcTemplate jdbc;

    public TransportTaskRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<TransportTaskResponse> find(long id) {
        return jdbc.query(BASE_SELECT + " AND t.id = ?", this::mapTask, id).stream().findFirst();
    }

    public PageResponse<TransportTaskResponse> list(int pageNo, int pageSize, LocalDate businessDate,
                                                     String status, Long templateId, Long driverId, String taskNo) {
        StringBuilder where = new StringBuilder(" WHERE t.deleted_at IS NULL");
        List<Object> args = new ArrayList<>();
        if (businessDate != null) { where.append(" AND t.business_date = ?"); args.add(businessDate); }
        if (status != null && !status.isBlank()) { where.append(" AND t.status = ?"); args.add(status); }
        if (templateId != null) { where.append(" AND t.template_id = ?"); args.add(templateId); }
        if (driverId != null) {
            where.append(" AND EXISTS (SELECT 1 FROM transport_participant x WHERE x.task_id = t.id AND x.driver_id = ?)");
            args.add(driverId);
        }
        if (taskNo != null && !taskNo.isBlank()) { where.append(" AND t.task_no LIKE ?"); args.add("%" + taskNo.trim() + "%"); }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM transport_task t" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(pageSize);
        pageArgs.add((pageNo - 1) * pageSize);
        List<TransportTaskResponse> records = jdbc.query(BASE_SELECT + where.substring(" WHERE t.deleted_at IS NULL".length())
                + " ORDER BY t.id DESC LIMIT ? OFFSET ?", this::mapTask, pageArgs.toArray());
        return new PageResponse<>(pageNo, pageSize, total == null ? 0 : total, records);
    }

    public record LastIdentity(String driverName, String plateNo) { }
    public java.util.Map<Long, LastIdentity> lastIdentities(List<Long> ids) {
        if (ids.isEmpty()) return java.util.Map.of();
        var result = new java.util.HashMap<Long, LastIdentity>();
        String marks = String.join(",", ids.stream().map(id -> "?").toList());
        jdbc.query("""
                SELECT p.task_id, d.name, CASE WHEN p.driver_type_snapshot='OUTSOURCED'
                  THEN p.outsourced_plate_no ELSE p.vehicle_plate_snapshot END AS plate
                FROM transport_participant p JOIN driver d ON d.id=p.driver_id
                WHERE p.task_id IN (%s) AND NOT EXISTS
                  (SELECT 1 FROM transport_participant newer WHERE newer.task_id=p.task_id AND newer.sequence_no>p.sequence_no)
                """.formatted(marks), (org.springframework.jdbc.core.RowCallbackHandler) rs ->
                result.put(rs.getLong("task_id"), new LastIdentity(rs.getString("name"), rs.getString("plate"))), ids.toArray());
        return result;
    }

    public PageResponse<DriverTaskResponse> listForDriver(long driverId, int pageNo, int pageSize, String status) {
        String extra = status == null || status.isBlank() ? "" : " AND t.status = ?";
        List<Object> args = new ArrayList<>(List.of(driverId));
        if (!extra.isEmpty()) args.add(status);
        String condition = """
                WHERE t.deleted_at IS NULL AND EXISTS
                (SELECT 1 FROM transport_participant x WHERE x.task_id = t.id AND x.driver_id = ?)
                """ + extra;
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM transport_task t " + condition,
                Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(pageSize);
        pageArgs.add((pageNo - 1) * pageSize);
        List<DriverTaskResponse> records = jdbc.query("""
                SELECT t.*, p.driver_id AS current_driver_id, d.name AS current_driver_name,
                       p.driver_type_snapshot,
                       CASE WHEN p.driver_type_snapshot = 'OUTSOURCED' THEN p.outsourced_plate_no
                            ELSE p.vehicle_plate_snapshot END AS current_plate,
                       w.gross_weight, w.tare_weight, w.net_weight
                FROM transport_task t
                LEFT JOIN transport_participant p ON p.id = t.current_participant_id
                LEFT JOIN driver d ON d.id = p.driver_id
                LEFT JOIN task_weight_version w ON w.id = t.effective_weight_version_id
                """ + condition + " ORDER BY t.id DESC LIMIT ? OFFSET ?", (rs, row) -> mapDriver(rs, driverId),
                pageArgs.toArray());
        return new PageResponse<>(pageNo, pageSize, total == null ? 0 : total, records);
    }

    public Optional<DriverTaskResponse> findForDriver(long id, long driverId) {
        return jdbc.query("""
                SELECT t.*, p.driver_id AS current_driver_id, d.name AS current_driver_name,
                       p.driver_type_snapshot,
                       CASE WHEN p.driver_type_snapshot = 'OUTSOURCED' THEN p.outsourced_plate_no
                            ELSE p.vehicle_plate_snapshot END AS current_plate,
                       w.gross_weight, w.tare_weight, w.net_weight
                FROM transport_task t
                LEFT JOIN transport_participant p ON p.id = t.current_participant_id
                LEFT JOIN driver d ON d.id = p.driver_id
                LEFT JOIN task_weight_version w ON w.id = t.effective_weight_version_id
                WHERE t.id = ? AND t.deleted_at IS NULL
                  AND EXISTS (SELECT 1 FROM transport_participant x WHERE x.task_id = t.id AND x.driver_id = ?)
                """, (rs, row) -> mapDriver(rs, driverId), id, driverId).stream().findFirst();
    }

    public List<TransportParticipantResponse> participants(long id, boolean includeSalary) {
        return jdbc.query("""
                SELECT p.*, d.name AS driver_name
                FROM transport_participant p JOIN driver d ON d.id = p.driver_id
                WHERE p.task_id = ? ORDER BY p.sequence_no
                """, (rs, row) -> new TransportParticipantResponse(
                rs.getLong("id"), rs.getInt("sequence_no"), rs.getLong("driver_id"),
                rs.getString("driver_name"), rs.getString("driver_type_snapshot"),
                rs.getString("vehicle_plate_snapshot"), rs.getString("trailer_plate_snapshot"),
                rs.getString("outsourced_plate_no"), rs.getString("source_type"), rs.getString("status"),
                rs.getInt("is_current") == 1, includeSalary ? rs.getBigDecimal("salary_amount") : null,
                toDateTime(rs, "assigned_at"), toDateTime(rs, "departed_at"), toDateTime(rs, "ended_at"),
                rs.getString("end_reason"), rs.getInt("version")), id);
    }

    public List<TaskWeightVersionResponse> weightVersions(long id) {
        return jdbc.query("""
                SELECT * FROM task_weight_version WHERE task_id = ? ORDER BY version_no
                """, (rs, row) -> {
            long versionId = rs.getLong("id");
            List<Long> ticketIds = jdbc.queryForList("""
                    SELECT attachment_id FROM task_unload_evidence
                    WHERE task_id = ? AND weight_version_id = ? ORDER BY id
                    """, Long.class, id, versionId);
            String raw = rs.getString("attachment_ids");
            List<Long> adjustments = raw == null || raw.isBlank() ? List.of()
                    : java.util.Arrays.stream(raw.split(",")).map(String::trim)
                        .filter(value -> !value.isBlank()).map(Long::valueOf).toList();
            Boolean met = rs.getObject("load_standard_met") == null ? null : rs.getInt("load_standard_met") == 1;
            return new TaskWeightVersionResponse(versionId, rs.getInt("version_no"), rs.getString("source_type"),
                    rs.getBigDecimal("gross_weight"), rs.getBigDecimal("tare_weight"), rs.getBigDecimal("net_weight"),
                    met == null ? "NOT_APPLICABLE" : met ? "MET" : "NOT_MET", rs.getString("reason"),
                    rs.getLong("operator_id"), toDateTime(rs, "operation_time"), adjustments, ticketIds);
        }, id);
    }

    private TransportTaskResponse mapTask(ResultSet rs, int row) throws SQLException {
        Long weightVersionId = optionalLong(rs, "effective_weight_version_id");
        Boolean met = rs.getObject("load_standard_met") == null ? null : rs.getInt("load_standard_met") == 1;
        String standardResult = weightVersionId == null ? "PENDING" : met == null ? "NOT_APPLICABLE" : met ? "MET" : "NOT_MET";
        return new TransportTaskResponse(rs.getLong("id"), rs.getString("task_no"), rs.getLong("template_id"),
                rs.getString("template_code_snapshot"), rs.getString("template_name_snapshot"),
                rs.getString("customer_name_snapshot"), rs.getString("product_name_snapshot"),
                rs.getString("loading_place_snapshot"), rs.getString("unloading_place_snapshot"),
                rs.getDate("business_date").toLocalDate(), rs.getString("status"),
                rs.getBigDecimal("actual_unit_price"), rs.getBigDecimal("actual_information_fee"),
                optionalLong(rs, "current_participant_id"), rs.getString("current_driver_name"),
                rs.getString("current_plate"), rs.getBigDecimal("gross_weight"), rs.getBigDecimal("tare_weight"),
                rs.getBigDecimal("net_weight"), weightVersionId, standardResult,
                toDateTime(rs, "completed_at"), rs.getInt("version"));
    }

    private DriverTaskResponse mapDriver(ResultSet rs, long driverId) throws SQLException {
        long currentDriver = rs.getLong("current_driver_id");
        boolean current = !rs.wasNull() && currentDriver == driverId;
        return new DriverTaskResponse(rs.getLong("id"), rs.getString("task_no"),
                rs.getString("customer_name_snapshot"), rs.getString("product_name_snapshot"),
                rs.getString("loading_place_snapshot"), rs.getString("unloading_place_snapshot"),
                rs.getDate("business_date").toLocalDate(), rs.getString("status"),
                rs.getString("current_driver_name"), rs.getString("current_plate"),
                rs.getBigDecimal("gross_weight"), rs.getBigDecimal("tare_weight"),
                rs.getBigDecimal("net_weight"), current, rs.getInt("version"));
    }

    private Long optionalLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private java.time.LocalDateTime toDateTime(ResultSet rs, String column) throws SQLException {
        java.sql.Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }
}
