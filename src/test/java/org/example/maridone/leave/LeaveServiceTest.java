package org.example.maridone.leave;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.example.maridone.core.employee.Employee;
import org.example.maridone.core.employee.EmployeeRepository;
import org.example.maridone.core.user.UserAccountRepository;
import org.example.maridone.config.PayrollConfig;
import org.example.maridone.enums.Directories;
import org.example.maridone.enums.LeaveType;
import org.example.maridone.enums.EmploymentStatus;
import org.example.maridone.enums.Position;
import org.example.maridone.enums.Status;
import org.example.maridone.core.user.UserAccount;
import org.example.maridone.exception.unauthorized.InvalidActionException;
import org.example.maridone.leave.balance.LeaveBalance;
import org.example.maridone.leave.balance.LeaveBalanceRepository;
import org.example.maridone.leave.dto.BalanceRequestDto;
import org.example.maridone.leave.dto.LeaveResponseDto;
import org.example.maridone.leave.dto.LeaveRequestDto;
import org.example.maridone.leave.request.LeaveRequest;
import org.example.maridone.leave.request.LeaveRequestRepository;
import org.example.maridone.notification.NotificationService;
import org.example.maridone.schedule.shift.TemplateShiftRepository;
import org.example.maridone.schedule.shift.TemplateShiftSchedule;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
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
    @Mock private TemplateShiftRepository templateShiftRepository;
    @Mock private LeaveMapper leaveMapper;
    @Mock private PayrollConfig payrollConfig;
    @Mock private NotificationService notificationService;

    @InjectMocks
    private LeaveService leaveService;

    @BeforeEach
    void setUpClock() {
        ReflectionTestUtils.setField(
                leaveService,
                "clock",
                Clock.fixed(Instant.parse("2026-03-16T00:00:00Z"), ZoneOffset.UTC)
        );
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
    void createLeaveRequest_ShouldSetStatusPending() {
        Long empId = 1L;
        LeaveRequestDto dto = new LeaveRequestDto();
        dto.setStartDateTime(LocalDate.of(2026, 3, 23).atTime(8, 0));
        dto.setEndDateTime(LocalDate.of(2026, 3, 23).atTime(17, 0));
        dto.setReason("Vacation");
        dto.setLeaveType(LeaveType.VACATION_LEAVE);

        Employee emp = new Employee();
        ReflectionTestUtils.setField(emp, "employeeId", empId);

        LeaveBalance leaveBalance = new LeaveBalance();
        leaveBalance.setBalanceHours(BigDecimal.valueOf(9));

        when(employeeRepository.findById(empId)).thenReturn(Optional.of(emp));
        when(leaveBalanceRepository.findByLeaveTypeAndEmployee_EmployeeId(LeaveType.VACATION_LEAVE, empId))
                .thenReturn(Optional.of(leaveBalance));
        when(templateShiftRepository.findAllByEmployeeId(empId)).thenReturn(List.of(
                createSchedule(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(17, 0), emp)
        ));

        List<LeaveRequest> results = leaveService.createLeaveRequest(dto, empId, null);
        Assertions.assertNotNull(results);
        results.forEach(result -> {
            Assertions.assertEquals(Status.PENDING, result.getRequestStatus());
            Assertions.assertEquals(emp, result.getEmployee());
            Assertions.assertEquals("Vacation", result.getReason());
        });
    }

    @Test
    void createLeaveRequest_ShouldRejectNonSickLeaveWithoutOneWeekNotice() {
        Long empId = 1L;
        LeaveRequestDto dto = new LeaveRequestDto();
        dto.setStartDateTime(LocalDate.of(2026, 3, 22).atTime(8, 0));
        dto.setEndDateTime(LocalDate.of(2026, 3, 22).atTime(17, 0));
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
    void createLeaveRequest_ShouldAllowSickLeaveWithoutOneWeekNotice() {
        Long empId = 1L;
        LeaveRequestDto dto = new LeaveRequestDto();
        dto.setStartDateTime(LocalDate.of(2026, 3, 17).atTime(8, 0));
        dto.setEndDateTime(LocalDate.of(2026, 3, 17).atTime(17, 0));
        dto.setReason("Flu symptoms");
        dto.setLeaveType(LeaveType.SICK_LEAVE);

        Employee emp = new Employee();
        ReflectionTestUtils.setField(emp, "employeeId", empId);

        LeaveBalance leaveBalance = new LeaveBalance();
        leaveBalance.setBalanceHours(BigDecimal.valueOf(9));

        when(employeeRepository.findById(empId)).thenReturn(Optional.of(emp));
        when(leaveBalanceRepository.findByLeaveTypeAndEmployee_EmployeeId(LeaveType.SICK_LEAVE, empId))
                .thenReturn(Optional.of(leaveBalance));
        when(templateShiftRepository.findAllByEmployeeId(empId)).thenReturn(List.of(
                createSchedule(DayOfWeek.TUESDAY, LocalTime.of(8, 0), LocalTime.of(17, 0), emp)
        ));

        List<LeaveRequest> results = leaveService.createLeaveRequest(dto, empId, null);

        Assertions.assertEquals(1, results.size());
        Assertions.assertEquals(LeaveType.SICK_LEAVE, results.get(0).getLeaveType());
    }

    @Test
    void createLeaveRequest_ShouldSplitPaidAndUnpaidUsingShiftCoverage() {
        Long empId = 1L;
        LeaveRequestDto dto = new LeaveRequestDto();
        dto.setStartDateTime(LocalDateTime.of(2026, 3, 23, 9, 0));
        dto.setEndDateTime(LocalDateTime.of(2026, 3, 27, 17, 0));
        dto.setReason("Vacation");
        dto.setLeaveType(LeaveType.VACATION_LEAVE);

        Employee emp = new Employee();
        ReflectionTestUtils.setField(emp, "employeeId", empId);

        LeaveBalance leaveBalance = new LeaveBalance();
        leaveBalance.setBalanceHours(BigDecimal.valueOf(20));

        when(employeeRepository.findById(empId)).thenReturn(Optional.of(emp));
        when(leaveBalanceRepository.findByLeaveTypeAndEmployee_EmployeeId(LeaveType.VACATION_LEAVE, empId))
                .thenReturn(Optional.of(leaveBalance));
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
        Assertions.assertEquals(LocalDateTime.of(2026, 3, 23, 9, 0), paid.getStartDateTime());
        Assertions.assertEquals(LocalDateTime.of(2026, 3, 25, 13, 0), paid.getEndDateTime());

        LeaveRequest unpaid = results.get(1);
        Assertions.assertFalse(unpaid.getPaid());
        Assertions.assertEquals(LocalDateTime.of(2026, 3, 25, 13, 0), unpaid.getStartDateTime());
        Assertions.assertEquals(LocalDateTime.of(2026, 3, 27, 17, 0), unpaid.getEndDateTime());
    }

    @Test
    void createLeaveRequest_ShouldMoveUnpaidPortionToNextShift_WhenPaidCoverageEndsAtShiftBoundary() {
        Long empId = 1L;
        LeaveRequestDto dto = new LeaveRequestDto();
        dto.setStartDateTime(LocalDateTime.of(2026, 7, 13, 9, 0));
        dto.setEndDateTime(LocalDateTime.of(2026, 7, 15, 17, 0));
        dto.setReason("Medical recovery");
        dto.setLeaveType(LeaveType.SICK_LEAVE);

        Employee emp = new Employee();
        ReflectionTestUtils.setField(emp, "employeeId", empId);

        LeaveBalance leaveBalance = new LeaveBalance();
        leaveBalance.setBalanceHours(BigDecimal.valueOf(16));

        when(employeeRepository.findById(empId)).thenReturn(Optional.of(emp));
        when(leaveBalanceRepository.findByLeaveTypeAndEmployee_EmployeeId(LeaveType.SICK_LEAVE, empId))
                .thenReturn(Optional.of(leaveBalance));
        when(templateShiftRepository.findAllByEmployeeId(empId)).thenReturn(List.of(
                createSchedule(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(17, 0), emp),
                createSchedule(DayOfWeek.TUESDAY, LocalTime.of(9, 0), LocalTime.of(17, 0), emp),
                createSchedule(DayOfWeek.WEDNESDAY, LocalTime.of(9, 0), LocalTime.of(17, 0), emp)
        ));

        List<LeaveRequest> results = leaveService.createLeaveRequest(dto, empId, null);

        Assertions.assertEquals(2, results.size());

        LeaveRequest paid = results.get(0);
        Assertions.assertTrue(paid.getPaid());
        Assertions.assertEquals(LocalDateTime.of(2026, 7, 13, 9, 0), paid.getStartDateTime());
        Assertions.assertEquals(LocalDateTime.of(2026, 7, 14, 17, 0), paid.getEndDateTime());

        LeaveRequest unpaid = results.get(1);
        Assertions.assertFalse(unpaid.getPaid());
        Assertions.assertEquals(LocalDateTime.of(2026, 7, 15, 9, 0), unpaid.getStartDateTime());
        Assertions.assertEquals(LocalDateTime.of(2026, 7, 15, 17, 0), unpaid.getEndDateTime());
    }

    @Test
    void createLeaveRequest_ShouldNotifyManager_WhenEmployeeHasManager() {
        Long empId = 1L;
        LeaveRequestDto dto = new LeaveRequestDto();
        dto.setStartDateTime(LocalDate.of(2026, 3, 23).atTime(8, 0));
        dto.setEndDateTime(LocalDate.of(2026, 3, 23).atTime(17, 0));
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
        when(templateShiftRepository.findAllByEmployeeId(empId)).thenReturn(List.of(
                createSchedule(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(17, 0), emp)
        ));

        leaveService.createLeaveRequest(dto, empId, null);

        verify(notificationService).createNotification(
                manager,
                "New team leave request from Richard Moore (2026-03-23 to 2026-03-23)",
                "HIGH");
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
        request.setStartDateTime(LocalDateTime.of(2026, 4, 7, 8, 0));
        request.setEndDateTime(LocalDateTime.of(2026, 4, 8, 17, 0));

        when(leaveRequestRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(request));
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenReturn(request);
        when(employeeRepository.findEmployeeIdsByPosition(Position.HR, EmploymentStatus.TERMINATED)).thenReturn(List.of(5L));
        when(employeeRepository.getReferenceById(5L)).thenReturn(hr);

        leaveService.cancelRequest(12L);

        Assertions.assertEquals(Status.CANCELLED, request.getRequestStatus());
        verify(notificationService).createNotification(
                hr,
                "Leave request from Richard Moore (April 7, 2026 to April 8, 2026) was cancelled.",
                "HIGH");
        verify(notificationService).createNotification(
                manager,
                "Team leave request from Richard Moore (April 7, 2026 to April 8, 2026) was cancelled.",
                "HIGH");
    }

    private TemplateShiftSchedule createSchedule(DayOfWeek day, LocalTime start, LocalTime end, Employee employee) {
        TemplateShiftSchedule schedule = new TemplateShiftSchedule();
        schedule.setDayOfWeek(day);
        schedule.setStartTime(start);
        schedule.setEndTime(end);
        schedule.setEmployee(employee);
        return schedule;
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
    void updateLeaveRequest_ShouldAllowManagerForHandledEmployee() {
        LeaveRequest request = new LeaveRequest();
        request.setRequestStatus(Status.PENDING);
        request.setPaid(false);

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

        Assertions.assertEquals(Status.APPROVED, result.getRequestStatus());
        Assertions.assertEquals("Flores, Adrian", result.getApprover());
        SecurityContextHolder.clearContext();
    }

    @Test
    void updateLeaveRequest_ShouldRejectManagerForUnhandledEmployee() {
        LeaveRequest request = new LeaveRequest();
        request.setRequestStatus(Status.PENDING);
        request.setPaid(false);

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
