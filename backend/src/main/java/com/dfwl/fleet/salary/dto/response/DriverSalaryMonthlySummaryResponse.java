package com.dfwl.fleet.salary.dto.response;

import java.math.BigDecimal;

public record DriverSalaryMonthlySummaryResponse(String businessMonth, BigDecimal totalAmount, long entryCount) {
}
