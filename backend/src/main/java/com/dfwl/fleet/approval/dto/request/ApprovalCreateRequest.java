package com.dfwl.fleet.approval.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

public record ApprovalCreateRequest(
        @NotBlank String approvalType,
        @NotBlank String businessType,
        Long businessId,
        @NotNull Map<String, Object> businessSnapshot,
        List<Long> attachmentIds
) {
}
