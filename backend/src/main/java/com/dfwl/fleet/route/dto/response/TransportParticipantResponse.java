package com.dfwl.fleet.route.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransportParticipantResponse(
        long id, int sequenceNo, long driverId, String driverName, String driverType,
        String companyVehiclePlate, String trailerPlate, String outsourcedPlate,
        String sourceType, String status, boolean current, BigDecimal salary,
        LocalDateTime assignedAt, LocalDateTime departedAt, LocalDateTime endedAt,
        String endReason, int version
) {}
