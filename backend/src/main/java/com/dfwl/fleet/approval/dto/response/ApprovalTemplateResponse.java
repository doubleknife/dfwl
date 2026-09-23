package com.dfwl.fleet.approval.dto.response;

public record ApprovalTemplateResponse(
        long id,
        String templateCode,
        String category,
        String approvalType,
        Long activeFlowId
) {
}
