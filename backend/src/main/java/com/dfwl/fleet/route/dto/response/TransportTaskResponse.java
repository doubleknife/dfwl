package com.dfwl.fleet.route.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record TransportTaskResponse(
        long id, String taskNo, long templateId, String templateCode, String templateName,
        String customerName, String productName, String loadingPlace, String unloadingPlace,
        LocalDate businessDate, String status, BigDecimal actualUnitPrice,
        BigDecimal actualInformationFee, Long currentParticipantId, String currentDriverName,
        String currentVehiclePlate, BigDecimal grossWeight, BigDecimal tareWeight,
        BigDecimal netWeight, Long effectiveWeightVersionId, String standardResult, LocalDateTime completedAt,
        int version
) {}
