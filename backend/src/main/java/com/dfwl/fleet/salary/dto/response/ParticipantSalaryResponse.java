package com.dfwl.fleet.salary.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record ParticipantSalaryResponse(
        long participantId, long taskId, String taskNo, int sequenceNo,
        long driverId, String driverName, String driverType, LocalDate businessDate,
        BigDecimal salaryAmount, String sourceType, LocalDateTime updatedAt
) {}
