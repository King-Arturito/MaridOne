package org.example.maridone.holiday;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public record HolidayLookup(Set<LocalDate> holidayDates, Set<LocalDate> regularHolidayDates, Map<LocalDate, String> holidayNames) {

    public HolidayLookup {
        holidayDates = holidayDates == null
                ? Set.of()
                : Collections.unmodifiableSet(new HashSet<>(holidayDates));
        regularHolidayDates = regularHolidayDates == null
                ? Set.of()
                : Collections.unmodifiableSet(new HashSet<>(regularHolidayDates));
        holidayNames = holidayNames == null
                ? Map.of()
                : Collections.unmodifiableMap(new HashMap<>(holidayNames));
    }

    public HolidayLookup(Set<LocalDate> holidayDates, Set<LocalDate> regularHolidayDates) {
        this(holidayDates, regularHolidayDates, Map.of());
    }

    public static HolidayLookup empty() {
        return new HolidayLookup(Set.of(), Set.of(), Map.of());
    }
}
