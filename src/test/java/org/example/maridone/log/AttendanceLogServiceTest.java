package org.example.maridone.log;

import org.example.maridone.config.DefaultConfig;
import org.example.maridone.core.employee.Employee;
import org.example.maridone.core.employee.EmployeeRepository;
import org.example.maridone.core.user.UserAccountRepository;
import org.example.maridone.enums.EmploymentStatus;
import org.example.maridone.exception.badrequest.InvalidActionException;
import org.example.maridone.exception.unauthorized.UnauthorizedAccessException;
import org.example.maridone.log.attendance.AttendanceLog;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttendanceLogServiceTest {

    @Mock
    private AttendanceLogRepository attendanceLogRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private DefaultConfig defaultConfig;

    @InjectMocks
    private AttendanceLogService attendanceLogService;

    @Test
    void attendance_ShouldThrow_WhenEmployeeIsSuspended() {
        Employee employee = new Employee();
        ReflectionTestUtils.setField(employee, "employeeId", 18L);
        employee.setEmploymentStatus(EmploymentStatus.SUSPENDED);

        when(employeeRepository.findById(18L)).thenReturn(Optional.of(employee));

        UnauthorizedAccessException error = Assertions.assertThrows(
                UnauthorizedAccessException.class,
                () -> attendanceLogService.attendance(18L)
        );

        Assertions.assertEquals("You are currently suspended and cannot log attendance.", error.getMessage());
        verify(attendanceLogRepository, never()).save(any());
    }

    @Test
    void attendance_ShouldUseLatestTimestamp_WhenLastLogWasIn() {
        Employee employee = new Employee();
        ReflectionTestUtils.setField(employee, "employeeId", 18L);
        employee.setEmploymentStatus(EmploymentStatus.REGULAR);
        employee.setIsVerified(true);

        AttendanceLog recent = new AttendanceLog();
        recent.setDirection("IN");
        recent.setTimestamp(Instant.parse("2024-01-10T08:00:00Z"));

        when(employeeRepository.findById(18L)).thenReturn(Optional.of(employee));
        when(defaultConfig.getTimeZone()).thenReturn(ZoneId.of("Asia/Manila"));
        when(attendanceLogRepository.findByEmployeeIdAndTimestampBetween(eq(18L), any(Instant.class), any(Instant.class), any(Sort.class)))
                .thenReturn(List.of(recent));

        attendanceLogService.attendance(18L);

        ArgumentCaptor<AttendanceLog> savedLogCaptor = ArgumentCaptor.forClass(AttendanceLog.class);
        verify(attendanceLogRepository).save(savedLogCaptor.capture());
        AttendanceLog savedLog = savedLogCaptor.getValue();
        Assertions.assertEquals("OUT", savedLog.getDirection());
        Assertions.assertEquals(18L, savedLog.getEmployeeId());
        Assertions.assertNotNull(savedLog.getTimestamp());
    }

    @Test
    void attendance_ShouldTimeIn_WhenPreviousDayWasNotTimedOut() {
        Employee employee = new Employee();
        ReflectionTestUtils.setField(employee, "employeeId", 18L);
        employee.setEmploymentStatus(EmploymentStatus.REGULAR);
        employee.setIsVerified(true);

        when(employeeRepository.findById(18L)).thenReturn(Optional.of(employee));
        when(defaultConfig.getTimeZone()).thenReturn(ZoneId.of("Asia/Manila"));
        when(attendanceLogRepository.findByEmployeeIdAndTimestampBetween(eq(18L), any(Instant.class), any(Instant.class), any(Sort.class)))
                .thenReturn(List.of());

        attendanceLogService.attendance(18L);

        ArgumentCaptor<AttendanceLog> savedLogCaptor = ArgumentCaptor.forClass(AttendanceLog.class);
        verify(attendanceLogRepository).save(savedLogCaptor.capture());
        AttendanceLog savedLog = savedLogCaptor.getValue();
        Assertions.assertEquals("IN", savedLog.getDirection());
        Assertions.assertEquals(18L, savedLog.getEmployeeId());
        Assertions.assertNotNull(savedLog.getTimestamp());
        verify(attendanceLogRepository, never()).findTopByEmployeeIdOrderByTimestampDescAttendanceIdDesc(18L);
    }

    @Test
    void attendance_ShouldThrow_WhenEmployeeIsNotVerified() {
        Employee employee = new Employee();
        ReflectionTestUtils.setField(employee, "employeeId", 18L);
        employee.setEmploymentStatus(EmploymentStatus.REGULAR);
        employee.setIsVerified(false);

        when(employeeRepository.findById(18L)).thenReturn(Optional.of(employee));

        InvalidActionException error = Assertions.assertThrows(
                InvalidActionException.class,
                () -> attendanceLogService.attendance(18L)
        );

        Assertions.assertEquals("Verify your account before starting attendance tracking.", error.getMessage());
        verify(attendanceLogRepository, never()).save(any());
    }

    @Test
    void resolveAttendanceDate_ShouldResetAtSixAmManilaTime() {
        ZoneId manila = ZoneId.of("Asia/Manila");

        Assertions.assertEquals(
                LocalDate.of(2026, 4, 13),
                attendanceLogService.resolveAttendanceDate(Instant.parse("2026-04-13T21:59:59Z"), manila)
        );
        Assertions.assertEquals(
                LocalDate.of(2026, 4, 14),
                attendanceLogService.resolveAttendanceDate(Instant.parse("2026-04-13T22:00:00Z"), manila)
        );
    }
}
