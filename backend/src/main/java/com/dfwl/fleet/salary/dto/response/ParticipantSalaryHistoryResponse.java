package com.dfwl.fleet.salary.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ParticipantSalaryHistoryResponse(
        long id, long participantId, BigDecimal beforeAmount, BigDecimal afterAmount,
        String reason, long operatorId, LocalDateTime operatedAt, String operatorPhone
) {}
