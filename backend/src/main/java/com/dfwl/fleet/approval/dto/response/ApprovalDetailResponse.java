package com.dfwl.fleet.approval.dto.response;

import com.dfwl.fleet.attachment.dto.response.AttachmentResponse;
import java.time.LocalDateTime;
import java.util.List;

public record ApprovalDetailResponse(
        ApprovalResponse approval,
        List<SubmissionVersion> submissions,
        List<TaskHistory> tasks
) {
    public record SubmissionVersion(
            Long id,
            Integer versionNo,
            String businessSnapshotJson,
            Long submittedBy,
            LocalDateTime submittedAt,
            List<AttachmentResponse> attachments
    ) {
    }

    public record TaskHistory(
            Long id,
            Long submissionVersionId,
            Integer submissionVersionNo,
            Integer nodeOrder,
            String nodeName,
            Long approverUserId,
            String status,
            Boolean invalidated,
            LocalDateTime createdAt,
            LocalDateTime completedAt,
            List<ActionHistory> actions
    ) {
    }

    public record ActionHistory(
            Long id,
            String actionType,
            Long operatorId,
            String comment,
            Integer targetNodeOrder,
            LocalDateTime operatedAt,
            List<AttachmentResponse> attachments
    ) {
    }
}
