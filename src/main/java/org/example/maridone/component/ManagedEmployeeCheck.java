package org.example.maridone.component;

import org.example.maridone.core.employee.EmployeeRepository;
import org.example.maridone.core.user.UserAccountRepository;
import org.example.maridone.schedule.shift.TemplateShiftRepository;
import org.springframework.stereotype.Component;

@Component("managedEmployeeCheck")
public class ManagedEmployeeCheck {

    private final UserAccountRepository userAccountRepository;
    private final EmployeeRepository employeeRepository;
    private final TemplateShiftRepository templateShiftRepository;

    public ManagedEmployeeCheck(UserAccountRepository userAccountRepository,
                                EmployeeRepository employeeRepository,
                                TemplateShiftRepository templateShiftRepository) {
        this.userAccountRepository = userAccountRepository;
        this.employeeRepository = employeeRepository;
        this.templateShiftRepository = templateShiftRepository;
    }

    public boolean canManageEmployee(Long employeeId, String username) {
        if (employeeId == null || username == null || username.isBlank()) {
            return false;
        }

        Long managerId = userAccountRepository.findEmployeeIdByUsername(username);
        if (managerId == null) {
            return false;
        }

        return employeeRepository.existsByEmployeeIdAndManager_EmployeeId(employeeId, managerId);
    }

    public boolean canManageShift(Long shiftId, String username) {
        if (shiftId == null || username == null || username.isBlank()) {
            return false;
        }

        Long managerId = userAccountRepository.findEmployeeIdByUsername(username);
        if (managerId == null) {
            return false;
        }

        return templateShiftRepository.existsByShiftIdAndEmployee_Manager_EmployeeId(shiftId, managerId);
    }
}
