package org.example.maridone.log;

import org.example.maridone.config.DefaultConfig;
import org.example.maridone.core.employee.Employee;
import org.example.maridone.core.employee.EmployeeRepository;
import org.example.maridone.enums.EmploymentStatus;
import org.example.maridone.log.attendance.AttendanceLog;
import org.example.maridone.log.dto.AttendanceImportSummaryDto;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttendanceImportServiceTest {

    @Mock
    private AttendanceLogRepository attendanceLogRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private DefaultConfig defaultConfig;

    @InjectMocks
    private AttendanceImportService attendanceImportService;

    @Test
    void importAttendanceCsv_ShouldImportSummaryRowsAndSplitOvernightTimeout() {
        Employee employee = buildEmployee(18L, EmploymentStatus.REGULAR, LocalDate.of(2020, 1, 1), null);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "attendance-report-2024-01.csv",
                "text/csv",
                """
                Employee ID,Employee Name,Date,Time In,Time Out
                18,Jane Doe,2024-01-10,21:30,05:15
                """.getBytes(StandardCharsets.UTF_8)
        );

        when(defaultConfig.getTimeZone()).thenReturn(ZoneId.of("UTC"));
        when(employeeRepository.findAllById(any())).thenReturn(List.of(employee));
        when(attendanceLogRepository.existsByEmployeeIdAndTimestampAndDirection(anyLong(), any(), anyString()))
                .thenReturn(false);

        AttendanceImportSummaryDto summary = attendanceImportService.importAttendanceCsv(file);

        Assertions.assertEquals(1, summary.totalRows());
        Assertions.assertEquals(1, summary.importedRows());
        Assertions.assertEquals(0, summary.skippedRows());
        Assertions.assertEquals(2, summary.importedLogs());
        Assertions.assertEquals(0, summary.issueCount());

        ArgumentCaptor<Iterable<AttendanceLog>> savedLogsCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(attendanceLogRepository).saveAll(savedLogsCaptor.capture());
        List<AttendanceLog> savedLogs = new ArrayList<>();
        savedLogsCaptor.getValue().forEach(savedLogs::add);

        Assertions.assertEquals(2, savedLogs.size());
        AttendanceLog timeIn = savedLogs.stream()
                .filter(log -> "IN".equals(log.getDirection()))
                .findFirst()
                .orElseThrow();
        AttendanceLog timeOut = savedLogs.stream()
                .filter(log -> "OUT".equals(log.getDirection()))
                .findFirst()
                .orElseThrow();

        Assertions.assertEquals(Instant.parse("2024-01-10T21:30:00Z"), timeIn.getTimestamp());
        Assertions.assertEquals(Instant.parse("2024-01-11T05:15:00Z"), timeOut.getTimestamp());
    }

    @Test
    void importAttendanceCsv_ShouldReportInvalidAndDuplicateRowsWithoutSaving() {
        Employee employee = buildEmployee(18L, EmploymentStatus.REGULAR, LocalDate.of(2020, 1, 1), null);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "attendance-report-2024-01.csv",
                "text/csv",
                """
                Employee ID,Employee Name,Date,Time In,Time Out
                18,Jane Doe,2024-01-10,,
                18,Jane Doe,2024-01-11,08:00,17:00
                """.getBytes(StandardCharsets.UTF_8)
        );

        when(defaultConfig.getTimeZone()).thenReturn(ZoneId.of("UTC"));
        when(employeeRepository.findAllById(any())).thenReturn(List.of(employee));
        when(attendanceLogRepository.existsByEmployeeIdAndTimestampAndDirection(anyLong(), any(), anyString()))
                .thenReturn(true);

        AttendanceImportSummaryDto summary = attendanceImportService.importAttendanceCsv(file);

        Assertions.assertEquals(2, summary.totalRows());
        Assertions.assertEquals(0, summary.importedRows());
        Assertions.assertEquals(2, summary.skippedRows());
        Assertions.assertEquals(0, summary.importedLogs());
        Assertions.assertEquals(2, summary.issueCount());
        Assertions.assertTrue(summary.issues().stream().anyMatch(issue ->
                issue.message().contains("At least one of Time In or Time Out is required.")
        ));
        Assertions.assertTrue(summary.issues().stream().anyMatch(issue ->
                issue.message().contains("already exists")
        ));
        verify(attendanceLogRepository, never()).saveAll(any());
    }

    private Employee buildEmployee(Long employeeId,
                                   EmploymentStatus status,
                                   LocalDate startDate,
                                   LocalDate endDate) {
        Employee employee = new Employee();
        ReflectionTestUtils.setField(employee, "employeeId", employeeId);
        employee.setEmploymentStatus(status);
        employee.setEmploymentDateStart(startDate);
        employee.setEmploymentDateEnd(endDate);
        employee.setFirstName("Jane");
        employee.setLastName("Doe");
        return employee;
    }
}
