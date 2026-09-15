package com.dfwl.fleet.testsupport;

import java.sql.Date;
import java.text.SimpleDateFormat;

public final class H2Functions {

    private H2Functions() {
    }

    public static String dateFormat(Date value, String pattern) {
        if (value == null || pattern == null) {
            return null;
        }
        String javaPattern = pattern
                .replace("%Y", "yyyy")
                .replace("%m", "MM")
                .replace("%d", "dd");
        return new SimpleDateFormat(javaPattern).format(value);
    }
}
