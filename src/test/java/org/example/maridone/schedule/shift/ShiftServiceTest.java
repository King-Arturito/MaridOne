package org.example.maridone.schedule.shift;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.example.maridone.config.DefaultConfig;
import org.example.maridone.core.employee.Employee;
import org.example.maridone.core.employee.EmployeeRepository;
import org.example.maridone.enums.EmploymentStatus;
import org.example.maridone.exception.unauthorized.InvalidActionException;
import org.example.maridone.leave.request.LeaveRequestRepository;
import org.example.maridone.notification.NotificationService;
import org.example.maridone.schedule.dto.DailyShiftOverrideRequestDto;
import org.example.maridone.schedule.dto.ShiftRequestDto;
import org.example.maridone.schedule.dto.ShiftResponseDto;
import org.example.maridone.schedule.mapper.ScheduleMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ShiftServiceTest {

    private static final ZoneId MANILA = ZoneId.of("Asia/Manila");

    @Mock private TemplateShiftRepository templateShiftRepository;
    @Mock private DailyShiftRepository dailyShiftRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private LeaveRequestRepository leaveRequestRepository;
    @Mock private ScheduleMapper scheduleMapper;
    @Mock private DefaultConfig defaultConfig;
    @Mock private NotificationService notificationService;

    @InjectMocks
    private ShiftService shiftService;

    @Test
    void createShifts_ShouldSave7Days() {
        Long empId = 1L;
        Employee emp = new Employee();
        ReflectionTestUtils.setField(emp, "employeeId", empId);

        when(employeeRepository.findById(empId)).thenReturn(Optional.of(emp));
        when(templateShiftRepository.saveAll(anyList())).thenReturn(Collections.emptyList());

        shiftService.createShifts(empId);

        ArgumentCaptor<List<TemplateShiftSchedule>> schedulesCaptor = ArgumentCaptor.forClass(List.class);
        verify(templateShiftRepository).saveAll(schedulesCaptor.capture());
        List<TemplateShiftSchedule> schedules = schedulesCaptor.getValue();
        Assertions.assertEquals(7, schedules.size());
        Assertions.assertTrue(schedules.stream().allMatch(schedule -> LocalTime.of(8, 0).equals(schedule.getStartTime())));
        Assertions.assertTrue(schedules.stream().allMatch(schedule -> LocalTime.of(17, 0).equals(schedule.getEndTime())));
    }

    @Test
    void getShiftSchedule_ShouldReturnList_WhenFound() {
        Long empId = 1L;
        TemplateShiftSchedule schedule = new TemplateShiftSchedule();
        schedule.setDayOfWeek(DayOfWeek.MONDAY);
        schedule.setStartTime(LocalTime.of(8, 0));
        schedule.setEndTime(LocalTime.of(17, 0));

        when(templateShiftRepository.findAllByEmployeeId(empId)).thenReturn(List.of(schedule));
        when(employeeRepository.existsById(empId)).thenReturn(true);
        when(scheduleMapper.toResponseDtos(List.of(schedule))).thenReturn(List.of(new ShiftResponseDto()));
        List<ShiftResponseDto> result = shiftService.getShiftSchedule(empId);
        Assertions.assertFalse(result.isEmpty());
    }

    @Test
    void addShiftSchedule_ShouldSave() {
        Long empId = 1L;
        ShiftRequestDto dto = new ShiftRequestDto();
        
        ReflectionTestUtils.setField(dto, "empId", empId);
        
        dto.setStartTime(LocalTime.of(9, 0));
        dto.setEndTime(LocalTime.of(18, 0));
        dto.setDayOfWeek(DayOfWeek.MONDAY);

        Employee emp = new Employee();
        ReflectionTestUtils.setField(emp, "employeeId", empId);

        when(employeeRepository.findById(empId)).thenReturn(Optional.of(emp));
        when(templateShiftRepository.save(any(TemplateShiftSchedule.class))).thenAnswer(i -> i.getArgument(0));

        TemplateShiftSchedule result = shiftService.addShiftSchedule(dto);
       
        Assertions.assertNotNull(result);
        Assertions.assertEquals(DayOfWeek.MONDAY, result.getDayOfWeek());
        verify(notificationService).createNotification(emp, "Your recurring Monday shift was set to 9:00 AM - 6:00 PM.", "HIGH");
    }

    @Test
    void upsertDailyShiftOverride_ShouldReplaceExistingDailyShiftForSpecificDate() {
        Long empId = 1L;
        LocalDate workDate = LocalDate.now(MANILA).plusDays(14);

        Employee employee = new Employee();
        ReflectionTestUtils.setField(employee, "employeeId", empId);

        TemplateShiftSchedule templateShift = new TemplateShiftSchedule();
        templateShift.setEmployee(employee);
        templateShift.setDayOfWeek(workDate.getDayOfWeek());
        templateShift.setStartTime(LocalTime.of(8, 0));
        templateShift.setEndTime(LocalTime.of(17, 0));

        DailyShiftSchedule existingDailyShift = new DailyShiftSchedule();
        existingDailyShift.setTemplateShiftSchedule(templateShift);
        existingDailyShift.setStartDateTime(LocalDateTime.of(workDate, LocalTime.of(8, 0)));
        existingDailyShift.setEndDateTime(LocalDateTime.of(workDate, LocalTime.of(17, 0)));

        DailyShiftOverrideRequestDto payload = new DailyShiftOverrideRequestDto();
        payload.setEmpId(empId);
        payload.setWorkDate(workDate);
        payload.setStartTime(LocalTime.of(13, 0));
        payload.setEndTime(LocalTime.of(22, 0));

        when(defaultConfig.getTimeZone()).thenReturn(MANILA);
        when(employeeRepository.findById(empId)).thenReturn(Optional.of(employee));
        when(templateShiftRepository.findByEmployeeIdAndDayOfWeek(empId, workDate.getDayOfWeek()))
                .thenReturn(Optional.of(templateShift));
        when(dailyShiftRepository.findAllByEmployeeIdAndPeriod(
                empId,
                workDate.atStartOfDay(),
                workDate.plusDays(1).atStartOfDay()
        )).thenReturn(List.of(existingDailyShift));
        when(dailyShiftRepository.save(any(DailyShiftSchedule.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DailyShiftSchedule saved = shiftService.upsertDailyShiftOverride(payload);

        verify(dailyShiftRepository).deleteAll(List.of(existingDailyShift));
        verify(templateShiftRepository, never()).save(any(TemplateShiftSchedule.class));
        Assertions.assertEquals(LocalDateTime.of(workDate, LocalTime.of(13, 0)), saved.getStartDateTime());
        Assertions.assertEquals(LocalDateTime.of(workDate, LocalTime.of(22, 0)), saved.getEndDateTime());
        Assertions.assertEquals(empId, saved.getEmployeeId());
    }

    @Test
    void upsertDailyShiftOverride_ShouldCreateFallbackTemplateWhenMissing() {
        Long empId = 1L;
        LocalDate workDate = LocalDate.now(MANILA).plusDays(15);

        Employee employee = new Employee();
        ReflectionTestUtils.setField(employee, "employeeId", empId);

        DailyShiftOverrideRequestDto payload = new DailyShiftOverrideRequestDto();
        payload.setEmpId(empId);
        payload.setWorkDate(workDate);
        payload.setStartTime(LocalTime.of(9, 0));
        payload.setEndTime(LocalTime.of(18, 0));

        when(defaultConfig.getTimeZone()).thenReturn(MANILA);
        when(employeeRepository.findById(empId)).thenReturn(Optional.of(employee));
        when(templateShiftRepository.findByEmployeeIdAndDayOfWeek(empId, workDate.getDayOfWeek()))
                .thenReturn(Optional.empty());
        when(templateShiftRepository.save(any(TemplateShiftSchedule.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(dailyShiftRepository.findAllByEmployeeIdAndPeriod(
                empId,
                workDate.atStartOfDay(),
                workDate.plusDays(1).atStartOfDay()
        )).thenReturn(List.of());
        when(dailyShiftRepository.save(any(DailyShiftSchedule.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DailyShiftSchedule saved = shiftService.upsertDailyShiftOverride(payload);

        verify(templateShiftRepository).save(any(TemplateShiftSchedule.class));
        Assertions.assertEquals(LocalDateTime.of(workDate, LocalTime.of(9, 0)), saved.getStartDateTime());
        Assertions.assertEquals(LocalDateTime.of(workDate, LocalTime.of(18, 0)), saved.getEndDateTime());
        Assertions.assertEquals(empId, saved.getEmployeeId());
    }

    @Test
    void upsertDailyShiftOverride_ShouldPersistDayOffEntry() {
        Long empId = 1L;
        LocalDate workDate = LocalDate.now(MANILA).plusDays(16);

        Employee employee = new Employee();
        ReflectionTestUtils.setField(employee, "employeeId", empId);

        TemplateShiftSchedule template = new TemplateShiftSchedule();
        template.setEmployee(employee);
        template.setDayOfWeek(workDate.getDayOfWeek());
        template.setStartTime(LocalTime.of(8, 0));
        template.setEndTime(LocalTime.of(17, 0));

        DailyShiftOverrideRequestDto payload = new DailyShiftOverrideRequestDto();
        payload.setEmpId(empId);
        payload.setWorkDate(workDate);
        payload.setDayOff(true);

        when(defaultConfig.getTimeZone()).thenReturn(MANILA);
        when(employeeRepository.findById(empId)).thenReturn(Optional.of(employee));
        when(templateShiftRepository.findByEmployeeIdAndDayOfWeek(empId, workDate.getDayOfWeek()))
                .thenReturn(Optional.of(template));
        when(dailyShiftRepository.findAllByEmployeeIdAndPeriod(
                empId,
                workDate.atStartOfDay(),
                workDate.plusDays(1).atStartOfDay()
        )).thenReturn(List.of());
        when(dailyShiftRepository.save(any(DailyShiftSchedule.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DailyShiftSchedule saved = shiftService.upsertDailyShiftOverride(payload);

        Assertions.assertTrue(saved.isDayOff());
        Assertions.assertEquals(LocalDateTime.of(workDate, LocalTime.MIDNIGHT), saved.getStartDateTime());
        Assertions.assertEquals(LocalDateTime.of(workDate, LocalTime.MIDNIGHT), saved.getEndDateTime());
        verify(notificationService).createNotification(
                employee,
                "Your shift for " + workDate.format(java.time.format.DateTimeFormatter.ofPattern("MMMM d, yyyy")) + " was marked as day off.",
                "HIGH"
        );
    }

    @Test
    void updateShiftSchedule_ShouldNotifyEmployee() {
        Long empId = 1L;
        Long shiftId = 5L;

        Employee employee = new Employee();
        ReflectionTestUtils.setField(employee, "employeeId", empId);

        TemplateShiftSchedule schedule = new TemplateShiftSchedule();
        schedule.setEmployee(employee);
        schedule.setDayOfWeek(DayOfWeek.TUESDAY);
        schedule.setStartTime(LocalTime.of(8, 0));
        schedule.setEndTime(LocalTime.of(17, 0));

        ShiftRequestDto payload = new ShiftRequestDto();
        ReflectionTestUtils.setField(payload, "shiftId", shiftId);
        payload.setDayOfWeek(DayOfWeek.TUESDAY);
        payload.setStartTime(LocalTime.of(10, 0));
        payload.setEndTime(LocalTime.of(21, 0));

        when(templateShiftRepository.findById(shiftId)).thenReturn(Optional.of(schedule));
        when(templateShiftRepository.save(any(TemplateShiftSchedule.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TemplateShiftSchedule updated = shiftService.updateShiftSchedule(payload);

        Assertions.assertEquals(LocalTime.of(10, 0), updated.getStartTime());
        Assertions.assertEquals(LocalTime.of(21, 0), updated.getEndTime());
        verify(notificationService).createNotification(
                employee,
                "Your recurring Tuesday shift was updated to 10:00 AM - 9:00 PM.",
                "HIGH"
        );
    }

    @Test
    void setDailyShifts_ShouldSkipEmployeesOutsideEmploymentWindow() {
        LocalDate today = LocalDate.now(MANILA);

        Employee futureEmployee = new Employee();
        ReflectionTestUtils.setField(futureEmployee, "employeeId", 1L);
        futureEmployee.setEmploymentStatus(EmploymentStatus.REGULAR);
        futureEmployee.setEmploymentDateStart(today.plusDays(1));

        Employee endedEmployee = new Employee();
        ReflectionTestUtils.setField(endedEmployee, "employeeId", 2L);
        endedEmployee.setEmploymentStatus(EmploymentStatus.TERMINATED);
        endedEmployee.setEmploymentDateStart(today.minusDays(10));
        endedEmployee.setEmploymentDateEnd(today.minusDays(1));

        Employee activeEmployee = new Employee();
        ReflectionTestUtils.setField(activeEmployee, "employeeId", 3L);
        activeEmployee.setEmploymentStatus(EmploymentStatus.REGULAR);
        activeEmployee.setEmploymentDateStart(today.minusDays(10));

        TemplateShiftSchedule activeTemplate = new TemplateShiftSchedule();
        activeTemplate.setEmployee(activeEmployee);
        activeTemplate.setDayOfWeek(today.getDayOfWeek());
        activeTemplate.setStartTime(LocalTime.of(8, 0));
        activeTemplate.setEndTime(LocalTime.of(17, 0));

        when(defaultConfig.getTimeZone()).thenReturn(MANILA);
        when(employeeRepository.findAll(org.mockito.ArgumentMatchers.<org.springframework.data.jpa.domain.Specification<Employee>>any()))
                .thenReturn(List.of(futureEmployee, endedEmployee, activeEmployee));
        when(dailyShiftRepository.findAllByEmployeeIdsAndPeriod(
                List.of(3L),
                today.atStartOfDay(),
                today.plusDays(1).atStartOfDay()
        )).thenReturn(List.of());
        when(templateShiftRepository.findAllByEmployeeIdInAndDayOfWeek(List.of(3L), today.getDayOfWeek()))
                .thenReturn(List.of(activeTemplate));
        when(leaveRequestRepository.findApprovedLeavesForDay(today.atStartOfDay(), today.plusDays(1).atStartOfDay()))
                .thenReturn(List.of());

        shiftService.setDailyShifts();

        ArgumentCaptor<List<DailyShiftSchedule>> dailyShiftsCaptor = ArgumentCaptor.forClass(List.class);
        verify(dailyShiftRepository).saveAll(dailyShiftsCaptor.capture());
        List<DailyShiftSchedule> savedDailyShifts = dailyShiftsCaptor.getValue();
        Assertions.assertEquals(1, savedDailyShifts.size());
        Assertions.assertEquals(3L, savedDailyShifts.get(0).getEmployeeId());
    }

    @Test
    void upsertDailyShiftOverride_ShouldRejectPastDates() {
        Long empId = 1L;
        LocalDate workDate = LocalDate.now(MANILA).minusDays(1);

        Employee employee = new Employee();
        ReflectionTestUtils.setField(employee, "employeeId", empId);

        DailyShiftOverrideRequestDto payload = new DailyShiftOverrideRequestDto();
        payload.setEmpId(empId);
        payload.setWorkDate(workDate);
        payload.setStartTime(LocalTime.of(8, 0));
        payload.setEndTime(LocalTime.of(17, 0));

        when(defaultConfig.getTimeZone()).thenReturn(MANILA);
        when(employeeRepository.findById(empId)).thenReturn(Optional.of(employee));

        Assertions.assertThrows(InvalidActionException.class, () -> shiftService.upsertDailyShiftOverride(payload));
        verify(dailyShiftRepository, never()).save(any(DailyShiftSchedule.class));
    }
}
