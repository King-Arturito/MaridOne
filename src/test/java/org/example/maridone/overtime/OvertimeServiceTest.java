package org.example.maridone.overtime;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;

import org.example.maridone.config.DefaultConfig;
import org.example.maridone.core.employee.Employee;
import org.example.maridone.core.employee.EmployeeRepository;
import org.example.maridone.core.user.UserAccount;
import org.example.maridone.core.user.UserAccountRepository;
import org.example.maridone.enums.EmploymentStatus;
import org.example.maridone.enums.Position;
import org.example.maridone.enums.Status;
import org.example.maridone.exception.conflict.OverlappingDatesException;
import org.example.maridone.exception.unauthorized.InvalidActionException;
import org.example.maridone.exception.unauthorized.OvertimeException;
import org.example.maridone.leave.request.LeaveRequestRepository;
import org.example.maridone.notification.NotificationService;
import org.example.maridone.notification.WorkflowEmailService;
import org.example.maridone.overtime.dto.OvertimeRequestDto;
import org.example.maridone.overtime.dto.OvertimeResponseDto;
import org.example.maridone.overtime.dto.OvertimeUpdateDto;
import org.example.maridone.overtime.mapper.OvertimeMapper;
import org.example.maridone.schedule.shift.TemplateShiftRepository;
import org.example.maridone.schedule.shift.TemplateShiftSchedule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OvertimeServiceTest {

    @Mock private OvertimeRequestRepository overtimeRequestRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private OvertimeMapper overtimeMapper;
    @Mock private UserAccountRepository userAccountRepository;
    @Mock private TemplateShiftRepository templateShiftRepository;
    @Mock private DefaultConfig defaultConfig;
    @Mock private NotificationService notificationService;
    @Mock private LeaveRequestRepository leaveRequestRepository;
    @Mock private WorkflowEmailService workflowEmailService;

    @InjectMocks
    private OvertimeService overtimeService;

    private MockedStatic<LocalDate> mockedLocalDate;
    private MockedStatic<LocalDateTime> mockedLocalDateTime;
    private LocalDate mockedDate;
    private LocalDateTime mockedDay;

    @BeforeEach
    void setUp() {
        lenient().when(defaultConfig.getTimeZone()).thenReturn(ZoneId.of("Asia/Manila"));

        mockedDate = LocalDate.of(2026, 4, 7);
        mockedDay = mockedDate.atStartOfDay();

        mockedLocalDate = Mockito.mockStatic(LocalDate.class, Mockito.CALLS_REAL_METHODS);
        mockedLocalDateTime = Mockito.mockStatic(LocalDateTime.class, Mockito.CALLS_REAL_METHODS);

        mockedLocalDate.when(() -> LocalDate.now(Mockito.any(ZoneId.class))).thenReturn(mockedDate);
        mockedLocalDate.when(LocalDate::now).thenReturn(mockedDate);

        mockedLocalDateTime.when(() -> LocalDateTime.now(Mockito.any(ZoneId.class))).thenReturn(mockedDay);
        mockedLocalDateTime.when(LocalDateTime::now).thenReturn(mockedDay);
    }

    @AfterEach
    void tearDownTimeMocks() {
        if (mockedLocalDate != null) {
            mockedLocalDate.close();
        }
        if (mockedLocalDateTime != null) {
            mockedLocalDateTime.close();
        }
    }

    @Test
    void createOvertimeRequest_ShouldSave() {
        String username = "niko123";
        ZoneId zoneId = ZoneId.of("Asia/Manila");
        LocalDate workDate = LocalDate.now(zoneId).plusDays(1);
        OvertimeRequestDto dto = new OvertimeRequestDto();
        dto.setWorkDate(workDate);
        dto.setStartTime(LocalDateTime.of(workDate, LocalTime.of(17, 0)));
        dto.setEndTime(LocalDateTime.of(workDate, LocalTime.of(19, 0)));
        OvertimeRequest request = new OvertimeRequest();
        Employee emp = new Employee();
        ReflectionTestUtils.setField(emp, "employeeId", 9L);
        TemplateShiftSchedule shift = new TemplateShiftSchedule();
        shift.setDayOfWeek(workDate.getDayOfWeek());
        shift.setStartTime(LocalTime.of(8, 0));
        shift.setEndTime(LocalTime.of(17, 0));

        when(overtimeMapper.toOvertimeRequest(dto)).thenReturn(request);
        when(employeeRepository.findOne(any(Specification.class))).thenReturn(Optional.of(emp));
        when(defaultConfig.getTimeZone()).thenReturn(zoneId);
        when(leaveRequestRepository.existsActiveOnDateRange(any(), any(), any())).thenReturn(false);
        when(templateShiftRepository.findAllByEmployeeId(9L)).thenReturn(java.util.List.of(shift));
        when(overtimeRequestRepository.save(any(OvertimeRequest.class))).thenReturn(request);

        OvertimeRequest result = overtimeService.createOvertimeRequest(dto, username);
        
        Assertions.assertNotNull(result);
    }

    @Test
    void createOvertimeRequest_ShouldRejectWindowInsideShift() {
        String username = "niko123";
        ZoneId zoneId = ZoneId.of("Asia/Manila");
        LocalDate workDate = LocalDate.now(zoneId).plusDays(1);
        OvertimeRequestDto dto = new OvertimeRequestDto();
        dto.setWorkDate(workDate);
        dto.setStartTime(LocalDateTime.of(workDate, LocalTime.of(9, 0)));
        dto.setEndTime(LocalDateTime.of(workDate, LocalTime.of(11, 0)));

        Employee emp = new Employee();
        ReflectionTestUtils.setField(emp, "employeeId", 9L);
        TemplateShiftSchedule shift = new TemplateShiftSchedule();
        shift.setDayOfWeek(workDate.getDayOfWeek());
        shift.setStartTime(LocalTime.of(8, 0));
        shift.setEndTime(LocalTime.of(17, 0));

        when(employeeRepository.findOne(any(Specification.class))).thenReturn(Optional.of(emp));
        when(defaultConfig.getTimeZone()).thenReturn(zoneId);
        when(leaveRequestRepository.existsActiveOnDateRange(any(), any(), any())).thenReturn(false);
        when(templateShiftRepository.findAllByEmployeeId(9L)).thenReturn(java.util.List.of(shift));

        Assertions.assertThrows(OvertimeException.class, () -> overtimeService.createOvertimeRequest(dto, username));
    }

    @Test
    void createOvertimeRequest_ShouldNotifyManager_WhenEmployeeHasManager() {
        String username = "niko123";
        ZoneId zoneId = ZoneId.of("Asia/Manila");
        LocalDate workDate = LocalDate.now(zoneId).plusDays(1);
        OvertimeRequestDto dto = new OvertimeRequestDto();
        dto.setWorkDate(workDate);
        dto.setStartTime(LocalDateTime.of(workDate, LocalTime.of(17, 0)));
        dto.setEndTime(LocalDateTime.of(workDate, LocalTime.of(19, 0)));

        Employee manager = new Employee();
        manager.setFirstName("Adrian");
        manager.setLastName("Flores");
        manager.setPosition(Position.MANAGER);
        manager.setEmploymentStatus(EmploymentStatus.REGULAR);
        UserAccount managerAccount = new UserAccount();
        managerAccount.setUsername("manager1");
        manager.setUserAccount(managerAccount);

        OvertimeRequest request = new OvertimeRequest();
        Employee emp = new Employee();
        ReflectionTestUtils.setField(emp, "employeeId", 9L);
        emp.setFirstName("Niko");
        emp.setLastName("Santos");
        emp.setManager(manager);
        TemplateShiftSchedule shift = new TemplateShiftSchedule();
        shift.setDayOfWeek(workDate.getDayOfWeek());
        shift.setStartTime(LocalTime.of(8, 0));
        shift.setEndTime(LocalTime.of(17, 0));

        when(overtimeMapper.toOvertimeRequest(dto)).thenReturn(request);
        when(employeeRepository.findOne(any(Specification.class))).thenReturn(Optional.of(emp));
        when(defaultConfig.getTimeZone()).thenReturn(zoneId);
        when(leaveRequestRepository.existsActiveOnDateRange(any(), any(), any())).thenReturn(false);
        when(templateShiftRepository.findAllByEmployeeId(9L)).thenReturn(java.util.List.of(shift));
        when(overtimeRequestRepository.save(any(OvertimeRequest.class))).thenReturn(request);

        overtimeService.createOvertimeRequest(dto, username);

        verify(notificationService).createNotification(
                manager,
                "New team overtime request from Niko Santos for " + workDate,
                "HIGH");
    }

    @Test
    void createOvertimeRequest_ShouldReject_WhenLeaveExistsOnSameDay() {
        String username = "niko123";
        ZoneId zoneId = ZoneId.of("Asia/Manila");
        LocalDate workDate = LocalDate.now(zoneId).plusDays(1);
        OvertimeRequestDto dto = new OvertimeRequestDto();
        dto.setWorkDate(workDate);
        dto.setStartTime(LocalDateTime.of(workDate, LocalTime.of(18, 0)));
        dto.setEndTime(LocalDateTime.of(workDate, LocalTime.of(20, 0)));

        Employee emp = new Employee();
        ReflectionTestUtils.setField(emp, "employeeId", 9L);

        when(employeeRepository.findOne(any(Specification.class))).thenReturn(Optional.of(emp));
        when(defaultConfig.getTimeZone()).thenReturn(zoneId);
        when(leaveRequestRepository.existsActiveOnDateRange(any(), any(), any())).thenReturn(true);

        OverlappingDatesException error = Assertions.assertThrows(
                OverlappingDatesException.class,
                () -> overtimeService.createOvertimeRequest(dto, username)
        );

        Assertions.assertTrue(error.getMessage().contains("same day"));
    }

    @Test
    void createOvertimeRequest_ShouldReject_WhenRequestWindowAlreadyStarted() {
        String username = "niko123";
        ZoneId zoneId = ZoneId.of("Asia/Manila");
        LocalDate workDate = LocalDate.of(2026, 3, 16);
        OvertimeRequestDto dto = new OvertimeRequestDto();
        dto.setWorkDate(workDate);
        dto.setStartTime(LocalDateTime.of(workDate, LocalTime.of(17, 0)));
        dto.setEndTime(LocalDateTime.of(workDate, LocalTime.of(19, 0)));

        Employee emp = new Employee();
        ReflectionTestUtils.setField(emp, "employeeId", 9L);

        when(employeeRepository.findOne(any(Specification.class))).thenReturn(Optional.of(emp));
        when(defaultConfig.getTimeZone()).thenReturn(zoneId);

        OvertimeException error = Assertions.assertThrows(
                OvertimeException.class,
                () -> overtimeService.createOvertimeRequest(dto, username)
        );

        Assertions.assertTrue(error.getMessage().contains("past dates"));
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
        emp.setFirstName("Niko");
        emp.setLastName("Santos");
        emp.setManager(manager);

        Employee hr = new Employee();
        hr.setFirstName("Pat");
        hr.setLastName("Reyes");

        OvertimeRequest request = new OvertimeRequest();
        request.setEmployee(emp);
        request.setRequestStatus(Status.PENDING);
        request.setWorkDate(mockedDate);
        request.setStartTime(LocalDateTime.now(defaultConfig.getTimeZone()).plusHours(1));

        when(overtimeRequestRepository.findByIdForUpdate(14L)).thenReturn(Optional.of(request));
        when(overtimeRequestRepository.save(any(OvertimeRequest.class))).thenReturn(request);
        when(employeeRepository.findEmployeeIdsByPosition(Position.HR, EmploymentStatus.TERMINATED)).thenReturn(java.util.List.of(5L));
        when(employeeRepository.getReferenceById(5L)).thenReturn(hr);

        overtimeService.cancelRequest(14L);

        Assertions.assertEquals(Status.CANCELLED, request.getRequestStatus());
        verify(notificationService).createNotification(
                hr,
                "Overtime request from Niko Santos for April 7, 2026 was cancelled.",
                "HIGH");
        verify(notificationService).createNotification(
                manager,
                "Team overtime request from Niko Santos for April 7, 2026 was cancelled.",
                "HIGH");
    }

    @Test
    void cancelRequest_ShouldAllowPendingOvertimeWithinOneHourOfStart() {
        Employee employee = new Employee();
        OvertimeRequest request = new OvertimeRequest();
        request.setEmployee(employee);
        request.setRequestStatus(Status.PENDING_MANAGER);
        request.setWorkDate(mockedDate);
        request.setStartTime(mockedDay.plusMinutes(30));

        when(overtimeRequestRepository.findByIdForUpdate(15L)).thenReturn(Optional.of(request));
        when(overtimeRequestRepository.save(any(OvertimeRequest.class))).thenReturn(request);
        when(employeeRepository.findEmployeeIdsByPosition(Position.HR, EmploymentStatus.TERMINATED)).thenReturn(java.util.List.of());

        overtimeService.cancelRequest(15L);

        Assertions.assertEquals(Status.CANCELLED, request.getRequestStatus());
        verify(overtimeRequestRepository).save(request);
    }

    @Test
    void cancelRequest_ShouldRejectApprovedOvertimeWithinOneHourOfStart() {
        Employee employee = new Employee();
        OvertimeRequest request = new OvertimeRequest();
        request.setEmployee(employee);
        request.setRequestStatus(Status.APPROVED);
        request.setWorkDate(mockedDate);
        request.setStartTime(mockedDay.plusMinutes(30));

        when(overtimeRequestRepository.findByIdForUpdate(16L)).thenReturn(Optional.of(request));

        InvalidActionException error = Assertions.assertThrows(
                InvalidActionException.class,
                () -> overtimeService.cancelRequest(16L)
        );

        Assertions.assertTrue(error.getMessage().contains("at least 1 hour before the start time"));
        verify(overtimeRequestRepository, never()).save(any(OvertimeRequest.class));
    }

    @Test
    void updateOvertimeRequest_ShouldSucceed_WhenPending() {
        Long otId = 1L;
        String approver = "admin";
        OvertimeUpdateDto updateDto = new OvertimeUpdateDto();
        updateDto.setOvertimeId(otId);
        updateDto.setUpdateStatus(Status.APPROVED);
        updateDto.setApproveReason("HR approved the overtime.");

        OvertimeRequest request = new OvertimeRequest();
        ReflectionTestUtils.setField(request, "overtimeId", otId); 
        request.setRequestStatus(Status.PENDING_HR);

        Employee admin = new Employee();
        ReflectionTestUtils.setField(admin, "employeeId", 1L);
        admin.setFirstName("Admin");
        admin.setLastName("User");
        admin.setPosition(Position.HR);

        UserAccount approverAccount = new UserAccount();
        approverAccount.setUsername(approver);
        approverAccount.setEmployee(admin);

        when(overtimeRequestRepository.findByIdForUpdate(otId)).thenReturn(Optional.of(request));
        when(userAccountRepository.findByUsername(approver)).thenReturn(Optional.of(approverAccount));
        when(overtimeRequestRepository.save(any(OvertimeRequest.class))).thenReturn(request);
        
        OvertimeResponseDto responseDto = new OvertimeResponseDto();
        when(overtimeMapper.toOvertimeResponseDto(request)).thenReturn(responseDto);

        OvertimeResponseDto result = overtimeService.updateOvertimeRequest(updateDto, approver);
        
        Assertions.assertNotNull(result);
    }

    @Test
    void getTeamOvertimeRequests_ShouldReturnHandledEmployeesOnly() {
        String username = "manager1";
        OvertimeRequest request = new OvertimeRequest();
        OvertimeResponseDto responseDto = new OvertimeResponseDto();
        Page<OvertimeRequest> page = new PageImpl<>(java.util.List.of(request));

        when(userAccountRepository.findEmployeeIdByUsername(username)).thenReturn(2L);
        when(employeeRepository.findHandledEmployeeIds(2L)).thenReturn(java.util.List.of(3L, 4L));
        when(overtimeRequestRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(overtimeMapper.toOvertimeResponseDto(request)).thenReturn(responseDto);

        Page<OvertimeResponseDto> result =
                overtimeService.getTeamOvertimeRequests(new OvertimeFilter(null, null, null, null, null, null), username, Pageable.ofSize(20));

        Assertions.assertEquals(1, result.getTotalElements());
    }

    @Test
    void updateOvertimeRequest_ShouldAllowManagerForHandledEmployee() {
        OvertimeRequest request = new OvertimeRequest();
        ReflectionTestUtils.setField(request, "overtimeId", 7L);
        request.setRequestStatus(Status.PENDING_MANAGER);
        Employee targetEmployee = new Employee();
        ReflectionTestUtils.setField(targetEmployee, "employeeId", 3L);
        request.setEmployee(targetEmployee);

        Employee managerEmployee = new Employee();
        ReflectionTestUtils.setField(managerEmployee, "employeeId", 2L);
        managerEmployee.setFirstName("Adrian");
        managerEmployee.setLastName("Flores");
        managerEmployee.setPosition(Position.MANAGER);

        UserAccount managerAccount = new UserAccount();
        managerAccount.setUsername("manager1");
        managerAccount.setEmployee(managerEmployee);

        OvertimeUpdateDto updateDto = new OvertimeUpdateDto();
        updateDto.setOvertimeId(7L);
        updateDto.setUpdateStatus(Status.APPROVED);
        updateDto.setApproveReason("Approved for the team deadline.");

        when(overtimeRequestRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(request));
        when(userAccountRepository.findByUsername("manager1")).thenReturn(Optional.of(managerAccount));
        when(userAccountRepository.findEmployeeIdByUsername("manager1")).thenReturn(2L);
        when(employeeRepository.findHandledEmployeeIds(2L)).thenReturn(java.util.List.of(3L));
        when(overtimeRequestRepository.save(any(OvertimeRequest.class))).thenReturn(request);
        when(overtimeMapper.toOvertimeResponseDto(request)).thenReturn(new OvertimeResponseDto());

        OvertimeResponseDto result = overtimeService.updateOvertimeRequest(updateDto, "manager1");

        Assertions.assertNotNull(result);
        Assertions.assertEquals(Status.PENDING_HR, request.getRequestStatus());
        Assertions.assertEquals("Flores, Adrian", request.getManagerApprover());
    }

    @Test
    void updateOvertimeRequest_ShouldRejectManagerForUnhandledEmployee() {
        OvertimeRequest request = new OvertimeRequest();
        ReflectionTestUtils.setField(request, "overtimeId", 8L);
        request.setRequestStatus(Status.PENDING_MANAGER);
        Employee targetEmployee = new Employee();
        ReflectionTestUtils.setField(targetEmployee, "employeeId", 9L);
        request.setEmployee(targetEmployee);

        Employee managerEmployee = new Employee();
        ReflectionTestUtils.setField(managerEmployee, "employeeId", 2L);
        managerEmployee.setPosition(Position.MANAGER);

        UserAccount managerAccount = new UserAccount();
        managerAccount.setUsername("manager1");
        managerAccount.setEmployee(managerEmployee);

        OvertimeUpdateDto updateDto = new OvertimeUpdateDto();
        updateDto.setOvertimeId(8L);
        updateDto.setUpdateStatus(Status.REJECTED);
        updateDto.setApproveReason("Not handled by this manager.");

        when(overtimeRequestRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(request));
        when(userAccountRepository.findByUsername("manager1")).thenReturn(Optional.of(managerAccount));
        when(userAccountRepository.findEmployeeIdByUsername("manager1")).thenReturn(2L);
        when(employeeRepository.findHandledEmployeeIds(2L)).thenReturn(java.util.List.of(3L, 4L));

        Assertions.assertThrows(InvalidActionException.class, () -> overtimeService.updateOvertimeRequest(updateDto, "manager1"));
    }
}
