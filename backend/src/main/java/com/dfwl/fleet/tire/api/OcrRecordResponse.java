package com.dfwl.fleet.tire.api;

import java.time.LocalDateTime;

public record OcrRecordResponse(
        long id,
        long attachmentId,
        String ocrProvider,
        String rawResultJson,
        String recognizedText,
        String confirmedText,
        Long confirmedBy,
        LocalDateTime confirmedAt) {
}
