package com.dfwl.fleet.tire.repository;

import com.dfwl.fleet.tire.dto.response.OcrRecordResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

/** OCR persistence only. Inventory and requests use the final V3 tire service. */
@Repository
public class TireRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    public TireRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc; this.mapper = mapper;
    }
    public boolean tireNoExists(String tireNo) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM tire WHERE tire_no=? AND status='AVAILABLE' AND deleted_at IS NULL
                """, Long.class, tireNo);
        return count != null && count > 0;
    }
    public Optional<OcrRecordResponse> findOcr(long id) {
        return jdbc.query("""
                SELECT id,attachment_id,ocr_provider,provider_request_id,raw_result_json,recognized_text,
                       candidate_text,candidates_json,ocr_status,error_code,error_message,
                       confirmed_text,confirmed_by,confirmed_at FROM ocr_record WHERE id=?
                """, (rs, row) -> {
            Timestamp at = rs.getTimestamp("confirmed_at");
            long by = rs.getLong("confirmed_by");
            boolean hasConfirmer = !rs.wasNull();
            return new OcrRecordResponse(rs.getLong("id"), rs.getLong("attachment_id"),
                    rs.getString("ocr_provider"), rs.getString("provider_request_id"),
                    rs.getString("raw_result_json"), rs.getString("recognized_text"),
                    rs.getString("candidate_text"), rs.getString("ocr_status"),
                    rs.getString("error_code"), rs.getString("error_message"),
                    candidates(rs.getString("candidates_json")), rs.getString("confirmed_text"),
                    hasConfirmer ? by : null, at == null ? null : at.toLocalDateTime());
        }, id).stream().findFirst();
    }
    public long createOcr(long attachmentId, String provider, String providerRequestId, String rawResultJson,
            String recognizedText, String candidateText, List<OcrRecordResponse.Candidate> candidates,
            String status, String errorCode, String errorMessage) {
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO ocr_record(attachment_id,ocr_provider,provider_request_id,raw_result_json,
                      recognized_text,candidate_text,candidates_json,ocr_status,error_code,error_message)
                    VALUES(?,?,?,?,?,?,?,?,?,?)
                    """, new String[] {"id"});
            statement.setLong(1, attachmentId);
            statement.setString(2, provider);
            statement.setString(3, providerRequestId);
            statement.setString(4, rawResultJson);
            statement.setString(5, recognizedText);
            statement.setString(6, candidateText);
            statement.setString(7, json(candidates));
            statement.setString(8, status);
            statement.setString(9, errorCode);
            statement.setString(10, errorMessage);
            return statement;
        }, key);
        return key.getKey().longValue();
    }
    public void confirmOcr(long id, String confirmedText, long operatorId) {
        jdbc.update("""
                UPDATE ocr_record SET confirmed_text=?,confirmed_by=?,confirmed_at=CURRENT_TIMESTAMP(3)
                WHERE id=?
                """, confirmedText, operatorId, id);
    }
    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw new IllegalStateException(ex); }
    }
    private List<OcrRecordResponse.Candidate> candidates(String value) {
        if (value == null || value.isBlank()) return List.of();
        try { return mapper.readValue(value, new TypeReference<>() {}); }
        catch (JsonProcessingException ex) { return List.of(); }
    }
}
