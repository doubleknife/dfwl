package com.dfwl.fleet.tire.ocr;

public interface OcrProvider {

    OcrProviderResult recognize(String imageBase64, String contentType);
}
