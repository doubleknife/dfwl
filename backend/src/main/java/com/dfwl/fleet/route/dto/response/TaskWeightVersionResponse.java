package com.dfwl.fleet.route.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record TaskWeightVersionResponse(
        long id, int versionNo, String sourceType, BigDecimal grossWeight, BigDecimal tareWeight, BigDecimal netWeight,
        String standardResult, String reason, long operatorId, LocalDateTime operatedAt,
        List<Long> evidenceAttachmentIds, List<Long> originalTicketAttachmentIds
) {}
