package com.dfwl.fleet.route.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record RouteWeightVersionResponse(
        Long id,
        Long routeId,
        Integer versionNo,
        BigDecimal grossWeight,
        BigDecimal tareWeight,
        BigDecimal netWeight,
        BigDecimal loadStandardThreshold,
        Boolean loadStandardMet,
        String sourceType,
        Long operatorId,
        LocalDateTime operationTime,
        String reason,
        List<Long> attachmentIds
) {
}
