package org.example.maridone.overtime;

import org.example.maridone.enums.EarningsType;
import org.example.maridone.enums.Status;

import java.time.Instant;
import java.time.LocalDate;

public record OvertimeFilter(
        Long overtimeId,
        Long employeeId,
        LocalDate workDate,
        Status requestStatus,
        Instant requestAt,
        EarningsType overtimeType
) {

}
