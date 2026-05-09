package org.example.maridone.log;

import org.example.maridone.config.DefaultConfig;
import org.example.maridone.core.employee.EmployeeRepository;
import org.example.maridone.exception.badrequest.InvalidActionException;
import org.example.maridone.log.attendance.AttendanceLog;
import org.example.maridone.log.dto.AttendanceImportIssueDto;
import org.example.maridone.log.dto.AttendanceImportSummaryDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class AttendanceImportService {
    private static final String SUMMARY_SECTION_MARKER = "attendancesummaryreport";

    private static final String HEADER_EMPLOYEE_ID = "employeeid";
    private static final String HEADER_EMPLOYEE_NAME = "employeename";
    private static final String HEADER_DATE = "date";
    private static final String HEADER_TIME_IN = "timein";
    private static final String HEADER_TIME_OUT = "timeout";
    private static final Set<String> REQUIRED_HEADERS = Set.of(
            HEADER_EMPLOYEE_ID,
            HEADER_EMPLOYEE_NAME,
            HEADER_DATE,
            HEADER_TIME_IN,
            HEADER_TIME_OUT
    );
    private static final List<DateTimeFormatter> SUPPORTED_TIME_FORMATTERS = List.of(
            new DateTimeFormatterBuilder()
                    .parseCaseInsensitive()
                    .appendPattern("H:mm")
                    .toFormatter(Locale.ENGLISH),
            new DateTimeFormatterBuilder()
                    .parseCaseInsensitive()
                    .appendPattern("HH:mm")
                    .toFormatter(Locale.ENGLISH),
            new DateTimeFormatterBuilder()
                    .parseCaseInsensitive()
                    .appendPattern("h:mm a")
                    .toFormatter(Locale.ENGLISH),
            new DateTimeFormatterBuilder()
                    .parseCaseInsensitive()
                    .appendPattern("hh:mm a")
                    .toFormatter(Locale.ENGLISH),
            new DateTimeFormatterBuilder()
                    .parseCaseInsensitive()
                    .appendPattern("h:mm")
                    .parseDefaulting(ChronoField.AMPM_OF_DAY, 0)
                    .toFormatter(Locale.ENGLISH)
    );

    private final AttendanceLogRepository attendanceLogRepository;
    private final EmployeeRepository employeeRepository;
    private final DefaultConfig defaultConfig;

    public AttendanceImportService(AttendanceLogRepository attendanceLogRepository,
                                   EmployeeRepository employeeRepository,
                                   DefaultConfig defaultConfig) {
        this.attendanceLogRepository = attendanceLogRepository;
        this.employeeRepository = employeeRepository;
        this.defaultConfig = defaultConfig;
    }

    @Transactional
    public AttendanceImportSummaryDto importAttendanceCsv(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidActionException("Attendance import CSV is empty.");
        }

        List<List<String>> csvRows = readCsvRows(file);
        if (csvRows.isEmpty()) {
            throw new InvalidActionException("Attendance import CSV is empty.");
        }

        int headerRowIndex = findHeaderRowIndex(csvRows);
        if (headerRowIndex == -1) {
            throw new InvalidActionException("Attendance import CSV is missing required detail headers.");
        }

        Map<String, Integer> headerIndexes = buildHeaderIndexes(csvRows.get(headerRowIndex));
        ensureRequiredHeaders(headerIndexes);

        List<ParsedAttendanceImportRow> parsedRows = new ArrayList<>();
        List<AttendanceImportIssueDto> issues = new ArrayList<>();
        int totalRows = 0;
        for (int rowIndex = headerRowIndex + 1; rowIndex < csvRows.size(); rowIndex++) {
            List<String> cells = csvRows.get(rowIndex);
            if (isRowEmpty(cells)) {
                continue;
            }
            if (isSummarySectionMarker(cells)) {
                // If summary section follows detail section, we might need to continue or break.
                // In our new export, summary is at top. If it's at bottom in old format, we stop.
                break;
            }
            totalRows++;
            try {
                parsedRows.add(parseAttendanceImportRow(rowIndex + 1, cells, headerIndexes));
            } catch (InvalidRowException error) {
                issues.add(issue(error));
            }
        }

        if (totalRows == 0) {
            return new AttendanceImportSummaryDto(
                    file.getOriginalFilename(),
                    0,
                    0,
                    0,
                    0,
                    0,
                    List.of()
            );
        }

        Map<Long, org.example.maridone.core.employee.Employee> employeesById = internalLoadEmployees(parsedRows);
        ZoneId zoneId = defaultConfig.getTimeZone();
        Instant now = Instant.now();
        Set<String> pendingEventKeys = new LinkedHashSet<>();
        List<AttendanceLog> logsToSave = new ArrayList<>();
        int importedRows = 0;
        int importedLogs = 0;

        for (ParsedAttendanceImportRow row : parsedRows) {
            org.example.maridone.core.employee.Employee employee = employeesById.get(row.employeeId());
            if (employee == null) {
                issues.add(issue(row, "Employee ID " + row.employeeId() + " was not found."));
                continue;
            }

            List<PendingAttendanceEvent> pendingEvents;
            try {
                pendingEvents = buildPendingEvents(row, employee, zoneId, now);
            } catch (InvalidRowException error) {
                issues.add(issue(error));
                continue;
            }
            if (pendingEvents.isEmpty()) {
                issues.add(issue(row, "At least one of Time In or Time Out is required."));
                continue;
            }

            String duplicateMessage = findDuplicateMessage(pendingEvents, pendingEventKeys);
            if (duplicateMessage != null) {
                issues.add(issue(row, duplicateMessage));
                continue;
            }

            for (PendingAttendanceEvent pendingEvent : pendingEvents) {
                pendingEventKeys.add(eventKey(pendingEvent.employeeId(), pendingEvent.timestamp(), pendingEvent.direction()));
                logsToSave.add(toAttendanceLog(pendingEvent));
            }
            importedRows++;
            importedLogs += pendingEvents.size();
        }

        if (!logsToSave.isEmpty()) {
            attendanceLogRepository.saveAll(logsToSave);
        }

        return new AttendanceImportSummaryDto(
                file.getOriginalFilename(),
                totalRows,
                importedRows,
                totalRows - importedRows,
                importedLogs,
                issues.size(),
                issues
        );
    }

    private int findHeaderRowIndex(List<List<String>> csvRows) {
        for (int i = 0; i < csvRows.size(); i++) {
            Map<String, Integer> indexes = buildHeaderIndexes(csvRows.get(i));
            boolean allFound = true;
            for (String required : REQUIRED_HEADERS) {
                if (!indexes.containsKey(required)) {
                    allFound = false;
                    break;
                }
            }
            if (allFound) {
                return i;
            }
        }
        return -1;
    }

    private Map<Long, org.example.maridone.core.employee.Employee> internalLoadEmployees(Collection<ParsedAttendanceImportRow> parsedRows) {
        Set<Long> employeeIds = new LinkedHashSet<>();
        for (ParsedAttendanceImportRow row : parsedRows) {
            employeeIds.add(row.employeeId());
        }

        Map<Long, org.example.maridone.core.employee.Employee> employeesById = new HashMap<>();
        for (org.example.maridone.core.employee.Employee employee : employeeRepository.findAllById(employeeIds)) {
            employeesById.put(employee.getEmployeeId(), employee);
        }
        return employeesById;
    }

    private List<PendingAttendanceEvent> buildPendingEvents(ParsedAttendanceImportRow row,
                                                            org.example.maridone.core.employee.Employee employee,
                                                            ZoneId zoneId,
                                                            Instant now) {
        List<PendingAttendanceEvent> pendingEvents = new ArrayList<>();
        if (row.timeIn() != null) {
            Instant timeIn = toInstant(row.date(), row.timeIn(), zoneId);
            String issue = validateEventTimestamp(employee, row.date());
            if (issue != null) {
                throw new InvalidRowException(row, issue);
            }
            if (timeIn.isAfter(now)) {
                throw new InvalidRowException(row, "Time In cannot be in the future.");
            }
            pendingEvents.add(new PendingAttendanceEvent(employee.getEmployeeId(), timeIn, "IN"));
        }
        if (row.timeOut() != null) {
            LocalDate timeOutDate = row.timeIn() != null && row.timeOut().isBefore(row.timeIn())
                    ? row.date().plusDays(1)
                    : row.date();
            Instant timeOut = toInstant(timeOutDate, row.timeOut(), zoneId);
            String issue = validateEventTimestamp(employee, timeOutDate);
            if (issue != null) {
                throw new InvalidRowException(row, issue);
            }
            if (timeOut.isAfter(now)) {
                throw new InvalidRowException(row, "Time Out cannot be in the future.");
            }
            pendingEvents.add(new PendingAttendanceEvent(employee.getEmployeeId(), timeOut, "OUT"));
        }
        return pendingEvents;
    }

    private String validateEventTimestamp(org.example.maridone.core.employee.Employee employee, LocalDate eventDate) {
        LocalDate employmentStart = employee.getEmploymentDateStart();
        if (employmentStart != null && eventDate.isBefore(employmentStart)) {
            return "The attendance date is earlier than the employee's start date.";
        }

        LocalDate employmentEnd = employee.getEmploymentDateEnd();
        if (employmentEnd != null && eventDate.isAfter(employmentEnd)) {
            return "The attendance date is later than the employee's employment end date.";
        }

        return null;
    }

    private String findDuplicateMessage(List<PendingAttendanceEvent> pendingEvents,
                                        Set<String> pendingEventKeys) {
        for (PendingAttendanceEvent pendingEvent : pendingEvents) {
            String eventKey = eventKey(pendingEvent.employeeId(), pendingEvent.timestamp(), pendingEvent.direction());
            if (pendingEventKeys.contains(eventKey)) {
                return "This row duplicates another attendance event in the same file.";
            }
            if (attendanceLogRepository.existsByEmployeeIdAndTimestampAndDirection(
                    pendingEvent.employeeId(),
                    pendingEvent.timestamp(),
                    pendingEvent.direction()
            )) {
                return "This row matches an attendance log that already exists.";
            }
        }
        return null;
    }

    private AttendanceLog toAttendanceLog(PendingAttendanceEvent pendingEvent) {
        AttendanceLog attendanceLog = new AttendanceLog();
        attendanceLog.setEmployeeId(pendingEvent.employeeId());
        attendanceLog.setTimestamp(pendingEvent.timestamp());
        attendanceLog.setDirection(pendingEvent.direction());
        return attendanceLog;
    }

    private ParsedAttendanceImportRow parseAttendanceImportRow(int rowNumber,
                                                               List<String> cells,
                                                               Map<String, Integer> headerIndexes) {
        String employeeIdValue = cell(cells, headerIndexes.get(HEADER_EMPLOYEE_ID));
        String dateValue = cell(cells, headerIndexes.get(HEADER_DATE));
        String timeInValue = cell(cells, headerIndexes.get(HEADER_TIME_IN));
        String timeOutValue = cell(cells, headerIndexes.get(HEADER_TIME_OUT));

        if (employeeIdValue.isBlank()) {
            throw new InvalidRowException(rowNumber, employeeIdValue, dateValue, "Employee ID is required.");
        }

        long employeeId;
        try {
            employeeId = Long.parseLong(employeeIdValue);
        } catch (NumberFormatException error) {
            throw new InvalidRowException(rowNumber, employeeIdValue, dateValue, "Employee ID must be a number.");
        }
        if (employeeId <= 0) {
            throw new InvalidRowException(rowNumber, employeeIdValue, dateValue, "Employee ID must be greater than zero.");
        }

        if (dateValue.isBlank()) {
            throw new InvalidRowException(rowNumber, employeeIdValue, dateValue, "Date is required.");
        }

        LocalDate date;
        try {
            date = LocalDate.parse(dateValue, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException error) {
            throw new InvalidRowException(rowNumber, employeeIdValue, dateValue, "Date must use YYYY-MM-DD format.");
        }

        LocalTime timeIn = parseTimeValue(timeInValue, "Time In", rowNumber, employeeIdValue, dateValue);
        LocalTime timeOut = parseTimeValue(timeOutValue, "Time Out", rowNumber, employeeIdValue, dateValue);

        if (timeIn == null && timeOut == null) {
            throw new InvalidRowException(rowNumber, employeeIdValue, dateValue, "At least one of Time In or Time Out is required.");
        }

        return new ParsedAttendanceImportRow(rowNumber, employeeId, employeeIdValue, dateValue, date, timeIn, timeOut);
    }

    private LocalTime parseTimeValue(String value,
                                     String label,
                                     int rowNumber,
                                     String employeeId,
                                     String date) {
        if (value == null || value.isBlank()) {
            return null;
        }

        for (DateTimeFormatter formatter : SUPPORTED_TIME_FORMATTERS) {
            try {
                return LocalTime.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
                // Try the next accepted export-friendly format.
            }
        }

        throw new InvalidRowException(
                rowNumber,
                employeeId,
                date,
                label + " must use HH:mm or h:mm AM/PM format."
        );
    }

    private Instant toInstant(LocalDate date, LocalTime time, ZoneId zoneId) {
        return LocalDateTime.of(date, time)
                .atZone(zoneId)
                .toInstant();
    }

    private Map<String, Integer> buildHeaderIndexes(List<String> headerRow) {
        Map<String, Integer> headerIndexes = new LinkedHashMap<>();
        for (int columnIndex = 0; columnIndex < headerRow.size(); columnIndex++) {
            headerIndexes.put(normalizeHeaderName(headerRow.get(columnIndex)), columnIndex);
        }
        return headerIndexes;
    }

    private void ensureRequiredHeaders(Map<String, Integer> headerIndexes) {
        List<String> missingHeaders = REQUIRED_HEADERS.stream()
                .filter(header -> !headerIndexes.containsKey(header))
                .map(this::displayHeaderName)
                .sorted()
                .toList();

        if (!missingHeaders.isEmpty()) {
            throw new InvalidActionException(
                    "Attendance import CSV is missing required columns: " + String.join(", ", missingHeaders) + "."
            );
        }
    }

    private String normalizeHeaderName(String value) {
        return value == null
                ? ""
                : value.replace("\uFEFF", "")
                .replaceAll("[^A-Za-z0-9]", "")
                .toLowerCase(Locale.ENGLISH);
    }

    private boolean isSummarySectionMarker(List<String> cells) {
        if (cells == null || cells.isEmpty()) {
            return false;
        }

        String firstCell = normalizeHeaderName(cells.get(0));
        return SUMMARY_SECTION_MARKER.equals(firstCell);
    }

    private String displayHeaderName(String value) {
        return switch (value) {
            case HEADER_EMPLOYEE_ID -> "Employee ID";
            case HEADER_EMPLOYEE_NAME -> "Employee Name";
            case HEADER_DATE -> "Date";
            case HEADER_TIME_IN -> "Time In";
            case HEADER_TIME_OUT -> "Time Out";
            default -> value;
        };
    }

    private List<List<String>> readCsvRows(MultipartFile file) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)
        )) {
            List<List<String>> rows = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                rows.add(parseCsvLine(line));
            }
            return rows;
        } catch (IOException error) {
            throw new InvalidActionException("Attendance import CSV could not be read.");
        }
    }

    private List<String> parseCsvLine(String line) {
        List<String> columns = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int index = 0; index < line.length(); index++) {
            char currentChar = line.charAt(index);
            if (currentChar == '"') {
                if (inQuotes && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    current.append('"');
                    index++;
                } else {
                    inQuotes = !inQuotes;
                }
                continue;
            }

            if (currentChar == ',' && !inQuotes) {
                columns.add(current.toString().trim());
                current.setLength(0);
                continue;
            }

            current.append(currentChar);
        }

        columns.add(current.toString().trim());
        return columns;
    }

    private boolean isRowEmpty(List<String> cells) {
        for (String cell : cells) {
            if (cell != null && !cell.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private String cell(List<String> cells, Integer index) {
        if (index == null || index < 0 || index >= cells.size()) {
            return "";
        }
        return cells.get(index).trim();
    }

    private String eventKey(Long employeeId, Instant timestamp, String direction) {
        return employeeId + "|" + timestamp + "|" + direction;
    }

    private AttendanceImportIssueDto issue(ParsedAttendanceImportRow row, String message) {
        return new AttendanceImportIssueDto(row.rowNumber(), row.employeeIdRaw(), row.dateRaw(), message);
    }

    private AttendanceImportIssueDto issue(InvalidRowException error) {
        return new AttendanceImportIssueDto(error.rowNumber, error.employeeId, error.date, error.getMessage());
    }

    private record ParsedAttendanceImportRow(
            int rowNumber,
            long employeeId,
            String employeeIdRaw,
            String dateRaw,
            LocalDate date,
            LocalTime timeIn,
            LocalTime timeOut
    ) {
    }

    private record PendingAttendanceEvent(
            Long employeeId,
            Instant timestamp,
            String direction
    ) {
    }

    private static class InvalidRowException extends RuntimeException {
        private final int rowNumber;
        private final String employeeId;
        private final String date;

        private InvalidRowException(int rowNumber, String employeeId, String date, String message) {
            super(message);
            this.rowNumber = rowNumber;
            this.employeeId = employeeId;
            this.date = date;
        }

        private InvalidRowException(ParsedAttendanceImportRow row, String message) {
            this(row.rowNumber(), row.employeeIdRaw(), row.dateRaw(), message);
        }
    }
}
