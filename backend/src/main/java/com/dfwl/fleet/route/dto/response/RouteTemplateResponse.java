package com.dfwl.fleet.route.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RouteTemplateResponse(
        long id,
        String stableCode,
        String name,
        String customerName,
        long productId,
        String loadingPlace,
        String unloadingPlace,
        BigDecimal defaultUnitPrice,
        BigDecimal defaultInformationFee,
        BigDecimal defaultDriverSalary,
        boolean enabled,
        String remark,
        int version,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
