package org.example.maridone.log.filter;

import org.example.maridone.core.employee.Employee;

import java.time.Instant;

public record AttendanceLogFilter (
        Long attendanceId,
        Employee employee,
        Instant timestamp,
        String direction
)
{}
