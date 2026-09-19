package com.dfwl.fleet.approval.dto.response;

import java.util.List;

public record ApprovalFlowDetailResponse(
        ApprovalFlowResponse flow,
        List<ApprovalFlowNodeResponse> nodes
) {
}
