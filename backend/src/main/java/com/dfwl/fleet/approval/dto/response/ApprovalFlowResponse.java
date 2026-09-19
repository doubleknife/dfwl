package com.dfwl.fleet.approval.dto.response;

public record ApprovalFlowResponse(
        Long id,
        String approvalType,
        String flowName,
        Integer versionNo,
        String status
) {
}
