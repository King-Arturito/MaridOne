package org.example.maridone.holiday;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.maridone.config.HolidayConfig;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

class CalendarificHolidayServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void getHolidayLookup_ShouldApplyManualOverridesWhenApiIsDisabled() throws Exception {
        Path cacheDir = tempDir.resolve("cache");
        Path overrideFile = tempDir.resolve("holiday-overrides.json");

        String overridesJson = """
                {
                  "overrides": [
                    { "date": "2026-05-01", "type": "REGULAR", "action": "ADD" },
                    { "date": "2026-05-02", "type": "SPECIAL_NON_WORKING", "action": "ADD" },
                    { "date": "2026-05-03", "action": "REMOVE" }
                  ]
                }
                """;
        Files.writeString(overrideFile, overridesJson);

        HolidayConfig properties = new HolidayConfig();
        properties.setEnabled(false);
        properties.setCountry("PH");
        properties.setCacheDir(cacheDir.toString());
        properties.setOverrideFile(overrideFile.toString());
        properties.setRefreshAfter(Duration.ofDays(365));

        CalendarificHolidayService service = new CalendarificHolidayService(properties, new ObjectMapper());
        HolidayLookup lookup = service.getHolidayLookup(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 3));

        Assertions.assertTrue(lookup.holidayDates().contains(LocalDate.of(2026, 5, 1)));
        Assertions.assertTrue(lookup.holidayDates().contains(LocalDate.of(2026, 5, 2)));
        Assertions.assertFalse(lookup.holidayDates().contains(LocalDate.of(2026, 5, 3)));
        Assertions.assertTrue(lookup.regularHolidayDates().contains(LocalDate.of(2026, 5, 1)));
        Assertions.assertFalse(lookup.regularHolidayDates().contains(LocalDate.of(2026, 5, 2)));
    }

    @Test
    void getHolidayLookup_ShouldApplyOverrideRemoveAndTypeOverrideOnTopOfCachedData() throws Exception {
        Path cacheDir = tempDir.resolve("cache");
        Path overrideFile = tempDir.resolve("holiday-overrides.json");
        Files.createDirectories(cacheDir);

        String cachedYearJson = """
                {
                  "country": "PH",
                  "year": 2026,
                  "fetchedAt": "%s",
                  "holidays": [
                    { "date": "2026-06-12", "type": "REGULAR" },
                    { "date": "2026-06-13", "type": "SPECIAL_NON_WORKING" }
                  ]
                }
                """.formatted(Instant.now().toString());
        Files.writeString(cacheDir.resolve("PH-2026.json"), cachedYearJson);

        String overridesJson = """
                {
                  "overrides": [
                    { "date": "2026-06-12", "action": "REMOVE" },
                    { "date": "2026-06-13", "type": "REGULAR", "action": "ADD" },
                    { "date": "2026-06-14", "type": "SPECIAL_NON_WORKING", "action": "ADD" }
                  ]
                }
                """;
        Files.writeString(overrideFile, overridesJson);

        HolidayConfig properties = new HolidayConfig();
        properties.setEnabled(false);
        properties.setCountry("PH");
        properties.setCacheDir(cacheDir.toString());
        properties.setOverrideFile(overrideFile.toString());
        properties.setRefreshAfter(Duration.ofDays(365));

        CalendarificHolidayService service = new CalendarificHolidayService(properties, new ObjectMapper());
        HolidayLookup lookup = service.getHolidayLookup(LocalDate.of(2026, 6, 12), LocalDate.of(2026, 6, 14));

        Assertions.assertFalse(lookup.holidayDates().contains(LocalDate.of(2026, 6, 12)));
        Assertions.assertTrue(lookup.holidayDates().contains(LocalDate.of(2026, 6, 13)));
        Assertions.assertTrue(lookup.holidayDates().contains(LocalDate.of(2026, 6, 14)));
        Assertions.assertTrue(lookup.regularHolidayDates().contains(LocalDate.of(2026, 6, 13)));
        Assertions.assertFalse(lookup.regularHolidayDates().contains(LocalDate.of(2026, 6, 14)));
    }

    @Test
    void getHolidayLookup_ShouldFallbackToStaleCacheWhenApiCallFails() throws Exception {
        Path cacheDir = tempDir.resolve("cache-api-fail-fallback");
        Path overrideFile = tempDir.resolve("override-api-fail-fallback.json");
        Files.createDirectories(cacheDir);

        String cachedYearJson = """
                {
                  "country": "PH",
                  "year": 2026,
                  "fetchedAt": "%s",
                  "holidays": [
                    { "date": "2026-07-10", "type": "REGULAR" }
                  ]
                }
                """.formatted(Instant.now().minus(Duration.ofDays(10)).toString());
        Files.writeString(cacheDir.resolve("PH-2026.json"), cachedYearJson);

        HolidayConfig properties = new HolidayConfig();
        properties.setEnabled(true);
        properties.setApiKey("dummy");
        properties.setCountry("PH");
        properties.setBaseUrl("http://127.0.0.1:1/holidays");
        properties.setCacheDir(cacheDir.toString());
        properties.setOverrideFile(overrideFile.toString());
        properties.setRefreshAfter(Duration.ofHours(1));
        properties.setConnectTimeout(Duration.ofMillis(200));
        properties.setRequestTimeout(Duration.ofMillis(200));

        CalendarificHolidayService service = new CalendarificHolidayService(properties, new ObjectMapper());
        HolidayLookup lookup = service.getHolidayLookup(LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 10));

        Assertions.assertTrue(lookup.holidayDates().contains(LocalDate.of(2026, 7, 10)));
        Assertions.assertTrue(lookup.regularHolidayDates().contains(LocalDate.of(2026, 7, 10)));
    }

    @Test
    void getHolidayLookup_ShouldReturnEmptyWhenApiFailsAndNoCacheExists() {
        Path cacheDir = tempDir.resolve("cache-api-fail-no-cache");
        Path overrideFile = tempDir.resolve("override-api-fail-no-cache.json");

        HolidayConfig properties = new HolidayConfig();
        properties.setEnabled(true);
        properties.setApiKey("dummy");
        properties.setCountry("PH");
        properties.setBaseUrl("http://127.0.0.1:1/holidays");
        properties.setCacheDir(cacheDir.toString());
        properties.setOverrideFile(overrideFile.toString());
        properties.setRefreshAfter(Duration.ofHours(1));
        properties.setConnectTimeout(Duration.ofMillis(200));
        properties.setRequestTimeout(Duration.ofMillis(200));

        CalendarificHolidayService service = new CalendarificHolidayService(properties, new ObjectMapper());
        HolidayLookup lookup = service.getHolidayLookup(LocalDate.of(2026, 7, 11), LocalDate.of(2026, 7, 11));

        Assertions.assertTrue(lookup.holidayDates().isEmpty());
        Assertions.assertTrue(lookup.regularHolidayDates().isEmpty());
    }

    @Test
    void getHolidayLookup_ShouldMergeHolidaysAcrossYearBoundary() throws Exception {
        Path cacheDir = tempDir.resolve("cache-cross-year");
        Path overrideFile = tempDir.resolve("override-cross-year.json");
        Files.createDirectories(cacheDir);

        String year2026Json = """
                {
                  "country": "PH",
                  "year": 2026,
                  "fetchedAt": "%s",
                  "holidays": [
                    { "date": "2026-12-31", "type": "REGULAR" }
                  ]
                }
                """.formatted(Instant.now().toString());
        Files.writeString(cacheDir.resolve("PH-2026.json"), year2026Json);

        String year2027Json = """
                {
                  "country": "PH",
                  "year": 2027,
                  "fetchedAt": "%s",
                  "holidays": [
                    { "date": "2027-01-01", "type": "SPECIAL_NON_WORKING" }
                  ]
                }
                """.formatted(Instant.now().toString());
        Files.writeString(cacheDir.resolve("PH-2027.json"), year2027Json);

        HolidayConfig properties = new HolidayConfig();
        properties.setEnabled(false);
        properties.setCountry("PH");
        properties.setCacheDir(cacheDir.toString());
        properties.setOverrideFile(overrideFile.toString());
        properties.setRefreshAfter(Duration.ofDays(365));

        CalendarificHolidayService service = new CalendarificHolidayService(properties, new ObjectMapper());
        HolidayLookup lookup = service.getHolidayLookup(LocalDate.of(2026, 12, 30), LocalDate.of(2027, 1, 2));

        Assertions.assertTrue(lookup.holidayDates().contains(LocalDate.of(2026, 12, 31)));
        Assertions.assertTrue(lookup.holidayDates().contains(LocalDate.of(2027, 1, 1)));
        Assertions.assertTrue(lookup.regularHolidayDates().contains(LocalDate.of(2026, 12, 31)));
        Assertions.assertFalse(lookup.regularHolidayDates().contains(LocalDate.of(2027, 1, 1)));
    }

    @Test
    void getHolidayLookup_ShouldIgnoreMalformedOverrideFile() throws Exception {
        Path cacheDir = tempDir.resolve("cache-malformed-override");
        Path overrideFile = tempDir.resolve("override-malformed.json");
        Files.createDirectories(cacheDir);
        Files.writeString(overrideFile, "{ invalid json ");

        String cachedYearJson = """
                {
                  "country": "PH",
                  "year": 2026,
                  "fetchedAt": "%s",
                  "holidays": [
                    { "date": "2026-08-21", "type": "REGULAR" }
                  ]
                }
                """.formatted(Instant.now().toString());
        Files.writeString(cacheDir.resolve("PH-2026.json"), cachedYearJson);

        HolidayConfig properties = new HolidayConfig();
        properties.setEnabled(false);
        properties.setCountry("PH");
        properties.setCacheDir(cacheDir.toString());
        properties.setOverrideFile(overrideFile.toString());
        properties.setRefreshAfter(Duration.ofDays(365));

        CalendarificHolidayService service = new CalendarificHolidayService(properties, new ObjectMapper());
        HolidayLookup lookup = Assertions.assertDoesNotThrow(
                () -> service.getHolidayLookup(LocalDate.of(2026, 8, 21), LocalDate.of(2026, 8, 21))
        );

        Assertions.assertTrue(lookup.holidayDates().contains(LocalDate.of(2026, 8, 21)));
        Assertions.assertTrue(lookup.regularHolidayDates().contains(LocalDate.of(2026, 8, 21)));
    }

    @Test
    void getHolidayLookup_ShouldUseCacheWhenEnabledButApiKeyMissing() throws Exception {
        Path cacheDir = tempDir.resolve("cache-missing-key");
        Path overrideFile = tempDir.resolve("override-missing-key.json");
        Files.createDirectories(cacheDir);

        String cachedYearJson = """
                {
                  "country": "PH",
                  "year": 2026,
                  "fetchedAt": "%s",
                  "holidays": [
                    { "date": "2026-09-01", "type": "SPECIAL_NON_WORKING" }
                  ]
                }
                """.formatted(Instant.now().minus(Duration.ofDays(30)).toString());
        Files.writeString(cacheDir.resolve("PH-2026.json"), cachedYearJson);

        HolidayConfig properties = new HolidayConfig();
        properties.setEnabled(true);
        properties.setApiKey("  ");
        properties.setCountry("PH");
        properties.setBaseUrl("http://127.0.0.1:1/holidays");
        properties.setCacheDir(cacheDir.toString());
        properties.setOverrideFile(overrideFile.toString());
        properties.setRefreshAfter(Duration.ofHours(1));
        properties.setConnectTimeout(Duration.ofMillis(200));
        properties.setRequestTimeout(Duration.ofMillis(200));

        CalendarificHolidayService service = new CalendarificHolidayService(properties, new ObjectMapper());
        HolidayLookup lookup = service.getHolidayLookup(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1));

        Assertions.assertTrue(lookup.holidayDates().contains(LocalDate.of(2026, 9, 1)));
        Assertions.assertFalse(lookup.regularHolidayDates().contains(LocalDate.of(2026, 9, 1)));
    }
}
