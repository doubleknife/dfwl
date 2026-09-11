package com.dfwl.fleet.tire.repository;

import com.dfwl.fleet.common.api.PageResponse;
import com.dfwl.fleet.tire.api.OcrRecordResponse;
import com.dfwl.fleet.tire.api.OcrTireNumberRequest;
import com.dfwl.fleet.tire.api.TireRequestResponse;
import com.dfwl.fleet.tire.api.TireResponse;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class TireRepository {

    private final JdbcTemplate jdbcTemplate;

    public TireRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public PageResponse<TireResponse> list(int pageNo, int pageSize) {
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tire WHERE deleted_at IS NULL", Long.class);
        List<TireResponse> records = jdbcTemplate.query("""
                SELECT id, tire_no, barcode, status, data_source, vehicle_id, driver_id, install_time, created_at
                FROM tire
                WHERE deleted_at IS NULL
                ORDER BY id DESC
                LIMIT ? OFFSET ?
                """, this::mapTire, pageSize, Math.max(pageNo - 1, 0) * pageSize);
        return new PageResponse<>(pageNo, pageSize, total == null ? 0 : total, records);
    }

    public Optional<TireResponse> findTire(long id) {
        return jdbcTemplate.query("""
                SELECT id, tire_no, barcode, status, data_source, vehicle_id, driver_id, install_time, created_at
                FROM tire
                WHERE id = ? AND deleted_at IS NULL
                """, this::mapTire, id).stream().findFirst();
    }

    public Optional<OcrRecordResponse> findOcr(long id) {
        return jdbcTemplate.query("""
                SELECT id, attachment_id, ocr_provider, raw_result_json, recognized_text,
                       confirmed_text, confirmed_by, confirmed_at
                FROM ocr_record
                WHERE id = ?
                """, this::mapOcr, id).stream().findFirst();
    }

    public long createOcr(OcrTireNumberRequest request) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO ocr_record (attachment_id, ocr_provider, raw_result_json, recognized_text)
                    VALUES (?, ?, ?, ?)
                    """, new String[]{"id"});
            ps.setLong(1, request.attachmentId());
            ps.setString(2, request.ocrProvider());
            ps.setString(3, request.rawResultJson());
            ps.setString(4, request.recognizedText());
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    public void confirmOcr(long id, String confirmedText, long operatorId) {
        jdbcTemplate.update("""
                UPDATE ocr_record
                SET confirmed_text = ?, confirmed_by = ?, confirmed_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, confirmedText, operatorId, id);
    }

    public long createRequest(long driverId, long vehicleId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO tire_request (driver_id, vehicle_id, status)
                    VALUES (?, ?, 'PENDING')
                    """, new String[]{"id"});
            ps.setLong(1, driverId);
            ps.setLong(2, vehicleId);
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    public void createRequestItem(long requestId, long tireId, String confirmedTireNo, Long ocrRecordId) {
        jdbcTemplate.update("""
                INSERT INTO tire_request_item (request_id, tire_id, confirmed_tire_no, ocr_record_id)
                VALUES (?, ?, ?, ?)
                """, requestId, tireId, confirmedTireNo, ocrRecordId);
    }

    public boolean reserveTire(long tireId) {
        return jdbcTemplate.update("""
                UPDATE tire
                SET status = 'APPROVAL_RESERVED'
                WHERE id = ? AND status = 'IN_STOCK' AND deleted_at IS NULL
                """, tireId) == 1;
    }

    public int releaseRequestTires(long requestId) {
        return jdbcTemplate.update("""
                UPDATE tire
                SET status = 'IN_STOCK', vehicle_id = NULL, driver_id = NULL, install_time = NULL
                WHERE status = 'APPROVAL_RESERVED'
                  AND id IN (SELECT tire_id FROM tire_request_item WHERE request_id = ?)
                """, requestId);
    }

    public boolean claimTire(long tireId, long driverId, long vehicleId, LocalDateTime installTime) {
        return jdbcTemplate.update("""
                UPDATE tire
                SET status = 'CLAIMED', driver_id = ?, vehicle_id = ?, install_time = ?
                WHERE id = ? AND status = 'APPROVAL_RESERVED' AND deleted_at IS NULL
                """, driverId, vehicleId, Timestamp.valueOf(installTime), tireId) == 1;
    }

    public void insertClaim(long tireId, long driverId, long vehicleId, long requestId,
                            LocalDateTime installTime, String sourceType) {
        jdbcTemplate.update("""
                INSERT INTO tire_claim (tire_id, driver_id, vehicle_id, request_id, install_time, source_type)
                VALUES (?, ?, ?, ?, ?, ?)
                """, tireId, driverId, vehicleId, requestId, Timestamp.valueOf(installTime), sourceType);
    }

    public void updateRequestStatus(long requestId, String status) {
        jdbcTemplate.update("UPDATE tire_request SET status = ? WHERE id = ?", status, requestId);
    }

    public void attachApproval(long requestId, long approvalInstanceId) {
        jdbcTemplate.update("UPDATE tire_request SET approval_instance_id = ? WHERE id = ?", approvalInstanceId, requestId);
    }

    public Optional<TireRequestResponse> findRequest(long id) {
        return jdbcTemplate.query("""
                SELECT id, driver_id, vehicle_id, approval_instance_id, status, created_at
                FROM tire_request
                WHERE id = ?
                """, (rs, rowNum) -> new TireRequestResponse(
                rs.getLong("id"),
                rs.getLong("driver_id"),
                rs.getLong("vehicle_id"),
                readLong(rs, "approval_instance_id"),
                rs.getString("status"),
                rs.getTimestamp("created_at").toLocalDateTime(),
                findRequestItems(rs.getLong("id"))), id).stream().findFirst();
    }

    public boolean attachmentExists(long id) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM file_attachment WHERE id = ?", Integer.class, id);
        return count != null && count > 0;
    }

    public boolean driverExists(long id) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM driver WHERE id = ? AND deleted_at IS NULL", Integer.class, id);
        return count != null && count > 0;
    }

    public boolean vehicleExists(long id) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM vehicle WHERE id = ? AND deleted_at IS NULL", Integer.class, id);
        return count != null && count > 0;
    }

    public boolean tireHasActiveRequest(long tireId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM tire_request_item i
                JOIN tire_request r ON r.id = i.request_id
                WHERE i.tire_id = ? AND r.status IN ('PENDING', 'APPROVED')
                """, Integer.class, tireId);
        return count != null && count > 0;
    }

    public boolean tireClaimed(long tireId) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tire_claim WHERE tire_id = ?", Integer.class, tireId);
        return count != null && count > 0;
    }

    private List<TireRequestResponse.Item> findRequestItems(long requestId) {
        return jdbcTemplate.query("""
                SELECT id, tire_id, confirmed_tire_no, ocr_record_id
                FROM tire_request_item
                WHERE request_id = ?
                ORDER BY id
                """, (rs, rowNum) -> new TireRequestResponse.Item(
                rs.getLong("id"),
                rs.getLong("tire_id"),
                rs.getString("confirmed_tire_no"),
                readLong(rs, "ocr_record_id")), requestId);
    }

    private TireResponse mapTire(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        Timestamp installTime = rs.getTimestamp("install_time");
        return new TireResponse(
                rs.getLong("id"),
                rs.getString("tire_no"),
                rs.getString("barcode"),
                rs.getString("status"),
                rs.getString("data_source"),
                readLong(rs, "vehicle_id"),
                readLong(rs, "driver_id"),
                installTime == null ? null : installTime.toLocalDateTime(),
                rs.getTimestamp("created_at").toLocalDateTime());
    }

    private OcrRecordResponse mapOcr(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        Timestamp confirmedAt = rs.getTimestamp("confirmed_at");
        return new OcrRecordResponse(
                rs.getLong("id"),
                rs.getLong("attachment_id"),
                rs.getString("ocr_provider"),
                rs.getString("raw_result_json"),
                rs.getString("recognized_text"),
                rs.getString("confirmed_text"),
                readLong(rs, "confirmed_by"),
                confirmedAt == null ? null : confirmedAt.toLocalDateTime());
    }

    private Long readLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}
