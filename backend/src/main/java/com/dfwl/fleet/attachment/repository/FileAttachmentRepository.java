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

    public boolean canCurrentDriverUploadUnloadTicket(long routeId, long userId, String phone) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM transport_task r
                JOIN transport_participant p ON p.id = r.current_participant_id AND p.task_id = r.id
                JOIN driver d ON d.id = p.driver_id
                WHERE r.id = ? AND r.deleted_at IS NULL AND r.status = 'IN_TRANSIT'
                  AND p.status = 'IN_TRANSIT' AND d.deleted_at IS NULL AND d.status = 1
                  AND (d.user_id = ? OR (d.user_id IS NULL AND d.phone = ?))
                """, Integer.class, routeId, userId, phone);
        return count != null && count == 1;
    }

    public boolean canDriverReadUnloadTicket(long routeId, long userId, String phone) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM transport_participant p
                JOIN driver d ON d.id = p.driver_id
                JOIN transport_task r ON r.id = p.task_id
                WHERE r.id = ? AND r.deleted_at IS NULL AND d.deleted_at IS NULL
                  AND (d.user_id = ? OR (d.user_id IS NULL AND d.phone = ?))
                """, Integer.class, routeId, userId, phone);
        return count != null && count > 0;
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

    public boolean importTaskMatches(long taskId, long attachmentId, long operatorId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM import_task
                WHERE id = ? AND source_attachment_id = ? AND created_by = ?
                """, Integer.class, taskId, attachmentId, operatorId);
        return count != null && count == 1;
    }

    public int bindImportFile(long attachmentId, long taskId, long operatorId) {
        return jdbcTemplate.update("""
                UPDATE file_attachment
                SET owner_type = 'IMPORT', owner_id = ?
                WHERE id = ? AND purpose = 'IMPORT_FILE' AND uploaded_by = ?
                  AND owner_type IN ('IMPORT', 'IMPORT_FILE') AND owner_id = 0
                """, taskId, attachmentId, operatorId);
    }

    public boolean ownerExists(String ownerType, long ownerId) {
        if (ownerId == 0) {
            return true;
        }
        return switch (ownerType) {
            case "TASK" -> exists("transport_task", ownerId, "deleted_at IS NULL");
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

    public boolean canViewSubmissionEvidence(long attachmentId, long userId, boolean canViewAll) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM approval_submission_attachment e
                JOIN approval_submission_version s ON s.id = e.submission_version_id
                JOIN approval_instance i ON i.id = s.approval_instance_id
                WHERE e.attachment_id = ? AND (
                    ? = 1 OR i.applicant_user_id = ? OR EXISTS (
                        SELECT 1 FROM approval_task t
                        WHERE t.approval_instance_id = i.id AND t.approver_user_id = ?
                    ) OR EXISTS (
                        SELECT 1 FROM approval_action a JOIN approval_task t ON t.id = a.task_id
                        WHERE t.approval_instance_id = i.id AND a.operator_id = ?
                    )
                )
                """, Integer.class, attachmentId, canViewAll ? 1 : 0, userId, userId, userId);
        return count != null && count > 0;
    }

    public boolean canViewApprovalInstance(long instanceId, long userId, boolean canViewAll) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM approval_instance i
                WHERE i.id = ? AND (? = 1 OR i.applicant_user_id = ? OR EXISTS (
                    SELECT 1 FROM approval_task t
                    WHERE t.approval_instance_id = i.id AND t.approver_user_id = ?
                ) OR EXISTS (
                    SELECT 1 FROM approval_action a JOIN approval_task t ON t.id = a.task_id
                    WHERE t.approval_instance_id = i.id AND a.operator_id = ?
                ))
                """, Integer.class, instanceId, canViewAll ? 1 : 0, userId, userId, userId);
        return count != null && count > 0;
    }

    public boolean canViewApprovalAction(long actionId, long userId, boolean canViewAll) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM approval_action a
                JOIN approval_task t ON t.id = a.task_id
                JOIN approval_instance i ON i.id = t.approval_instance_id
                WHERE a.id = ? AND (? = 1 OR i.applicant_user_id = ? OR EXISTS (
                    SELECT 1 FROM approval_task visible
                    WHERE visible.approval_instance_id = i.id AND visible.approver_user_id = ?
                ) OR EXISTS (
                    SELECT 1 FROM approval_action own JOIN approval_task task ON task.id = own.task_id
                    WHERE task.approval_instance_id = i.id AND own.operator_id = ?
                ))
                """, Integer.class, actionId, canViewAll ? 1 : 0, userId, userId, userId);
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
