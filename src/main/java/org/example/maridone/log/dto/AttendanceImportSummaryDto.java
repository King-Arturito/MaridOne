package org.example.maridone.log.dto;

import java.util.List;

public record AttendanceImportSummaryDto(
        String fileName,
        int totalRows,
        int importedRows,
        int skippedRows,
        int importedLogs,
        int issueCount,
        List<AttendanceImportIssueDto> issues
) {
    public AttendanceImportSummaryDto {
        fileName = fileName == null ? "" : fileName;
        issues = issues == null ? List.of() : List.copyOf(issues);
    }
}
