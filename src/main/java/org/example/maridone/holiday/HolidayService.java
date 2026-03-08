package org.example.maridone.holiday;

import java.time.LocalDate;

public interface HolidayService {
    HolidayLookup getHolidayLookup(LocalDate startDate, LocalDate endDate);
}
