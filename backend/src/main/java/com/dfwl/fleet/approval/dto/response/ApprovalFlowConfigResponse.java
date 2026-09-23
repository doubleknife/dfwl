package com.dfwl.fleet.approval.dto.response;

import java.util.List;

public record ApprovalFlowConfigResponse(
        ApprovalFlowResponse flow,
        List<ApprovalFlowNodeResponse> nodes,
        String evidenceRequirement,
        String configurationStatus,
        List<Long> initiatorRoleIds,
        ExpenseConfig expenseConfig
) {
    public record ExpenseConfig(String expenseType, List<String> inputModes, List<String> attributionTypes) {
    }
}
