package org.example.maridone.leave;

import java.math.BigDecimal;
import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.example.maridone.config.DefaultConfig;
import org.example.maridone.core.employee.Employee;
import org.example.maridone.core.employee.EmployeeRepository;
import org.example.maridone.core.user.UserAccountRepository;
import org.example.maridone.config.PayrollConfig;
import org.example.maridone.enums.LeaveType;
import org.example.maridone.enums.EmploymentStatus;
import org.example.maridone.enums.Position;
import org.example.maridone.enums.Status;
import org.example.maridone.core.user.UserAccount;
import org.example.maridone.exception.conflict.OverlappingDatesException;
import org.example.maridone.exception.badrequest.InvalidActionException;
import org.example.maridone.leave.balance.LeaveBalance;
import org.example.maridone.leave.balance.LeaveBalanceRepository;
import org.example.maridone.leave.dto.BalanceRequestDto;
import org.example.maridone.leave.dto.LeaveResponseDto;
import org.example.maridone.leave.dto.LeaveRequestDto;
import org.example.maridone.leave.request.LeaveRequest;
import org.example.maridone.leave.request.LeaveRequestRepository;
import org.example.maridone.holiday.HolidayLookup;
import org.example.maridone.holiday.HolidayService;
import org.example.maridone.notification.NotificationService;
import org.example.maridone.notification.WorkflowEmailService;
import org.example.maridone.overtime.OvertimeRequestRepository;
import org.example.maridone.schedule.shift.DailyShiftRepository;
import org.example.maridone.schedule.shift.DailyShiftSchedule;
import org.example.maridone.schedule.shift.TemplateShiftRepository;
import org.example.maridone.schedule.shift.TemplateShiftSchedule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class LeaveServiceTest {

    @Mock private LeaveBalanceRepository leaveBalanceRepository;
    @Mock private LeaveRequestRepository leaveRequestRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private UserAccountRepository userAccountRepository;
    @Mock private DailyShiftRepository dailyShiftRepository;
    @Mock private TemplateShiftRepository templateShiftRepository;
    @Mock private LeaveMapper leaveMapper;
    @Mock private PayrollConfig payrollConfig;
    @Mock private DefaultConfig defaultConfig;
    @Mock private NotificationService notificationService;
    @Mock private OvertimeRequestRepository overtimeRequestRepository;
    @Mock private WorkflowEmailService workflowEmailService;
    @Mock private HolidayService holidayService;

    @InjectMocks
    private LeaveService leaveService;

    private LocalDateTime getFixedDate() {
        return LocalDateTime.now(defaultConfig.getTimeZone()).plusMonths(2).plusDays(15);
    }
    private MockedStatic<LocalDate> mockedLocalDate;
    private MockedStatic<LocalDateTime> mockedLocalDateTime;
    private LocalDateTime mockedDay;

    @AfterEach
    void tearDownTimeMocks() {
        if (mockedLocalDate != null) {
            mockedLocalDate.close();
        }
        if (mockedLocalDateTime != null) {
            mockedLocalDateTime.close();
        }
    }

    @BeforeEach
    void setUp() {
        lenient().when(defaultConfig.getTimeZone()).thenReturn(ZoneId.of("Asia/Manila"));
        lenient().when(holidayService.getHolidayLookup(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(HolidayLookup.empty());

        LocalDate mockedDate = LocalDate.of(2026, 4, 13);
        mockedDay = mockedDate.atStartOfDay();

        mockedLocalDate = Mockito.mockStatic(LocalDate.class, Mockito.CALLS_REAL_METHODS);
        mockedLocalDateTime = Mockito.mockStatic(LocalDateTime.class, Mockito.CALLS_REAL_METHODS);

        mockedLocalDate.when(() -> LocalDate.now(Mockito.any(ZoneId.class))).thenReturn(mockedDate);
        mockedLocalDate.when(LocalDate::now).thenReturn(mockedDate);

        mockedLocalDateTime.when(() -> LocalDateTime.now(Mockito.any(ZoneId.class))).thenReturn(mockedDay);
        mockedLocalDateTime.when(LocalDateTime::now).thenReturn(mockedDay);
    }

    @Test
    void createLeaveBalance_ShouldSave_WhenNoDuplicate() {
        Long empId = 1L;
        BalanceRequestDto dto = new BalanceRequestDto();
        dto.setLeaveType(LeaveType.VACATION_LEAVE);
        dto.setBalanceHours(BigDecimal.valueOf(10));

        Employee emp = new Employee();
        ReflectionTestUtils.setField(emp, "employeeId", empId);
        
        LeaveBalance mappedBalance = new LeaveBalance();
        mappedBalance.setEmployee(emp);
        mappedBalance.setLeaveType(LeaveType.VACATION_LEAVE);

        when(employeeRepository.findById(empId)).thenReturn(Optional.of(emp));
        when(leaveBalanceRepository.findOne(any(Specification.class))).thenReturn(Optional.empty());
        when(leaveMapper.toEntity(dto, emp)).thenReturn(mappedBalance);
        when(leaveBalanceRepository.save(any(LeaveBalance.class))).thenReturn(mappedBalance);

        LeaveBalance result = leaveService.createLeaveBalance(empId, dto);

        Assertions.assertNotNull(result);
        Assertions.assertEquals(LeaveType.VACATION_LEAVE, result.getLeaveType());
    }

    @Test
    void getBalance_ShouldKeepNewHireCreditsAtZeroBeforeSixMonths() {
        Long empId = 44L;
        Employee emp = new Employee();
        ReflectionTestUtils.setField(emp, "employeeId", empId);
        emp.setEmploymentStatus(EmploymentStatus.NEW_HIRE);
        emp.setEmploymentDateStart(LocalDate.of(2026, 2, 1));

        LeaveBalance sickLeave = new LeaveBalance();
        sickLeave.setEmployee(emp);
        sickLeave.setLeaveType(LeaveType.SICK_LEAVE);
        sickLeave.setBalanceHours(BigDecimal.valueOf(40));

        LeaveBalance vacationLeave = new LeaveBalance();
        vacationLeave.setEmployee(emp);
        vacationLeave.setLeaveType(LeaveType.VACATION_LEAVE);
        vacationLeave.setBalanceHours(BigDecimal.valueOf(40));

        when(employeeRepository.findById(empId)).thenReturn(Optional.of(emp));
        when(leaveBalanceRepository.findByEmployee_EmployeeId(empId)).thenReturn(List.of(sickLeave, vacationLeave));
        when(leaveMapper.toBalanceResponsesDto(anyList())).thenReturn(List.of());

        leaveService.getBalance(empId);

        verify(leaveBalanceRepository).saveAll(argThat(balances -> {
            List<LeaveBalance> collected = new ArrayList<>();
            balances.forEach(collected::add);
            return collected.size() == 2 &&
                    collected.stream().allMatch(balance -> balance.getBalanceHours().compareTo(BigDecimal.ZERO) == 0);
        }));
    }

    @Test
    void getBalance_ShouldAutoGrantCreditsAfterSixMonthsForNewHire() {
        Long empId = 45L;
        Employee emp = new Employee();
        ReflectionTestUtils.setField(emp, "employeeId", empId);
        emp.setEmploymentStatus(EmploymentStatus.NEW_HIRE);
        emp.setEmploymentDateStart(LocalDate.of(2025, 10, 1));

        LeaveBalance sickLeave = new LeaveBalance();
        sickLeave.setEmployee(emp);
        sickLeave.setLeaveType(LeaveType.SICK_LEAVE);
        sickLeave.setBalanceHours(BigDecimal.ZERO);

        LeaveBalance vacationLeave = new LeaveBalance();
        vacationLeave.setEmployee(emp);
        vacationLeave.setLeaveType(LeaveType.VACATION_LEAVE);
        vacationLeave.setBalanceHours(BigDecimal.ZERO);

        when(employeeRepository.findById(empId)).thenReturn(Optional.of(emp));
        when(leaveBalanceRepository.findByEmployee_EmployeeId(empId)).thenReturn(List.of(sickLeave, vacationLeave));
        when(leaveMapper.toBalanceResponsesDto(anyList())).thenReturn(List.of());
        when(payrollConfig.getSickLeaveHours()).thenReturn(BigDecimal.valueOf(40));
        when(payrollConfig.getVacationLeaveHours()).thenReturn(BigDecimal.valueOf(40));

        leaveService.getBalance(empId);

        verify(leaveBalanceRepository).saveAll(argThat(balances -> {
            List<LeaveBalance> collected = new ArrayList<>();
            balances.forEach(collected::add);
            return collected.size() == 2 &&
                    collected.stream().allMatch(balance -> balance.getBalanceHours().compareTo(BigDecimal.valueOf(40)) == 0);
        }));
    }

    @Test
    void createLeaveRequest_ShouldSetStatusPendingManager() {
        Long empId = 1L;
        LeaveRequestDto dto = new LeaveRequestDto();
        dto.setStartDateTime(mockedDay.plusDays(7).withHour(8));
        dto.setEndDateTime(mockedDay.plusDays(7).withHour(17));
        dto.setReason("Vacation");
        dto.setLeaveType(LeaveType.VACATION_LEAVE);

        Employee emp = new Employee();
        ReflectionTestUtils.setField(emp, "employeeId", empId);
        Employee manager = new Employee();
        manager.setPosition(Position.MANAGER);
        emp.setManager(manager);

        LeaveBalance leaveBalance = new LeaveBalance();
        leaveBalance.setBalanceHours(BigDecimal.valueOf(9));

        when(employeeRepository.findById(empId)).thenReturn(Optional.of(emp));
        when(leaveBalanceRepository.findByLeaveTypeAndEmployee_EmployeeId(LeaveType.VACATION_LEAVE, empId))
                .thenReturn(Optional.of(leaveBalance));
        when(overtimeRequestRepository.existsActiveOnWorkDateRange(any(), any(), any())).thenReturn(false);
        when(templateShiftRepository.findAllByEmployeeId(empId)).thenReturn(List.of(
                createSchedule(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(17, 0), emp)
        ));

        List<LeaveRequest> results = leaveService.createLeaveRequest(dto, empId, null);
        Assertions.assertNotNull(results);
        results.forEach(result -> {
            Assertions.assertEquals(Status.PENDING_MANAGER, result.getRequestStatus());
            Assertions.assertEquals(emp, result.getEmployee());
            Assertions.assertEquals("Vacation", result.getReason());
        });
    }

    @Test
    void createLeaveRequest_ShouldRejectNonSickLeaveWithoutOneWeekNotice() {
        Long empId = 1L;
        LeaveRequestDto dto = new LeaveRequestDto();
        dto.setStartDateTime(mockedDay.plusDays(4).withHour(8));
        dto.setEndDateTime(mockedDay.plusDays(4).withHour(17));
        dto.setReason("Vacation");
        dto.setLeaveType(LeaveType.VACATION_LEAVE);

        Employee emp = new Employee();
        ReflectionTestUtils.setField(emp, "employeeId", empId);

        when(employeeRepository.findById(empId)).thenReturn(Optional.of(emp));

        InvalidActionException error =
                Assertions.assertThrows(InvalidActionException.class, () -> leaveService.createLeaveRequest(dto, empId, null));

        Assertions.assertTrue(error.getMessage().contains("at least 7 days before the leave date"));
    }

    @Test
    void createLeaveRequest_ShouldAllowEmergencyLeaveWithoutOneWeekNotice() {
        Long empId = 1L;
        LeaveRequestDto dto = new LeaveRequestDto();
        dto.setStartDateTime(mockedDay.plusDays(1).withHour(8));
        dto.setEndDateTime(mockedDay.plusDays(1).withHour(17));
        dto.setReason("Flu symptoms");
        dto.setLeaveType(LeaveType.EMERGENCY_LEAVE);

        Employee emp = new Employee();
        ReflectionTestUtils.setField(emp, "employeeId", empId);

        LeaveBalance leaveBalance = new LeaveBalance();
        leaveBalance.setBalanceHours(BigDecimal.valueOf(9));

        when(employeeRepository.findById(empId)).thenReturn(Optional.of(emp));
        when(overtimeRequestRepository.existsActiveOnWorkDateRange(any(), any(), any())).thenReturn(false);

        List<LeaveRequest> results = leaveService.createLeaveRequest(dto, empId, null);

        Assertions.assertEquals(1, results.size());
        Assertions.assertEquals(LeaveType.EMERGENCY_LEAVE, results.get(0).getLeaveType());
    }

    @Test
    void createLeaveRequest_ShouldReject_WhenRequestWindowAlreadyStarted() {
        Long empId = 1L;
        LeaveRequestDto dto = new LeaveRequestDto();
        dto.setStartDateTime(LocalDateTime.of(2026, 3, 16, 8, 0));
        dto.setEndDateTime(LocalDateTime.of(2026, 3, 16, 17, 0));
        dto.setReason("Late filing");
        dto.setLeaveType(LeaveType.SICK_LEAVE);

        Employee emp = new Employee();
        ReflectionTestUtils.setField(emp, "employeeId", empId);

        when(employeeRepository.findById(empId)).thenReturn(Optional.of(emp));

        InvalidActionException error = Assertions.assertThrows(
                InvalidActionException.class,
                () -> leaveService.createLeaveRequest(dto, empId, null)
        );

        Assertions.assertTrue(error.getMessage().contains("already started or passed"));
    }

    @ParameterizedTest
    @EnumSource(value = LeaveType.class, names = {
            "SICK_LEAVE",
            "EMERGENCY_LEAVE",
            "MAGNA_CARTA",
            "SOLO_PARENT_LEAVE",
            "RA9262",
    })
    void createLeaveRequest_AdvanceRestricted_ShouldReject_WhenFiledTwoMonthsOrMoreAhead(LeaveType restrictedLeaveTypes) {
        Long empId = 1L;
        LeaveRequestDto dto = new LeaveRequestDto();
        LocalDateTime fixedDate = getFixedDate();
        dto.setStartDateTime(fixedDate.withHour(9));
        dto.setEndDateTime(fixedDate.withHour(17));
        dto.setReason("Planned vacation");
        dto.setLeaveType(restrictedLeaveTypes);

        Employee emp = new Employee();
        ReflectionTestUtils.setField(emp, "employeeId", empId);

        LeaveBalance leaveBalance = new LeaveBalance();
        leaveBalance.setBalanceHours(BigDecimal.valueOf(20));
        when(employeeRepository.findById(empId)).thenReturn(Optional.of(emp));

        InvalidActionException error = Assertions.assertThrows(
                InvalidActionException.class,
                () -> leaveService.createLeaveRequest(dto, empId, null)
        );

        Assertions.assertEquals("Leave requests cannot be filed 2 or more months in advance for this leave type.", error.getMessage());
    }

    @Test
    void createLeaveRequest_ShouldSplitPaidAndUnpaidUsingShiftCoverage() {
        Long empId = 1L;
        LeaveRequestDto dto = new LeaveRequestDto();
        dto.setStartDateTime(mockedDay.plusDays(7).withHour(9));
        dto.setEndDateTime(mockedDay.plusDays(11).withHour(17));
        dto.setReason("Vacation");
        dto.setLeaveType(LeaveType.VACATION_LEAVE);

        Employee emp = new Employee();
        ReflectionTestUtils.setField(emp, "employeeId", empId);

        LeaveBalance leaveBalance = new LeaveBalance();
        leaveBalance.setBalanceHours(BigDecimal.valueOf(20));

        when(employeeRepository.findById(empId)).thenReturn(Optional.of(emp));
        when(leaveBalanceRepository.findByLeaveTypeAndEmployee_EmployeeId(LeaveType.VACATION_LEAVE, empId))
                .thenReturn(Optional.of(leaveBalance));
        when(overtimeRequestRepository.existsActiveOnWorkDateRange(any(), any(), any())).thenReturn(false);
        when(templateShiftRepository.findAllByEmployeeId(empId)).thenReturn(List.of(
                createSchedule(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(17, 0), emp),
                createSchedule(DayOfWeek.TUESDAY, LocalTime.of(9, 0), LocalTime.of(17, 0), emp),
                createSchedule(DayOfWeek.WEDNESDAY, LocalTime.of(9, 0), LocalTime.of(17, 0), emp),
                createSchedule(DayOfWeek.THURSDAY, LocalTime.of(9, 0), LocalTime.of(17, 0), emp),
                createSchedule(DayOfWeek.FRIDAY, LocalTime.of(9, 0), LocalTime.of(17, 0), emp)
        ));
        List<LeaveRequest> results = leaveService.createLeaveRequest(dto, empId,null);
        Assertions.assertEquals(2, results.size());

        LeaveRequest paid = results.get(0);
        Assertions.assertTrue(paid.getPaid());
        Assertions.assertEquals(dto.getStartDateTime().withHour(9), paid.getStartDateTime());
            //1 hour break accounted first
        Assertions.assertEquals(dto.getStartDateTime().plusDays(2).withHour(16), paid.getEndDateTime());

        LeaveRequest unpaid = results.get(1);
        Assertions.assertFalse(unpaid.getPaid());
        Assertions.assertEquals(dto.getStartDateTime().plusDays(2).withHour(16), unpaid.getStartDateTime());
        Assertions.assertEquals(dto.getStartDateTime().plusDays(4).withHour(17), unpaid.getEndDateTime());
    }


    @Test
    void createLeaveRequest_ShouldMoveUnpaidPortionToNextShift_WhenPaidCoverageEndsAtShiftBoundary() {
        Long empId = 1L;
        LeaveRequestDto dto = new LeaveRequestDto();

        dto.setStartDateTime(mockedDay.plusDays(7).withHour(9));
        dto.setEndDateTime(mockedDay.plusDays(9).withHour(17));
        dto.setReason("Medical recovery");
        dto.setLeaveType(LeaveType.SICK_LEAVE);

        Employee emp = new Employee();
        ReflectionTestUtils.setField(emp, "employeeId", empId);

        LeaveBalance leaveBalance = new LeaveBalance();
        leaveBalance.setBalanceHours(BigDecimal.valueOf(14));

        when(employeeRepository.findById(empId)).thenReturn(Optional.of(emp));
        when(leaveBalanceRepository.findByLeaveTypeAndEmployee_EmployeeId(LeaveType.SICK_LEAVE, empId))
                .thenReturn(Optional.of(leaveBalance));
        when(overtimeRequestRepository.existsActiveOnWorkDateRange(any(), any(), any())).thenReturn(false);
        when(templateShiftRepository.findAllByEmployeeId(empId)).thenReturn(List.of(
                createSchedule(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(17, 0), emp),
                createSchedule(DayOfWeek.TUESDAY, LocalTime.of(9, 0), LocalTime.of(17, 0), emp),
                createSchedule(DayOfWeek.WEDNESDAY, LocalTime.of(9, 0), LocalTime.of(17, 0), emp)
        ));

        List<LeaveRequest> results = leaveService.createLeaveRequest(dto, empId, null);

        Assertions.assertEquals(2, results.size());

        LeaveRequest paid = results.get(0);
        Assertions.assertTrue(paid.getPaid());
        Assertions.assertEquals(mockedDay.plusDays(7).withHour(9), paid.getStartDateTime());
        Assertions.assertEquals(mockedDay.plusDays(8).withHour(17), paid.getEndDateTime());

        LeaveRequest unpaid = results.get(1);
        Assertions.assertFalse(unpaid.getPaid());
        Assertions.assertEquals(mockedDay.plusDays(9).withHour(9), unpaid.getStartDateTime());
        Assertions.assertEquals(mockedDay.plusDays(9).withHour(17), unpaid.getEndDateTime());
    }

    @Test
    void createLeaveRequest_ShouldIgnoreHolidayHoursWhenEvaluatingBalanceCoverage() {
        Long empId = 1L;
        LocalDate startDate = LocalDate.of(2026, 4, 20);
        LocalDate endDate = LocalDate.of(2026, 4, 21);
        LeaveRequestDto dto = new LeaveRequestDto();
        dto.setStartDateTime(LocalDateTime.of(2026, 4, 20, 9, 0));
        dto.setEndDateTime(LocalDateTime.of(2026, 4, 21, 13, 0));
        dto.setReason("Vacation");
        dto.setLeaveType(LeaveType.VACATION_LEAVE);

        Employee emp = new Employee();
        ReflectionTestUtils.setField(emp, "employeeId", empId);

        LeaveBalance leaveBalance = new LeaveBalance();
        leaveBalance.setBalanceHours(BigDecimal.valueOf(4));

        when(employeeRepository.findById(empId)).thenReturn(Optional.of(emp));
        when(leaveBalanceRepository.findByLeaveTypeAndEmployee_EmployeeId(LeaveType.VACATION_LEAVE, empId))
                .thenReturn(Optional.of(leaveBalance));
        when(overtimeRequestRepository.existsActiveOnWorkDateRange(any(), any(), any())).thenReturn(false);
        when(templateShiftRepository.findAllByEmployeeId(empId)).thenReturn(List.of(
                createSchedule(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(13, 0), emp),
                createSchedule(DayOfWeek.TUESDAY, LocalTime.of(9, 0), LocalTime.of(13, 0), emp)
        ));
        when(holidayService.getHolidayLookup(startDate, endDate))
                .thenReturn(new HolidayLookup(Set.of(endDate), Set.of()));

        List<LeaveRequest> results = leaveService.createLeaveRequest(dto, empId, null);

        Assertions.assertEquals(1, results.size());
        Assertions.assertTrue(results.get(0).getPaid());
        Assertions.assertEquals(LocalDateTime.of(2026, 4, 20, 9, 0), results.get(0).getStartDateTime());
        Assertions.assertEquals(LocalDateTime.of(2026, 4, 21, 13, 0), results.get(0).getEndDateTime());
    }

    @Test
    void createLeaveRequest_ShouldNotifyManager_WhenEmployeeHasManager() {
        Long empId = 1L;
        LeaveRequestDto dto = new LeaveRequestDto();
        dto.setStartDateTime(mockedDay.plusDays(7).withHour(9));
        dto.setEndDateTime(mockedDay.plusDays(9).withHour(17));
        dto.setReason("Vacation");
        dto.setLeaveType(LeaveType.VACATION_LEAVE);

        Employee manager = new Employee();
        manager.setFirstName("Adrian");
        manager.setLastName("Flores");
        manager.setPosition(Position.MANAGER);
        manager.setEmploymentStatus(EmploymentStatus.REGULAR);
        UserAccount managerAccount = new UserAccount();
        managerAccount.setUsername("manager1");
        manager.setUserAccount(managerAccount);

        Employee emp = new Employee();
        ReflectionTestUtils.setField(emp, "employeeId", empId);
        emp.setFirstName("Richard");
        emp.setLastName("Moore");
        emp.setManager(manager);

        LeaveBalance leaveBalance = new LeaveBalance();
        leaveBalance.setBalanceHours(BigDecimal.valueOf(9));

        when(employeeRepository.findById(empId)).thenReturn(Optional.of(emp));
        when(leaveBalanceRepository.findByLeaveTypeAndEmployee_EmployeeId(LeaveType.VACATION_LEAVE, empId))
                .thenReturn(Optional.of(leaveBalance));
        when(overtimeRequestRepository.existsActiveOnWorkDateRange(any(), any(), any())).thenReturn(false);
        when(templateShiftRepository.findAllByEmployeeId(empId)).thenReturn(List.of(
                createSchedule(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(17, 0), emp)
        ));

        leaveService.createLeaveRequest(dto, empId, null);

        verify(notificationService).createNotification(
                manager,
                "New team leave request from Richard Moore (2026-04-20 to 2026-04-22)",
                "HIGH");
    }

    @Test
    void createLeaveRequest_ShouldReject_WhenOvertimeExistsOnAnyCoveredDay() {
        Long empId = 1L;
        LeaveRequestDto dto = new LeaveRequestDto();
        dto.setStartDateTime(mockedDay.plusDays(7).withHour(9));
        dto.setEndDateTime(mockedDay.plusDays(9).withHour(17));
        dto.setReason("Medical leave");
        dto.setLeaveType(LeaveType.SICK_LEAVE);

        Employee emp = new Employee();
        ReflectionTestUtils.setField(emp, "employeeId", empId);

        when(employeeRepository.findById(empId)).thenReturn(Optional.of(emp));
        when(overtimeRequestRepository.existsActiveOnWorkDateRange(any(), any(), any())).thenReturn(true);

        OverlappingDatesException error = Assertions.assertThrows(
                OverlappingDatesException.class,
                () -> leaveService.createLeaveRequest(dto, empId, null)
        );

        Assertions.assertTrue(error.getMessage().contains("same day"));
    }

    @Test
    void cancelRequest_ShouldNotifyManager_WhenEmployeeHasManager() {
        Employee manager = new Employee();
        manager.setFirstName("Adrian");
        manager.setLastName("Flores");
        manager.setPosition(Position.MANAGER);
        manager.setEmploymentStatus(EmploymentStatus.REGULAR);
        UserAccount managerAccount = new UserAccount();
        managerAccount.setUsername("manager1");
        manager.setUserAccount(managerAccount);

        Employee emp = new Employee();
        emp.setFirstName("Richard");
        emp.setLastName("Moore");
        emp.setManager(manager);

        Employee hr = new Employee();
        hr.setFirstName("Pat");
        hr.setLastName("Reyes");

        LeaveRequest request = new LeaveRequest();
        request.setEmployee(emp);
        request.setRequestStatus(Status.PENDING);
        request.setStartDateTime(mockedDay.plusDays(7).withHour(9));
        request.setEndDateTime(mockedDay.plusDays(9).withHour(17));

        when(leaveRequestRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(request));
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenReturn(request);
        when(employeeRepository.findEmployeeIdsByPosition(Position.HR, EmploymentStatus.TERMINATED)).thenReturn(List.of(5L));
        when(employeeRepository.getReferenceById(5L)).thenReturn(hr);

        leaveService.cancelRequest(12L);

        Assertions.assertEquals(Status.CANCELLED, request.getRequestStatus());
        verify(notificationService).createNotification(
                hr,
                "Leave request from Richard Moore (April 20, 2026 to April 22, 2026) was cancelled.",
                "HIGH");
        verify(notificationService).createNotification(
                manager,
                "Team leave request from Richard Moore (April 20, 2026 to April 22, 2026) was cancelled.",
                "HIGH");
    }

    @Test
    void cancelRequest_ShouldAllowPendingLeaveWithinTwoDaysOfStart() {
        Employee employee = new Employee();
        LeaveRequest request = new LeaveRequest();
        request.setEmployee(employee);
        request.setRequestStatus(Status.PENDING_HR);
        request.setStartDateTime(mockedDay.plusDays(1).withHour(9));
        request.setEndDateTime(mockedDay.plusDays(1).withHour(17));

        when(leaveRequestRepository.findByIdForUpdate(13L)).thenReturn(Optional.of(request));
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenReturn(request);
        when(employeeRepository.findEmployeeIdsByPosition(Position.HR, EmploymentStatus.TERMINATED)).thenReturn(List.of());

        leaveService.cancelRequest(13L);

        Assertions.assertEquals(Status.CANCELLED, request.getRequestStatus());
        verify(leaveRequestRepository).save(request);
    }

    @Test
    void cancelRequest_ShouldRejectApprovedLeaveWithinTwoDaysOfStart() {
        Employee employee = new Employee();
        LeaveRequest request = new LeaveRequest();
        request.setEmployee(employee);
        request.setRequestStatus(Status.APPROVED);
        request.setStartDateTime(mockedDay.plusDays(1).withHour(9));
        request.setEndDateTime(mockedDay.plusDays(1).withHour(17));

        when(leaveRequestRepository.findByIdForUpdate(14L)).thenReturn(Optional.of(request));

        InvalidActionException error = Assertions.assertThrows(
                InvalidActionException.class,
                () -> leaveService.cancelRequest(14L)
        );

        Assertions.assertTrue(error.getMessage().contains("at least 2 days before the start date"));
        verify(leaveRequestRepository, never()).save(any(LeaveRequest.class));
    }

    private TemplateShiftSchedule createSchedule(DayOfWeek day, LocalTime start, LocalTime end, Employee employee) {
        TemplateShiftSchedule schedule = new TemplateShiftSchedule();
        schedule.setDayOfWeek(day);
        schedule.setStartTime(start);
        schedule.setEndTime(end);
        schedule.setEmployee(employee);
        return schedule;
    }

    private DailyShiftSchedule createDailyShift(
            LocalDateTime start,
            LocalDateTime end,
            boolean dayOff,
            Employee employee
    ) {
        TemplateShiftSchedule schedule = createSchedule(
                start.toLocalDate().getDayOfWeek(),
                start.toLocalTime(),
                end.toLocalTime(),
                employee
        );

        DailyShiftSchedule shift = new DailyShiftSchedule();
        shift.setTemplateShiftSchedule(schedule);
        shift.setStartDateTime(start);
        shift.setEndDateTime(end);
        shift.setDayOff(dayOff);
        return shift;
    }

    @Test
    void getTeamLeaveRequests_ShouldReturnHandledEmployeesOnly() {
        String username = "manager1";
        LeaveRequest request = new LeaveRequest();
        LeaveResponseDto responseDto = new LeaveResponseDto();
        Page<LeaveRequest> page = new PageImpl<>(List.of(request));

        when(userAccountRepository.findEmployeeIdByUsername(username)).thenReturn(2L);
        when(employeeRepository.findHandledEmployeeIds(2L)).thenReturn(List.of(3L, 4L));
        when(leaveRequestRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(leaveMapper.toLeaveResponseDto(request)).thenReturn(responseDto);

        Page<LeaveResponseDto> result =
                leaveService.getTeamLeaveRequests(new LeaveFilter(null, null, null, null, null), username, Pageable.ofSize(20));

        Assertions.assertEquals(1, result.getTotalElements());
    }

    @Test
    void createLeaveRequest_ShouldUseDailyOverridesWhenSplittingPaidAndUnpaid() {
        Long empId = 1L;
        LeaveRequestDto dto = new LeaveRequestDto();
        dto.setStartDateTime(LocalDateTime.of(2026, 4, 20, 9, 0));
        dto.setEndDateTime(LocalDateTime.of(2026, 4, 22, 13, 0));
        dto.setReason("Vacation");
        dto.setLeaveType(LeaveType.VACATION_LEAVE);

        Employee emp = new Employee();
        ReflectionTestUtils.setField(emp, "employeeId", empId);

        DailyShiftSchedule dayOffShift = createDailyShift(
                LocalDateTime.of(2026, 4, 21, 0, 0),
                LocalDateTime.of(2026, 4, 21, 0, 0),
                true,
                emp
        );

        LeaveBalance leaveBalance = new LeaveBalance();
        leaveBalance.setBalanceHours(BigDecimal.valueOf(6));

        when(employeeRepository.findById(empId)).thenReturn(Optional.of(emp));
        when(leaveBalanceRepository.findByLeaveTypeAndEmployee_EmployeeId(LeaveType.VACATION_LEAVE, empId))
                .thenReturn(Optional.of(leaveBalance));
        when(overtimeRequestRepository.existsActiveOnWorkDateRange(any(), any(), any())).thenReturn(false);
        when(templateShiftRepository.findAllByEmployeeId(empId)).thenReturn(List.of(
                createSchedule(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(13, 0), emp),
                createSchedule(DayOfWeek.TUESDAY, LocalTime.of(9, 0), LocalTime.of(13, 0), emp),
                createSchedule(DayOfWeek.WEDNESDAY, LocalTime.of(9, 0), LocalTime.of(13, 0), emp)
        ));
        when(dailyShiftRepository.findAllByEmployeeIdAndPeriod(any(), any(), any())).thenReturn(List.of(dayOffShift));

        List<LeaveRequest> results = leaveService.createLeaveRequest(dto, empId, null);

        Assertions.assertEquals(2, results.size());

        LeaveRequest paid = results.get(0);
        Assertions.assertTrue(paid.getPaid());
        Assertions.assertEquals(LocalDateTime.of(2026, 4, 20, 9, 0), paid.getStartDateTime());
        Assertions.assertEquals(LocalDateTime.of(2026, 4, 22, 11, 0), paid.getEndDateTime());

        LeaveRequest unpaid = results.get(1);
        Assertions.assertFalse(unpaid.getPaid());
        Assertions.assertEquals(LocalDateTime.of(2026, 4, 22, 11, 0), unpaid.getStartDateTime());
        Assertions.assertEquals(LocalDateTime.of(2026, 4, 22, 13, 0), unpaid.getEndDateTime());
    }

    @Test
    void updateLeaveRequest_ShouldDeductOnlyShiftCoveredHoursUsingDailyOverrides() {
        DailyShiftSchedule overriddenShift = createDailyShift(
                LocalDateTime.of(2026, 3, 24, 10, 0),
                LocalDateTime.of(2026, 3, 24, 12, 0),
                false,
                null
        );
        Employee requestEmployee = new Employee();
        ReflectionTestUtils.setField(requestEmployee, "employeeId", 3L);

        LeaveRequest request = new LeaveRequest();
        request.setEmployee(requestEmployee);
        request.setRequestStatus(Status.PENDING_HR);
        request.setLeaveType(LeaveType.SICK_LEAVE);
        request.setPaid(true);
        request.setSuggestedPaid(true);
        request.setStartDateTime(LocalDateTime.of(2026, 3, 23, 9, 0));
        request.setEndDateTime(LocalDateTime.of(2026, 3, 24, 13, 0));

        Employee hrEmployee = new Employee();
        ReflectionTestUtils.setField(hrEmployee, "employeeId", 2L);
        hrEmployee.setFirstName("Pat");
        hrEmployee.setLastName("Reyes");
        hrEmployee.setPosition(Position.HR);

        UserAccount hrAccount = new UserAccount();
        hrAccount.setUsername("hr1");
        hrAccount.setEmployee(hrEmployee);

        LeaveBalance leaveBalance = new LeaveBalance();
        leaveBalance.setLeaveType(LeaveType.SICK_LEAVE);
        leaveBalance.setBalanceHours(BigDecimal.valueOf(10));

        LeaveRequestDto payload = new LeaveRequestDto();
        payload.setLeaveType(LeaveType.SICK_LEAVE);
        payload.setRequestStatus(Status.APPROVED);
        payload.setApproverReason("Approved after checking the schedule.");
        payload.setPaid(true);

        Authentication authentication = org.mockito.Mockito.mock(Authentication.class);
        SecurityContext securityContext = org.mockito.Mockito.mock(SecurityContext.class);

        when(authentication.getName()).thenReturn("hr1");
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        when(userAccountRepository.findByUsername("hr1")).thenReturn(Optional.of(hrAccount));
        when(leaveRequestRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(request));
        when(leaveRequestRepository.findEmployeeIdByRequestId(21L)).thenReturn(Optional.of(3L));
        when(templateShiftRepository.findAllByEmployeeId(3L)).thenReturn(List.of(
                createSchedule(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(13, 0), requestEmployee),
                createSchedule(DayOfWeek.TUESDAY, LocalTime.of(9, 0), LocalTime.of(13, 0), requestEmployee)
        ));
        when(dailyShiftRepository.findAllByEmployeeIdAndPeriod(any(), any(), any())).thenReturn(List.of(overriddenShift));
        when(leaveBalanceRepository.findOne(any(Specification.class))).thenReturn(Optional.of(leaveBalance));
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenReturn(request);

        LeaveRequest result = leaveService.updateLeaveRequest(payload, 21L);

        Assertions.assertEquals(Status.APPROVED, result.getRequestStatus());
        Assertions.assertEquals(new BigDecimal("4.00"), leaveBalance.getBalanceHours());
        SecurityContextHolder.clearContext();
    }

    @Test
    void updateLeaveRequest_ShouldNotDeductHolidayHoursFromLeaveBalance() {
        LocalDate startDate = LocalDate.of(2026, 4, 20);
        LocalDate endDate = LocalDate.of(2026, 4, 21);
        Employee requestEmployee = new Employee();
        ReflectionTestUtils.setField(requestEmployee, "employeeId", 3L);

        LeaveRequest request = new LeaveRequest();
        request.setEmployee(requestEmployee);
        request.setRequestStatus(Status.PENDING_HR);
        request.setLeaveType(LeaveType.SICK_LEAVE);
        request.setPaid(true);
        request.setSuggestedPaid(true);
        request.setStartDateTime(LocalDateTime.of(2026, 4, 20, 9, 0));
        request.setEndDateTime(LocalDateTime.of(2026, 4, 21, 13, 0));

        Employee hrEmployee = new Employee();
        ReflectionTestUtils.setField(hrEmployee, "employeeId", 2L);
        hrEmployee.setFirstName("Pat");
        hrEmployee.setLastName("Reyes");
        hrEmployee.setPosition(Position.HR);

        UserAccount hrAccount = new UserAccount();
        hrAccount.setUsername("hr1");
        hrAccount.setEmployee(hrEmployee);

        LeaveBalance leaveBalance = new LeaveBalance();
        leaveBalance.setLeaveType(LeaveType.SICK_LEAVE);
        leaveBalance.setBalanceHours(BigDecimal.valueOf(10));

        LeaveRequestDto payload = new LeaveRequestDto();
        payload.setLeaveType(LeaveType.SICK_LEAVE);
        payload.setRequestStatus(Status.APPROVED);
        payload.setApproverReason("Approved after holiday validation.");
        payload.setPaid(true);

        Authentication authentication = org.mockito.Mockito.mock(Authentication.class);
        SecurityContext securityContext = org.mockito.Mockito.mock(SecurityContext.class);

        when(authentication.getName()).thenReturn("hr1");
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        when(userAccountRepository.findByUsername("hr1")).thenReturn(Optional.of(hrAccount));
        when(leaveRequestRepository.findByIdForUpdate(22L)).thenReturn(Optional.of(request));
        when(leaveRequestRepository.findEmployeeIdByRequestId(22L)).thenReturn(Optional.of(3L));
        when(templateShiftRepository.findAllByEmployeeId(3L)).thenReturn(List.of(
                createSchedule(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(13, 0), requestEmployee),
                createSchedule(DayOfWeek.TUESDAY, LocalTime.of(9, 0), LocalTime.of(13, 0), requestEmployee)
        ));
        when(leaveBalanceRepository.findOne(any(Specification.class))).thenReturn(Optional.of(leaveBalance));
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenReturn(request);
        when(holidayService.getHolidayLookup(startDate, endDate))
                .thenReturn(new HolidayLookup(Set.of(endDate), Set.of()));

        LeaveRequest result = leaveService.updateLeaveRequest(payload, 22L);

        Assertions.assertEquals(Status.APPROVED, result.getRequestStatus());
        Assertions.assertEquals(0, leaveBalance.getBalanceHours().compareTo(new BigDecimal("6.00")));
        SecurityContextHolder.clearContext();
    }

    @Test
    void updateLeaveRequest_ShouldAllowManagerForHandledEmployee() {
        LeaveRequest request = new LeaveRequest();
        request.setRequestStatus(Status.PENDING_MANAGER);
        request.setPaid(false);
        request.setStartDateTime(mockedDay.plusDays(7).withHour(9));
        request.setEndDateTime(mockedDay.plusDays(9).withHour(17));

        Employee employee = new Employee();
        ReflectionTestUtils.setField(employee, "employeeId", 3L);
        request.setEmployee(employee);

        Employee managerEmployee = new Employee();
        managerEmployee.setFirstName("Adrian");
        managerEmployee.setLastName("Flores");
        managerEmployee.setPosition(Position.MANAGER);

        UserAccount managerAccount = new UserAccount();
        managerAccount.setUsername("manager1");
        managerAccount.setEmployee(managerEmployee);

        LeaveRequestDto payload = new LeaveRequestDto();
        payload.setLeaveType(LeaveType.VACATION_LEAVE);
        payload.setRequestStatus(Status.APPROVED);
        payload.setApproverReason("Approved for your team schedule.");

        Authentication authentication = org.mockito.Mockito.mock(Authentication.class);
        SecurityContext securityContext = org.mockito.Mockito.mock(SecurityContext.class);

        when(authentication.getName()).thenReturn("manager1");
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        when(userAccountRepository.findByUsername("manager1")).thenReturn(Optional.of(managerAccount));
        when(userAccountRepository.findEmployeeIdByUsername("manager1")).thenReturn(2L);
        when(leaveRequestRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(request));
        when(leaveRequestRepository.findEmployeeIdByRequestId(9L)).thenReturn(Optional.of(3L));
        ReflectionTestUtils.setField(managerEmployee, "employeeId", 2L);
        when(employeeRepository.findHandledEmployeeIds(2L)).thenReturn(List.of(3L));
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenReturn(request);

        LeaveRequest result = leaveService.updateLeaveRequest(payload, 9L);

        Assertions.assertEquals(Status.PENDING_HR, result.getRequestStatus());
        Assertions.assertEquals("Flores, Adrian", result.getManagerApprover());
        SecurityContextHolder.clearContext();
    }

    @Test
    void updateLeaveRequest_ShouldRejectManagerForUnhandledEmployee() {
        LeaveRequest request = new LeaveRequest();
        request.setRequestStatus(Status.PENDING_MANAGER);
        request.setPaid(false);
        request.setStartDateTime(mockedDay.plusDays(7).withHour(9));
        request.setEndDateTime(mockedDay.plusDays(9).withHour(17));

        Employee employee = new Employee();
        ReflectionTestUtils.setField(employee, "employeeId", 7L);
        request.setEmployee(employee);

        Employee managerEmployee = new Employee();
        ReflectionTestUtils.setField(managerEmployee, "employeeId", 2L);
        managerEmployee.setPosition(Position.MANAGER);

        UserAccount managerAccount = new UserAccount();
        managerAccount.setUsername("manager1");
        managerAccount.setEmployee(managerEmployee);

        LeaveRequestDto payload = new LeaveRequestDto();
        payload.setLeaveType(LeaveType.VACATION_LEAVE);
        payload.setRequestStatus(Status.REJECTED);
        payload.setApproverReason("Not your direct team request.");

        Authentication authentication = org.mockito.Mockito.mock(Authentication.class);
        SecurityContext securityContext = org.mockito.Mockito.mock(SecurityContext.class);

        when(authentication.getName()).thenReturn("manager1");
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        when(userAccountRepository.findByUsername("manager1")).thenReturn(Optional.of(managerAccount));
        when(userAccountRepository.findEmployeeIdByUsername("manager1")).thenReturn(2L);
        when(leaveRequestRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(request));
        when(leaveRequestRepository.findEmployeeIdByRequestId(10L)).thenReturn(Optional.of(7L));
        when(employeeRepository.findHandledEmployeeIds(2L)).thenReturn(List.of(3L, 4L));

        Assertions.assertThrows(InvalidActionException.class, () -> leaveService.updateLeaveRequest(payload, 10L));
        SecurityContextHolder.clearContext();
    }
}
