package com.dfwl.fleet.approval.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

public record ApprovalCreateRequest(
        String approvalType,
        String businessType,
        Long businessId,
        @NotNull Map<String, Object> businessSnapshot,
        List<Long> attachmentIds,
        Long templateId,
        String attributionType,
        Long routeId
) {
    public ApprovalCreateRequest(String approvalType, String businessType, Long businessId,
                                 Map<String, Object> businessSnapshot, List<Long> attachmentIds) {
        this(approvalType, businessType, businessId, businessSnapshot, attachmentIds, null, null, null);
    }
}
