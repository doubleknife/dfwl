package com.dfwl.fleet.master.validation;

import com.dfwl.fleet.common.error.BusinessException;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.Period;

/** Identity card is the sole source for the driver's birth date and current age. */
public final class MainlandIdCard {
    private static final int[] WEIGHTS = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
    private static final String CHECK_DIGITS = "10X98765432";

    private MainlandIdCard() { }

    public static LocalDate requireBirthDate(String value) {
        LocalDate date = birthDateOrNull(value);
        if (date == null) throw new BusinessException("SYS_002", "身份证号格式、出生日期或校验码不正确");
        return date;
    }

    public static LocalDate birthDateOrNull(String value) {
        if (value == null || !value.matches("[1-9][0-9]{16}[0-9X]")
                || !value.substring(0, 2).matches("11|12|13|14|15|21|22|23|31|32|33|34|35|36|37|41|42|43|44|45|46|50|51|52|53|54|61|62|63|64|65")) return null;
        int sum = 0;
        for (int i = 0; i < WEIGHTS.length; i++) sum += (value.charAt(i) - '0') * WEIGHTS[i];
        if (value.charAt(17) != CHECK_DIGITS.charAt(sum % 11)) return null;
        try {
            LocalDate birthDate = LocalDate.of(Integer.parseInt(value.substring(6, 10)),
                    Integer.parseInt(value.substring(10, 12)), Integer.parseInt(value.substring(12, 14)));
            return birthDate.isAfter(LocalDate.now()) ? null : birthDate;
        } catch (DateTimeException ex) {
            return null;
        }
    }

    public static Integer age(LocalDate birthDate) {
        return age(birthDate, LocalDate.now());
    }

    static Integer age(LocalDate birthDate, LocalDate today) {
        return birthDate == null ? null : Period.between(birthDate, today).getYears();
    }
}
