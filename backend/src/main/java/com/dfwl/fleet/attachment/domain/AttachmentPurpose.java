package com.dfwl.fleet.attachment.domain;

import java.util.Arrays;
import java.util.Optional;

public enum AttachmentPurpose {
    TIRE_OCR,
    WEIGHT_ADJUST,
    APPROVAL_APPLICATION,
    APPROVAL_ACTION,
    IMPORT_FILE;

    public static Optional<AttachmentPurpose> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(purpose -> purpose.name().equals(value.trim().toUpperCase()))
                .findFirst();
    }
}
