package org.example.maridone.core.filter;

import org.example.maridone.enums.EmploymentStatus;
import org.example.maridone.enums.Position;

import java.time.LocalDate;
import java.util.List;

public record EmployeeFilter(
        Long employeeId,
        String firstName,
        String middleName,
        String lastName,
        List<EmploymentStatus> employmentStatusList,
        LocalDate hiredDateStart,
        LocalDate hiredDateEnd,
        String email,
        List<Position> positionList
) {}
