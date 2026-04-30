package org.example.maridone.leave;

import org.example.maridone.core.employee.Employee;
import org.example.maridone.enums.EmploymentStatus;

import java.time.LocalDate;

public final class LeaveBalancePolicy {

    private LeaveBalancePolicy() {
    }

    public static boolean isEligibleForDefaultCredits(Employee employee, LocalDate asOfDate) {
        if (employee == null || asOfDate == null) {
            return false;
        }

        EmploymentStatus status = employee.getEmploymentStatus();
        if (status == EmploymentStatus.TERMINATED || status == EmploymentStatus.SUSPENDED || status == EmploymentStatus.PART_TIME) {
            return false;
        }

        if (status == EmploymentStatus.REGULAR) {
            return true;
        }

        if (status == EmploymentStatus.NEW_HIRE) {
            LocalDate startDate = employee.getEmploymentDateStart();
            return startDate != null && !startDate.plusMonths(6).isAfter(asOfDate);
        }

        return false;
    }
}
