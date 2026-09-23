package com.dfwl.fleet.approval.spi;

import java.util.List;
import java.util.Map;

public record ApprovalCreationCommand(
        String approvalType,
        String businessType,
        Long businessId,
        Map<String, Object> businessSnapshot,
        List<Long> attachmentIds) {
}
