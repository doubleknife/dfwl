package com.dfwl.fleet.tire.ocr;

import java.util.List;

public record OcrProviderResult(
        String provider,
        String requestId,
        String rawResultJson,
        List<DetectedText> detections
) {
    public record DetectedText(String text, Double confidence) {
    }
}
