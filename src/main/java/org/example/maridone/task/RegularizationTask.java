package org.example.maridone.task;

import org.example.maridone.core.employee.EmployeeService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class RegularizationTask {
    private final EmployeeService employeeService;

    public RegularizationTask(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    /*
        modify Scheduled to be ran everyday at midnight
        verify if regularization is automatic or declared with handbook
        if automatic, modify this method in EmployeeService
            batchUpdateEmployees

        logic:
            find all employees that are up for regularization
                employment >= 6 months and
                employee is not regular/terminated/part-time
            put into a list and then update
     */
    @Scheduled()
    public void regularize() {
        List<Long> employeeIds = new ArrayList<>();
        employeeService.batchUpdateEmploymentStatus(employeeIds);
    }
}
