package com.dfwl.fleet.tire.ocr;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "fleet.ocr", name = "provider", havingValue = "DISABLED", matchIfMissing = true)
public class DisabledOcrProvider implements OcrProvider {

    @Override
    public OcrProviderResult recognize(String imageBase64, String contentType) {
        throw new OcrProviderException("OCR_DISABLED", "OCR provider is disabled");
    }
}
