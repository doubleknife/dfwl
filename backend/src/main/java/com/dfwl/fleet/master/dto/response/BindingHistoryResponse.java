package com.dfwl.fleet.master.dto.response;

import java.time.LocalDateTime;

public record BindingHistoryResponse(
        Long id,
        Long subjectId,
        Long targetId,
        LocalDateTime bindTime,
        LocalDateTime unbindTime,
        Long bindBy,
        Long unbindBy,
        String unbindReason
) {
}
