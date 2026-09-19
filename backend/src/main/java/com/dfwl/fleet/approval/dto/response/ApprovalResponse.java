package com.dfwl.fleet.approval.dto.response;

import java.time.LocalDateTime;

public record ApprovalResponse(
        Long id,
        String approvalNo,
        String approvalType,
        Long flowId,
        Integer flowVersion,
        String businessType,
        Long businessId,
        Long applicantUserId,
        String status,
        Integer currentNodeOrder,
        LocalDateTime createdAt,
        LocalDateTime completedAt
) {
}
