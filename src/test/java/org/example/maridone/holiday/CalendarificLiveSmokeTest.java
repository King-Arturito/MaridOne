package org.example.maridone.holiday;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.stream.Collectors;

import org.example.maridone.config.HolidayConfig;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class CalendarificLiveSmokeTest {

    @Test
    void liveFetch_ShouldReturnHolidays_AndWriteCacheFile() {
        String apiKey = System.getenv("CALENDARIFIC_API_KEY");
        String liveFlag = System.getenv("CALENDARIFIC_LIVE_TEST");

        Assumptions.assumeTrue(apiKey != null && !apiKey.isBlank(),
                "Skipping live test: CALENDARIFIC_API_KEY not set");
        Assumptions.assumeTrue("true".equalsIgnoreCase(liveFlag),
                "Skipping live test: set CALENDARIFIC_LIVE_TEST=true to run");

        HolidayConfig properties = new HolidayConfig();
        properties.setEnabled(true);
        properties.setApiKey(apiKey);
        properties.setCountry("PH");
        properties.setBaseUrl("https://calendarific.com/api/v2/holidays");
        properties.setCacheDir(".cache/holidays");
        properties.setOverrideFile(".cache/holidays/holiday-overrides.json");
        properties.setRefreshAfter(Duration.ofDays(365));
        properties.setConnectTimeout(Duration.ofSeconds(5));
        properties.setRequestTimeout(Duration.ofSeconds(15));

        CalendarificHolidayService service = new CalendarificHolidayService(properties, new ObjectMapper());

        int year = LocalDate.now(ZoneId.of("Asia/Manila")).getYear();
        HolidayLookup lookup = service.getHolidayLookup(
                LocalDate.of(year, 1, 1),
                LocalDate.of(year, 12, 31)
        );

        Assertions.assertFalse(lookup.holidayDates().isEmpty(),
                "Expected non-empty holiday result from Calendarific");
        Path cacheDir = Paths.get(".cache/holidays");
        Path expectedCacheFile = cacheDir.resolve("PH-" + year + ".json");
        String cacheDirFiles = "(cache dir missing)";
        if (Files.exists(cacheDir)) {
            try (var files = Files.list(cacheDir)) {
                cacheDirFiles = files
                    .map(path -> path.getFileName().toString())
                    .sorted()
                    .collect(Collectors.joining(", "));
            } catch (Exception ignored) {
                cacheDirFiles = "(failed to list cache dir)";
            }
        }
        Assertions.assertTrue(
                Files.exists(expectedCacheFile),
                "Expected year cache file to be written at "
                        + expectedCacheFile + " but found: " + cacheDirFiles
        );
    }
}
