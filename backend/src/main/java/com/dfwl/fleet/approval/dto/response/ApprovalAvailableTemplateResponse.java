package com.dfwl.fleet.approval.dto.response;

import java.util.List;

public record ApprovalAvailableTemplateResponse(
        Long templateId,
        String templateCode,
        String category,
        Long flowId,
        Integer versionNo,
        String flowName,
        String evidenceRequirement,
        String expenseType,
        List<String> allowedInputModes,
        List<String> allowedAttributionTypes
) {
}
