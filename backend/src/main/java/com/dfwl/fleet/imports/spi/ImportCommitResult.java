package com.dfwl.fleet.imports.spi;

import java.util.Objects;

/**
 * Business outcome for imports to persist on import_row. SUCCESS and UNPUBLISHED both
 * retain the new businessId; FAILURE has null businessId and the stable error details.
 * UNPUBLISHED is a distinct completed business write, not a generic success/failure.
 */
public record ImportCommitResult(Status finalStatus, Long businessId, String businessUniqueKey,
                                 String errorCode, String errorMessage) {
    public ImportCommitResult {
        Objects.requireNonNull(finalStatus, "finalStatus");
    }

    public enum Status {
        SUCCESS, UNPUBLISHED, FAILURE
    }
}
