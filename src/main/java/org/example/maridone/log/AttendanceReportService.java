package org.example.maridone.log;

import org.example.maridone.config.DefaultConfig;
import org.example.maridone.core.employee.Employee;
import org.example.maridone.core.employee.EmployeeRepository;
import org.example.maridone.core.user.UserAccountRepository;
import org.example.maridone.enums.EmploymentStatus;
import org.example.maridone.exception.notfound.AccountNotFoundException;
import org.example.maridone.exception.notfound.EmployeeNotFoundException;
import org.example.maridone.exception.unauthorized.InvalidActionException;
import org.example.maridone.leave.request.LeaveRequest;
import org.example.maridone.leave.request.LeaveRequestRepository;
import org.example.maridone.log.attendance.AttendanceLog;
import org.example.maridone.payroll.PayrollCalculator;
import org.example.maridone.schedule.shift.DailyShiftRepository;
import org.example.maridone.schedule.shift.DailyShiftSchedule;
import org.example.maridone.schedule.shift.TemplateShiftRepository;
import org.example.maridone.schedule.shift.TemplateShiftSchedule;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class AttendanceReportService {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final String CSV_HEADER =
            "Employee ID,Employee Name,Department,Manager,Manager Head,Work Date,Shift Start,Shift End,Time In,Time Out,Status,Status Color,Late Minutes\n";

    private final EmployeeRepository employeeRepository;
    private final UserAccountRepository userAccountRepository;
    private final AttendanceLogRepository attendanceLogRepository;
    private final DailyShiftRepository dailyShiftRepository;
    private final TemplateShiftRepository templateShiftRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final PayrollCalculator payrollCalculator;
    private final DefaultConfig defaultConfig;

    public AttendanceReportService(EmployeeRepository employeeRepository,
                                   UserAccountRepository userAccountRepository,
                                   AttendanceLogRepository attendanceLogRepository,
                                   DailyShiftRepository dailyShiftRepository,
                                   TemplateShiftRepository templateShiftRepository,
                                   LeaveRequestRepository leaveRequestRepository,
                                   PayrollCalculator payrollCalculator,
                                   DefaultConfig defaultConfig) {
        this.employeeRepository = employeeRepository;
        this.userAccountRepository = userAccountRepository;
        this.attendanceLogRepository = attendanceLogRepository;
        this.dailyShiftRepository = dailyShiftRepository;
        this.templateShiftRepository = templateShiftRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.payrollCalculator = payrollCalculator;
        this.defaultConfig = defaultConfig;
    }

    public byte[] exportAttendanceReport(LocalDate from, LocalDate to, Long employeeId) {
        List<Employee> employees = resolveEmployeesForReport(from, to, employeeId);
        return buildCsv(from, to, employees);
    }

    public byte[] exportTeamAttendanceReport(LocalDate from, LocalDate to, Long employeeId, String username) {
        validateDateRange(from, to);
        Long managerId = userAccountRepository.findEmployeeIdByUsername(username);
        if (managerId == null) {
            throw new AccountNotFoundException(username);
        }

        List<Employee> employees;
        if (employeeId != null) {
            Employee employee = employeeRepository.findById(employeeId)
                    .orElseThrow(() -> new EmployeeNotFoundException(employeeId));
            if (employee.getManager() == null || !managerId.equals(employee.getManager().getEmployeeId())) {
                throw new InvalidActionException("Managers can only export attendance reports for their own team.");
            }
            employees = List.of(employee);
        } else {
            employees = employeeRepository.findByManager_EmployeeIdAndEmploymentStatusNotOrderByLastNameAscFirstNameAsc(
                    managerId,
                    EmploymentStatus.TERMINATED
            );
        }

        return buildCsv(from, to, employees);
    }

    private List<Employee> resolveEmployeesForReport(LocalDate from, LocalDate to, Long employeeId) {
        validateDateRange(from, to);
        if (employeeId != null) {
            return List.of(employeeRepository.findById(employeeId)
                    .orElseThrow(() -> new EmployeeNotFoundException(employeeId)));
        }

        return employeeRepository.findPayrollEligibleEmployees(from, to, EmploymentStatus.TERMINATED).stream()
                .filter(employee -> employee.getEmploymentStatus() != EmploymentStatus.TERMINATED)
                .sorted(Comparator.comparing(Employee::getLastName, Comparator.nullsLast(String::compareToIgnoreCase))
                        .thenComparing(Employee::getFirstName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
    }

    private byte[] buildCsv(LocalDate from, LocalDate to, List<Employee> employees) {
        if (employees == null || employees.isEmpty()) {
            return CSV_HEADER.getBytes(StandardCharsets.UTF_8);
        }

        List<Long> employeeIds = employees.stream()
                .map(Employee::getEmployeeId)
                .filter(Objects::nonNull)
                .toList();

        Map<Long, Map<LocalDate, List<DailyShiftSchedule>>> dailySchedulesByEmployeeAndDate =
                groupDailySchedulesByEmployeeAndDate(employeeIds, from, to);
        Map<Long, Map<DayOfWeek, List<TemplateShiftSchedule>>> templateSchedulesByEmployeeAndDay =
                groupTemplateSchedulesByEmployeeAndDay(templateShiftRepository.findAllByEmployeeIdIn(employeeIds));
        Map<Long, List<AttendanceLog>> attendanceLogsByEmployee =
                groupAttendanceLogs(employeeIds, from, to);
        Map<Long, List<LeaveRequest>> leavesByEmployee =
                groupLeavesByEmployee(employeeIds, from, to);

        StringBuilder csv = new StringBuilder(CSV_HEADER);
        for (Employee employee : employees) {
            for (LocalDate workDate = from; !workDate.isAfter(to); workDate = workDate.plusDays(1)) {
                AttendanceReportRow row = buildRow(
                        employee,
                        workDate,
                        dailySchedulesByEmployeeAndDate,
                        templateSchedulesByEmployeeAndDay,
                        attendanceLogsByEmployee.getOrDefault(employee.getEmployeeId(), List.of()),
                        leavesByEmployee.getOrDefault(employee.getEmployeeId(), List.of())
                );
                if (row == null) {
                    continue;
                }
                appendRow(csv, row);
            }
        }

        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private AttendanceReportRow buildRow(Employee employee,
                                         LocalDate workDate,
                                         Map<Long, Map<LocalDate, List<DailyShiftSchedule>>> dailySchedulesByEmployeeAndDate,
                                         Map<Long, Map<DayOfWeek, List<TemplateShiftSchedule>>> templateSchedulesByEmployeeAndDay,
                                         List<AttendanceLog> attendanceLogs,
                                         List<LeaveRequest> approvedLeaves) {
        ResolvedShift shift = resolveShift(employee.getEmployeeId(), workDate, dailySchedulesByEmployeeAndDate, templateSchedulesByEmployeeAndDay);
        if (shift == null) {
            return null;
        }

        if (hasApprovedLeave(approvedLeaves, workDate, shift)) {
            return null;
        }

        AttendanceLog inLog = payrollCalculator.findInLogForDate(attendanceLogs, workDate);
        AttendanceLog outLog = inLog == null ? null : payrollCalculator.findOutLogAfterIn(attendanceLogs, inLog, workDate);

        AttendanceStatus status = AttendanceStatus.ABSENT;
        long lateMinutes = 0L;

        if (inLog != null) {
            lateMinutes = calculateLateMinutes(shift, inLog);
            status = lateMinutes > 0 ? AttendanceStatus.LATE : AttendanceStatus.PRESENT;
        }

        return new AttendanceReportRow(
                employee.getEmployeeId(),
                formatEmployeeName(employee),
                resolveDepartment(employee),
                formatEmployeeName(employee.getManager()),
                resolveManagerHeadLabel(employee.getManager()),
                workDate,
                shift.startTime(),
                shift.endTime(),
                toLocalTime(inLog),
                toLocalTime(outLog),
                status,
                lateMinutes
        );
    }

    private Map<Long, Map<LocalDate, List<DailyShiftSchedule>>> groupDailySchedulesByEmployeeAndDate(List<Long> employeeIds,
                                                                                                      LocalDate from,
                                                                                                      LocalDate to) {
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime endExclusive = to.plusDays(1).atStartOfDay();
        return dailyShiftRepository.findAllByEmployeeIdsAndPeriod(employeeIds, start, endExclusive).stream()
                .filter(schedule -> !schedule.isDayOff())
                .filter(schedule -> schedule.getEmployeeId() != null && schedule.getWorkDate() != null)
                .collect(Collectors.groupingBy(
                        DailyShiftSchedule::getEmployeeId,
                        LinkedHashMap::new,
                        Collectors.groupingBy(DailyShiftSchedule::getWorkDate, LinkedHashMap::new, Collectors.toList())
                ));
    }

    private Map<Long, Map<DayOfWeek, List<TemplateShiftSchedule>>> groupTemplateSchedulesByEmployeeAndDay(
            List<TemplateShiftSchedule> schedules
    ) {
        return schedules.stream()
                .filter(schedule -> schedule.getEmployeeId() != null && schedule.getDayOfWeek() != null)
                .collect(Collectors.groupingBy(
                        TemplateShiftSchedule::getEmployeeId,
                        LinkedHashMap::new,
                        Collectors.groupingBy(TemplateShiftSchedule::getDayOfWeek, LinkedHashMap::new, Collectors.toList())
                ));
    }

    private Map<Long, List<AttendanceLog>> groupAttendanceLogs(List<Long> employeeIds, LocalDate from, LocalDate to) {
        ZoneId zoneId = defaultConfig.getTimeZone();
        Instant start = from.atStartOfDay(zoneId).toInstant();
        Instant end = to.atTime(LocalTime.MAX).plusHours(6).atZone(zoneId).toInstant();
        return attendanceLogRepository.findByEmployeeIdInAndTimestampBetween(employeeIds, start, end, Sort.by(Sort.Direction.ASC, "timestamp")).stream()
                .collect(Collectors.groupingBy(AttendanceLog::getEmployeeId, LinkedHashMap::new, Collectors.toList()));
    }

    private Map<Long, List<LeaveRequest>> groupLeavesByEmployee(List<Long> employeeIds, LocalDate from, LocalDate to) {
        return leaveRequestRepository.findApprovedLeavesForPeriod(employeeIds, from.atStartOfDay(), to.plusDays(1).atStartOfDay()).stream()
                .collect(Collectors.groupingBy(leave -> leave.getEmployee().getEmployeeId(), LinkedHashMap::new, Collectors.toList()));
    }

    private ResolvedShift resolveShift(Long employeeId,
                                       LocalDate workDate,
                                       Map<Long, Map<LocalDate, List<DailyShiftSchedule>>> dailySchedulesByEmployeeAndDate,
                                       Map<Long, Map<DayOfWeek, List<TemplateShiftSchedule>>> templateSchedulesByEmployeeAndDay) {
        Map<LocalDate, List<DailyShiftSchedule>> dailyByDate = dailySchedulesByEmployeeAndDate.getOrDefault(employeeId, Map.of());
        List<DailyShiftSchedule> dailySchedules = dailyByDate.getOrDefault(workDate, List.of());
        if (!dailySchedules.isEmpty()) {
            DailyShiftSchedule earliest = dailySchedules.stream()
                    .min(Comparator.comparing(DailyShiftSchedule::getStartDateTime))
                    .orElse(null);
            if (earliest != null) {
                return new ResolvedShift(earliest.getStartTime(), earliest.getEndTime(), dailySchedules);
            }
        }

        Map<DayOfWeek, List<TemplateShiftSchedule>> templatesByDay = templateSchedulesByEmployeeAndDay.getOrDefault(employeeId, Map.of());
        List<TemplateShiftSchedule> templateSchedules = templatesByDay.getOrDefault(workDate.getDayOfWeek(), List.of());
        if (templateSchedules.isEmpty()) {
            return null;
        }

        TemplateShiftSchedule earliestTemplate = templateSchedules.stream()
                .min(Comparator.comparing(TemplateShiftSchedule::getStartTime))
                .orElse(null);
        return earliestTemplate == null ? null : new ResolvedShift(earliestTemplate.getStartTime(), earliestTemplate.getEndTime(), List.of());
    }

    private boolean hasApprovedLeave(List<LeaveRequest> approvedLeaves, LocalDate workDate, ResolvedShift shift) {
        if (approvedLeaves == null || approvedLeaves.isEmpty()) {
            return false;
        }

        if (shift.dailySchedules().isEmpty()) {
            return approvedLeaves.stream().anyMatch(leave -> leave.coversDate(workDate));
        }

        return shift.dailySchedules().stream()
                .anyMatch(schedule -> approvedLeaves.stream().anyMatch(leave -> leaveOverlapsSchedule(leave, schedule)));
    }

    private boolean leaveOverlapsSchedule(LeaveRequest leave, DailyShiftSchedule schedule) {
        if (leave == null
                || schedule == null
                || leave.getStartDateTime() == null
                || leave.getEndDateTime() == null
                || schedule.getStartDateTime() == null
                || schedule.getEndDateTime() == null) {
            return false;
        }

        return leave.getStartDateTime().isBefore(schedule.getEndDateTime())
                && leave.getEndDateTime().isAfter(schedule.getStartDateTime());
    }

    private long calculateLateMinutes(ResolvedShift shift, AttendanceLog inLog) {
        if (shift == null || inLog == null || shift.startTime() == null) {
            return 0L;
        }

        LocalTime actualIn = toLocalTime(inLog);
        if (actualIn == null || !actualIn.isAfter(shift.startTime())) {
            return 0L;
        }

        return java.time.Duration.between(shift.startTime(), actualIn).toMinutes();
    }

    private LocalTime toLocalTime(AttendanceLog attendanceLog) {
        if (attendanceLog == null || attendanceLog.getTimestamp() == null) {
            return null;
        }
        return attendanceLog.getTimestamp().atZone(defaultConfig.getTimeZone()).toLocalTime();
    }

    private void appendRow(StringBuilder csv, AttendanceReportRow row) {
        csv.append(csv(row.employeeId()))
                .append(',').append(csv(row.employeeName()))
                .append(',').append(csv(row.department()))
                .append(',').append(csv(row.managerName()))
                .append(',').append(csv(row.managerHeadLabel()))
                .append(',').append(csv(formatDate(row.workDate())))
                .append(',').append(csv(formatTime(row.shiftStart())))
                .append(',').append(csv(formatTime(row.shiftEnd())))
                .append(',').append(csv(formatTime(row.timeIn())))
                .append(',').append(csv(formatTime(row.timeOut())))
                .append(',').append(csv(row.status().name()))
                .append(',').append(csv(row.status().color()))
                .append(',').append(csv(row.lateMinutes() > 0 ? String.valueOf(row.lateMinutes()) : "0"))
                .append('\n');
    }

    private String csv(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        String escaped = text.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }

    private String formatDate(LocalDate value) {
        return value == null ? null : DATE_FORMAT.format(value);
    }

    private String formatTime(LocalTime value) {
        return value == null ? null : TIME_FORMAT.format(value);
    }

    private String formatEmployeeName(Employee employee) {
        if (employee == null) {
            return null;
        }

        return Stream.of(employee.getFirstName(), employee.getMiddleName(), employee.getLastName())
                .filter(part -> part != null && !part.isBlank())
                .collect(Collectors.joining(" "));
    }

    private String resolveDepartment(Employee employee) {
        Employee current = employee;
        while (current != null) {
            if (current.getEmpTitle() != null) {
                return current.getEmpTitle().getDisplayName();
            }
            current = current.getManager();
        }
        return null;
    }

    private String resolveManagerHeadLabel(Employee manager) {
        if (manager == null) {
            return null;
        }

        if (manager.getEmpTitle() != null) {
            return formatEnumLabel(manager.getEmpTitle().name());
        }

        return manager.getPosition() == null ? null : formatEnumLabel(manager.getPosition().name());
    }

    private String formatEnumLabel(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        return Stream.of(raw.trim().split("_+"))
                .filter(part -> !part.isBlank())
                .map(part -> part.substring(0, 1) + part.substring(1).toLowerCase())
                .collect(Collectors.joining(" "));
    }

    private void validateDateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new InvalidActionException("Attendance report date range is required.");
        }
        if (to.isBefore(from)) {
            throw new InvalidActionException("Attendance report end date cannot be earlier than the start date.");
        }
    }

    private record ResolvedShift(LocalTime startTime, LocalTime endTime, List<DailyShiftSchedule> dailySchedules) {}

    private record AttendanceReportRow(Long employeeId,
                                       String employeeName,
                                       String department,
                                       String managerName,
                                       String managerHeadLabel,
                                       LocalDate workDate,
                                       LocalTime shiftStart,
                                       LocalTime shiftEnd,
                                       LocalTime timeIn,
                                       LocalTime timeOut,
                                       AttendanceStatus status,
                                       long lateMinutes) {}

    private enum AttendanceStatus {
        PRESENT("GREEN"),
        LATE("YELLOW"),
        ABSENT("RED");

        private final String color;

        AttendanceStatus(String color) {
            this.color = color;
        }

        public String color() {
            return color;
        }
    }
}
