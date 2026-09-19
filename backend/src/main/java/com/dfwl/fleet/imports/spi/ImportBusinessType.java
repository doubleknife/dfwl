package com.dfwl.fleet.imports.spi;

import java.util.Locale;
import java.util.Optional;

/** Stable business identifiers, independent of the existing application service's nested enum. */
public enum ImportBusinessType {
    ROUTE, TIRE, EXPENSE, SALARY;

    /** Unknown, blank and null values have no matching type; dispatchers must not choose a fallback. */
    public static Optional<ImportBusinessType> parse(String value) {
        if (value == null) return Optional.empty();
        try {
            return Optional.of(valueOf(value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
