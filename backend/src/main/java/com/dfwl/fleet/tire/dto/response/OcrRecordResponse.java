package com.dfwl.fleet.tire.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record OcrRecordResponse(
        long id,
        long attachmentId,
        String ocrProvider,
        String providerRequestId,
        String rawResultJson,
        String recognizedText,
        String candidateText,
        String ocrStatus,
        String errorCode,
        String errorMessage,
        List<Candidate> candidates,
        String confirmedText,
        Long confirmedBy,
        LocalDateTime confirmedAt) {
    public record Candidate(String candidate, Double confidence, String sourceText, boolean inventoryMatched) {
    }
}
