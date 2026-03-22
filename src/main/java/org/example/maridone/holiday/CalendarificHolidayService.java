package org.example.maridone.holiday;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.example.maridone.config.HolidayConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class CalendarificHolidayService implements HolidayService {

    private static final Logger logger = LoggerFactory.getLogger(CalendarificHolidayService.class);

    private static final Set<String> REGULAR_NAME_HINTS = Set.of(
            "new years day",
            "new year day",
            "day of valor",
            "araw ng kagitingan",
            "maundy thursday",
            "good friday",
            "labor day",
            "independence day",
            "national heroes day",
            "bonifacio day",
            "christmas day",
            "rizal day",
            "eidl fitr",
            "eid al fitr",
            "eidul fitr",
            "eidul adha",
            "eid al adha",
            "eidl adha"
    );

    private static final Set<String> SPECIAL_NON_WORKING_NAME_HINTS = Set.of(
            "ninoy aquino day",
            "all saints day",
            "feast of the immaculate conception",
            "last day of the year",
            "chinese new year",
            "black saturday",
            "edsa people power revolution anniversary"
    );

    private final HolidayConfig holidayConfig;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final ConcurrentHashMap<YearCacheKey, YearCacheEntry> memoryCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<YearCacheKey, Object> keyLocks = new ConcurrentHashMap<>();

    public CalendarificHolidayService(HolidayConfig holidayConfig, ObjectMapper objectMapper) {
        this.holidayConfig = holidayConfig;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(holidayConfig.getConnectTimeout())
                .build();
    }

    @Override
    public HolidayLookup getHolidayLookup(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
            return HolidayLookup.empty();
        }

        String country = holidayConfig.getCountry() == null
                ? "PH"
                : holidayConfig.getCountry().trim().toUpperCase(Locale.ROOT);
        Map<LocalDate, HolidayType> merged = new HashMap<>();

        for (int year = startDate.getYear(); year <= endDate.getYear(); year++) {
            Map<LocalDate, HolidayType> yearHolidays = loadYearHolidays(country, year);
            yearHolidays.forEach((date, type) -> {
                if (!date.isBefore(startDate) && !date.isAfter(endDate)) {
                    merged.merge(date, type, CalendarificHolidayService::preferRegularHoliday);
                }
            });
        }

        applyOverrides(merged, startDate, endDate);

        Set<LocalDate> holidayDates = new HashSet<>(merged.keySet());
        Set<LocalDate> regularHolidayDates = new HashSet<>();
        merged.forEach((date, type) -> {
            if (type == HolidayType.REGULAR) {
                regularHolidayDates.add(date);
            }
        });

        return new HolidayLookup(holidayDates, regularHolidayDates);
    }

    private Map<LocalDate, HolidayType> loadYearHolidays(String country, int year) {
        YearCacheKey key = new YearCacheKey(country, year);
        Instant now = Instant.now();
        YearCacheEntry existing = memoryCache.get(key);
        if (isFresh(existing, now)) {
            return existing.holidays;
        }

        synchronized (keyLocks.computeIfAbsent(key, unused -> new Object())) {
            YearCacheEntry cached = memoryCache.get(key);
            if (isFresh(cached, now)) {
                return cached.holidays;
            }

            Path cacheFile = cacheFilePath(country, year);
            YearCacheEntry fromFile = readCacheFile(cacheFile);
            if (fromFile != null) {
                memoryCache.put(key, fromFile);
                if (isFresh(fromFile, now) || !isApiEnabled()) {
                    return fromFile.holidays;
                }
            }

            if (!isApiEnabled()) {
                return fromFile == null ? Map.of() : fromFile.holidays;
            }

            try {
                Map<LocalDate, HolidayType> apiHolidays = fetchFromCalendarific(country, year);
                YearCacheEntry updated = new YearCacheEntry(apiHolidays, now);
                memoryCache.put(key, updated);
                writeCacheFile(cacheFile, key, updated);
                return updated.holidays;
            } catch (Exception ex) {
                logger.warn("Failed fetching Calendarific holidays for {}-{}: {}", country, year, ex.getMessage());
                YearCacheEntry fallback = memoryCache.get(key);
                if (fallback != null) {
                    return fallback.holidays;
                }
                return fromFile == null ? Map.of() : fromFile.holidays;
            }
        }
    }

    private Map<LocalDate, HolidayType> fetchFromCalendarific(String country, int year) throws IOException, InterruptedException {
        String url = buildUrl(country, year);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(holidayConfig.getRequestTimeout())
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Calendarific response status: " + response.statusCode());
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode holidays = root.path("response").path("holidays");
        if (!holidays.isArray()) {
            return Map.of();
        }

        Map<LocalDate, HolidayType> result = new HashMap<>();
        for (JsonNode holidayNode : holidays) {
            LocalDate date = extractHolidayDate(holidayNode);
            if (date == null) {
                continue;
            }

            HolidayType holidayType = resolveHolidayType(holidayNode);
            if (holidayType == null) {
                continue;
            }
            result.merge(date, holidayType, CalendarificHolidayService::preferRegularHoliday);
        }
        return result;
    }

    private LocalDate extractHolidayDate(JsonNode holidayNode) {
        String iso = holidayNode.path("date").path("iso").asText("");
        if (iso.isBlank()) {
            return null;
        }
        if (iso.length() > 10) {
            iso = iso.substring(0, 10);
        }
        try {
            return LocalDate.parse(iso);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private HolidayType resolveHolidayType(JsonNode holidayNode) {
        String name = holidayNode.path("name").asText("");
        String description = holidayNode.path("description").asText("");
        String primaryType = holidayNode.path("primary_type").asText("");
        StringBuilder typeText = new StringBuilder();
        for (JsonNode node : holidayNode.path("type")) {
            if (!node.asText("").isBlank()) {
                typeText.append(' ').append(node.asText(""));
            }
        }

        String combined = normalizeText(name + " " + description + " " + primaryType + " " + typeText);
        if (combined.contains("special working")) {
            return null;
        }
        if (combined.contains("regular holiday")) {
            return HolidayType.REGULAR;
        }
        if (combined.contains("special nonworking") || combined.contains("special non working")) {
            return HolidayType.SPECIAL_NON_WORKING;
        }

        String normalizedName = normalizeText(name);
        if (containsAny(normalizedName, REGULAR_NAME_HINTS)) {
            return HolidayType.REGULAR;
        }
        if (containsAny(normalizedName, SPECIAL_NON_WORKING_NAME_HINTS)) {
            return HolidayType.SPECIAL_NON_WORKING;
        }
        return null;
    }

    private void applyOverrides(Map<LocalDate, HolidayType> merged, LocalDate startDate, LocalDate endDate) {
        String overrideFilePath = holidayConfig.getOverrideFile();
        if (overrideFilePath == null || overrideFilePath.isBlank()) {
            return;
        }

        Path path = Paths.get(overrideFilePath);
        if (!Files.exists(path)) {
            return;
        }

        try {
            JsonNode root = objectMapper.readTree(path.toFile());
            JsonNode overrides = root.isArray() ? root : root.path("overrides");
            if (!overrides.isArray()) {
                return;
            }

            for (JsonNode override : overrides) {
                LocalDate date = parseLocalDate(override.path("date").asText(""));
                if (date == null || date.isBefore(startDate) || date.isAfter(endDate)) {
                    continue;
                }

                String action = override.path("action").asText("ADD").trim().toUpperCase(Locale.ROOT);
                if ("REMOVE".equals(action)) {
                    merged.remove(date);
                    continue;
                }

                HolidayType holidayType = parseHolidayType(override.path("type").asText(""));
                if (holidayType != null) {
                    merged.put(date, holidayType);
                }
            }
        } catch (Exception ex) {
            logger.warn("Failed reading holiday override file {}: {}", path, ex.getMessage());
        }
    }

    private boolean isApiEnabled() {
        return holidayConfig.isEnabled()
                && holidayConfig.getApiKey() != null
                && !holidayConfig.getApiKey().isBlank();
    }

    private boolean isFresh(YearCacheEntry entry, Instant now) {
        if (entry == null) {
            return false;
        }
        return entry.fetchedAt.plus(holidayConfig.getRefreshAfter()).isAfter(now);
    }

    private Path cacheFilePath(String country, int year) {
        return Paths.get(holidayConfig.getCacheDir(), country + "-" + year + ".json");
    }

    private YearCacheEntry readCacheFile(Path path) {
        if (!Files.exists(path)) {
            return null;
        }

        try {
            JsonNode root = objectMapper.readTree(path.toFile());
            Instant fetchedAt = parseInstant(root.path("fetchedAt").asText(""));
            if (fetchedAt == null) {
                fetchedAt = Instant.now();
            }

            Map<LocalDate, HolidayType> holidays = new HashMap<>();
            JsonNode holidayNodes = root.path("holidays");
            if (holidayNodes.isArray()) {
                for (JsonNode holidayNode : holidayNodes) {
                    LocalDate date = parseLocalDate(holidayNode.path("date").asText(""));
                    HolidayType holidayType = parseHolidayType(holidayNode.path("type").asText(""));
                    if (date != null && holidayType != null) {
                        holidays.merge(date, holidayType, CalendarificHolidayService::preferRegularHoliday);
                    }
                }
            }
            return new YearCacheEntry(holidays, fetchedAt);
        } catch (Exception ex) {
            logger.warn("Failed reading holiday cache file {}: {}", path, ex.getMessage());
            return null;
        }
    }

    private void writeCacheFile(Path cacheFile, YearCacheKey key, YearCacheEntry entry) {
        try {
            Path parent = cacheFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            JsonNode root = buildCacheJson(key, entry);

            String fileName = cacheFile.getFileName() == null
                    ? "holidays-cache.json"
                    : cacheFile.getFileName().toString();
            Path tempFile = cacheFile.resolveSibling(fileName + ".tmp");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(tempFile.toFile(), root);
            try {
                Files.move(tempFile, cacheFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException | UnsupportedOperationException ignored) {
                try {
                    Files.move(tempFile, cacheFile, StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception moveEx) {
                    objectMapper.writerWithDefaultPrettyPrinter().writeValue(cacheFile.toFile(), root);
                    Files.deleteIfExists(tempFile);
                }
            }
        } catch (Exception ex) {
            logger.warn("Failed writing holiday cache file {}: {}", cacheFile, ex.getMessage());
        }
    }

    private JsonNode buildCacheJson(YearCacheKey key, YearCacheEntry entry) {
        JsonNode root = objectMapper.createObjectNode()
                .put("country", key.country)
                .put("year", key.year)
                .put("fetchedAt", entry.fetchedAt.toString());

        JsonNode holidays = ((com.fasterxml.jackson.databind.node.ObjectNode) root).putArray("holidays");
        entry.holidays.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(item -> ((com.fasterxml.jackson.databind.node.ArrayNode) holidays).addObject()
                        .put("date", item.getKey().toString())
                        .put("type", item.getValue().name()));
        return root;
    }

    private String buildUrl(String country, int year) {
        String query = "api_key=" + encode(holidayConfig.getApiKey())
                + "&country=" + encode(country)
                + "&year=" + year;

        String baseUrl = holidayConfig.getBaseUrl();
        if (baseUrl.contains("?")) {
            return baseUrl + "&" + query;
        }
        return baseUrl + "?" + query;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String normalizeText(String text) {
        return text == null
                ? ""
                : text.toLowerCase(Locale.ROOT)
                        .replaceAll("[^a-z0-9 ]", " ")
                        .replaceAll("\\s+", " ")
                        .trim();
    }

    private static boolean containsAny(String normalizedText, Set<String> hints) {
        for (String hint : hints) {
            if (normalizedText.contains(hint)) {
                return true;
            }
        }
        return false;
    }

    private static HolidayType preferRegularHoliday(HolidayType first, HolidayType second) {
        if (first == HolidayType.REGULAR || second == HolidayType.REGULAR) {
            return HolidayType.REGULAR;
        }
        return HolidayType.SPECIAL_NON_WORKING;
    }

    private static HolidayType parseHolidayType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        try {
            return HolidayType.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static LocalDate parseLocalDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value.trim());
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private record YearCacheKey(String country, int year) { }

    private static class YearCacheEntry {
        private final Map<LocalDate, HolidayType> holidays;
        private final Instant fetchedAt;

        private YearCacheEntry(Map<LocalDate, HolidayType> holidays, Instant fetchedAt) {
            this.holidays = Map.copyOf(holidays);
            this.fetchedAt = fetchedAt;
        }
    }
}
