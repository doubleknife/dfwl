package com.dfwl.fleet.common.error;

public class BusinessException extends RuntimeException {

    private final String code;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode.name(), errorCode.defaultMessage());
    }

    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}

