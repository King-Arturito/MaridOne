package org.example.maridone.core.employee;

import java.util.Optional;
import java.util.List;
import java.util.ArrayList;
import java.time.Duration;

import org.example.maridone.config.PayrollConfig;
import org.example.maridone.core.dto.EmployeeRequestDto;
import org.example.maridone.core.dto.EmployeeResponseDto;
import org.example.maridone.core.mapper.CoreMapper;
import org.example.maridone.core.user.UserAccountRepository;
import org.example.maridone.embeddable.Address;
import org.example.maridone.enums.EmploymentStatus;
import org.example.maridone.enums.ExemptionStatus;
import org.example.maridone.enums.LeaveType;
import org.example.maridone.enums.ManagerTitle;
import org.example.maridone.enums.Position;
import org.example.maridone.exception.notfound.EmployeeNotFoundException;
import org.example.maridone.exception.unauthorized.InvalidActionException;
import org.example.maridone.leave.balance.LeaveBalance;
import org.example.maridone.leave.balance.LeaveBalanceRepository;
import org.example.maridone.schedule.mapper.ScheduleMapper;
import org.example.maridone.schedule.shift.ShiftService;
import org.example.maridone.schedule.shift.TemplateShiftRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private CoreMapper coreMapper;

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private TemplateShiftRepository templateShiftRepository;

    @Mock
    private ScheduleMapper scheduleMapper;

    @Mock
    private ShiftService shiftService;

    @Mock
    private PayrollConfig payrollConfig;

    @Mock
    private LeaveBalanceRepository leaveBalanceRepository;

    @InjectMocks
    private EmployeeService employeeService;

    private void stubScheduleDependencies() {
        when(templateShiftRepository.findAllByEmployeeIdIn(anyCollection())).thenReturn(List.of());
        when(payrollConfig.getGracePeriod()).thenReturn(Duration.ZERO);
    }

    @Test
    void createEmployee_ShouldReturnResponseDto() {
        stubScheduleDependencies();
        EmployeeRequestDto requestDto = new EmployeeRequestDto();
        requestDto.setFirstName("Frieren");
        
        Employee employee = new Employee();
        ReflectionTestUtils.setField(employee, "employeeId", 1L);
        employee.setFirstName("Frieren");
        employee.setLastName("Test");
        employee.setEmail("frieren@example.com");
        employee.setPhoneNumber("09171234567");
        employee.setBirthDate(java.time.LocalDate.of(2000, 1, 1));
        employee.setPosition(Position.EMPLOYEE);

        EmployeeResponseDto responseDto = new EmployeeResponseDto();
        responseDto.setId(1L);
        responseDto.setFirstName("Frieren");

        when(coreMapper.toEmployee(any(EmployeeRequestDto.class))).thenReturn(employee);
        when(employeeRepository.save(any(Employee.class))).thenReturn(employee);
        when(coreMapper.toEmployeeResponse(any(Employee.class))).thenReturn(responseDto);
        when(userAccountRepository.findUsernameByEmployeeEmployeeId(anyLong())).thenReturn(Optional.empty());
        when(payrollConfig.getSickLeaveHours()).thenReturn(java.math.BigDecimal.valueOf(40));
        when(payrollConfig.getVacationLeaveHours()).thenReturn(java.math.BigDecimal.valueOf(40));

        EmployeeResponseDto result = employeeService.createEmployee(requestDto);

        Assertions.assertNotNull(result);
        Assertions.assertEquals(1L, result.getId()); 
        Assertions.assertEquals("Frieren", result.getFirstName());
        Assertions.assertNotNull(employee.getEmploymentDateStart());
        Assertions.assertEquals(ExemptionStatus.NON_EXEMPT, employee.getExemptionStatus());
        verify(employeeRepository).save(any(Employee.class));
        verify(shiftService).createShifts(employee);
        verify(leaveBalanceRepository).saveAll(argThat(balances -> {
                    List<LeaveBalance> collected = new ArrayList<>();
                    balances.forEach(collected::add);
                    return collected.size() == 2 &&
                            collected.stream().map(LeaveBalance::getLeaveType).toList().containsAll(List.of(
                                LeaveType.SICK_LEAVE,
                                LeaveType.VACATION_LEAVE
                        ));
                }
        ));
    }

    @Test
    void getEmployee_ShouldReturnResponse_WhenFound() {
        stubScheduleDependencies();
        Long empId = 1L;
        Employee employee = new Employee();
        ReflectionTestUtils.setField(employee, "employeeId", empId);
        Employee manager = new Employee();
        manager.setEmpTitle(ManagerTitle.HEAD_OF_PEOPLE_OPERATIONS);
        employee.setManager(manager);

        EmployeeResponseDto responseDto = new EmployeeResponseDto();
        responseDto.setId(empId); 

        when(employeeRepository.findById(empId)).thenReturn(Optional.of(employee));
        when(coreMapper.toEmployeeResponse(employee)).thenReturn(responseDto);
        when(userAccountRepository.findUsernameByEmployeeEmployeeId(anyLong())).thenReturn(Optional.empty());

        EmployeeResponseDto result = employeeService.getEmployee(empId);

        Assertions.assertNotNull(result);
        Assertions.assertEquals(empId, result.getId()); 
        Assertions.assertEquals("People Operations Department", result.getDepartment());
    }

    @Test
    void getEmployee_ShouldThrowException_WhenNotFound() {
        Long empId = 99L;
        when(employeeRepository.findById(empId)).thenReturn(Optional.empty());

        Assertions.assertThrows(EmployeeNotFoundException.class, () -> {
            employeeService.getEmployee(empId);
        });
    }

    @Test
    void createEmployee_ShouldThrow_WhenPhoneNumberIsNotExactly11Digits() {
        EmployeeRequestDto requestDto = new EmployeeRequestDto();
        Employee employee = new Employee();
        employee.setFirstName("Frieren");
        employee.setLastName("Test");
        employee.setEmail("frieren@example.com");
        employee.setPhoneNumber("0917123456");
        employee.setBirthDate(java.time.LocalDate.of(2000, 1, 1));
        employee.setPosition(Position.EMPLOYEE);

        when(coreMapper.toEmployee(any(EmployeeRequestDto.class))).thenReturn(employee);

        IllegalArgumentException error = Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> employeeService.createEmployee(requestDto)
        );

        Assertions.assertEquals("Phone number must be exactly 11 digits.", error.getMessage());
        verify(employeeRepository, never()).save(any(Employee.class));
    }

    @Test
    void createEmployee_ShouldSkipDefaultLeaveBalances_ForSuspendedEmployee() {
        stubScheduleDependencies();
        EmployeeRequestDto requestDto = new EmployeeRequestDto();

        Employee employee = new Employee();
        ReflectionTestUtils.setField(employee, "employeeId", 8L);
        employee.setFirstName("Lucas");
        employee.setLastName("Suspended");
        employee.setEmail("lucas@example.com");
        employee.setPhoneNumber("09171234567");
        employee.setBirthDate(java.time.LocalDate.of(2000, 1, 1));
        employee.setPosition(Position.EMPLOYEE);
        employee.setEmploymentStatus(EmploymentStatus.SUSPENDED);

        EmployeeResponseDto responseDto = new EmployeeResponseDto();
        responseDto.setId(8L);

        when(coreMapper.toEmployee(any(EmployeeRequestDto.class))).thenReturn(employee);
        when(employeeRepository.save(any(Employee.class))).thenReturn(employee);
        when(coreMapper.toEmployeeResponse(any(Employee.class))).thenReturn(responseDto);
        when(userAccountRepository.findUsernameByEmployeeEmployeeId(anyLong())).thenReturn(Optional.empty());

        employeeService.createEmployee(requestDto);

        verify(leaveBalanceRepository, never()).saveAll(any());
    }

    @Test
    void createEmployee_ShouldAssignManagerDepartment_WhenPositionIsManager() {
        stubScheduleDependencies();
        EmployeeRequestDto requestDto = new EmployeeRequestDto();
        requestDto.setDepartment("People Operations Department");

        Employee employee = new Employee();
        ReflectionTestUtils.setField(employee, "employeeId", 18L);
        employee.setFirstName("Cecilia");
        employee.setLastName("Reyes");
        employee.setEmail("cecilia@example.com");
        employee.setPhoneNumber("09171234567");
        employee.setBirthDate(java.time.LocalDate.of(1990, 6, 18));
        employee.setPosition(Position.MANAGER);

        EmployeeResponseDto responseDto = new EmployeeResponseDto();
        responseDto.setId(18L);

        when(coreMapper.toEmployee(any(EmployeeRequestDto.class))).thenReturn(employee);
        when(employeeRepository.save(any(Employee.class))).thenReturn(employee);
        when(coreMapper.toEmployeeResponse(any(Employee.class))).thenReturn(responseDto);
        when(userAccountRepository.findUsernameByEmployeeEmployeeId(anyLong())).thenReturn(Optional.empty());
        when(payrollConfig.getSickLeaveHours()).thenReturn(java.math.BigDecimal.valueOf(40));
        when(payrollConfig.getVacationLeaveHours()).thenReturn(java.math.BigDecimal.valueOf(40));

        employeeService.createEmployee(requestDto);

        Assertions.assertEquals(ManagerTitle.HEAD_OF_PEOPLE_OPERATIONS, employee.getEmpTitle());
    }

    @Test
    void getTeamEmployees_ShouldReturnHandledEmployeesExcludingTerminated() {
        stubScheduleDependencies();
        Employee teamMember = new Employee();
        ReflectionTestUtils.setField(teamMember, "employeeId", 11L);
        teamMember.setFirstName("Team");
        teamMember.setLastName("Member");

        EmployeeResponseDto responseDto = new EmployeeResponseDto();
        responseDto.setId(11L);
        responseDto.setFirstName("Team");

        when(userAccountRepository.findEmployeeIdByUsername("manager1")).thenReturn(2L);
        when(employeeRepository.findByManager_EmployeeIdAndEmploymentStatusNotOrderByLastNameAscFirstNameAsc(
                2L,
                EmploymentStatus.TERMINATED
        )).thenReturn(List.of(teamMember));
        when(coreMapper.toEmployeeResponse(teamMember)).thenReturn(responseDto);
        when(userAccountRepository.findUsernameByEmployeeEmployeeId(11L)).thenReturn(Optional.of("team11"));

        List<EmployeeResponseDto> result = employeeService.getTeamEmployees("manager1");

        Assertions.assertEquals(1, result.size());
        Assertions.assertEquals(11L, result.get(0).getId());
        Assertions.assertEquals("team11", result.get(0).getUserAccountName());
    }

    @Test
    void updateStatus_ShouldThrow_WhenEmployeeAlreadyTerminated() {
        Employee employee = new Employee();
        ReflectionTestUtils.setField(employee, "employeeId", 14L);
        employee.setEmploymentStatus(EmploymentStatus.TERMINATED);

        EmployeeRequestDto requestDto = new EmployeeRequestDto();
        requestDto.setEmploymentStatus(EmploymentStatus.TERMINATED);

        when(employeeRepository.findById(14L)).thenReturn(Optional.of(employee));

        InvalidActionException error = Assertions.assertThrows(
                InvalidActionException.class,
                () -> employeeService.updateStatus(14L, requestDto)
        );

        Assertions.assertEquals("This employee is already terminated.", error.getMessage());
        verify(employeeRepository, never()).save(any(Employee.class));
    }

    @Test
    void updateSelfEmployeeProfile_ShouldOnlyChangeContactFields() {
        stubScheduleDependencies();
        Employee employee = new Employee();
        ReflectionTestUtils.setField(employee, "employeeId", 21L);
        employee.setEmail("old@example.com");
        employee.setPhoneNumber("09170000000");
        employee.setIsVerified(true);

        Address existingAddress = new Address();
        existingAddress.setCity("Makati");
        employee.setAddress(existingAddress);

        EmployeeRequestDto requestDto = new EmployeeRequestDto();
        requestDto.setEmail("new@example.com");
        requestDto.setPhoneNumber("09171234567");
        Address newAddress = new Address();
        newAddress.setCity("Pasig");
        requestDto.setAddress(newAddress);

        when(employeeRepository.findById(21L)).thenReturn(Optional.of(employee));
        when(employeeRepository.save(any(Employee.class))).thenReturn(employee);
        when(coreMapper.toEmployeeResponse(any(Employee.class))).thenReturn(new EmployeeResponseDto());
        when(userAccountRepository.findUsernameByEmployeeEmployeeId(anyLong())).thenReturn(Optional.empty());

        employeeService.updateSelfEmployeeProfile(21L, requestDto);

        Assertions.assertEquals("new@example.com", employee.getEmail());
        Assertions.assertEquals("09171234567", employee.getPhoneNumber());
        Assertions.assertEquals("Pasig", employee.getAddress().getCity());
        Assertions.assertFalse(employee.getIsVerified());
    }

    @Test
    void updateEmployee_ShouldClearManagerDepartment_WhenPositionChangesAwayFromManager() {
        stubScheduleDependencies();
        Employee employee = new Employee();
        ReflectionTestUtils.setField(employee, "employeeId", 30L);
        employee.setPosition(Position.MANAGER);
        employee.setEmpTitle(ManagerTitle.PRODUCTION_SUPERVISOR);

        EmployeeRequestDto requestDto = new EmployeeRequestDto();
        requestDto.setPosition(Position.EMPLOYEE);

        when(employeeRepository.findById(30L)).thenReturn(Optional.of(employee));
        when(employeeRepository.save(any(Employee.class))).thenReturn(employee);
        when(coreMapper.toEmployeeResponse(any(Employee.class))).thenReturn(new EmployeeResponseDto());
        when(userAccountRepository.findUsernameByEmployeeEmployeeId(anyLong())).thenReturn(Optional.empty());

        employeeService.updateEmployee(30L, requestDto);

        Assertions.assertNull(employee.getEmpTitle());
    }

    @Test
    void updateSelfEmployeeProfile_ShouldRejectRestrictedFields() {
        Employee employee = new Employee();
        ReflectionTestUtils.setField(employee, "employeeId", 22L);

        EmployeeRequestDto requestDto = new EmployeeRequestDto();
        requestDto.setPosition(Position.HR);

        when(employeeRepository.findById(22L)).thenReturn(Optional.of(employee));

        InvalidActionException error = Assertions.assertThrows(
                InvalidActionException.class,
                () -> employeeService.updateSelfEmployeeProfile(22L, requestDto)
        );

        Assertions.assertEquals("You can only update your contact information and address.", error.getMessage());
        verify(employeeRepository, never()).save(any(Employee.class));
    }
}
