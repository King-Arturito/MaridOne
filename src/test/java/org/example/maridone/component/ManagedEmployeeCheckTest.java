package org.example.maridone.component;

import org.example.maridone.core.employee.EmployeeRepository;
import org.example.maridone.core.user.UserAccountRepository;
import org.example.maridone.schedule.shift.TemplateShiftRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManagedEmployeeCheckTest {

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private TemplateShiftRepository templateShiftRepository;

    @InjectMocks
    private ManagedEmployeeCheck managedEmployeeCheck;

    @Test
    void canManageEmployee_ShouldReturnTrue_WhenEmployeeBelongsToManager() {
        when(userAccountRepository.findEmployeeIdByUsername("manager1")).thenReturn(10L);
        when(employeeRepository.existsByEmployeeIdAndManager_EmployeeId(22L, 10L)).thenReturn(true);

        Assertions.assertTrue(managedEmployeeCheck.canManageEmployee(22L, "manager1"));
    }

    @Test
    void canManageShift_ShouldReturnFalse_WhenShiftDoesNotBelongToManagersTeam() {
        when(userAccountRepository.findEmployeeIdByUsername("manager1")).thenReturn(10L);
        when(templateShiftRepository.existsByShiftIdAndEmployee_Manager_EmployeeId(99L, 10L)).thenReturn(false);

        Assertions.assertFalse(managedEmployeeCheck.canManageShift(99L, "manager1"));
    }
}
