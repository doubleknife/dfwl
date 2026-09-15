package com.dfwl.fleet.attachment.repository;

import com.dfwl.fleet.attachment.domain.FileAttachment;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class FileAttachmentRepository {

    private final JdbcTemplate jdbcTemplate;

    public FileAttachmentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<FileAttachment> list(String ownerType, Long ownerId, String purpose) {
        return jdbcTemplate.query("""
                SELECT id, owner_type, owner_id, purpose, storage_key, original_filename,
                       content_type, file_size, file_hash, uploaded_by, uploaded_at
                FROM file_attachment
                WHERE (? IS NULL OR owner_type = ?)
                  AND (? IS NULL OR owner_id = ?)
                  AND (? IS NULL OR purpose = ?)
                ORDER BY id DESC
                """, this::mapAttachment,
                ownerType, ownerType,
                ownerId, ownerId,
                purpose, purpose);
    }

    public Optional<FileAttachment> find(long id) {
        return jdbcTemplate.query("""
                SELECT id, owner_type, owner_id, purpose, storage_key, original_filename,
                       content_type, file_size, file_hash, uploaded_by, uploaded_at
                FROM file_attachment
                WHERE id = ?
                """, this::mapAttachment, id).stream().findFirst();
    }

    public List<FileAttachment> findByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", ids.stream().map(id -> "?").toList());
        return jdbcTemplate.query("""
                SELECT id, owner_type, owner_id, purpose, storage_key, original_filename,
                       content_type, file_size, file_hash, uploaded_by, uploaded_at
                FROM file_attachment
                WHERE id IN (%s)
                ORDER BY id
                """.formatted(placeholders), this::mapAttachment, ids.toArray());
    }

    public Optional<FileAttachment> findDuplicate(String ownerType, long ownerId, String purpose,
                                                  String fileHash, String originalFilename, long fileSize) {
        return jdbcTemplate.query("""
                SELECT id, owner_type, owner_id, purpose, storage_key, original_filename,
                       content_type, file_size, file_hash, uploaded_by, uploaded_at
                FROM file_attachment
                WHERE owner_type = ? AND owner_id = ? AND purpose = ?
                  AND file_hash = ? AND original_filename = ? AND file_size = ?
                ORDER BY id DESC LIMIT 1
                """, this::mapAttachment, ownerType, ownerId, purpose, fileHash, originalFilename, fileSize)
                .stream()
                .findFirst();
    }

    public long create(String ownerType, long ownerId, String purpose, String storageKey,
                       String originalFilename, String contentType, long fileSize, String fileHash,
                       long uploadedBy) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            jdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement("""
                        INSERT INTO file_attachment (owner_type, owner_id, purpose, storage_key, original_filename,
                                                     content_type, file_size, file_hash, uploaded_by)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, new String[]{"id"});
                ps.setString(1, ownerType);
                ps.setLong(2, ownerId);
                ps.setString(3, purpose);
                ps.setString(4, storageKey);
                ps.setString(5, originalFilename);
                ps.setString(6, contentType);
                ps.setLong(7, fileSize);
                ps.setString(8, fileHash);
                ps.setLong(9, uploadedBy);
                return ps;
            }, keyHolder);
        } catch (DataAccessException ex) {
            throw ex;
        }
        return keyHolder.getKey().longValue();
    }

    public int bindTemporaryApprovalAttachments(long uploadedBy, List<Long> ids, long approvalInstanceId, long submissionVersionId) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        String placeholders = String.join(",", ids.stream().map(id -> "?").toList());
        List<Object> args = new java.util.ArrayList<>();
        args.add(approvalInstanceId);
        args.add(uploadedBy);
        args.add(uploadedBy);
        args.addAll(ids);
        return jdbcTemplate.update("""
                UPDATE file_attachment
                SET owner_type = 'APPROVAL', owner_id = ?
                WHERE owner_type = 'APPROVAL_UPLOAD'
                  AND owner_id = ?
                  AND purpose = 'APPROVAL_APPLICATION'
                  AND uploaded_by = ?
                  AND id IN (%s)
                """.formatted(placeholders), args.toArray());
    }

    public int bindTemporaryApprovalActionAttachments(long uploadedBy, List<Long> ids, long actionId) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        String placeholders = String.join(",", ids.stream().map(id -> "?").toList());
        List<Object> args = new java.util.ArrayList<>();
        args.add(actionId);
        args.add(uploadedBy);
        args.add(uploadedBy);
        args.addAll(ids);
        return jdbcTemplate.update("""
                UPDATE file_attachment
                SET owner_type = 'APPROVAL_ACTION', owner_id = ?
                WHERE owner_type = 'APPROVAL_UPLOAD'
                  AND owner_id = ?
                  AND purpose = 'APPROVAL_ACTION'
                  AND uploaded_by = ?
                  AND id IN (%s)
                """.formatted(placeholders), args.toArray());
    }

    public boolean ownerExists(String ownerType, long ownerId) {
        if (ownerId == 0) {
            return true;
        }
        return switch (ownerType) {
            case "ROUTE" -> exists("route_task", ownerId, "deleted_at IS NULL");
            case "TIRE", "TIRE_OCR" -> exists("tire", ownerId, "deleted_at IS NULL");
            case "APPROVAL", "APPROVAL_APPLICATION" -> exists("approval_instance", ownerId, null);
            case "APPROVAL_ACTION" -> exists("approval_action", ownerId, null);
            case "APPROVAL_UPLOAD" -> true;
            case "IMPORT", "IMPORT_FILE" -> exists("import_task", ownerId, null);
            default -> false;
        };
    }

    public boolean approvalOwnerInProcess(long ownerId) {
        if (ownerId == 0) {
            return true;
        }
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM approval_instance
                WHERE id = ? AND status IN ('PENDING', 'RETURNED_TO_APPLICANT')
                """, Integer.class, ownerId);
        return count != null && count > 0;
    }

    private boolean exists(String tableName, long id, String extraCondition) {
        String sql = "SELECT COUNT(*) FROM " + tableName + " WHERE id = ?" +
                (extraCondition == null ? "" : " AND " + extraCondition);
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, id);
        return count != null && count > 0;
    }

    private FileAttachment mapAttachment(ResultSet rs, int rowNum) throws SQLException {
        Timestamp uploadedAt = rs.getTimestamp("uploaded_at");
        return new FileAttachment(
                rs.getLong("id"),
                rs.getString("owner_type"),
                rs.getLong("owner_id"),
                rs.getString("purpose"),
                rs.getString("storage_key"),
                rs.getString("original_filename"),
                rs.getString("content_type"),
                rs.getLong("file_size"),
                rs.getString("file_hash"),
                rs.getLong("uploaded_by"),
                uploadedAt == null ? null : uploadedAt.toLocalDateTime());
    }
}
