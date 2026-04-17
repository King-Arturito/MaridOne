package org.example.maridone.log.timecard;

import org.example.maridone.config.DefaultConfig;
import org.example.maridone.core.employee.Employee;
import org.example.maridone.core.employee.EmployeeRepository;
import org.example.maridone.enums.EmploymentStatus;
import org.example.maridone.enums.ExemptionStatus;
import org.example.maridone.enums.ManagerTitle;
import org.example.maridone.enums.Position;
import org.example.maridone.log.AttendanceLogRepository;
import org.example.maridone.log.attendance.AttendanceLog;
import org.example.maridone.overtime.OvertimeRequestRepository;
import org.example.maridone.payroll.PayrollCalculator;
import org.example.maridone.schedule.shift.DailyShiftRepository;
import org.example.maridone.schedule.shift.TemplateShiftRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TimecardServiceTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private AttendanceLogRepository attendanceLogRepository;

    @Mock
    private DailyShiftRepository dailyShiftRepository;

    @Mock
    private TemplateShiftRepository templateShiftRepository;

    @Mock
    private OvertimeRequestRepository overtimeRequestRepository;

    @Mock
    private PayrollCalculator payrollCalculator;

    private TimecardService timecardService;

    @BeforeEach
    void setUp() {
        DefaultConfig defaultConfig = new DefaultConfig();
        defaultConfig.setTimeZone(ZoneId.of("Asia/Manila"));
        timecardService = new TimecardService(
                employeeRepository,
                attendanceLogRepository,
                dailyShiftRepository,
                templateShiftRepository,
                overtimeRequestRepository,
                defaultConfig,
                createTemplateEngine(),
                payrollCalculator
        );
    }

    @Test
    void generateTimecardPdf_shouldRenderPdfForCompletedAttendance() {
        LocalDate workDate = LocalDate.of(2026, 4, 14);
        Employee manager = employee(7L, "Mina", "Santos", Position.MANAGER);
        manager.setEmpTitle(ManagerTitle.HEAD_OF_PEOPLE_OPERATIONS);

        Employee employee = employee(18L, "Juan", "Dela Cruz", Position.EMPLOYEE);
        employee.setManager(manager);

        AttendanceLog inLog = attendanceLog(employee.getEmployeeId(), "IN", "2026-04-14T00:00:00Z");
        AttendanceLog outLog = attendanceLog(employee.getEmployeeId(), "OUT", "2026-04-14T09:00:00Z");
        PayrollCalculator.AttendanceSession session = new PayrollCalculator.AttendanceSession(inLog, outLog);

        stubCommonDependencies(employee, workDate, workDate, List.of(inLog, outLog));
        when(payrollCalculator.findAttendanceSessionsForDate(anyList(), eq(workDate))).thenReturn(List.of(session));
        when(payrollCalculator.calculateHoursFromAttendanceSessions(List.of(session))).thenReturn(new BigDecimal("9.00"));
        when(payrollCalculator.deductUnpaidLunchHour(new BigDecimal("9.00"))).thenReturn(new BigDecimal("8.00"));

        TimecardService.GeneratedTimecardPdf pdf = timecardService.generateTimecardPdf(employee.getEmployeeId(), workDate, workDate);

        Assertions.assertEquals("Timecard_Juan_Dela_Cruz_2026-04-14_to_2026-04-14.pdf", pdf.fileName());
        Assertions.assertTrue(pdf.content().length > 0);
        Assertions.assertTrue(new String(pdf.content(), 0, 4, StandardCharsets.US_ASCII).startsWith("%PDF"));
    }

    @Test
    void generateTimecardPdf_shouldRenderPdfWhenMetadataAndAttendanceAreSparse() {
        LocalDate from = LocalDate.of(2026, 4, 11);
        LocalDate to = LocalDate.of(2026, 4, 25);
        Employee employee = employee(22L, "Robert", "Brown", null);
        employee.setManager(null);
        employee.setEmpTitle(null);

        stubCommonDependencies(employee, from, to, List.of());
        when(payrollCalculator.findAttendanceSessionsForDate(anyList(), any(LocalDate.class))).thenReturn(null);
        when(payrollCalculator.calculateHoursFromAttendanceSessions(anyList())).thenReturn(BigDecimal.ZERO);
        when(payrollCalculator.deductUnpaidLunchHour(BigDecimal.ZERO)).thenReturn(BigDecimal.ZERO);

        TimecardService.GeneratedTimecardPdf pdf = timecardService.generateTimecardPdf(employee.getEmployeeId(), from, to);

        Assertions.assertEquals("Timecard_Robert_Brown_2026-04-11_to_2026-04-25.pdf", pdf.fileName());
        Assertions.assertTrue(pdf.content().length > 0);
        Assertions.assertTrue(new String(pdf.content(), 0, 4, StandardCharsets.US_ASCII).startsWith("%PDF"));
    }

    private void stubCommonDependencies(Employee employee, LocalDate from, LocalDate to, List<AttendanceLog> logs) {
        when(employeeRepository.findById(employee.getEmployeeId())).thenReturn(Optional.of(employee));
        when(attendanceLogRepository.findByEmployeeIdAndTimestampBetween(eq(employee.getEmployeeId()), any(Instant.class), any(Instant.class), any(Sort.class)))
                .thenReturn(logs);
        when(dailyShiftRepository.findAllByEmployeeIdAndPeriod(eq(employee.getEmployeeId()), any(), any()))
                .thenReturn(List.of());
        when(templateShiftRepository.findAllByEmployeeId(employee.getEmployeeId())).thenReturn(List.of());
        when(overtimeRequestRepository.findApprovedOvertimeForPeriod(eq(List.of(employee.getEmployeeId())), eq(from), eq(to)))
                .thenReturn(List.of());
    }

    private SpringTemplateEngine createTemplateEngine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(false);

        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }

    private Employee employee(Long id, String firstName, String lastName, Position position) {
        Employee employee = new Employee();
        employee.setFirstName(firstName);
        employee.setLastName(lastName);
        employee.setPosition(position);
        employee.setEmploymentStatus(EmploymentStatus.REGULAR);
        employee.setExemptionStatus(ExemptionStatus.NON_EXEMPT);
        org.springframework.test.util.ReflectionTestUtils.setField(employee, "employeeId", id);
        return employee;
    }

    private AttendanceLog attendanceLog(Long employeeId, String direction, String timestamp) {
        AttendanceLog log = new AttendanceLog();
        log.setEmployeeId(employeeId);
        log.setDirection(direction);
        log.setTimestamp(Instant.parse(timestamp));
        return log;
    }
}
