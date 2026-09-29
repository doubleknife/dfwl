package com.dfwl.fleet.salary.dto.response;

import com.dfwl.fleet.common.api.PageResponse;

public record SalarySlipResponse(
        DriverSalaryMonthlySummaryResponse summary,
        PageResponse<ParticipantSalaryResponse> details
) {}
