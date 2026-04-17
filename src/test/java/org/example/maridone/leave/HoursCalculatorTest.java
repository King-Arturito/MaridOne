package org.example.maridone.leave;

import org.example.maridone.core.employee.Employee;
import org.example.maridone.enums.LeaveType;
import org.example.maridone.exception.notfound.ShiftsNotFoundException;
import org.example.maridone.leave.dto.LeaveRequestDto;
import org.example.maridone.schedule.shift.DailyShiftRepository;
import org.example.maridone.schedule.shift.DailyShiftSchedule;
import org.example.maridone.schedule.shift.TemplateShiftRepository;
import org.example.maridone.schedule.shift.TemplateShiftSchedule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HoursCalculatorTest {

    @Mock
    private TemplateShiftRepository templateShiftRepository;

    @Mock
    private DailyShiftRepository dailyShiftRepository;


    @InjectMocks
    private LeaveService leaveService;

    private final Long empId = 1L;

    private Employee dummyEmployee;

    @BeforeEach
    void setUp() {
        dummyEmployee = new Employee();
        ReflectionTestUtils.setField(dummyEmployee, "employeeId", 1L);
    }


    private LeaveRequestDto createPayload(LocalDateTime start, LocalDateTime end) {
        LeaveRequestDto dto = new LeaveRequestDto();
        dto.setStartDateTime(start);
        dto.setEndDateTime(end);
        dto.setReason("TEST");
        dto.setLeaveType(LeaveType.SICK_LEAVE);
        return dto;
    }

    private TemplateShiftSchedule createSchedule(DayOfWeek day, LocalTime start, LocalTime end, Employee emp) {
        TemplateShiftSchedule schedule = new TemplateShiftSchedule();
        schedule.setDayOfWeek(day);
        schedule.setStartTime(start);
        schedule.setEndTime(end);
        schedule.setEmployee(emp);
        return schedule;
    }

    private List<TemplateShiftSchedule> getMockSchedules(Employee employee) {
        return List.of(
                createSchedule(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(17, 0), employee),
                createSchedule(DayOfWeek.TUESDAY, LocalTime.of(9, 0), LocalTime.of(17, 0), employee),
                createSchedule(DayOfWeek.WEDNESDAY, LocalTime.of(22, 0), LocalTime.of(6, 0), employee)
        );
    }

    private DailyShiftSchedule createDailyShift(LocalDateTime start, LocalDateTime end, boolean dayOff, Employee employee) {
        TemplateShiftSchedule template = new TemplateShiftSchedule();
        template.setEmployee(employee);
        template.setDayOfWeek(start.toLocalDate().getDayOfWeek());
        template.setStartTime(start.toLocalTime());
        template.setEndTime(end.toLocalTime());

        DailyShiftSchedule shift = new DailyShiftSchedule();
        shift.setTemplateShiftSchedule(template);
        shift.setStartDateTime(start);
        shift.setEndDateTime(end);
        shift.setDayOff(dayOff);
        return shift;
    }


    @Test
    void testNoShifts_ThrowsException() {
        when(templateShiftRepository.findAllByEmployeeId(empId)).thenReturn(Collections.emptyList());
        when(dailyShiftRepository.findAllByEmployeeIdAndPeriod(eq(empId), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());
        LeaveRequestDto payload = createPayload(LocalDateTime.now(), LocalDateTime.now().plusDays(1));

        ShiftsNotFoundException exception = assertThrows(ShiftsNotFoundException.class, () -> {
            leaveService.calculateRequestHours(payload, empId);
        });

        assertEquals("No Shift Schedule for Employee ID: " + empId, exception.getMessage());
    }

    @Test
    void testExactShiftMatch() {
        when(templateShiftRepository.findAllByEmployeeId(empId)).thenReturn(getMockSchedules(dummyEmployee));

        //MONDAY
        LocalDateTime start = LocalDateTime.of(2026, 3, 23, 9, 0);
        LocalDateTime end = LocalDateTime.of(2026, 3, 23, 17, 0);
        LeaveRequestDto payload = createPayload(start, end);

        BigDecimal hours = leaveService.calculateRequestHours(payload, empId);

        assertEquals(new BigDecimal("7.00"), hours);
    }

    @Test
    void testPartialShift_StartAndEndClamped() {
        when(templateShiftRepository.findAllByEmployeeId(empId)).thenReturn(getMockSchedules(dummyEmployee));

        LocalDateTime start = LocalDateTime.of(2026, 3, 23, 12, 0);
        LocalDateTime end = LocalDateTime.of(2026, 3, 23, 15, 0);
        LeaveRequestDto payload = createPayload(start, end);

        BigDecimal hours = leaveService.calculateRequestHours(payload, empId);

        assertEquals(new BigDecimal("3.00"), hours);
    }

    @Test
    void testOutsideShiftHours_ShouldBeZero() {
        when(templateShiftRepository.findAllByEmployeeId(empId)).thenReturn(getMockSchedules(dummyEmployee));

        LocalDateTime start = LocalDateTime.of(2026, 3, 23, 18, 0);
        LocalDateTime end = LocalDateTime.of(2026, 3, 23, 20, 0);
        LeaveRequestDto payload = createPayload(start, end);

        BigDecimal hours = leaveService.calculateRequestHours(payload, empId);

        assertEquals(new BigDecimal("0"), hours);
    }

    @Test
    void testOvernightShift_FullCoverage() {
        when(templateShiftRepository.findAllByEmployeeId(empId)).thenReturn(getMockSchedules(dummyEmployee));

        //WEDNESDAY
        LocalDateTime start = LocalDateTime.of(2026, 3, 25, 0, 0);
        //THURSDAY
        LocalDateTime end = LocalDateTime.of(2026, 3, 26, 23, 59);
        LeaveRequestDto payload = createPayload(start, end);

        BigDecimal hours = leaveService.calculateRequestHours(payload, empId);

        assertEquals(new BigDecimal("7.00"), hours);
    }

    @Test
    void testOvernightShift_PartialCoverage_NextDay() {
        when(templateShiftRepository.findAllByEmployeeId(empId)).thenReturn(getMockSchedules(dummyEmployee));

        //THURSDAY 2AM
        LocalDateTime start = LocalDateTime.of(2026, 3, 26, 2, 0);
        //THURDAY
        LocalDateTime end = LocalDateTime.of(2026, 3, 26, 12, 0);
        LeaveRequestDto payload = createPayload(start, end);

        BigDecimal hours = leaveService.calculateRequestHours(payload, empId);

        assertEquals(new BigDecimal("0"), hours);
    }

    @Test
    void testMultipleDays_FullCoverage() {
        when(templateShiftRepository.findAllByEmployeeId(empId)).thenReturn(getMockSchedules(dummyEmployee));
        LocalDateTime start = LocalDateTime.of(2026, 3, 23, 7, 0);
        LocalDateTime end = LocalDateTime.of(2026, 3, 30, 6, 0);

        LeaveRequestDto payload = createPayload(start, end);
        BigDecimal hours = leaveService.calculateRequestHours(payload, empId);

        assertEquals(new BigDecimal("21.00"), hours);
    }

    @Test
    void testMultipleDays_UsesDailyOverrideInsteadOfTemplate() {
        when(templateShiftRepository.findAllByEmployeeId(empId)).thenReturn(List.of(
                createSchedule(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(13, 0), dummyEmployee),
                createSchedule(DayOfWeek.TUESDAY, LocalTime.of(9, 0), LocalTime.of(13, 0), dummyEmployee)
        ));
        when(dailyShiftRepository.findAllByEmployeeIdAndPeriod(eq(empId), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(
                        createDailyShift(
                                LocalDateTime.of(2026, 3, 24, 10, 0),
                                LocalDateTime.of(2026, 3, 24, 12, 0),
                                false,
                                dummyEmployee
                        )
                ));

        LeaveRequestDto payload = createPayload(
                LocalDateTime.of(2026, 3, 23, 9, 0),
                LocalDateTime.of(2026, 3, 24, 13, 0)
        );

        BigDecimal hours = leaveService.calculateRequestHours(payload, empId);

        assertEquals(new BigDecimal("6.00"), hours);
    }

    @Test
    void testDayOffOverride_ProducesZeroHoursForThatDate() {
        when(templateShiftRepository.findAllByEmployeeId(empId)).thenReturn(List.of(
                createSchedule(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(13, 0), dummyEmployee),
                createSchedule(DayOfWeek.TUESDAY, LocalTime.of(9, 0), LocalTime.of(13, 0), dummyEmployee)
        ));
        when(dailyShiftRepository.findAllByEmployeeIdAndPeriod(eq(empId), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(
                        createDailyShift(
                                LocalDateTime.of(2026, 3, 24, 0, 0),
                                LocalDateTime.of(2026, 3, 24, 0, 0),
                                true,
                                dummyEmployee
                        )
                ));

        LeaveRequestDto payload = createPayload(
                LocalDateTime.of(2026, 3, 23, 9, 0),
                LocalDateTime.of(2026, 3, 24, 13, 0)
        );

        BigDecimal hours = leaveService.calculateRequestHours(payload, empId);

        assertEquals(new BigDecimal("4.00"), hours);
    }

    @Test
    void testMultipleDailyWindows_AreSummedForTheSameDate() {
        when(templateShiftRepository.findAllByEmployeeId(empId)).thenReturn(List.of(
                createSchedule(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(17, 0), dummyEmployee)
        ));
        when(dailyShiftRepository.findAllByEmployeeIdAndPeriod(eq(empId), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(
                        createDailyShift(
                                LocalDateTime.of(2026, 3, 23, 9, 0),
                                LocalDateTime.of(2026, 3, 23, 11, 0),
                                false,
                                dummyEmployee
                        ),
                        createDailyShift(
                                LocalDateTime.of(2026, 3, 23, 14, 0),
                                LocalDateTime.of(2026, 3, 23, 16, 0),
                                false,
                                dummyEmployee
                        )
                ));

        LeaveRequestDto payload = createPayload(
                LocalDateTime.of(2026, 3, 23, 8, 0),
                LocalDateTime.of(2026, 3, 23, 17, 0)
        );

        BigDecimal hours = leaveService.calculateRequestHours(payload, empId);

        assertEquals(new BigDecimal("4.00"), hours);
    }
}
