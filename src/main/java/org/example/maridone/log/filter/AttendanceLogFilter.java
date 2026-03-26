package org.example.maridone.log.filter;


import java.time.Instant;

public record AttendanceLogFilter (
        Long attendanceId,
        Long employeeId,
        Instant timestampAfter,
        Instant timestampBefore,
        String direction,
        String firstName,
        String lastName,
        String middleName
)
{}
