package com.dfwl.fleet.approval.spi;

public record ApprovalBusinessContext(
        Long approvalInstanceId,
        String approvalType,
        String businessType,
        Long businessId
) {
}
