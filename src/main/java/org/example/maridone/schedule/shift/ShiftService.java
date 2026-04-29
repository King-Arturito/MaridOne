package org.example.maridone.schedule.shift;


import org.example.maridone.annotation.ExecutionTime;
import org.example.maridone.annotation.AutoScheduled;
import org.example.maridone.common.CommonSpecs;
import org.example.maridone.config.DefaultConfig;
import org.example.maridone.core.employee.Employee;
import org.example.maridone.core.employee.EmployeeRepository;
import org.example.maridone.enums.EmploymentStatus;
import org.example.maridone.exception.notfound.EmployeeNotFoundException;
import org.example.maridone.exception.notfound.ShiftsNotFoundException;
import org.example.maridone.exception.unauthorized.InvalidActionException;
import org.example.maridone.leave.request.LeaveRequest;
import org.example.maridone.leave.request.LeaveRequestRepository;
import org.example.maridone.notification.NotificationService;
import org.example.maridone.schedule.dto.DailyShiftOverrideRequestDto;
import org.example.maridone.schedule.dto.DailyShiftSegmentDto;
import org.example.maridone.schedule.dto.ShiftRequestDto;
import org.example.maridone.schedule.dto.ShiftResponseDto;
import org.example.maridone.schedule.mapper.ScheduleMapper;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ShiftService {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

    private final TemplateShiftRepository templateShiftRepository;
    private final DailyShiftRepository dailyShiftRepository;
    private final EmployeeRepository employeeRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final ScheduleMapper scheduleMapper;
    private final DefaultConfig defaultConfig;
    private final NotificationService notificationService;

    public ShiftService(
            TemplateShiftRepository templateShiftRepository,
            EmployeeRepository employeeRepository,
            DailyShiftRepository dailyShiftRepository,
            LeaveRequestRepository leaveRequestRepository,
            ScheduleMapper scheduleMapper,
            DefaultConfig defaultConfig,
            NotificationService notificationService) {
        this.templateShiftRepository = templateShiftRepository;
        this.employeeRepository = employeeRepository;
        this.dailyShiftRepository = dailyShiftRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.scheduleMapper = scheduleMapper;
        this.defaultConfig = defaultConfig;
        this.notificationService = notificationService;
    }

    //create all for 7 days [monday, ...., sunday]
    //used for onboarding ONLY
    @Transactional
    @ExecutionTime
    public void createShifts(Long empId) {
        Employee emp = employeeRepository.findById(empId).orElseThrow(() -> new EmployeeNotFoundException(empId));
        createShifts(emp);
    }

    @Transactional
    @ExecutionTime
    public void createShifts(Employee employee) {
        if (employee == null || employee.getEmployeeId() == null) {
            throw new IllegalArgumentException("Employee is required to create template shifts.");
        }
        createTemplateShifts(employee);
    }

    private void createTemplateShifts(Employee emp) {
        List<TemplateShiftSchedule> schedules = Arrays.stream(DayOfWeek.values()).map(day -> {
            TemplateShiftSchedule templateShiftSchedule = new TemplateShiftSchedule();
            templateShiftSchedule.setEmployee(emp);
            templateShiftSchedule.setStartTime(LocalTime.of(8,0));
            templateShiftSchedule.setEndTime(LocalTime.of(17,0));
            templateShiftSchedule.setDayOfWeek(day);
            return templateShiftSchedule;
        }).collect(Collectors.toList());
        templateShiftRepository.saveAll(schedules);
    }

    //get shift schedules of a single employee
    @ExecutionTime
    public List<ShiftResponseDto> getShiftSchedule(Long empId) {
        if (!employeeRepository.existsById(empId)) {
            throw new EmployeeNotFoundException(empId);
        }
        List<TemplateShiftSchedule> schedules = templateShiftRepository.findAllByEmployeeId(empId);
        if (schedules.isEmpty()) {
            throw new ShiftsNotFoundException("Employee ID: " + empId + "has no schedule.");
        }
        return scheduleMapper.toResponseDtos(schedules);
    }

    @Transactional(readOnly = true)
    @ExecutionTime
    public List<DailyShiftSchedule> getDailyShifts(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            return List.of();
        }

        LocalDate from = startDate;
        LocalDate to = endDate;
        if (to.isBefore(from)) {
            from = endDate;
            to = startDate;
        }

        return dailyShiftRepository.findAllByPeriod(
                from.atStartOfDay(),
                to.plusDays(1).atStartOfDay()
        );
    }

    @Transactional(readOnly = true)
    @ExecutionTime
    public List<DailyShiftSchedule> getDailyShiftsForEmployee(Long empId, LocalDate startDate, LocalDate endDate) {
        if (empId == null || !employeeRepository.existsById(empId)) {
            throw new EmployeeNotFoundException(empId);
        }

        if (startDate == null || endDate == null) {
            return List.of();
        }

        LocalDate from = startDate;
        LocalDate to = endDate;
        if (to.isBefore(from)) {
            from = endDate;
            to = startDate;
        }

        return dailyShiftRepository.findAllByEmployeeIdAndPeriod(
                empId,
                from.atStartOfDay(),
                to.plusDays(1).atStartOfDay()
        );
    }

    @Transactional
    @ExecutionTime
    public TemplateShiftSchedule addShiftSchedule(ShiftRequestDto payload) {
        Employee emp = employeeRepository.findById(payload.getEmpId()).orElseThrow(() -> new EmployeeNotFoundException(payload.getEmpId()));
        TemplateShiftSchedule templateShiftSchedule = new TemplateShiftSchedule();
        templateShiftSchedule.setEmployee(emp);
        templateShiftSchedule.setStartTime(payload.getStartTime());
        templateShiftSchedule.setEndTime(payload.getEndTime());
        templateShiftSchedule.setDayOfWeek(payload.getDayOfWeek());
        templateShiftRepository.save(templateShiftSchedule);
        notifyTemplateShiftChange(emp, payload.getDayOfWeek(), payload.getStartTime(), payload.getEndTime(), "set");
        return templateShiftSchedule;
    }

    @Transactional
    @ExecutionTime
    public TemplateShiftSchedule updateShiftSchedule(ShiftRequestDto payload) {
        TemplateShiftSchedule schedule = templateShiftRepository.findById(payload.getShiftId()).orElseThrow(() -> new ShiftsNotFoundException("Shift of ID: " + payload.getShiftId() +" not found."));
        schedule.setStartTime(payload.getStartTime());
        schedule.setEndTime(payload.getEndTime());
        schedule.setDayOfWeek(payload.getDayOfWeek());
        templateShiftRepository.save(schedule);
        notifyTemplateShiftChange(schedule.getEmployee(), payload.getDayOfWeek(), payload.getStartTime(), payload.getEndTime(), "updated");
        return schedule;
    }

    @Transactional
    @ExecutionTime
    public void deleteShiftSchedule(Long shiftId) {
        TemplateShiftSchedule schedule = templateShiftRepository.findById(shiftId)
                .orElseThrow(() -> new ShiftsNotFoundException("Shift of ID: " + shiftId + " not found."));
        templateShiftRepository.delete(schedule);
        notifyTemplateShiftDayOff(schedule.getEmployee(), schedule.getDayOfWeek());
    }

    @Transactional
    @ExecutionTime
    public DailyShiftSchedule upsertDailyShiftOverride(DailyShiftOverrideRequestDto payload) {
        Employee employee = employeeRepository.findById(payload.getEmpId())
                .orElseThrow(() -> new EmployeeNotFoundException(payload.getEmpId()));

        DailyShiftSchedule shift = upsertDailyShiftOverrides(employee, payload).get(0);
        notifyDailyShiftOverride(employee, payload.getWorkDate(), payload.getStartTime(), payload.getEndTime(), payload.isDayOff());
        return shift;
    }

    @Transactional
    @ExecutionTime
    public List<DailyShiftSchedule> upsertDailyShiftOverrides(DailyShiftOverrideRequestDto payload) {
        Employee employee = employeeRepository.findById(payload.getEmpId())
                .orElseThrow(() -> new EmployeeNotFoundException(payload.getEmpId()));

        List<DailyShiftSchedule> shifts = upsertDailyShiftOverrides(employee, payload);
        notifyDailyShiftOverrides(employee, payload.getWorkDate(), shifts);
        return shifts;
    }

    /*
        for task scheduling:
    */

    @Transactional
    @AutoScheduled
    public void setDailyShifts() {
        LocalDate today = LocalDate.now(defaultConfig.getTimeZone());
        DayOfWeek targetDay = today.getDayOfWeek();
        List<DailyShiftSchedule> schedulesToSave = new ArrayList<>();

        Specification<Employee> specs = Specification.allOf(
                CommonSpecs.fieldNotEquals("employmentStatus", EmploymentStatus.TERMINATED)
        );

        //store employee ids based on the specs
        List<Long> empIds = employeeRepository.findAll(specs).stream()
                .filter(employee -> isEmployedOn(employee, today))
                .map(Employee::getEmployeeId)
                .toList();

        List<DailyShiftSchedule> existingDailyShifts = dailyShiftRepository.findAllByEmployeeIdsAndPeriod(
                empIds,
                today.atStartOfDay(),
                today.plusDays(1).atStartOfDay()
        );
        Set<Long> employeesWithDailyShifts = existingDailyShifts.stream()
                .map(DailyShiftSchedule::getEmployeeId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        //create a mapping for empId -> many TemplateShiftSchedule
        Map<Long, List<TemplateShiftSchedule>> empToTemplates = templateShiftRepository
                .findAllByEmployeeIdInAndDayOfWeek(empIds, targetDay)
                .stream()
                .collect(Collectors.groupingBy(
                        TemplateShiftSchedule::getEmployeeId
                ));

        //create a mapping for empId -> many leaves
        Map<Long, List<LeaveRequest>> empToRequests = leaveRequestRepository
                .findApprovedLeavesForDay(today.atStartOfDay(), today.plusDays(1).atStartOfDay())
                .stream()
                .collect(Collectors.groupingBy(
                        leaveRequest -> leaveRequest.getEmployee().getEmployeeId()));

        //iterate through empIds in empToTemplates
        empToTemplates.forEach((empId, schedules) -> {
            if (employeesWithDailyShifts.contains(empId)) {
                return;
            }
            List<LeaveRequest> leaves = empToRequests.getOrDefault(empId, List.of());
            schedules.forEach(schedule -> {
                if (leaves.isEmpty()) {
                    schedulesToSave.add(buildShift(schedule,
                            LocalDateTime.of(today, schedule.getStartTime()),
                            LocalDateTime.of(today, schedule.getEndTime()).plusDays(
                                    schedule.getEndTime().isBefore(schedule.getStartTime()) ? 1 : 0
                            )));
                } else {
                    LeaveRequest leave = leaves.get(0);
                    schedulesToSave.addAll(splitShift(schedule, leave, today));
                }
            });
        });

        dailyShiftRepository.saveAll(schedulesToSave);
    }

    //for partial shifts
    private List<DailyShiftSchedule> splitShift(TemplateShiftSchedule schedule, LeaveRequest leave, LocalDate date) {
        LocalDateTime shiftStart = LocalDateTime.of(date, schedule.getStartTime());
        //if endTime of shift schedule < startTime, it means midnight.
        //so add 1 day to the shiftEnd, else just set normally
        LocalDateTime shiftEnd = schedule.getEndTime().isBefore(schedule.getStartTime())
                ? LocalDateTime.of(date.plusDays(1), schedule.getEndTime())
                : LocalDateTime.of(date, schedule.getEndTime());


        LocalDateTime leaveFrom = leave.getStartDateTime();
        LocalDateTime leaveTo = leave.getEndDateTime();

        // no overlap, generate normally
        if (leaveTo.isBefore(shiftStart) || leaveFrom.isAfter(shiftEnd)) {
            return List.of(buildShift(schedule,shiftStart,shiftEnd));
        }

        // full overlap, skip entirely
        if (!leaveFrom.isAfter(shiftStart) && !leaveTo.isBefore(shiftEnd)) {
            return List.of();
        }

        List<DailyShiftSchedule> result = new ArrayList<>();

        // segment before leave
        if (shiftStart.isBefore(leaveFrom)) {
            result.add(buildShift(schedule, shiftStart, leaveFrom));
        }

        // segment after leave
        if (shiftEnd.isAfter(leaveTo)) {
            result.add(buildShift(schedule, leaveTo, shiftEnd));
        }

        return result;
    }

    private DailyShiftSchedule buildShift(TemplateShiftSchedule schedule, LocalDateTime start, LocalDateTime end) {
        DailyShiftSchedule daily = new DailyShiftSchedule();
        daily.setStartDateTime(start);
        daily.setEndDateTime(end);
        daily.setTemplateShiftSchedule(schedule);
        daily.setDayOff(false);
        return daily;
    }

    private List<DailyShiftSchedule> upsertDailyShiftOverrides(Employee employee, DailyShiftOverrideRequestDto payload) {
        LocalDate workDate = payload.getWorkDate();
        assertEditableWorkDate(workDate);
        TemplateShiftSchedule templateSchedule = resolveTemplateSchedule(employee, workDate);

        LocalDateTime rangeStart = workDate.atStartOfDay();
        LocalDateTime rangeEndExclusive = workDate.plusDays(1).atStartOfDay();

        List<DailyShiftSchedule> existingShifts = dailyShiftRepository.findAllByEmployeeIdAndPeriod(
                employee.getEmployeeId(),
                rangeStart,
                rangeEndExclusive
        );
        if (!existingShifts.isEmpty()) {
            dailyShiftRepository.deleteAll(existingShifts);
        }

        if (payload.isDayOff()) {
            DailyShiftSchedule dayOffShift = new DailyShiftSchedule();
            dayOffShift.setTemplateShiftSchedule(templateSchedule);
            dayOffShift.setDayOff(true);
            dayOffShift.setStartDateTime(rangeStart);
            dayOffShift.setEndDateTime(rangeStart);
            return List.of(dailyShiftRepository.save(dayOffShift));
        }

        List<DailyShiftSegmentDto> requestedSegments = normalizeRequestedSegments(payload);
        List<ShiftWindow> shiftWindows = requestedSegments.stream()
                .map(segment -> toShiftWindow(workDate, segment))
                .sorted(Comparator.comparing(ShiftWindow::start))
                .toList();

        assertNoOverlappingOverrideWindows(shiftWindows);

        List<DailyShiftSchedule> overrideShifts = shiftWindows.stream()
                .map(window -> buildOverrideShift(templateSchedule, window))
                .toList();

        return dailyShiftRepository.saveAll(overrideShifts);
    }

    private List<DailyShiftSegmentDto> normalizeRequestedSegments(DailyShiftOverrideRequestDto payload) {
        List<DailyShiftSegmentDto> segments = payload.getSegments();
        if (segments != null && !segments.isEmpty()) {
            List<DailyShiftSegmentDto> normalized = segments.stream()
                    .filter(Objects::nonNull)
                    .toList();
            if (normalized.isEmpty()) {
                throw new InvalidActionException("At least one shift segment is required unless the date is marked as day off.");
            }
            return normalized;
        }

        DailyShiftSegmentDto singleSegment = new DailyShiftSegmentDto();
        singleSegment.setStartTime(payload.getStartTime());
        singleSegment.setEndTime(payload.getEndTime());
        return List.of(singleSegment);
    }

    private ShiftWindow toShiftWindow(LocalDate workDate, DailyShiftSegmentDto segment) {
        assertValidOverrideTimes(segment.getStartTime(), segment.getEndTime());
        LocalDateTime start = LocalDateTime.of(workDate, segment.getStartTime());
        LocalDateTime end = LocalDateTime.of(workDate, segment.getEndTime()).plusDays(
                segment.getEndTime().isBefore(segment.getStartTime()) ? 1 : 0
        );
        return new ShiftWindow(start, end);
    }

    private void assertNoOverlappingOverrideWindows(List<ShiftWindow> shiftWindows) {
        for (int index = 1; index < shiftWindows.size(); index++) {
            ShiftWindow previous = shiftWindows.get(index - 1);
            ShiftWindow current = shiftWindows.get(index);
            if (current.start().isBefore(previous.end())) {
                throw new InvalidActionException("Custom shift segments cannot overlap each other.");
            }
        }
    }

    private DailyShiftSchedule buildOverrideShift(TemplateShiftSchedule templateSchedule, ShiftWindow window) {
        DailyShiftSchedule overrideShift = new DailyShiftSchedule();
        overrideShift.setTemplateShiftSchedule(templateSchedule);
        overrideShift.setDayOff(false);
        overrideShift.setStartDateTime(window.start());
        overrideShift.setEndDateTime(window.end());
        return overrideShift;
    }

    private TemplateShiftSchedule resolveTemplateSchedule(Employee employee, LocalDate workDate) {
        return templateShiftRepository
                .findByEmployeeIdAndDayOfWeek(employee.getEmployeeId(), workDate.getDayOfWeek())
                .orElseGet(() -> createFallbackTemplateShift(employee, workDate.getDayOfWeek()));
    }

    private TemplateShiftSchedule createFallbackTemplateShift(Employee employee, DayOfWeek dayOfWeek) {
        TemplateShiftSchedule templateShiftSchedule = new TemplateShiftSchedule();
        templateShiftSchedule.setEmployee(employee);
        templateShiftSchedule.setStartTime(LocalTime.of(8, 0));
        templateShiftSchedule.setEndTime(LocalTime.of(17, 0));
        templateShiftSchedule.setDayOfWeek(dayOfWeek);
        return templateShiftRepository.save(templateShiftSchedule);
    }

    private void assertEditableWorkDate(LocalDate workDate) {
        if (workDate == null) {
            throw new InvalidActionException("A work date is required.");
        }

        LocalDate today = LocalDate.now(defaultConfig.getTimeZone());
        if (workDate.isBefore(today)) {
            throw new InvalidActionException("Past schedule dates can no longer be changed.");
        }
    }

    private void assertValidOverrideTimes(LocalTime startTime, LocalTime endTime) {
        if (startTime == null || endTime == null) {
            throw new InvalidActionException("Start time and end time are required unless the date is marked as day off.");
        }

        if (startTime.equals(endTime)) {
            throw new InvalidActionException("Start time and end time cannot be the same unless the date is marked as day off.");
        }
    }

    private boolean isEmployedOn(Employee employee, LocalDate targetDate) {
        if (employee == null || targetDate == null || employee.getEmploymentDateStart() == null) {
            return false;
        }

        if (targetDate.isBefore(employee.getEmploymentDateStart())) {
            return false;
        }

        return employee.getEmploymentDateEnd() == null || !targetDate.isAfter(employee.getEmploymentDateEnd());
    }

    private void notifyTemplateShiftChange(Employee employee, DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime, String action) {
        if (employee == null || dayOfWeek == null || startTime == null || endTime == null) {
            return;
        }

        String message = String.format(
                "Your recurring %s shift was %s to %s.",
                formatDayOfWeek(dayOfWeek),
                action,
                formatTimeRange(startTime, endTime)
        );
        notificationService.createNotification(employee, message, "HIGH");
    }

    private void notifyTemplateShiftDayOff(Employee employee, DayOfWeek dayOfWeek) {
        if (employee == null || dayOfWeek == null) {
            return;
        }

        String message = String.format(
                "Your recurring %s schedule was marked as day off.",
                formatDayOfWeek(dayOfWeek)
        );
        notificationService.createNotification(employee, message, "HIGH");
    }

    private void notifyDailyShiftOverride(Employee employee, LocalDate workDate, LocalTime startTime, LocalTime endTime, boolean dayOff) {
        if (employee == null || workDate == null) {
            return;
        }

        String message = dayOff
                ? String.format("Your shift for %s was marked as day off.", DATE_FORMATTER.format(workDate))
                : String.format(
                "Your shift for %s was updated to %s.",
                DATE_FORMATTER.format(workDate),
                formatTimeRange(startTime, endTime)
        );
        notificationService.createNotification(employee, message, "HIGH");
    }

    private void notifyDailyShiftOverrides(Employee employee, LocalDate workDate, List<DailyShiftSchedule> shifts) {
        if (employee == null || workDate == null || shifts == null || shifts.isEmpty()) {
            return;
        }

        boolean isDayOff = shifts.stream().anyMatch(DailyShiftSchedule::isDayOff);
        String message = isDayOff
                ? String.format("Your shift for %s was marked as day off.", DATE_FORMATTER.format(workDate))
                : String.format(
                "Your shift for %s was updated to %s.",
                DATE_FORMATTER.format(workDate),
                shifts.stream()
                        .sorted(Comparator.comparing(DailyShiftSchedule::getStartDateTime))
                        .map(shift -> formatTimeRange(shift.getStartTime(), shift.getEndTime()))
                        .collect(Collectors.joining(", "))
        );
        notificationService.createNotification(employee, message, "HIGH");
    }

    private String formatDayOfWeek(DayOfWeek dayOfWeek) {
        String lower = dayOfWeek.name().toLowerCase();
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private String formatTimeRange(LocalTime startTime, LocalTime endTime) {
        return TIME_FORMATTER.format(startTime) + " - " + TIME_FORMATTER.format(endTime);
    }

    private record ShiftWindow(LocalDateTime start, LocalDateTime end) {}
}
