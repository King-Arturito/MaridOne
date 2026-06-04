package org.example.maridone.leave;

import org.example.maridone.core.employee.Employee;
import org.example.maridone.enums.EmploymentStatus;
import org.example.maridone.enums.EmploymentType;

import java.time.LocalDate;

public final class LeaveBalancePolicy {

    private LeaveBalancePolicy() {
    }

    public static boolean isEligibleForDefaultCredits(Employee employee, LocalDate asOfDate) {
        if (employee == null || asOfDate == null) {
            return false;
        }

        if (employee.getEmploymentStatus() == EmploymentStatus.TERMINATED
                || employee.getEmploymentStatus() == EmploymentStatus.SUSPENDED) {
            return false;
        }

        EmploymentType employmentType = resolveEmploymentType(employee);
        if (employmentType == EmploymentType.REGULAR_FULL_TIME) {
            return true;
        }

        if (employmentType == EmploymentType.PROBATIONARY_FULL_TIME) {
            LocalDate startDate = employee.getEmploymentDateStart();
            return startDate != null && !startDate.plusMonths(6).isAfter(asOfDate);
        }

        return false;
    }

    private static EmploymentType resolveEmploymentType(Employee employee) {
        if (employee.getEmploymentType() != null) {
            return employee.getEmploymentType();
        }

        EmploymentStatus status = employee.getEmploymentStatus();
        if (status == EmploymentStatus.NEW_HIRE) {
            return EmploymentType.PROBATIONARY_FULL_TIME;
        }
        if (status == EmploymentStatus.PART_TIME) {
            return EmploymentType.PART_TIME;
        }
        return EmploymentType.REGULAR_FULL_TIME;
    }
}
