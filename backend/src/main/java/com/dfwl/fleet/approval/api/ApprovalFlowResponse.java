package com.dfwl.fleet.approval.api;

public record ApprovalFlowResponse(
        Long id,
        String approvalType,
        String flowName,
        Integer versionNo,
        String status
) {
}
