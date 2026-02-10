package org.example.maridone.core.employee;

import java.util.Optional;

import org.example.maridone.core.dto.EmployeeRequestDto;
import org.example.maridone.core.dto.EmployeeResponseDto;
import org.example.maridone.core.mapper.CoreMapper;
import org.example.maridone.exception.EmployeeNotFoundException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private CoreMapper coreMapper;

    @InjectMocks
    private EmployeeService employeeService;

    @Test
    void createEmployee_ShouldReturnResponseDto() {
        EmployeeRequestDto requestDto = new EmployeeRequestDto();
        requestDto.setFirstName("Frieren");
        
        Employee employee = new Employee();
        ReflectionTestUtils.setField(employee, "employeeId", 1L);
        employee.setFirstName("Frieren");

        EmployeeResponseDto responseDto = new EmployeeResponseDto();
        responseDto.setId(1L);
        responseDto.setFirstName("Frieren");

        when(coreMapper.employeeRequestToEmployee(any(EmployeeRequestDto.class))).thenReturn(employee);
        when(employeeRepository.save(any(Employee.class))).thenReturn(employee);
        when(coreMapper.employeeToEmployeeResponse(any(Employee.class))).thenReturn(responseDto);

        EmployeeResponseDto result = employeeService.createEmployee(requestDto);

        Assertions.assertNotNull(result);
        Assertions.assertEquals(1L, result.getId()); 
        Assertions.assertEquals("Frieren", result.getFirstName());
        verify(employeeRepository).save(any(Employee.class));
    }

    @Test
    void getEmployee_ShouldReturnResponse_WhenFound() {
        Long empId = 1L;
        Employee employee = new Employee();
        ReflectionTestUtils.setField(employee, "employeeId", empId);

        EmployeeResponseDto responseDto = new EmployeeResponseDto();
        responseDto.setId(empId); 

        when(employeeRepository.findById(empId)).thenReturn(Optional.of(employee));
        when(coreMapper.employeeToEmployeeResponse(employee)).thenReturn(responseDto);

        EmployeeResponseDto result = employeeService.getEmployee(empId);

        Assertions.assertNotNull(result);
        Assertions.assertEquals(empId, result.getId()); 
    }

    @Test
    void getEmployee_ShouldThrowException_WhenNotFound() {
        Long empId = 99L;
        when(employeeRepository.findById(empId)).thenReturn(Optional.empty());

        Assertions.assertThrows(EmployeeNotFoundException.class, () -> {
            employeeService.getEmployee(empId);
        });
    }
}