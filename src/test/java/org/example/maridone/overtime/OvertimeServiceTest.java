package org.example.maridone.overtime;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;

import org.example.maridone.config.DefaultConfig;
import org.example.maridone.core.employee.Employee;
import org.example.maridone.core.employee.EmployeeRepository;
import org.example.maridone.core.user.UserAccount;
import org.example.maridone.core.user.UserAccountRepository;
import org.example.maridone.enums.EmploymentStatus;
import org.example.maridone.enums.Position;
import org.example.maridone.enums.Status;
import org.example.maridone.exception.unauthorized.InvalidActionException;
import org.example.maridone.exception.unauthorized.OvertimeException;
import org.example.maridone.notification.NotificationService;
import org.example.maridone.overtime.dto.OvertimeRequestDto;
import org.example.maridone.overtime.dto.OvertimeResponseDto;
import org.example.maridone.overtime.dto.OvertimeUpdateDto;
import org.example.maridone.overtime.mapper.OvertimeMapper;
import org.example.maridone.schedule.shift.TemplateShiftRepository;
import org.example.maridone.schedule.shift.TemplateShiftSchedule;
import org.junit.jupiter.api.Assertions;
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

    @InjectMocks
    private OvertimeService overtimeService;

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
        when(templateShiftRepository.findAllByEmployeeId(9L)).thenReturn(java.util.List.of(shift));
        when(overtimeRequestRepository.save(any(OvertimeRequest.class))).thenReturn(request);

        overtimeService.createOvertimeRequest(dto, username);

        verify(notificationService).createNotification(
                manager,
                "New team overtime request from Niko Santos for " + workDate,
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
        emp.setFirstName("Niko");
        emp.setLastName("Santos");
        emp.setManager(manager);

        Employee hr = new Employee();
        hr.setFirstName("Pat");
        hr.setLastName("Reyes");

        OvertimeRequest request = new OvertimeRequest();
        request.setEmployee(emp);
        request.setRequestStatus(Status.PENDING);
        request.setWorkDate(LocalDate.of(2026, 4, 7));

        when(overtimeRequestRepository.findById(14L)).thenReturn(Optional.of(request));
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
    void updateOvertimeRequest_ShouldSucceed_WhenPending() {
        Long otId = 1L;
        String approver = "admin";
        OvertimeUpdateDto updateDto = new OvertimeUpdateDto();
        updateDto.setOvertimeId(otId);
        updateDto.setUpdateStatus(Status.APPROVED);

        OvertimeRequest request = new OvertimeRequest();
        ReflectionTestUtils.setField(request, "overtimeId", otId); 
        request.setRequestStatus(Status.PENDING); 

        Employee admin = new Employee();
        ReflectionTestUtils.setField(admin, "employeeId", 1L);
        admin.setFirstName("Admin");
        admin.setLastName("User");
        admin.setPosition(Position.HR);

        UserAccount approverAccount = new UserAccount();
        approverAccount.setUsername(approver);
        approverAccount.setEmployee(admin);

        when(overtimeRequestRepository.findById(otId)).thenReturn(Optional.of(request));
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
        request.setRequestStatus(Status.PENDING);
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

        when(overtimeRequestRepository.findById(7L)).thenReturn(Optional.of(request));
        when(userAccountRepository.findByUsername("manager1")).thenReturn(Optional.of(managerAccount));
        when(userAccountRepository.findEmployeeIdByUsername("manager1")).thenReturn(2L);
        when(employeeRepository.findHandledEmployeeIds(2L)).thenReturn(java.util.List.of(3L));
        when(overtimeRequestRepository.save(any(OvertimeRequest.class))).thenReturn(request);
        when(overtimeMapper.toOvertimeResponseDto(request)).thenReturn(new OvertimeResponseDto());

        OvertimeResponseDto result = overtimeService.updateOvertimeRequest(updateDto, "manager1");

        Assertions.assertNotNull(result);
        Assertions.assertEquals(Status.APPROVED, request.getRequestStatus());
        Assertions.assertEquals("Flores, Adrian", request.getApprover());
    }

    @Test
    void updateOvertimeRequest_ShouldRejectManagerForUnhandledEmployee() {
        OvertimeRequest request = new OvertimeRequest();
        ReflectionTestUtils.setField(request, "overtimeId", 8L);
        request.setRequestStatus(Status.PENDING);
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

        when(overtimeRequestRepository.findById(8L)).thenReturn(Optional.of(request));
        when(userAccountRepository.findByUsername("manager1")).thenReturn(Optional.of(managerAccount));
        when(userAccountRepository.findEmployeeIdByUsername("manager1")).thenReturn(2L);
        when(employeeRepository.findHandledEmployeeIds(2L)).thenReturn(java.util.List.of(3L, 4L));

        Assertions.assertThrows(InvalidActionException.class, () -> overtimeService.updateOvertimeRequest(updateDto, "manager1"));
    }
}
