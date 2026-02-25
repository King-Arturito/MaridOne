package org.example.maridone.schedule.shift;


import org.example.maridone.common.CommonSpecs;
import org.example.maridone.config.DefaultProperties;
import org.example.maridone.core.employee.Employee;
import org.example.maridone.core.employee.EmployeeRepository;
import org.example.maridone.enums.EmploymentStatus;
import org.example.maridone.exception.EmployeeNotFoundException;
import org.example.maridone.exception.ShiftsNotFoundException;
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
    private final DefaultProperties defaultProperties;

    public ShiftService(
            TemplateShiftRepository templateShiftRepository,
            EmployeeRepository employeeRepository,
            DailyShiftRepository dailyShiftRepository,
            LeaveRequestRepository leaveRequestRepository,
            ScheduleMapper scheduleMapper,
            DefaultProperties defaultProperties) {
        this.templateShiftRepository = templateShiftRepository;
        this.employeeRepository = employeeRepository;
        this.dailyShiftRepository = dailyShiftRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.scheduleMapper = scheduleMapper;
        this.defaultProperties = defaultProperties;
    }

    //create all for 7 days [monday, ...., sunday]
    //used for onboarding ONLY
    @Transactional
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
    public void setDailyShifts() {
        DayOfWeek targetDay = LocalDate.now(defaultProperties.getTimeZone()).getDayOfWeek();
        //what we need to save
        List<DailyShiftSchedule> schedulesToSave = new ArrayList<>();

        Specification<Employee> specs = Specification.allOf(
                CommonSpecs.fieldNotEquals("employmentStatus", EmploymentStatus.TERMINATED)
        );

        //get all employee ids with status != terminated
        List<Long> empIds = employeeRepository.findAll(specs).stream()
                .map(Employee::getEmployeeId)
                .toList();

        /*
            get all shift templates for employee ids in empIds,
            group shift templates into empId
         */
        Map<Long, List<TemplateShiftSchedule>> empToTemplates = templateShiftRepository
                .findAllByEmployeeIdInAndDayOfWeek(empIds, targetDay)
                .stream()
                .collect(Collectors.groupingBy(
                        TemplateShiftSchedule::getEmployeeId
                ));

        Map<Long, List<LeaveRequest>>  empToRequests = leaveRequestRepository
                .findApprovedLeavesForDay(LocalDate.now(defaultProperties.getTimeZone()))
                .stream()
                .collect(Collectors.groupingBy(
                        leaveRequest -> leaveRequest.getEmployee().getEmployeeId()));

        //remove all employees inside empToTemplates since they have a leave for this day
        empToTemplates.keySet().removeAll(empToRequests.keySet());

        //loop through empToTemplates based on empIds, and adds to shift schedules for today
        empToTemplates.forEach((empId, schedules) ->
                schedules.forEach(schedule -> {
                    DailyShiftSchedule dailyShiftSchedule = new DailyShiftSchedule();
                    dailyShiftSchedule.setScheduledDate(LocalDate.now(defaultProperties.getTimeZone()));
                    dailyShiftSchedule.setStartTime(schedule.getStartTime());
                    dailyShiftSchedule.setEndTime(schedule.getEndTime());
                    dailyShiftSchedule.setTemplateShiftSchedule(schedule);
                    schedulesToSave.add(dailyShiftSchedule);
                })
        );
        dailyShiftRepository.saveAll(schedulesToSave);
    }


}
