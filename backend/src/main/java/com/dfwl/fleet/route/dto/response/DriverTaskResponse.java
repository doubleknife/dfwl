package com.dfwl.fleet.route.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

/** The driver contract deliberately excludes all price, salary, profit and threshold facts. */
public record DriverTaskResponse(
        long id, String taskNo, String customerName, String productName,
        String loadingPlace, String unloadingPlace, LocalDate businessDate,
        String status, String vehiclePlate, BigDecimal grossWeight,
        BigDecimal tareWeight, BigDecimal netWeight, boolean currentParticipant, int version
) {}
