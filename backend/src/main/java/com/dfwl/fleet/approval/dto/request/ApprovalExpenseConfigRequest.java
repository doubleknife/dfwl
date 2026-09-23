package com.dfwl.fleet.approval.dto.request;

import java.util.List;

public record ApprovalExpenseConfigRequest(
        String expenseType,
        List<String> inputModes,
        List<String> attributionTypes
) {
}
