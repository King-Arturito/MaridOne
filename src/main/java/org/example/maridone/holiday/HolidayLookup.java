package org.example.maridone.holiday;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public record HolidayLookup(Set<LocalDate> holidayDates, Set<LocalDate> regularHolidayDates) {

    public HolidayLookup {
        holidayDates = holidayDates == null
                ? Set.of()
                : Collections.unmodifiableSet(new HashSet<>(holidayDates));
        regularHolidayDates = regularHolidayDates == null
                ? Set.of()
                : Collections.unmodifiableSet(new HashSet<>(regularHolidayDates));
    }

    public static HolidayLookup empty() {
        return new HolidayLookup(Set.of(), Set.of());
    }
}
