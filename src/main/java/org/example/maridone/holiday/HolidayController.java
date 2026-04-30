package org.example.maridone.holiday;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/holidays")
public class HolidayController {

    private final HolidayService holidayService;

    public HolidayController(HolidayService holidayService) {
        this.holidayService = holidayService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public HolidayLookupResponse getHolidayLookup(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        LocalDate from = startDate;
        LocalDate to = endDate;
        if (to.isBefore(from)) {
            from = endDate;
            to = startDate;
        }

        HolidayLookup lookup = holidayService.getHolidayLookup(from, to);
        return new HolidayLookupResponse(
                lookup.holidayDates().stream().sorted().map(LocalDate::toString).toList(),
                lookup.regularHolidayDates().stream().sorted().map(LocalDate::toString).toList(),
                lookup.holidayNamesByDate().entrySet().stream()
                        .filter(entry -> entry.getKey() != null && entry.getValue() != null && !entry.getValue().isBlank())
                        .sorted(Map.Entry.comparingByKey())
                        .collect(java.util.stream.Collectors.toMap(
                                entry -> entry.getKey().toString(),
                                Map.Entry::getValue,
                                (left, right) -> left,
                                java.util.LinkedHashMap::new
                        ))
        );
    }

    public record HolidayLookupResponse(
            List<String> holidayDates,
            List<String> regularHolidayDates,
            Map<String, String> holidayNamesByDate
    ) {
    }
}
