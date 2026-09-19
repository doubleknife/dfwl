package com.dfwl.fleet.system.dto.response;

import java.time.LocalDateTime;

public record AuditLogResponse(
        long id,
        String module,
        String businessType,
        Long businessId,
        String operationType,
        String beforeJson,
        String afterJson,
        long operatorId,
        LocalDateTime operationTime,
        String reason,
        String ip,
        String terminal
) {
}
