package com.dfwl.fleet.approval.api;

import java.util.List;

public record ApprovalFlowDetailResponse(
        ApprovalFlowResponse flow,
        List<ApprovalFlowNodeResponse> nodes
) {
}
