package com.dfwl.fleet.tire.ocr;

public class OcrProviderException extends RuntimeException {

    private final String errorCode;

    public OcrProviderException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public OcrProviderException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
