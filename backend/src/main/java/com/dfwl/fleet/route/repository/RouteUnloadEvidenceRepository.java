package com.dfwl.fleet.route.repository;

import com.dfwl.fleet.attachment.domain.FileAttachment;
import com.dfwl.fleet.common.error.BusinessException;
import com.dfwl.fleet.common.error.ErrorCode;
import java.util.List;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RouteUnloadEvidenceRepository {
    private static final Set<String> IMAGE_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/heic", "image/heif");
    private final JdbcTemplate jdbc;

    public RouteUnloadEvidenceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Called after locking route_task, in the unload transaction. */
    public void bind(long routeId, long participantId, long weightVersionId,
                     long uploadedBy, List<Long> attachmentIds) {
        if (attachmentIds == null || attachmentIds.isEmpty() || attachmentIds.size() > 10
                || attachmentIds.stream().anyMatch(id -> id == null || id <= 0)
                || Set.copyOf(attachmentIds).size() != attachmentIds.size()) {
            throw new BusinessException(ErrorCode.ATTACHMENT_004);
        }
        for (Long attachmentId : attachmentIds.stream().sorted().toList()) {
            List<FileAttachment> found = jdbc.query("""
                    SELECT id, owner_type, owner_id, purpose, storage_key, original_filename,
                           content_type, file_size, file_hash, uploaded_by, uploaded_at
                    FROM file_attachment WHERE id = ? FOR UPDATE
                    """, (rs, row) -> new FileAttachment(
                    rs.getLong("id"), rs.getString("owner_type"), rs.getLong("owner_id"),
                    rs.getString("purpose"), rs.getString("storage_key"),
                    rs.getString("original_filename"), rs.getString("content_type"),
                    rs.getLong("file_size"), rs.getString("file_hash"), rs.getLong("uploaded_by"),
                    rs.getTimestamp("uploaded_at").toLocalDateTime()), attachmentId);
            if (found.size() != 1) throw new BusinessException(ErrorCode.ATTACHMENT_004);
            FileAttachment attachment = found.get(0);
            if (!"ROUTE".equals(attachment.ownerType()) || attachment.ownerId() != routeId
                    || !"ROUTE_UNLOAD_WEIGHT_TICKET".equals(attachment.purpose())
                    || attachment.uploadedBy() != uploadedBy
                    || !IMAGE_TYPES.contains(attachment.contentType())) {
                throw new BusinessException(ErrorCode.ATTACHMENT_004);
            }
            try {
                jdbc.update("""
                        INSERT INTO route_unload_evidence
                            (route_id, participant_id, weight_version_id, attachment_id, uploaded_by)
                        VALUES (?, ?, ?, ?, ?)
                        """, routeId, participantId, weightVersionId, attachmentId, uploadedBy);
            } catch (DataIntegrityViolationException ex) {
                throw new BusinessException(ErrorCode.ATTACHMENT_004);
            }
        }
    }

}
