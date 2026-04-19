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
import org.example.maridone.overtime.OvertimeRequest;
import org.example.maridone.overtime.OvertimeRequestRepository;
import org.example.maridone.payroll.PayrollCalculator;
import org.example.maridone.schedule.shift.DailyShiftRepository;
import org.example.maridone.schedule.shift.DailyShiftSchedule;
import org.example.maridone.schedule.shift.TemplateShiftRepository;
import org.example.maridone.schedule.shift.TemplateShiftSchedule;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.Duration;
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
    private static final String CSV_SUMMARY_TITLE = "Attendance Summary Report";
    private static final String CSV_SUMMARY_HEADER =
            "Employee ID,Employee Name,Department,Position,Evaluated Days,Present Days,Late Days,Early Out Days,Incomplete Days,Absent Days,Outside Schedule Days,Total Late Minutes,Total Early Out Minutes\n";
    private static final String CSV_DETAIL_HEADER =
            "Employee ID,Employee Name,Date,Time In,Time Out,Department,Position,Scheduled Start,Scheduled End,Status,Late,Early Out,Late Minutes,Early Out Minutes\n";

    private final EmployeeRepository employeeRepository;
    private final UserAccountRepository userAccountRepository;
    private final AttendanceLogRepository attendanceLogRepository;
    private final DailyShiftRepository dailyShiftRepository;
    private final TemplateShiftRepository templateShiftRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final OvertimeRequestRepository overtimeRequestRepository;
    private final PayrollCalculator payrollCalculator;
    private final DefaultConfig defaultConfig;

    public AttendanceReportService(EmployeeRepository employeeRepository,
                                   UserAccountRepository userAccountRepository,
                                   AttendanceLogRepository attendanceLogRepository,
                                   DailyShiftRepository dailyShiftRepository,
                                   TemplateShiftRepository templateShiftRepository,
                                   LeaveRequestRepository leaveRequestRepository,
                                   OvertimeRequestRepository overtimeRequestRepository,
                                   PayrollCalculator payrollCalculator,
                                   DefaultConfig defaultConfig) {
        this.employeeRepository = employeeRepository;
        this.userAccountRepository = userAccountRepository;
        this.attendanceLogRepository = attendanceLogRepository;
        this.dailyShiftRepository = dailyShiftRepository;
        this.templateShiftRepository = templateShiftRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.overtimeRequestRepository = overtimeRequestRepository;
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
            return CSV_DETAIL_HEADER.getBytes(StandardCharsets.UTF_8);
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
        Map<Long, List<OvertimeRequest>> overtimeByEmployee =
                groupOvertimeByEmployee(employeeIds, from, to);

        List<DailyAttendanceOutcome> allOutcomes = new ArrayList<>();
        for (Employee employee : employees) {
            for (LocalDate workDate = from; !workDate.isAfter(to); workDate = workDate.plusDays(1)) {
                DailyAttendanceOutcome outcome = evaluateDailyAttendance(
                        employee,
                        workDate,
                        dailySchedulesByEmployeeAndDate,
                        templateSchedulesByEmployeeAndDay,
                        attendanceLogsByEmployee.getOrDefault(employee.getEmployeeId(), List.of()),
                        leavesByEmployee.getOrDefault(employee.getEmployeeId(), List.of()),
                        overtimeByEmployee.getOrDefault(employee.getEmployeeId(), List.of())
                );
                if (outcome != null) {
                    allOutcomes.add(outcome);
                }
            }
        }

        StringBuilder csv = new StringBuilder();
        appendSummarySection(csv, allOutcomes, employees);
        csv.append("\n");
        appendDetailSection(csv, allOutcomes);

        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private DailyAttendanceOutcome evaluateDailyAttendance(Employee employee,
                                                            LocalDate workDate,
                                                            Map<Long, Map<LocalDate, List<DailyShiftSchedule>>> dailySchedulesByEmployeeAndDate,
                                                            Map<Long, Map<DayOfWeek, List<TemplateShiftSchedule>>> templateSchedulesByEmployeeAndDay,
                                                            List<AttendanceLog> attendanceLogs,
                                                            List<LeaveRequest> approvedLeaves,
                                                            List<OvertimeRequest> approvedOvertime) {
        ZoneId zoneId = defaultConfig.getTimeZone();
        ResolvedShift shift = resolveShift(employee.getEmployeeId(), workDate, dailySchedulesByEmployeeAndDate, templateSchedulesByEmployeeAndDay);
        List<InstantInterval> overtimeWindows = resolveApprovedOvertimeWindows(workDate, approvedOvertime, zoneId);
        if (shift == null && overtimeWindows.isEmpty()) {
            return null;
        }

        if (shift != null && hasApprovedLeave(approvedLeaves, workDate, shift)) {
            return null;
        }

        List<PayrollCalculator.AttendanceSession> sessions = payrollCalculator.findAttendanceSessionsForDate(attendanceLogs, workDate);
        LocalTime timeIn = sessions.isEmpty() ? null : toLocalTime(sessions.get(0).inLog());
        LocalTime timeOut = sessions.isEmpty() ? null : toLocalTime(sessions.get(sessions.size() - 1).outLog());

        AttendanceStatus status;
        boolean hasCompletePair = !sessions.isEmpty();
        boolean hasAnyLog = hasAnyLogForDate(attendanceLogs, workDate);

        List<InstantInterval> scheduleWindows = resolveScheduleWindows(workDate, shift, zoneId);
        ScheduleCoverage scheduleCoverage = computeScheduleCoverage(scheduleWindows, sessions);
        boolean hasOutsideScheduleCoverage =
                hasCompletePair
                        && overtimeWindows.isEmpty()
                        && !scheduleWindows.isEmpty()
                        && scheduleCoverage.relevantIn() == null;

        if (hasCompletePair && !scheduleWindows.isEmpty() && scheduleCoverage.coversFullSchedule()) {
            status = AttendanceStatus.PRESENT;
        } else if (hasOutsideScheduleCoverage) {
            status = AttendanceStatus.OUTSIDE_SCHEDULE;
        } else if (hasAnyLog) {
            status = hasCompletePair ? AttendanceStatus.ABSENT : AttendanceStatus.INCOMPLETE;
        } else {
            status = AttendanceStatus.ABSENT;
        }

        boolean late = scheduleCoverage.relevantIn() != null
                && scheduleCoverage.scheduleStart() != null
                && scheduleCoverage.relevantIn().isAfter(scheduleCoverage.scheduleStart());
        boolean earlyOut = scheduleCoverage.relevantOut() != null
                && scheduleCoverage.scheduleEnd() != null
                && scheduleCoverage.relevantOut().isBefore(scheduleCoverage.scheduleEnd());
        long lateMinutes = late ? Duration.between(scheduleCoverage.scheduleStart(), scheduleCoverage.relevantIn()).toMinutes() : 0;
        long earlyOutMinutes = earlyOut ? Duration.between(scheduleCoverage.relevantOut(), scheduleCoverage.scheduleEnd()).toMinutes() : 0;
        LocalTime displayStart = shift != null ? shift.startTime() : resolveWindowStartTime(overtimeWindows, zoneId);
        LocalTime displayEnd = shift != null ? shift.endTime() : resolveWindowEndTime(overtimeWindows, zoneId);

        return new DailyAttendanceOutcome(
                employee,
                workDate,
                displayStart,
                displayEnd,
                timeIn,
                timeOut,
                status,
                late,
                earlyOut,
                lateMinutes,
                earlyOutMinutes
        );
    }

    private List<InstantInterval> resolveScheduleWindows(LocalDate workDate, ResolvedShift shift, ZoneId zoneId) {
        if (shift == null || workDate == null || zoneId == null) {
            return List.of();
        }

        if (shift.dailySchedules() != null && !shift.dailySchedules().isEmpty()) {
            return shift.dailySchedules().stream()
                    .map(schedule -> toScheduleInterval(schedule, zoneId))
                    .flatMap(Optional::stream)
                    .sorted(Comparator.comparing(InstantInterval::start))
                    .toList();
        }

        if (shift.startTime() == null || shift.endTime() == null) {
            return List.of();
        }

        LocalDateTime start = LocalDateTime.of(workDate, shift.startTime());
        LocalDateTime end = LocalDateTime.of(workDate, shift.endTime());
        if (!end.isAfter(start)) {
            end = end.plusDays(1);
        }

        return List.of(new InstantInterval(start.atZone(zoneId).toInstant(), end.atZone(zoneId).toInstant()));
    }

    private List<InstantInterval> resolveApprovedOvertimeWindows(LocalDate workDate, List<OvertimeRequest> approvedOvertime, ZoneId zoneId) {
        if (workDate == null || zoneId == null || approvedOvertime == null || approvedOvertime.isEmpty()) {
            return List.of();
        }

        return approvedOvertime.stream()
                .filter(request -> workDate.equals(request.getWorkDate()))
                .map(request -> toOvertimeInterval(request, zoneId))
                .flatMap(Optional::stream)
                .sorted(Comparator.comparing(InstantInterval::start))
                .toList();
    }

    private Optional<InstantInterval> toOvertimeInterval(OvertimeRequest request, ZoneId zoneId) {
        if (request == null || zoneId == null || request.getStartTime() == null || request.getEndTime() == null) {
            return Optional.empty();
        }

        Instant start = request.getStartTime().atZone(zoneId).toInstant();
        Instant end = request.getEndTime().atZone(zoneId).toInstant();
        if (!end.isAfter(start)) {
            end = end.plus(Duration.ofDays(1));
        }

        return end.isAfter(start) ? Optional.of(new InstantInterval(start, end)) : Optional.empty();
    }

    private LocalTime resolveWindowStartTime(List<InstantInterval> windows, ZoneId zoneId) {
        if (windows == null || windows.isEmpty() || zoneId == null) {
            return null;
        }

        Instant start = windows.stream().map(InstantInterval::start).min(Instant::compareTo).orElse(null);
        return start == null ? null : start.atZone(zoneId).toLocalTime();
    }

    private LocalTime resolveWindowEndTime(List<InstantInterval> windows, ZoneId zoneId) {
        if (windows == null || windows.isEmpty() || zoneId == null) {
            return null;
        }

        Instant end = windows.stream().map(InstantInterval::end).max(Instant::compareTo).orElse(null);
        return end == null ? null : end.atZone(zoneId).toLocalTime();
    }

    private Optional<InstantInterval> toScheduleInterval(DailyShiftSchedule schedule, ZoneId zoneId) {
        if (schedule == null || zoneId == null || schedule.getStartDateTime() == null || schedule.getEndDateTime() == null) {
            return Optional.empty();
        }

        Instant start = schedule.getStartDateTime().atZone(zoneId).toInstant();
        Instant end = schedule.getEndDateTime().atZone(zoneId).toInstant();
        if (!end.isAfter(start)) {
            end = end.plus(Duration.ofDays(1));
        }

        return end.isAfter(start) ? Optional.of(new InstantInterval(start, end)) : Optional.empty();
    }

    private ScheduleCoverage computeScheduleCoverage(
            List<InstantInterval> scheduleWindows,
            List<PayrollCalculator.AttendanceSession> sessions
    ) {
        if (scheduleWindows == null || scheduleWindows.isEmpty() || sessions == null || sessions.isEmpty()) {
            Instant scheduleStart = scheduleWindows == null || scheduleWindows.isEmpty()
                    ? null
                    : scheduleWindows.stream().map(InstantInterval::start).min(Instant::compareTo).orElse(null);
            Instant scheduleEnd = scheduleWindows == null || scheduleWindows.isEmpty()
                    ? null
                    : scheduleWindows.stream().map(InstantInterval::end).max(Instant::compareTo).orElse(null);
            return new ScheduleCoverage(false, scheduleStart, scheduleEnd, null, null);
        }

        Instant scheduleStart = scheduleWindows.stream().map(InstantInterval::start).min(Instant::compareTo).orElse(null);
        Instant scheduleEnd = scheduleWindows.stream().map(InstantInterval::end).max(Instant::compareTo).orElse(null);

        boolean coversFull = true;
        Instant relevantIn = null;
        Instant relevantOut = null;

        for (InstantInterval window : scheduleWindows) {
            long requiredMinutes = Duration.between(window.start(), window.end()).toMinutes();
            if (requiredMinutes <= 0) {
                continue;
            }

            List<InstantInterval> overlaps = new ArrayList<>();
            for (PayrollCalculator.AttendanceSession session : sessions) {
                if (session == null || session.inLog() == null || session.outLog() == null) continue;
                Instant in = session.inLog().getTimestamp();
                Instant out = session.outLog().getTimestamp();
                if (in == null || out == null || !out.isAfter(in)) continue;
                InstantInterval sessionInterval = new InstantInterval(in, out);
                intersect(sessionInterval, window).ifPresent(overlaps::add);
            }

            List<InstantInterval> merged = mergeIntervals(overlaps);
            long coveredMinutes = merged.stream()
                    .mapToLong(interval -> Duration.between(interval.start(), interval.end()).toMinutes())
                    .sum();

            if (coveredMinutes < requiredMinutes) {
                coversFull = false;
            }

            for (InstantInterval interval : merged) {
                if (relevantIn == null || interval.start().isBefore(relevantIn)) {
                    relevantIn = interval.start();
                }
                if (relevantOut == null || interval.end().isAfter(relevantOut)) {
                    relevantOut = interval.end();
                }
            }
        }

        return new ScheduleCoverage(coversFull, scheduleStart, scheduleEnd, relevantIn, relevantOut);
    }

    private Optional<InstantInterval> intersect(InstantInterval a, InstantInterval b) {
        if (a == null || b == null) {
            return Optional.empty();
        }
        Instant start = a.start().isAfter(b.start()) ? a.start() : b.start();
        Instant end = a.end().isBefore(b.end()) ? a.end() : b.end();
        return end.isAfter(start) ? Optional.of(new InstantInterval(start, end)) : Optional.empty();
    }

    private List<InstantInterval> mergeIntervals(List<InstantInterval> intervals) {
        if (intervals == null || intervals.isEmpty()) {
            return List.of();
        }

        List<InstantInterval> sorted = intervals.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(InstantInterval::start).thenComparing(InstantInterval::end))
                .toList();

        List<InstantInterval> merged = new ArrayList<>();
        for (InstantInterval current : sorted) {
            if (merged.isEmpty()) {
                merged.add(current);
                continue;
            }

            InstantInterval last = merged.get(merged.size() - 1);
            if (current.start().isAfter(last.end())) {
                merged.add(current);
                continue;
            }

            Instant end = current.end().isAfter(last.end()) ? current.end() : last.end();
            merged.set(merged.size() - 1, new InstantInterval(last.start(), end));
        }

        return merged;
    }

    private record InstantInterval(Instant start, Instant end) {}

    private record ScheduleCoverage(
            boolean coversFullSchedule,
            Instant scheduleStart,
            Instant scheduleEnd,
            Instant relevantIn,
            Instant relevantOut
    ) {}

    private boolean hasAnyLogForDate(List<AttendanceLog> logs, LocalDate date) {
        return logs.stream().anyMatch(log -> toLocalDate(log).equals(date));
    }

    private LocalDate toLocalDate(AttendanceLog log) {
        return log.getTimestamp().atZone(defaultConfig.getTimeZone()).toLocalDate();
    }

    private Map<Long, List<OvertimeRequest>> groupOvertimeByEmployee(List<Long> employeeIds, LocalDate from, LocalDate to) {
        return overtimeRequestRepository.findApprovedOvertimeForPeriod(employeeIds, from, to).stream()
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(this::resolveOvertimeEmployeeId, LinkedHashMap::new, Collectors.toList()));
    }

    private Long resolveOvertimeEmployeeId(OvertimeRequest request) {
        Long employeeId = request.getEmployeeId();
        if (employeeId != null) {
            return employeeId;
        }
        if (request.getEmployee() != null) {
            return request.getEmployee().getEmployeeId();
        }
        throw new IllegalStateException("Approved overtime request is missing employee information.");
    }

    private void appendSummarySection(StringBuilder csv, List<DailyAttendanceOutcome> outcomes, List<Employee> employees) {
        csv.append(csv(CSV_SUMMARY_TITLE)).append("\n");
        csv.append(CSV_SUMMARY_HEADER);

        Map<Long, List<DailyAttendanceOutcome>> outcomesByEmployee = outcomes.stream()
                .collect(Collectors.groupingBy(o -> o.employee().getEmployeeId()));

        long totalEvaluated = 0, totalPresent = 0, totalLate = 0, totalEarlyOut = 0, totalIncomplete = 0,
                totalAbsent = 0, totalOutsideSchedule = 0, totalLateMin = 0, totalEarlyOutMin = 0;

        for (Employee employee : employees) {
            List<DailyAttendanceOutcome> empOutcomes = outcomesByEmployee.getOrDefault(employee.getEmployeeId(), List.of());
            if (empOutcomes.isEmpty()) continue;

            long evaluated = empOutcomes.size();
            long present = empOutcomes.stream().filter(o -> o.status() == AttendanceStatus.PRESENT).count();
            long lateCount = empOutcomes.stream().filter(o -> o.status() == AttendanceStatus.PRESENT && o.late()).count();
            long earlyOutCount = empOutcomes.stream().filter(o -> o.status() == AttendanceStatus.PRESENT && o.earlyOut()).count();
            long incomplete = empOutcomes.stream().filter(o -> o.status() == AttendanceStatus.INCOMPLETE).count();
            long absent = empOutcomes.stream().filter(o -> o.status() == AttendanceStatus.ABSENT).count();
            long outsideSchedule = empOutcomes.stream().filter(o -> o.status() == AttendanceStatus.OUTSIDE_SCHEDULE).count();
            long lateMin = empOutcomes.stream().filter(o -> o.status() == AttendanceStatus.PRESENT).mapToLong(DailyAttendanceOutcome::lateMinutes).sum();
            long earlyOutMin = empOutcomes.stream().filter(o -> o.status() == AttendanceStatus.PRESENT).mapToLong(DailyAttendanceOutcome::earlyOutMinutes).sum();

            csv.append(csv(employee.getEmployeeId()))
                    .append(',').append(csv(formatEmployeeName(employee)))
                    .append(',').append(csv(resolveDepartment(employee)))
                    .append(',').append(csv(employee.getPosition()))
                    .append(',').append(csv(evaluated))
                    .append(',').append(csv(present))
                    .append(',').append(csv(lateCount))
                    .append(',').append(csv(earlyOutCount))
                    .append(',').append(csv(incomplete))
                    .append(',').append(csv(absent))
                    .append(',').append(csv(outsideSchedule))
                    .append(',').append(csv(lateMin))
                    .append(',').append(csv(earlyOutMin))
                    .append('\n');

            totalEvaluated += evaluated;
            totalPresent += present;
            totalLate += lateCount;
            totalEarlyOut += earlyOutCount;
            totalIncomplete += incomplete;
            totalAbsent += absent;
            totalOutsideSchedule += outsideSchedule;
            totalLateMin += lateMin;
            totalEarlyOutMin += earlyOutMin;
        }

        csv.append(csv("TOTALS"))
                .append(",,,,")
                .append(csv(totalEvaluated))
                .append(',').append(csv(totalPresent))
                .append(',').append(csv(totalLate))
                .append(',').append(csv(totalEarlyOut))
                .append(',').append(csv(totalIncomplete))
                .append(',').append(csv(totalAbsent))
                .append(',').append(csv(totalOutsideSchedule))
                .append(',').append(csv(totalLateMin))
                .append(',').append(csv(totalEarlyOutMin))
                .append('\n');
    }

    private void appendDetailSection(StringBuilder csv, List<DailyAttendanceOutcome> outcomes) {
        csv.append(CSV_DETAIL_HEADER);
        for (DailyAttendanceOutcome o : outcomes) {
            csv.append(csv(o.employee().getEmployeeId()))
                    .append(',').append(csv(formatEmployeeName(o.employee())))
                    .append(',').append(csv(formatDate(o.workDate())))
                    .append(',').append(csv(formatTime(o.timeIn())))
                    .append(',').append(csv(formatTime(o.timeOut())))
                    .append(',').append(csv(resolveDepartment(o.employee())))
                    .append(',').append(csv(o.employee().getPosition()))
                    .append(',').append(csv(formatTime(o.shiftStart())))
                    .append(',').append(csv(formatTime(o.shiftEnd())))
                    .append(',').append(csv(o.status().name()))
                    .append(',').append(csv(o.late() ? "YES" : "NO"))
                    .append(',').append(csv(o.earlyOut() ? "YES" : "NO"))
                    .append(',').append(csv(o.lateMinutes()))
                    .append(',').append(csv(o.earlyOutMinutes()))
                    .append('\n');
        }
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

    private LocalTime toLocalTime(AttendanceLog attendanceLog) {
        if (attendanceLog == null || attendanceLog.getTimestamp() == null) {
            return null;
        }
        return attendanceLog.getTimestamp().atZone(defaultConfig.getTimeZone()).toLocalTime();
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

    private void validateDateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new InvalidActionException("Attendance report date range is required.");
        }
        if (to.isBefore(from)) {
            throw new InvalidActionException("Attendance report end date cannot be earlier than the start date.");
        }
    }

    private record ResolvedShift(LocalTime startTime, LocalTime endTime, List<DailyShiftSchedule> dailySchedules) {}

    private record DailyAttendanceOutcome(
            Employee employee,
            LocalDate workDate,
            LocalTime shiftStart,
            LocalTime shiftEnd,
            LocalTime timeIn,
            LocalTime timeOut,
            AttendanceStatus status,
            boolean late,
            boolean earlyOut,
            long lateMinutes,
            long earlyOutMinutes
    ) {}

    private enum AttendanceStatus {
        PRESENT,
        INCOMPLETE,
        ABSENT,
        OUTSIDE_SCHEDULE
    }
}
