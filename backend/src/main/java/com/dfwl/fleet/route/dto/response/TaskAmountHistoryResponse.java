package com.dfwl.fleet.route.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TaskAmountHistoryResponse(
        long id, String amountKind, BigDecimal beforeAmount, BigDecimal afterAmount,
        String reason, long operatorId, LocalDateTime operatedAt
) {}
