package org.example.maridone.log.timecard;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.example.maridone.common.CommonCalculator;
import org.example.maridone.config.DefaultConfig;
import org.example.maridone.core.employee.Employee;
import org.example.maridone.core.employee.EmployeeRepository;
import org.example.maridone.exception.notfound.EmployeeNotFoundException;
import org.example.maridone.log.AttendanceLogRepository;
import org.example.maridone.log.attendance.AttendanceLog;
import org.example.maridone.overtime.OvertimeRequest;
import org.example.maridone.overtime.OvertimeRequestRepository;
import org.example.maridone.payroll.PayrollCalculator;
import org.example.maridone.schedule.shift.DailyShiftRepository;
import org.example.maridone.schedule.shift.DailyShiftSchedule;
import org.example.maridone.schedule.shift.TemplateShiftRepository;
import org.example.maridone.schedule.shift.TemplateShiftSchedule;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
public class TimecardService {
    private static final String TIMECARD_LOGO_RESOURCE_PATH = "/static/images/marid-logo-transparent.png";
    private static final String COMPANY_NAME = "Marid Industries Corporation";
    private static final String COMPANY_ADDRESS = "Address";
    private static final String COMPANY_EMAIL = "operations@marid.global";

    private final EmployeeRepository employeeRepository;
    private final AttendanceLogRepository attendanceLogRepository;
    private final DailyShiftRepository dailyShiftRepository;
    private final TemplateShiftRepository templateShiftRepository;
    private final OvertimeRequestRepository overtimeRequestRepository;
    private final DefaultConfig defaultConfig;
    private final TemplateEngine templateEngine;
    private final PayrollCalculator payrollCalculator;

    public TimecardService(EmployeeRepository employeeRepository,
                           AttendanceLogRepository attendanceLogRepository,
                           DailyShiftRepository dailyShiftRepository,
                           TemplateShiftRepository templateShiftRepository,
                           OvertimeRequestRepository overtimeRequestRepository,
                           DefaultConfig defaultConfig,
                           TemplateEngine templateEngine,
                           PayrollCalculator payrollCalculator) {
        this.employeeRepository = employeeRepository;
        this.attendanceLogRepository = attendanceLogRepository;
        this.dailyShiftRepository = dailyShiftRepository;
        this.templateShiftRepository = templateShiftRepository;
        this.overtimeRequestRepository = overtimeRequestRepository;
        this.defaultConfig = defaultConfig;
        this.templateEngine = templateEngine;
        this.payrollCalculator = payrollCalculator;
    }

    public record GeneratedTimecardPdf(
            String fileName,
            byte[] content
    ) {}

    public record CompanyDetails(
            String name,
            String addressLine1,
            String email,
            String logoUrl
    ) {}

    public record DailyLogRow(
            LocalDate date,
            LocalTime timeIn,
            LocalTime timeOut,
            BigDecimal breaksHours,
            BigDecimal regularHours,
            BigDecimal overtimeHours,
            BigDecimal totalHours
    ) {}

    public record EmployeeCard(
            String name,
            String department,
            String designation,
            String manager,
            LocalDate periodStart,
            LocalDate periodEnd,
            List<DailyLogRow> dailyLogsList,
            BigDecimal totalBreakHours,
            BigDecimal totalRegularHours,
            BigDecimal totalOvertimeHours,
            BigDecimal totalOverallHours
    ) {}

    @Transactional(readOnly = true)
    public GeneratedTimecardPdf generateTimecardPdf(Long employeeId, LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("Both from and to dates are required.");
        }
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("The timecard date range is invalid.");
        }

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new EmployeeNotFoundException(employeeId));

        EmployeeCard employeeCard = sanitizeEmployeeCard(buildEmployeeCard(employee, from, to));
        CompanyDetails companyDetails = new CompanyDetails(
                COMPANY_NAME,
                COMPANY_ADDRESS,
                COMPANY_EMAIL,
                getLogoUrl()
        );

        Context context = new Context();
        context.setVariable("companyDetails", companyDetails);
        context.setVariable("employeeCard", employeeCard);

        String htmlContent = templateEngine.process("timecard-template", context);
        String safeName = sanitizeFileNamePart(employeeCard.name());
        String fileName = "Timecard_" + safeName + "_" + from + "_to_" + to + ".pdf";

        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(htmlContent, Objects.requireNonNull(getClass().getResource("/templates/")).toExternalForm());
            builder.toStream(os);
            builder.run();
            return new GeneratedTimecardPdf(fileName, os.toByteArray());
        } catch (Exception e) {
            String reason = e.getMessage() == null || e.getMessage().isBlank() ? "Unknown PDF rendering error." : e.getMessage();
            throw new RuntimeException("Failed to generate timecard for employee " + employeeId + ": " + reason, e);
        }
    }

    private EmployeeCard buildEmployeeCard(Employee employee, LocalDate from, LocalDate to) {
        ZoneId timeZone = defaultConfig.getTimeZone();
        Instant rangeStart = from.atStartOfDay(timeZone).toInstant();
        Instant rangeEnd = to.plusDays(1).atTime(6, 0).atZone(timeZone).toInstant();

        List<AttendanceLog> attendanceLogs = attendanceLogRepository.findByEmployeeIdAndTimestampBetween(
                employee.getEmployeeId(),
                rangeStart,
                rangeEnd,
                Sort.by(Sort.Direction.ASC, "timestamp")
        );
        List<DailyShiftSchedule> dailyShiftSchedules = dailyShiftRepository.findAllByEmployeeIdAndPeriod(
                employee.getEmployeeId(),
                from.atStartOfDay(),
                to.plusDays(1).atStartOfDay()
        );
        List<TemplateShiftSchedule> templateShiftSchedules = templateShiftRepository.findAllByEmployeeId(employee.getEmployeeId());
        List<OvertimeRequest> approvedOvertimeRequests = overtimeRequestRepository.findApprovedOvertimeForPeriod(
                List.of(employee.getEmployeeId()),
                from,
                to
        );

        Map<LocalDate, List<DailyShiftSchedule>> dailyShiftMap = dailyShiftSchedules.stream()
                .filter(Objects::nonNull)
                .filter(shift -> shift.getWorkDate() != null)
                .collect(java.util.stream.Collectors.groupingBy(DailyShiftSchedule::getWorkDate, LinkedHashMap::new, java.util.stream.Collectors.toList()));

        Map<java.time.DayOfWeek, List<TemplateShiftSchedule>> templateShiftMap = templateShiftSchedules.stream()
                .filter(Objects::nonNull)
                .filter(schedule -> schedule.getDayOfWeek() != null)
                .collect(java.util.stream.Collectors.groupingBy(TemplateShiftSchedule::getDayOfWeek, LinkedHashMap::new, java.util.stream.Collectors.toList()));

        Map<LocalDate, List<OvertimeRequest>> overtimeByDate = approvedOvertimeRequests.stream()
                .filter(Objects::nonNull)
                .filter(request -> request.getWorkDate() != null)
                .collect(java.util.stream.Collectors.groupingBy(OvertimeRequest::getWorkDate, LinkedHashMap::new, java.util.stream.Collectors.toList()));

        List<DailyLogRow> dailyLogs = new ArrayList<>();
        BigDecimal totalBreakHours = BigDecimal.ZERO;
        BigDecimal totalRegularHours = BigDecimal.ZERO;
        BigDecimal totalOvertimeHours = BigDecimal.ZERO;
        BigDecimal totalOverallHours = BigDecimal.ZERO;

        for (LocalDate current = from; !current.isAfter(to); current = current.plusDays(1)) {
            DailyLogRow row = buildDailyLogRow(
                    current,
                    attendanceLogs,
                    resolveEffectiveSchedules(current, dailyShiftMap, templateShiftMap),
                    overtimeByDate.getOrDefault(current, List.of())
            );
            dailyLogs.add(row);
            totalBreakHours = totalBreakHours.add(row.breaksHours());
            totalRegularHours = totalRegularHours.add(row.regularHours());
            totalOvertimeHours = totalOvertimeHours.add(row.overtimeHours());
            totalOverallHours = totalOverallHours.add(row.totalHours());
        }

        Employee targetForTitle = employee.getManager() != null ? employee.getManager() : employee;
        String department = Optional.ofNullable(targetForTitle.getEmpTitle())
                .map(value -> value.getDisplayName())
                .orElse("-");
        String designation = employee.getPosition() != null ? employee.getPosition().toString() : "-";
        String manager = Optional.ofNullable(employee.getManager())
                .map(value -> value.getFirstName() + " " + value.getLastName())
                .filter(value -> !value.isBlank())
                .orElse("-");

        return new EmployeeCard(
                employee.getFirstName() + " " + employee.getLastName(),
                department,
                designation,
                manager,
                from,
                to,
                dailyLogs,
                CommonCalculator.normalize(totalBreakHours),
                CommonCalculator.normalize(totalRegularHours),
                CommonCalculator.normalize(totalOvertimeHours),
                CommonCalculator.normalize(totalOverallHours)
        );
    }

    private DailyLogRow buildDailyLogRow(LocalDate workDate,
                                         List<AttendanceLog> attendanceLogs,
                                         List<DailyShiftSchedule> effectiveSchedules,
                                         List<OvertimeRequest> approvedOvertimeRequests) {
        List<PayrollCalculator.AttendanceSession> attendanceSessions = Optional.ofNullable(
                payrollCalculator.findAttendanceSessionsForDate(attendanceLogs, workDate)
        ).orElse(List.of());

        BigDecimal rawHours = payrollCalculator.calculateHoursFromAttendanceSessions(attendanceSessions);
        BigDecimal adjustedActualHours = payrollCalculator.deductUnpaidLunchHour(rawHours);
        BigDecimal breakHours = CommonCalculator.normalize(rawHours.subtract(adjustedActualHours).max(BigDecimal.ZERO));

        BigDecimal overtimeHours = approvedOvertimeRequests.stream()
                .map(request -> payrollCalculator.calculateWorkedHoursWithinWindow(
                        attendanceSessions,
                        request.getStartTime(),
                        request.getEndTime()
                ))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        overtimeHours = CommonCalculator.normalize(overtimeHours);

        BigDecimal regularHours;
        if (!effectiveSchedules.isEmpty()) {
            regularHours = payrollCalculator.deductUnpaidLunchHour(
                    payrollCalculator.calculateWorkedHoursWithinSchedules(effectiveSchedules, attendanceSessions)
            );
        } else if (rawHours.compareTo(BigDecimal.ZERO) > 0) {
            regularHours = adjustedActualHours.subtract(overtimeHours).max(BigDecimal.ZERO);
        } else {
            regularHours = BigDecimal.ZERO;
        }
        regularHours = CommonCalculator.normalize(regularHours);

        BigDecimal totalHours = CommonCalculator.normalize(regularHours.add(overtimeHours));
        AttendanceLog firstInLog = payrollCalculator.findInLogForDate(attendanceLogs, workDate);
        LocalTime timeIn = firstInLog != null
                ? toLocalTime(firstInLog.getTimestamp())
                : attendanceSessions.isEmpty() ? null : toLocalTime(attendanceSessions.get(0).inLog().getTimestamp());
        LocalTime timeOut = attendanceSessions.isEmpty() ? null : toLocalTime(attendanceSessions.get(attendanceSessions.size() - 1).outLog().getTimestamp());

        return new DailyLogRow(
                workDate,
                timeIn,
                timeOut,
                breakHours,
                regularHours,
                overtimeHours,
                totalHours
        );
    }

    private List<DailyShiftSchedule> resolveEffectiveSchedules(
            LocalDate workDate,
            Map<LocalDate, List<DailyShiftSchedule>> dailyShiftMap,
            Map<java.time.DayOfWeek, List<TemplateShiftSchedule>> templateShiftMap
    ) {
        List<DailyShiftSchedule> dailyOverrides = dailyShiftMap.get(workDate);
        if (dailyOverrides != null && !dailyOverrides.isEmpty()) {
            if (dailyOverrides.stream().anyMatch(DailyShiftSchedule::isDayOff)) {
                return List.of();
            }

            return dailyOverrides.stream()
                    .filter(Objects::nonNull)
                    .filter(shift -> shift.getStartDateTime() != null && shift.getEndDateTime() != null)
                    .filter(shift -> shift.getEndDateTime().isAfter(shift.getStartDateTime()))
                    .sorted(Comparator.comparing(DailyShiftSchedule::getStartDateTime))
                    .toList();
        }

        List<TemplateShiftSchedule> templateSchedules = templateShiftMap.getOrDefault(workDate.getDayOfWeek(), List.of());
        return templateSchedules.stream()
                .map(schedule -> toDailyShiftSchedule(workDate, schedule))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(DailyShiftSchedule::getStartDateTime))
                .toList();
    }

    private DailyShiftSchedule toDailyShiftSchedule(LocalDate workDate, TemplateShiftSchedule schedule) {
        if (schedule == null || schedule.getStartTime() == null || schedule.getEndTime() == null) {
            return null;
        }

        LocalDateTime start = LocalDateTime.of(workDate, schedule.getStartTime());
        LocalDateTime end = LocalDateTime.of(
                schedule.getEndTime().isAfter(schedule.getStartTime()) ? workDate : workDate.plusDays(1),
                schedule.getEndTime()
        );
        if (!end.isAfter(start)) {
            return null;
        }

        DailyShiftSchedule dailyShift = new DailyShiftSchedule();
        dailyShift.setTemplateShiftSchedule(schedule);
        dailyShift.setStartDateTime(start);
        dailyShift.setEndDateTime(end);
        dailyShift.setDayOff(false);
        return dailyShift;
    }

    private LocalTime toLocalTime(Instant instant) {
        return instant == null ? null : instant.atZone(defaultConfig.getTimeZone()).toLocalTime();
    }

    private EmployeeCard sanitizeEmployeeCard(EmployeeCard employeeCard) {
        List<DailyLogRow> safeLogs = Optional.ofNullable(employeeCard.dailyLogsList())
                .orElse(List.of())
                .stream()
                .filter(Objects::nonNull)
                .filter(row -> row.date() != null)
                .map(this::sanitizeDailyLogRow)
                .toList();

        return new EmployeeCard(
                sanitizeDisplayValue(employeeCard.name()),
                sanitizeDisplayValue(employeeCard.department()),
                sanitizeDisplayValue(employeeCard.designation()),
                sanitizeDisplayValue(employeeCard.manager()),
                employeeCard.periodStart(),
                employeeCard.periodEnd(),
                safeLogs,
                normalizeHours(employeeCard.totalBreakHours()),
                normalizeHours(employeeCard.totalRegularHours()),
                normalizeHours(employeeCard.totalOvertimeHours()),
                normalizeHours(employeeCard.totalOverallHours())
        );
    }

    private DailyLogRow sanitizeDailyLogRow(DailyLogRow row) {
        return new DailyLogRow(
                row.date(),
                row.timeIn(),
                row.timeOut(),
                normalizeHours(row.breaksHours()),
                normalizeHours(row.regularHours()),
                normalizeHours(row.overtimeHours()),
                normalizeHours(row.totalHours())
        );
    }

    private BigDecimal normalizeHours(BigDecimal value) {
        return CommonCalculator.normalize(Optional.ofNullable(value).orElse(BigDecimal.ZERO));
    }

    private String sanitizeDisplayValue(String value) {
        if (value == null) {
            return "-";
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? "-" : trimmed;
    }

    private String sanitizeFileNamePart(String value) {
        String normalized = sanitizeDisplayValue(value)
                .replaceAll("\\s+", "_")
                .replaceAll("[^A-Za-z0-9_-]", "");
        return normalized.isBlank() || "-".equals(normalized) ? "employee" : normalized;
    }

    private String getLogoUrl() {
        return Optional.ofNullable(getClass().getResource(TIMECARD_LOGO_RESOURCE_PATH))
                .map(Object::toString)
                .orElse("");
    }
}
