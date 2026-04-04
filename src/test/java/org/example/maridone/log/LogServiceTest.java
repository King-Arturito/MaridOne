package org.example.maridone.log;

import org.example.maridone.core.employee.Employee;
import org.example.maridone.core.employee.EmployeeRepository;
import org.example.maridone.core.user.UserAccountRepository;
import org.example.maridone.enums.EmploymentStatus;
import org.example.maridone.exception.badrequest.UnauthorizedAccessException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LogServiceTest {

    @Mock
    private ActivityLogRepository activityLogRepository;

    @Mock
    private AttendanceLogRepository attendanceLogRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private UserAccountRepository userAccountRepository;

    @InjectMocks
    private LogService logService;

    @Test
    void attendance_ShouldThrow_WhenEmployeeIsSuspended() {
        Employee employee = new Employee();
        ReflectionTestUtils.setField(employee, "employeeId", 18L);
        employee.setEmploymentStatus(EmploymentStatus.SUSPENDED);

        when(employeeRepository.findById(18L)).thenReturn(Optional.of(employee));

        UnauthorizedAccessException error = Assertions.assertThrows(
                UnauthorizedAccessException.class,
                () -> logService.attendance(18L)
        );

        Assertions.assertEquals("You are currently suspended and cannot log attendance.", error.getMessage());
        verify(attendanceLogRepository, never()).save(any());
    }
}
