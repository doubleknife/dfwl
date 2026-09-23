package com.dfwl.fleet.approval.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record ApprovalTemplateCreateRequest(
        @NotBlank String templateCode,
        @NotBlank String category,
        @NotBlank String flowName,
        String evidenceRequirement,
        List<Long> initiatorRoleIds,
        @Valid @NotEmpty List<ApprovalFlowUpdateRequest.Node> nodes,
        @Valid ApprovalExpenseConfigRequest expenseConfig
) {
}
