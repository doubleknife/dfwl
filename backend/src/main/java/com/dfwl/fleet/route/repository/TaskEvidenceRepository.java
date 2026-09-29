package com.dfwl.fleet.route.repository;

import com.dfwl.fleet.common.error.BusinessException;
import com.dfwl.fleet.common.error.ErrorCode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TaskEvidenceRepository {
    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/heic", "image/heif");
    private final JdbcTemplate jdbc;

    public TaskEvidenceRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void bindOriginalTickets(long taskId, long participantId, long versionId,
                                    long actor, List<Long> ids) {
        if (ids == null || ids.isEmpty() || ids.size() > 10 || new HashSet<>(ids).size() != ids.size())
            throw new BusinessException(ErrorCode.ATTACHMENT_004);
        for (Long id : ids) {
            if (id == null || id <= 0 || !valid(id, taskId, "ROUTE_UNLOAD_WEIGHT_TICKET", actor))
                throw new BusinessException(ErrorCode.ATTACHMENT_004);
            jdbc.update("""
                    INSERT INTO task_unload_evidence
                    (task_id, participant_id, weight_version_id, attachment_id, uploaded_by)
                    VALUES (?, ?, ?, ?, ?)
                    """, taskId, participantId, versionId, id, actor);
        }
    }

    public void validateAdjustmentEvidence(long taskId, List<Long> ids) {
        if (ids == null || ids.isEmpty()) return;
        if (ids.size() > 10 || new HashSet<>(ids).size() != ids.size())
            throw new BusinessException(ErrorCode.ATTACHMENT_004);
        for (Long id : ids) {
            if (id == null || id <= 0 || !valid(id, taskId, "WEIGHT_ADJUST", null))
                throw new BusinessException(ErrorCode.ATTACHMENT_004);
        }
    }

    private boolean valid(long id, long taskId, String purpose, Long uploadedBy) {
        List<String> mimeTypes = jdbc.query("""
                SELECT content_type FROM file_attachment
                WHERE id = ? AND owner_type = 'TASK' AND owner_id = ? AND purpose = ?
                  AND (? IS NULL OR uploaded_by = ?) FOR UPDATE
                """, (rs, row) -> rs.getString("content_type"), id, taskId, purpose, uploadedBy, uploadedBy);
        return mimeTypes.size() == 1 && IMAGE_TYPES.contains(mimeTypes.getFirst());
    }
}
