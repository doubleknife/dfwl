package com.dfwl.fleet.imports.repository;

import com.dfwl.fleet.common.api.PageResponse;
import com.dfwl.fleet.imports.dto.response.ImportRowResponse;
import com.dfwl.fleet.imports.dto.response.ImportTaskResponse;
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
public class ImportRepository {

    private final JdbcTemplate jdbcTemplate;

    public ImportRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long createTask(String batchNo, String businessType, Long templateId, Long originalFileId,
                           int totalCount, int successCount, int failureCount, long operatorId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO import_task (batch_no, business_type, template_id, original_file_id, total_count,
                                             success_count, unpublished_count, failure_count, status, uploaded_by)
                    VALUES (?, ?, ?, ?, ?, ?, 0, ?, 'PREVIEWED', ?)
                    """, new String[]{"id"});
            ps.setString(1, batchNo);
            ps.setString(2, businessType);
            ps.setObject(3, templateId);
            ps.setLong(4, originalFileId);
            ps.setInt(5, totalCount);
            ps.setInt(6, successCount);
            ps.setInt(7, failureCount);
            ps.setLong(8, operatorId);
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    public void insertRow(long taskId, int rowNo, String rawJson, String normalizedJson, String previewStatus,
                          String errorCode, String errorMessage, String businessType, String businessUniqueKey) {
        jdbcTemplate.update("""
                INSERT INTO import_row (import_task_id, row_no, raw_data_json, normalized_data_json, preview_status,
                                        preview_error_code, preview_error_message, business_type, business_unique_key)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, taskId, rowNo, rawJson, normalizedJson, previewStatus, errorCode, errorMessage,
                businessType, businessUniqueKey);
    }

    public Optional<ImportTaskResponse> findTask(long id, boolean includeRows) {
        return jdbcTemplate.query("""
                SELECT * FROM import_task WHERE id = ?
                """, (rs, rowNum) -> new ImportTaskResponse(
                rs.getLong("id"),
                rs.getString("batch_no"),
                rs.getString("business_type"),
                readLong(rs, "template_id"),
                rs.getLong("original_file_id"),
                rs.getInt("total_count"),
                rs.getInt("success_count"),
                rs.getInt("unpublished_count"),
                rs.getInt("failure_count"),
                rs.getString("status"),
                rs.getLong("uploaded_by"),
                rs.getTimestamp("uploaded_at").toLocalDateTime(),
                rs.getTimestamp("completed_at") == null ? null : rs.getTimestamp("completed_at").toLocalDateTime(),
                includeRows ? findRows(rs.getLong("id")) : List.of()), id).stream().findFirst();
    }

    public boolean importFileExists(long attachmentId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM file_attachment
                WHERE id = ? AND purpose = 'IMPORT_FILE'
                """, Integer.class, attachmentId);
        return count != null && count > 0;
    }

    public Optional<ImportTemplateRecord> findTemplate(long templateId, String businessType) {
        return jdbcTemplate.query("""
                SELECT id, template_name, business_type, status
                FROM import_template
                WHERE id = ? AND business_type = ? AND status = 1
                """, (rs, rowNum) -> new ImportTemplateRecord(
                rs.getLong("id"),
                rs.getString("template_name"),
                rs.getString("business_type"),
                rs.getInt("status")), templateId, businessType).stream().findFirst();
    }

    public List<FieldMappingRecord> fieldMappings(long templateId) {
        return jdbcTemplate.query("""
                SELECT source_column, target_field, required_flag
                FROM import_field_mapping
                WHERE template_id = ?
                ORDER BY id
                """, (rs, rowNum) -> new FieldMappingRecord(
                rs.getString("source_column"),
                rs.getString("target_field"),
                rs.getInt("required_flag") == 1), templateId);
    }

    public PageResponse<ImportTaskResponse> listTasks(int pageNo, int pageSize) {
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM import_task", Long.class);
        List<ImportTaskResponse> records = jdbcTemplate.query("""
                SELECT * FROM import_task ORDER BY id DESC LIMIT ? OFFSET ?
                """, (rs, rowNum) -> new ImportTaskResponse(
                rs.getLong("id"),
                rs.getString("batch_no"),
                rs.getString("business_type"),
                readLong(rs, "template_id"),
                rs.getLong("original_file_id"),
                rs.getInt("total_count"),
                rs.getInt("success_count"),
                rs.getInt("unpublished_count"),
                rs.getInt("failure_count"),
                rs.getString("status"),
                rs.getLong("uploaded_by"),
                rs.getTimestamp("uploaded_at").toLocalDateTime(),
                rs.getTimestamp("completed_at") == null ? null : rs.getTimestamp("completed_at").toLocalDateTime(),
                List.of()), pageSize, Math.max(pageNo - 1, 0) * pageSize);
        return new PageResponse<>(pageNo, pageSize, total == null ? 0 : total, records);
    }

    public List<ImportRowResponse> findRows(long taskId) {
        return jdbcTemplate.query("""
                SELECT * FROM import_row WHERE import_task_id = ? ORDER BY row_no
                """, (rs, rowNum) -> new ImportRowResponse(
                rs.getLong("id"),
                rs.getInt("row_no"),
                rs.getString("raw_data_json"),
                rs.getString("normalized_data_json"),
                rs.getString("preview_status"),
                rs.getString("preview_error_code"),
                rs.getString("preview_error_message"),
                rs.getString("final_status"),
                rs.getString("final_error_code"),
                rs.getString("final_error_message"),
                rs.getString("business_type"),
                readLong(rs, "business_id"),
                rs.getString("business_unique_key")), taskId);
    }

    public boolean committedKeyExists(String businessType, String key) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM import_row
                WHERE business_type = ? AND business_unique_key = ? AND final_status IN ('SUCCESS', 'UNPUBLISHED')
                """, Integer.class, businessType, key);
        return count != null && count > 0;
    }

    public boolean beginCommit(long taskId) {
        return jdbcTemplate.update("""
                UPDATE import_task
                SET status = 'COMMITTING'
                WHERE id = ? AND status = 'PREVIEWED'
                """, taskId) == 1;
    }

    public void markCommitted(long taskId, int successCount, int unpublishedCount, int failureCount) {
        jdbcTemplate.update("""
                UPDATE import_task
                SET success_count = ?, unpublished_count = ?, failure_count = ?, status = 'COMMITTED', completed_at = ?
                WHERE id = ? AND status IN ('PREVIEWED', 'COMMITTING')
                """, successCount, unpublishedCount, failureCount, Timestamp.valueOf(LocalDateTime.now()), taskId);
    }

    public void updatePreviewCounts(long taskId, int successCount, int failureCount) {
        jdbcTemplate.update("""
                UPDATE import_task
                SET success_count = ?, failure_count = ?, status = 'PREVIEWED'
                WHERE id = ?
                """, successCount, failureCount, taskId);
    }

    public void markRowFinal(long rowId, String status, String errorCode, String errorMessage,
                             String businessType, Long businessId, String businessUniqueKey) {
        jdbcTemplate.update("""
                UPDATE import_row
                SET final_status = ?, final_error_code = ?, final_error_message = ?,
                    business_type = ?, business_id = ?, business_unique_key = ?
                WHERE id = ?
                """, status, errorCode, errorMessage, businessType, businessId, businessUniqueKey, rowId);
    }

    public Optional<Long> findImportedBusiness(String businessType, String businessUniqueKey) {
        return jdbcTemplate.query("""
                SELECT business_id
                FROM import_row
                WHERE business_type = ? AND business_unique_key = ?
                  AND final_status IN ('SUCCESS', 'UNPUBLISHED')
                  AND business_id IS NOT NULL
                ORDER BY id LIMIT 1
                """, (rs, rowNum) -> rs.getLong("business_id"), businessType, businessUniqueKey).stream().findFirst();
    }

    private Long readLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }


    public record ImportTemplateRecord(long id, String templateName, String businessType, int status) {
    }

    public record FieldMappingRecord(String sourceColumn, String targetField, boolean required) {
    }
}
