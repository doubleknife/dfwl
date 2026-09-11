package com.dfwl.fleet.master.api;

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
