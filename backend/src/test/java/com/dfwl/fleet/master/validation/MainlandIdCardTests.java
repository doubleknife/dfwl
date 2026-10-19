package com.dfwl.fleet.master.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class MainlandIdCardTests {
    @Test
    void validatesRealDateAndChecksum() {
        assertThat(MainlandIdCard.requireBirthDate("11010519491231002X"))
                .isEqualTo(LocalDate.of(1949, 12, 31));
        for (String invalid : new String[] {"11010519490230002X", "110105194912310020", "123"}) {
            assertThatThrownBy(() -> MainlandIdCard.requireBirthDate(invalid))
                    .hasMessageContaining("身份证号");
        }
    }

    @Test
    void ageChangesOnTheBirthdayRatherThanTheCalendarYear() {
        LocalDate birth = LocalDate.of(2000, 10, 15);
        assertThat(MainlandIdCard.age(birth, LocalDate.of(2026, 10, 14))).isEqualTo(25);
        assertThat(MainlandIdCard.age(birth, LocalDate.of(2026, 10, 15))).isEqualTo(26);
    }
}
