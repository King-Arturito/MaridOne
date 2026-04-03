package org.example.maridone.core.employee;

import java.util.Optional;
import java.util.List;
import java.time.Duration;

import org.example.maridone.config.PayrollConfig;
import org.example.maridone.core.dto.EmployeeRequestDto;
import org.example.maridone.core.dto.EmployeeResponseDto;
import org.example.maridone.core.mapper.CoreMapper;
import org.example.maridone.core.user.UserAccountRepository;
import org.example.maridone.enums.EmploymentStatus;
import org.example.maridone.enums.ExemptionStatus;
import org.example.maridone.enums.ManagerTitle;
import org.example.maridone.enums.Position;
import org.example.maridone.exception.notfound.EmployeeNotFoundException;
import org.example.maridone.schedule.mapper.ScheduleMapper;
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
    private PayrollConfig payrollConfig;

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

        EmployeeResponseDto result = employeeService.createEmployee(requestDto);

        Assertions.assertNotNull(result);
        Assertions.assertEquals(1L, result.getId()); 
        Assertions.assertEquals("Frieren", result.getFirstName());
        Assertions.assertNotNull(employee.getEmploymentDateStart());
        Assertions.assertEquals(ExemptionStatus.NON_EXEMPT, employee.getExemptionStatus());
        verify(employeeRepository).save(any(Employee.class));
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
}
