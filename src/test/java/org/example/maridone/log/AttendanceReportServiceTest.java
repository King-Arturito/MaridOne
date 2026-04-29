package org.example.maridone.log;

import org.example.maridone.config.DefaultConfig;
import org.example.maridone.core.employee.Employee;
import org.example.maridone.core.employee.EmployeeRepository;
import org.example.maridone.core.user.UserAccountRepository;
import org.example.maridone.enums.EmploymentStatus;
import org.example.maridone.enums.ManagerTitle;
import org.example.maridone.enums.Position;
import org.example.maridone.leave.request.LeaveRequest;
import org.example.maridone.leave.request.LeaveRequestRepository;
import org.example.maridone.log.attendance.AttendanceLog;
import org.example.maridone.overtime.OvertimeRequest;
import org.example.maridone.overtime.OvertimeRequestRepository;
import org.example.maridone.payroll.PayrollCalculator;
import org.example.maridone.schedule.shift.DailyShiftRepository;
import org.example.maridone.schedule.shift.DailyShiftSchedule;
import org.example.maridone.schedule.shift.TemplateShiftRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttendanceReportServiceTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private AttendanceLogRepository attendanceLogRepository;

    @Mock
    private DailyShiftRepository dailyShiftRepository;

    @Mock
    private TemplateShiftRepository templateShiftRepository;

    @Mock
    private LeaveRequestRepository leaveRequestRepository;

    @Mock
    private OvertimeRequestRepository overtimeRequestRepository;

    @Mock
    private PayrollCalculator payrollCalculator;

    private DefaultConfig defaultConfig;

    private AttendanceReportService attendanceReportService;

    @BeforeEach
    void setUp() {
        defaultConfig = new DefaultConfig();
        defaultConfig.setTimeZone(ZoneId.of("Asia/Manila"));
        attendanceReportService = new AttendanceReportService(
                employeeRepository,
                userAccountRepository,
                attendanceLogRepository,
                dailyShiftRepository,
                templateShiftRepository,
                leaveRequestRepository,
                overtimeRequestRepository,
                payrollCalculator,
                defaultConfig
        );
    }

    @Test
    void exportAttendanceReport_ShouldMarkAbsentWhenScheduledWindowNotFullyCovered() {
        LocalDate workDate = LocalDate.of(2026, 4, 14);
        Employee manager = employee(7L, "Mina", "Santos", Position.MANAGER, EmploymentStatus.REGULAR);
        manager.setEmpTitle(ManagerTitle.HEAD_OF_PEOPLE_OPERATIONS);

        Employee employee = employee(18L, "Juan", "Dela Cruz", Position.EMPLOYEE, EmploymentStatus.REGULAR);
        employee.setManager(manager);

        DailyShiftSchedule shift = new DailyShiftSchedule();
        shift.setStartDateTime(LocalDateTime.of(2026, 4, 14, 8, 0));
        shift.setEndDateTime(LocalDateTime.of(2026, 4, 14, 17, 0));
        ReflectionTestUtils.setField(shift, "templateShiftSchedule", null);
        ReflectionTestUtils.setField(shift, "dayOff", false);
        ReflectionTestUtils.setField(shift, "dailyShiftId", 1L);
        ReflectionTestUtils.setField(shift, "templateShiftSchedule", templateShiftFor(employee));

        AttendanceLog inLog = attendanceLog(employee.getEmployeeId(), "IN", "2026-04-14T00:20:00Z");
        AttendanceLog outLog = attendanceLog(employee.getEmployeeId(), "OUT", "2026-04-14T09:00:00Z");

        when(employeeRepository.findById(employee.getEmployeeId())).thenReturn(Optional.of(employee));
        when(dailyShiftRepository.findAllByEmployeeIdsAndPeriod(eq(List.of(employee.getEmployeeId())), any(), any()))
                .thenReturn(List.of(shift));
        when(templateShiftRepository.findAllByEmployeeIdIn(List.of(employee.getEmployeeId()))).thenReturn(List.of());
        when(attendanceLogRepository.findByEmployeeIdInAndTimestampBetween(eq(List.of(employee.getEmployeeId())), any(), any(), any(Sort.class)))
                .thenReturn(List.of(inLog, outLog));
        when(leaveRequestRepository.findApprovedLeavesForPeriod(eq(List.of(employee.getEmployeeId())), any(), any()))
                .thenReturn(List.of());
        when(overtimeRequestRepository.findApprovedOvertimeForPeriod(eq(List.of(employee.getEmployeeId())), any(), any()))
                .thenReturn(List.of());
        when(payrollCalculator.findAttendanceSessionsForDate(List.of(inLog, outLog), workDate))
                .thenReturn(List.of(new PayrollCalculator.AttendanceSession(inLog, outLog)));

        String csv = new String(attendanceReportService.exportAttendanceReport(workDate, workDate, employee.getEmployeeId()), StandardCharsets.UTF_8);

        Assertions.assertTrue(csv.contains("Attendance Summary Report"));
        Assertions.assertTrue(csv.contains("\"People Operations Department\""));
        Assertions.assertTrue(csv.contains("\"2026-04-14\""));
        Assertions.assertTrue(csv.contains("\"ABSENT\",\"YES\",\"NO\",\"20\",\"0\""));
    }

    @Test
    void exportAttendanceReport_ShouldMarkPresentWhenScheduledWindowFullyCovered() {
        LocalDate workDate = LocalDate.of(2026, 4, 14);
        Employee employee = employee(19L, "Ana", "Reyes", Position.EMPLOYEE, EmploymentStatus.REGULAR);

        DailyShiftSchedule shift = new DailyShiftSchedule();
        shift.setStartDateTime(LocalDateTime.of(2026, 4, 14, 8, 0));
        shift.setEndDateTime(LocalDateTime.of(2026, 4, 14, 17, 0));
        ReflectionTestUtils.setField(shift, "templateShiftSchedule", templateShiftFor(employee));

        AttendanceLog inLog = attendanceLog(employee.getEmployeeId(), "IN", "2026-04-13T23:00:00Z"); // 07:00 Manila
        AttendanceLog outLog = attendanceLog(employee.getEmployeeId(), "OUT", "2026-04-14T09:00:00Z"); // 17:00 Manila

        when(employeeRepository.findById(employee.getEmployeeId())).thenReturn(Optional.of(employee));
        when(dailyShiftRepository.findAllByEmployeeIdsAndPeriod(eq(List.of(employee.getEmployeeId())), any(), any()))
                .thenReturn(List.of(shift));
        when(templateShiftRepository.findAllByEmployeeIdIn(List.of(employee.getEmployeeId()))).thenReturn(List.of());
        when(attendanceLogRepository.findByEmployeeIdInAndTimestampBetween(eq(List.of(employee.getEmployeeId())), any(), any(), any(Sort.class)))
                .thenReturn(List.of(inLog, outLog));
        when(leaveRequestRepository.findApprovedLeavesForPeriod(eq(List.of(employee.getEmployeeId())), any(), any()))
                .thenReturn(List.of());
        when(overtimeRequestRepository.findApprovedOvertimeForPeriod(eq(List.of(employee.getEmployeeId())), any(), any()))
                .thenReturn(List.of());
        when(payrollCalculator.findAttendanceSessionsForDate(List.of(inLog, outLog), workDate))
                .thenReturn(List.of(new PayrollCalculator.AttendanceSession(inLog, outLog)));

        String csv = new String(attendanceReportService.exportAttendanceReport(workDate, workDate, employee.getEmployeeId()), StandardCharsets.UTF_8);

        Assertions.assertTrue(csv.contains("\"2026-04-14\""));
        Assertions.assertTrue(csv.contains("\"PRESENT\",\"NO\",\"NO\",\"0\",\"0\""));
    }

    @Test
    void exportAttendanceReport_ShouldMarkIncompleteWhenNoCompleteSessionExists() {
        LocalDate workDate = LocalDate.of(2026, 4, 15);
        Employee employee = employee(19L, "Ana", "Reyes", Position.EMPLOYEE, EmploymentStatus.REGULAR);

        DailyShiftSchedule shift = new DailyShiftSchedule();
        shift.setStartDateTime(LocalDateTime.of(2026, 4, 15, 8, 0));
        shift.setEndDateTime(LocalDateTime.of(2026, 4, 15, 17, 0));
        ReflectionTestUtils.setField(shift, "templateShiftSchedule", templateShiftFor(employee));

        AttendanceLog inOnly = attendanceLog(employee.getEmployeeId(), "IN", "2026-04-15T00:00:00Z"); // 08:00 Manila

        when(employeeRepository.findById(employee.getEmployeeId())).thenReturn(Optional.of(employee));
        when(dailyShiftRepository.findAllByEmployeeIdsAndPeriod(eq(List.of(employee.getEmployeeId())), any(), any()))
                .thenReturn(List.of(shift));
        when(templateShiftRepository.findAllByEmployeeIdIn(List.of(employee.getEmployeeId()))).thenReturn(List.of());
        when(attendanceLogRepository.findByEmployeeIdInAndTimestampBetween(eq(List.of(employee.getEmployeeId())), any(), any(), any(Sort.class)))
                .thenReturn(List.of(inOnly));
        when(leaveRequestRepository.findApprovedLeavesForPeriod(eq(List.of(employee.getEmployeeId())), any(), any()))
                .thenReturn(List.of());
        when(overtimeRequestRepository.findApprovedOvertimeForPeriod(eq(List.of(employee.getEmployeeId())), any(), any()))
                .thenReturn(List.of());
        when(payrollCalculator.findAttendanceSessionsForDate(List.of(inOnly), workDate))
                .thenReturn(List.of());

        String csv = new String(attendanceReportService.exportAttendanceReport(workDate, workDate, employee.getEmployeeId()), StandardCharsets.UTF_8);

        Assertions.assertTrue(csv.contains("\"2026-04-15\""));
        Assertions.assertTrue(csv.contains("\"INCOMPLETE\",\"NO\",\"NO\",\"0\",\"0\""));
    }

    @Test
    void exportAttendanceReport_ShouldMarkOutsideScheduleWhenLogsAreFullyOutsideShiftWithoutApprovedOvertime() {
        LocalDate workDate = LocalDate.of(2026, 4, 18);
        Employee employee = employee(21L, "Marco", "Tan", Position.EMPLOYEE, EmploymentStatus.REGULAR);

        DailyShiftSchedule shift = new DailyShiftSchedule();
        shift.setStartDateTime(LocalDateTime.of(2026, 4, 18, 8, 0));
        shift.setEndDateTime(LocalDateTime.of(2026, 4, 18, 17, 0));
        ReflectionTestUtils.setField(shift, "templateShiftSchedule", templateShiftFor(employee));

        AttendanceLog inLog = attendanceLog(employee.getEmployeeId(), "IN", "2026-04-18T10:00:00Z");
        AttendanceLog outLog = attendanceLog(employee.getEmployeeId(), "OUT", "2026-04-18T12:00:00Z");

        when(employeeRepository.findById(employee.getEmployeeId())).thenReturn(Optional.of(employee));
        when(dailyShiftRepository.findAllByEmployeeIdsAndPeriod(eq(List.of(employee.getEmployeeId())), any(), any()))
                .thenReturn(List.of(shift));
        when(templateShiftRepository.findAllByEmployeeIdIn(List.of(employee.getEmployeeId()))).thenReturn(List.of());
        when(attendanceLogRepository.findByEmployeeIdInAndTimestampBetween(eq(List.of(employee.getEmployeeId())), any(), any(), any(Sort.class)))
                .thenReturn(List.of(inLog, outLog));
        when(leaveRequestRepository.findApprovedLeavesForPeriod(eq(List.of(employee.getEmployeeId())), any(), any()))
                .thenReturn(List.of());
        when(overtimeRequestRepository.findApprovedOvertimeForPeriod(eq(List.of(employee.getEmployeeId())), any(), any()))
                .thenReturn(List.of());
        when(payrollCalculator.findAttendanceSessionsForDate(List.of(inLog, outLog), workDate))
                .thenReturn(List.of(new PayrollCalculator.AttendanceSession(inLog, outLog)));

        String csv = new String(attendanceReportService.exportAttendanceReport(workDate, workDate, employee.getEmployeeId()), StandardCharsets.UTF_8);

        Assertions.assertTrue(csv.contains("Outside Schedule Days"));
        Assertions.assertTrue(csv.contains("\"2026-04-18\""));
        Assertions.assertTrue(csv.contains("\"08:00\",\"17:00\",\"OUTSIDE_SCHEDULE\",\"NO\",\"NO\",\"0\",\"0\""));
        Assertions.assertTrue(csv.contains("\"1\",\"0\",\"0\",\"0\",\"0\",\"0\",\"1\",\"0\",\"0\""));
    }

    @Test
    void exportAttendanceReport_ShouldNotMarkOutsideScheduleWhenApprovedOvertimeExistsForThatDate() {
        LocalDate workDate = LocalDate.of(2026, 4, 18);
        Employee employee = employee(21L, "Marco", "Tan", Position.EMPLOYEE, EmploymentStatus.REGULAR);

        DailyShiftSchedule shift = new DailyShiftSchedule();
        shift.setStartDateTime(LocalDateTime.of(2026, 4, 18, 8, 0));
        shift.setEndDateTime(LocalDateTime.of(2026, 4, 18, 17, 0));
        ReflectionTestUtils.setField(shift, "templateShiftSchedule", templateShiftFor(employee));

        AttendanceLog inLog = attendanceLog(employee.getEmployeeId(), "IN", "2026-04-18T10:00:00Z");
        AttendanceLog outLog = attendanceLog(employee.getEmployeeId(), "OUT", "2026-04-18T12:00:00Z");
        OvertimeRequest overtimeRequest = approvedOvertime(employee, workDate,
                LocalDateTime.of(2026, 4, 18, 18, 0),
                LocalDateTime.of(2026, 4, 18, 20, 0));

        when(employeeRepository.findById(employee.getEmployeeId())).thenReturn(Optional.of(employee));
        when(dailyShiftRepository.findAllByEmployeeIdsAndPeriod(eq(List.of(employee.getEmployeeId())), any(), any()))
                .thenReturn(List.of(shift));
        when(templateShiftRepository.findAllByEmployeeIdIn(List.of(employee.getEmployeeId()))).thenReturn(List.of());
        when(attendanceLogRepository.findByEmployeeIdInAndTimestampBetween(eq(List.of(employee.getEmployeeId())), any(), any(), any(Sort.class)))
                .thenReturn(List.of(inLog, outLog));
        when(leaveRequestRepository.findApprovedLeavesForPeriod(eq(List.of(employee.getEmployeeId())), any(), any()))
                .thenReturn(List.of());
        when(overtimeRequestRepository.findApprovedOvertimeForPeriod(eq(List.of(employee.getEmployeeId())), any(), any()))
                .thenReturn(List.of(overtimeRequest));
        when(payrollCalculator.findAttendanceSessionsForDate(List.of(inLog, outLog), workDate))
                .thenReturn(List.of(new PayrollCalculator.AttendanceSession(inLog, outLog)));

        String csv = new String(attendanceReportService.exportAttendanceReport(workDate, workDate, employee.getEmployeeId()), StandardCharsets.UTF_8);

        Assertions.assertTrue(csv.contains("\"2026-04-18\""));
        Assertions.assertTrue(csv.contains("\"08:00\",\"17:00\",\"ABSENT\",\"NO\",\"NO\",\"0\",\"0\""));
        Assertions.assertTrue(csv.contains("\"1\",\"0\",\"0\",\"0\",\"0\",\"1\",\"0\",\"0\",\"0\""));
    }

    @Test
    void exportAttendanceReport_ShouldSkipApprovedLeaveDays() {
        LocalDate workDate = LocalDate.of(2026, 4, 16);
        Employee employee = employee(20L, "Luis", "Lim", Position.EMPLOYEE, EmploymentStatus.REGULAR);

        DailyShiftSchedule shift = new DailyShiftSchedule();
        shift.setStartDateTime(LocalDateTime.of(2026, 4, 16, 8, 0));
        shift.setEndDateTime(LocalDateTime.of(2026, 4, 16, 17, 0));
        ReflectionTestUtils.setField(shift, "templateShiftSchedule", templateShiftFor(employee));

        LeaveRequest leave = new LeaveRequest();
        leave.setEmployee(employee);
        leave.setStartDateTime(LocalDateTime.of(2026, 4, 16, 0, 0));
        leave.setEndDateTime(LocalDateTime.of(2026, 4, 17, 0, 0));

        when(employeeRepository.findById(employee.getEmployeeId())).thenReturn(Optional.of(employee));
        when(dailyShiftRepository.findAllByEmployeeIdsAndPeriod(eq(List.of(employee.getEmployeeId())), any(), any()))
                .thenReturn(List.of(shift));
        when(templateShiftRepository.findAllByEmployeeIdIn(List.of(employee.getEmployeeId()))).thenReturn(List.of());
        when(attendanceLogRepository.findByEmployeeIdInAndTimestampBetween(eq(List.of(employee.getEmployeeId())), any(), any(), any(Sort.class)))
                .thenReturn(List.of());
        when(leaveRequestRepository.findApprovedLeavesForPeriod(eq(List.of(employee.getEmployeeId())), any(), any()))
                .thenReturn(List.of(leave));
        when(overtimeRequestRepository.findApprovedOvertimeForPeriod(eq(List.of(employee.getEmployeeId())), any(), any()))
                .thenReturn(List.of());

        String csv = new String(attendanceReportService.exportAttendanceReport(workDate, workDate, employee.getEmployeeId()), StandardCharsets.UTF_8);

        Assertions.assertTrue(csv.contains("Attendance Summary Report"));
        Assertions.assertTrue(csv.contains("Employee ID,Employee Name,Date,Time In,Time Out,Department,Position,Scheduled Start,Scheduled End,Status,Late,Early Out,Late Minutes,Early Out Minutes"));
        Assertions.assertFalse(csv.contains("\"" + employee.getEmployeeId() + "\""));
    }

    private Employee employee(Long id, String firstName, String lastName, Position position, EmploymentStatus status) {
        Employee employee = new Employee();
        ReflectionTestUtils.setField(employee, "employeeId", id);
        employee.setFirstName(firstName);
        employee.setLastName(lastName);
        employee.setPosition(position);
        employee.setEmploymentStatus(status);
        employee.setEmploymentDateStart(LocalDate.of(2025, 1, 1));
        employee.setCompanyEmail(firstName.toLowerCase() + "@test.com");
        employee.setPhoneNumber("09171234567");
        return employee;
    }

    private org.example.maridone.schedule.shift.TemplateShiftSchedule templateShiftFor(Employee employee) {
        org.example.maridone.schedule.shift.TemplateShiftSchedule shift = new org.example.maridone.schedule.shift.TemplateShiftSchedule();
        shift.setEmployee(employee);
        shift.setStartTime(java.time.LocalTime.of(8, 0));
        shift.setEndTime(java.time.LocalTime.of(17, 0));
        shift.setDayOfWeek(java.time.DayOfWeek.TUESDAY);
        return shift;
    }

    private OvertimeRequest approvedOvertime(Employee employee, LocalDate workDate, LocalDateTime start, LocalDateTime end) {
        OvertimeRequest request = new OvertimeRequest();
        request.setEmployee(employee);
        request.setWorkDate(workDate);
        request.setStartTime(start);
        request.setEndTime(end);
        return request;
    }

    private AttendanceLog attendanceLog(Long employeeId, String direction, String timestamp) {
        AttendanceLog log = new AttendanceLog();
        log.setEmployeeId(employeeId);
        log.setDirection(direction);
        log.setTimestamp(Instant.parse(timestamp));
        return log;
    }
}
