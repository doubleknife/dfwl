package com.dfwl.fleet.imports.spi;

/**
 * Key resolution or read-only business validation outcome. A successful result carries
 * the resolved key with null error fields; failure preserves the original code/message.
 * The key can be null when malformed input prevents its derivation. Failure is a business
 * outcome, never a substitute for a database exception or a partially committed write.
 */
public record ImportValidationResult(boolean success, String businessUniqueKey,
                                     String errorCode, String errorMessage) {
}
