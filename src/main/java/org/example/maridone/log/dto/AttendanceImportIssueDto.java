package org.example.maridone.log.dto;

public record AttendanceImportIssueDto(
        int rowNumber,
        String employeeId,
        String date,
        String message
) {
}
