package com.dfwl.fleet.imports.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record ImportTaskResponse(
        Long id,
        String batchNo,
        String businessType,
        Long templateId,
        Long originalFileId,
        Integer totalCount,
        Integer successCount,
        Integer unpublishedCount,
        Integer failureCount,
        String status,
        Long uploadedBy,
        LocalDateTime uploadedAt,
        LocalDateTime completedAt,
        List<ImportRowResponse> rows
) {
}
