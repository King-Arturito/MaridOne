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
import org.example.maridone.leave.request.LeaveRequest;
import org.example.maridone.leave.request.LeaveRequestRepository;
import org.example.maridone.schedule.dto.ShiftRequestDto;
import org.example.maridone.schedule.dto.ShiftResponseDto;
import org.example.maridone.schedule.mapper.ScheduleMapper;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ShiftService {

    private final TemplateShiftRepository templateShiftRepository;
    private final DailyShiftRepository dailyShiftRepository;
    private final EmployeeRepository employeeRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final ScheduleMapper scheduleMapper;
    private final DefaultConfig defaultConfig;

    public ShiftService(
            TemplateShiftRepository templateShiftRepository,
            EmployeeRepository employeeRepository,
            DailyShiftRepository dailyShiftRepository,
            LeaveRequestRepository leaveRequestRepository,
            ScheduleMapper scheduleMapper,
            DefaultConfig defaultConfig) {
        this.templateShiftRepository = templateShiftRepository;
        this.employeeRepository = employeeRepository;
        this.dailyShiftRepository = dailyShiftRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.scheduleMapper = scheduleMapper;
        this.defaultConfig = defaultConfig;
    }

    //create all for 7 days [monday, ...., sunday]
    //used for onboarding ONLY
    @Transactional
    @ExecutionTime
    public void createShifts(Long empId) {
        Employee emp = employeeRepository.findById(empId).orElseThrow(() -> new EmployeeNotFoundException(empId));
        List<TemplateShiftSchedule> schedules = Arrays.stream(DayOfWeek.values()).map(day -> {
            TemplateShiftSchedule templateShiftSchedule = new TemplateShiftSchedule();
            templateShiftSchedule.setEmployee(emp);
            templateShiftSchedule.setStartTime(LocalTime.of(1,0));
            templateShiftSchedule.setEndTime(LocalTime.of(2,0));
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
        return schedule;
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
                .map(Employee::getEmployeeId)
                .toList();

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
        return daily;
    }
}
