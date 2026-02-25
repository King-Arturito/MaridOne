package org.example.maridone.schedule.shift;


import org.example.maridone.core.employee.Employee;
import org.example.maridone.core.employee.EmployeeRepository;
import org.example.maridone.enums.EarningsType;
import org.example.maridone.exception.EmployeeNotFoundException;
import org.example.maridone.exception.ShiftsNotFoundException;
import org.example.maridone.schedule.dto.ShiftRequestDto;
import org.example.maridone.schedule.dto.ShiftResponseDto;
import org.example.maridone.schedule.mapper.ScheduleMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ShiftService {

    private final ShiftRepository shiftRepository;
    private final EmployeeRepository employeeRepository;
    private final ScheduleMapper scheduleMapper;

    public ShiftService(
            ShiftRepository shiftRepository,
            EmployeeRepository employeeRepository,
            ScheduleMapper scheduleMapper) {
        this.shiftRepository = shiftRepository;
        this.employeeRepository = employeeRepository;
        this.scheduleMapper = scheduleMapper;
    }

    //create all for 7 days [monday, ...., sunday]
    //used for onboarding ONLY
    @Transactional
    public void createShifts(Long empId) {
        Employee emp = employeeRepository.findById(empId).orElseThrow(() -> new EmployeeNotFoundException(empId));
        List<ShiftSchedule> schedules = Arrays.stream(DayOfWeek.values()).map(day -> {
            ShiftSchedule shiftSchedule = new ShiftSchedule();
            shiftSchedule.setEmployee(emp);
            shiftSchedule.setStartTime(LocalTime.of(1,0));
            shiftSchedule.setEndTime(LocalTime.of(2,0));
            shiftSchedule.setDayOfWeek(day);
            shiftSchedule.setEarningsType(EarningsType.BASIC);
            return shiftSchedule;
        }).collect(Collectors.toList());
        shiftRepository.saveAll(schedules);
    }

    public List<ShiftResponseDto> getShiftSchedule(Long empId) {
        if (!employeeRepository.existsById(empId)) {
            throw new EmployeeNotFoundException(empId);
        }
        List<ShiftSchedule> schedules = shiftRepository.findAllByEmployeeId(empId);
        if (schedules.isEmpty()) {
            throw new ShiftsNotFoundException("Employee ID: " + empId + "has no schedule.");
        }
        return scheduleMapper.toResponseDtos(schedules);
    }

    @Transactional
    public ShiftSchedule addShiftSchedule(ShiftRequestDto payload) {
        Employee emp = employeeRepository.findById(payload.getEmpId()).orElseThrow(() -> new EmployeeNotFoundException(payload.getEmpId()));
        ShiftSchedule shiftSchedule = new ShiftSchedule();
        shiftSchedule.setEmployee(emp);
        shiftSchedule.setStartTime(payload.getStartTime());
        shiftSchedule.setEndTime(payload.getEndTime());
        shiftSchedule.setDayOfWeek(payload.getDayOfWeek());
        shiftSchedule.setEarningsType(payload.getEarningsType());
        shiftRepository.save(shiftSchedule);
        return shiftSchedule;
    }

    @Transactional
    public ShiftSchedule updateShiftSchedule(ShiftRequestDto payload) {
        ShiftSchedule schedule = shiftRepository.findById(payload.getShiftId()).orElseThrow(() -> new ShiftsNotFoundException("Shift of ID: " + payload.getShiftId() +" not found."));
        schedule.setStartTime(payload.getStartTime());
        schedule.setEndTime(payload.getEndTime());
        schedule.setDayOfWeek(payload.getDayOfWeek());
        schedule.setEarningsType(payload.getEarningsType());
        shiftRepository.save(schedule);
        return schedule;
    }

    //todo: addTempShiftSchedule
}
