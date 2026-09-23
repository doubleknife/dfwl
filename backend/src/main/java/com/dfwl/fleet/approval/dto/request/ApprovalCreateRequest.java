package com.dfwl.fleet.approval.dto.request;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.List;
import java.util.Map;

public record ApprovalCreateRequest(
        String approvalType,
        String businessType,
        Long businessId,
        Map<String, Object> businessSnapshot,
        List<Long> attachmentIds,
        Long templateId,
        String attributionType,
        Long routeId,
        @JsonDeserialize(contentUsing = ExactExpenseItemDeserializer.class) List<Map<String, Object>> items
) {
    public ApprovalCreateRequest(String approvalType, String businessType, Long businessId,
                                 Map<String, Object> businessSnapshot, List<Long> attachmentIds) {
        this(approvalType, businessType, businessId, businessSnapshot, attachmentIds, null, null, null, null);
    }

    public ApprovalCreateRequest(String approvalType, String businessType, Long businessId,
                                 Map<String, Object> businessSnapshot, List<Long> attachmentIds,
                                 Long templateId, String attributionType, Long routeId) {
        this(approvalType, businessType, businessId, businessSnapshot, attachmentIds,
                templateId, attributionType, routeId, null);
    }
}
