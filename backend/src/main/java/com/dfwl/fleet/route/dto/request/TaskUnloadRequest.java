package com.dfwl.fleet.route.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

public record TaskUnloadRequest(@NotNull BigDecimal grossWeight, @NotNull BigDecimal tareWeight,
                                @NotNull List<Long> ticketAttachmentIds, @NotBlank String requestKey) {}
