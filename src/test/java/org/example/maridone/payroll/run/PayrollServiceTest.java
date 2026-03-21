package org.example.maridone.payroll.run;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.example.maridone.config.DefaultConfig;
import org.example.maridone.config.PayrollConfig;
import org.example.maridone.core.employee.Employee;
import org.example.maridone.core.employee.EmployeeRepository;
import org.example.maridone.enums.DeductionType;
import org.example.maridone.enums.ExemptionStatus;
import org.example.maridone.enums.Status;
import org.example.maridone.holiday.HolidayLookup;
import org.example.maridone.holiday.HolidayService;
import org.example.maridone.leave.request.LeaveRequest;
import org.example.maridone.leave.request.LeaveRequestRepository;
import org.example.maridone.log.AttendanceLogRepository;
import org.example.maridone.log.attendance.AttendanceLog;
import org.example.maridone.overtime.OvertimeRequestRepository;
import org.example.maridone.payroll.BracketService;
import org.example.maridone.payroll.PayrollCalculator;
import org.example.maridone.payroll.PayrollService;
import org.example.maridone.payroll.item.PayrollItem;
import org.example.maridone.payroll.item.component.DeductionsRepository;
import org.example.maridone.payroll.item.component.EarningsRepository;
import org.example.maridone.payroll.mapper.PayrollMapper;
import org.example.maridone.schedule.shift.DailyShiftRepository;
import org.example.maridone.schedule.shift.DailyShiftSchedule;
import org.example.maridone.schedule.shift.TemplateShiftSchedule;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayrollServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Manila");

    @Mock private PayrollRunRepository payrollRunRepository;
    @Mock private PayrollItemRepository payrollItemRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private AttendanceLogRepository attendanceLogRepository;
    @Mock private DailyShiftRepository dailyShiftRepository;
    @Mock private OvertimeRequestRepository overtimeRequestRepository;
    @Mock private LeaveRequestRepository leaveRequestRepository;
    @Mock private HolidayService holidayService;
    @Mock private DefaultConfig defaultConfig;
    @Mock private EarningsRepository earningsRepository;
    @Mock private DeductionsRepository deductionsRepository;
    @Mock private PayrollMapper payrollMapper;

    private PayrollService payrollService;

    @BeforeEach
    void setUp() {
        PayrollConfig payrollConfig = new PayrollConfig();
        BracketService bracketService = new BracketService(payrollConfig);
        PayrollCalculator payrollCalculator = new PayrollCalculator(
                earningsRepository,
                deductionsRepository,
                bracketService,
                defaultConfig,
                payrollConfig
        );

        payrollService = new PayrollService(
                payrollRunRepository,
                payrollItemRepository,
                employeeRepository,
                attendanceLogRepository,
                dailyShiftRepository,
                overtimeRequestRepository,
                leaveRequestRepository,
                holidayService,
                payrollCalculator,
                payrollMapper,
                defaultConfig,
                payrollConfig,
                bracketService
        );
    }

    @Test
    void processPayrollNonExempt_NoLogs_ShouldCreateAbsentDeduction() {
        LocalDate workDate = LocalDate.of(2026, 3, 2);
        PayrollRun run = buildSingleDayRun(workDate);
        Employee employee = buildEmployee(1L, ExemptionStatus.NON_EXEMPT, "624000.00");

        when(defaultConfig.getTimeZone()).thenReturn(ZONE);
        when(attendanceLogRepository.findByEmployeeIdInAndTimestampBetween(anyList(), any(Instant.class), any(Instant.class), any()))
                .thenReturn(List.of());
        when(overtimeRequestRepository.findAll(any(Specification.class)))
                .thenReturn(List.of());
        when(dailyShiftRepository.findAllByEmployeeIdsAndPeriod(anyList(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(buildDailyShift(employee, workDate, LocalTime.of(8, 0), LocalTime.of(17, 0))));
        when(leaveRequestRepository.findApprovedLeavesForPeriod(anyList(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(holidayService.getHolidayLookup(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(HolidayLookup.empty());

        List<PayrollItem> items = new ArrayList<>();
        payrollService.processPayrollNonExempt(run, items, List.of(employee));

        Assertions.assertEquals(1, items.size());
        Assertions.assertTrue(items.get(0).getDeductions().stream()
                .anyMatch(line -> line.getDeductionType() == DeductionType.ABSENT_DEDUCTION));
    }

    @Test
    void processPayrollNonExempt_SplitAttendanceAcrossSplitSchedules_ShouldNotCreateAttendanceDeductions() {
        LocalDate workDate = LocalDate.of(2026, 3, 21);
        PayrollRun run = buildSingleDayRun(workDate);
        Employee employee = buildEmployee(1L, ExemptionStatus.NON_EXEMPT, "624000.00");
        DailyShiftSchedule morningShift = buildDailyShift(employee, workDate, LocalTime.of(8, 0), LocalTime.of(11, 0));
        DailyShiftSchedule afternoonShift = buildDailyShift(employee, workDate, LocalTime.of(13, 0), LocalTime.of(17, 0));
        LeaveRequest middleLeave = buildLeave(employee, workDate.atTime(11, 0), workDate.atTime(13, 0));

        List<AttendanceLog> attendanceLogs = List.of(
                buildAttendanceLog(employee, workDate, LocalTime.of(8, 0), "IN"),
                buildAttendanceLog(employee, workDate, LocalTime.of(11, 0), "OUT"),
                buildAttendanceLog(employee, workDate, LocalTime.of(13, 0), "IN"),
                buildAttendanceLog(employee, workDate, LocalTime.of(17, 0), "OUT")
        );

        when(defaultConfig.getTimeZone()).thenReturn(ZONE);
        when(attendanceLogRepository.findByEmployeeIdInAndTimestampBetween(anyList(), any(Instant.class), any(Instant.class), any()))
                .thenReturn(attendanceLogs);
        when(overtimeRequestRepository.findAll(any(Specification.class)))
                .thenReturn(List.of());
        when(dailyShiftRepository.findAllByEmployeeIdsAndPeriod(anyList(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(morningShift, afternoonShift));
        when(leaveRequestRepository.findApprovedLeavesForPeriod(anyList(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(middleLeave));
        when(holidayService.getHolidayLookup(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(HolidayLookup.empty());

        List<PayrollItem> items = new ArrayList<>();
        payrollService.processPayrollNonExempt(run, items, List.of(employee));

        Assertions.assertEquals(1, items.size());
        Assertions.assertTrue(items.get(0).getDeductions().stream()
                .noneMatch(line -> line.getDeductionType() == DeductionType.ABSENT_DEDUCTION));
        Assertions.assertTrue(items.get(0).getDeductions().stream()
                .noneMatch(line -> line.getDeductionType() == DeductionType.LATE_PENALTY));
    }

    @Test
    void processPayrollExempt_NoLogs_ShouldCreateAbsentDeductionWithoutLatePenalty() {
        LocalDate workDate = LocalDate.of(2026, 3, 2);
        PayrollRun run = buildSingleDayRun(workDate);
        Employee employee = buildEmployee(2L, ExemptionStatus.EXEMPT, "624000.00");

        when(defaultConfig.getTimeZone()).thenReturn(ZONE);
        when(attendanceLogRepository.findByEmployeeIdInAndTimestampBetween(anyList(), any(Instant.class), any(Instant.class), any()))
                .thenReturn(List.of());
        when(dailyShiftRepository.findAllByEmployeeIdsAndPeriod(anyList(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(buildDailyShift(employee, workDate, LocalTime.of(8, 0), LocalTime.of(17, 0))));
        when(leaveRequestRepository.findApprovedLeavesForPeriod(anyList(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(holidayService.getHolidayLookup(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(HolidayLookup.empty());

        List<PayrollItem> items = payrollService.processPayrollExempt(run, List.of(employee));

        Assertions.assertEquals(1, items.size());
        Assertions.assertTrue(items.get(0).getDeductions().stream()
                .anyMatch(line -> line.getDeductionType() == DeductionType.ABSENT_DEDUCTION));
        Assertions.assertTrue(items.get(0).getDeductions().stream()
                .noneMatch(line -> line.getDeductionType() == DeductionType.LATE_PENALTY));
    }

    @Test
    void processPayrollExempt_NonOverlappingLeave_ShouldStillCreateAbsentDeduction() {
        LocalDate workDate = LocalDate.of(2026, 3, 21);
        PayrollRun run = buildSingleDayRun(workDate);
        Employee employee = buildEmployee(8L, ExemptionStatus.EXEMPT, "624000.00");
        DailyShiftSchedule dailyShift = buildDailyShift(employee, workDate, LocalTime.of(8, 0), LocalTime.of(17, 0));
        LeaveRequest lateNightLeave = buildLeave(employee, workDate.atTime(23, 30), workDate.plusDays(1).atTime(0, 30));

        when(defaultConfig.getTimeZone()).thenReturn(ZONE);
        when(attendanceLogRepository.findByEmployeeIdInAndTimestampBetween(anyList(), any(Instant.class), any(Instant.class), any()))
                .thenReturn(List.of());
        when(dailyShiftRepository.findAllByEmployeeIdsAndPeriod(anyList(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(dailyShift));
        when(leaveRequestRepository.findApprovedLeavesForPeriod(anyList(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(lateNightLeave));
        when(holidayService.getHolidayLookup(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(HolidayLookup.empty());

        List<PayrollItem> items = payrollService.processPayrollExempt(run, List.of(employee));

        Assertions.assertEquals(1, items.size());
        Assertions.assertTrue(items.get(0).getDeductions().stream()
                .anyMatch(line -> line.getDeductionType() == DeductionType.ABSENT_DEDUCTION));
    }

    @Test
    void processPayrollExempt_OverlappingLeave_ShouldSuppressAbsentDeduction() {
        LocalDate workDate = LocalDate.of(2026, 3, 21);
        PayrollRun run = buildSingleDayRun(workDate);
        Employee employee = buildEmployee(8L, ExemptionStatus.EXEMPT, "624000.00");
        DailyShiftSchedule dailyShift = buildDailyShift(employee, workDate, LocalTime.of(8, 0), LocalTime.of(17, 0));
        LeaveRequest fullDayLeave = buildLeave(employee, workDate.atTime(8, 0), workDate.atTime(17, 0));

        when(defaultConfig.getTimeZone()).thenReturn(ZONE);
        when(attendanceLogRepository.findByEmployeeIdInAndTimestampBetween(anyList(), any(Instant.class), any(Instant.class), any()))
                .thenReturn(List.of());
        when(dailyShiftRepository.findAllByEmployeeIdsAndPeriod(anyList(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(dailyShift));
        when(leaveRequestRepository.findApprovedLeavesForPeriod(anyList(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(fullDayLeave));
        when(holidayService.getHolidayLookup(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(HolidayLookup.empty());

        List<PayrollItem> items = payrollService.processPayrollExempt(run, List.of(employee));

        Assertions.assertEquals(1, items.size());
        Assertions.assertTrue(items.get(0).getDeductions().stream()
                .noneMatch(line -> line.getDeductionType() == DeductionType.ABSENT_DEDUCTION));
    }

    private PayrollRun buildSingleDayRun(LocalDate date) {
        PayrollRun run = new PayrollRun();
        run.setPeriodStart(date);
        run.setPeriodEnd(date);
        return run;
    }

    private Employee buildEmployee(Long employeeId, ExemptionStatus exemptionStatus, String yearlySalary) {
        Employee employee = new Employee();
        ReflectionTestUtils.setField(employee, "employeeId", employeeId);
        employee.setExemptionStatus(exemptionStatus);
        employee.setYearlySalary(new BigDecimal(yearlySalary));
        return employee;
    }

    private DailyShiftSchedule buildDailyShift(Employee employee, LocalDate workDate, LocalTime startTime, LocalTime endTime) {
        TemplateShiftSchedule templateShiftSchedule = new TemplateShiftSchedule();
        templateShiftSchedule.setEmployee(employee);
        templateShiftSchedule.setDayOfWeek(workDate.getDayOfWeek());
        templateShiftSchedule.setStartTime(startTime);
        templateShiftSchedule.setEndTime(endTime);
        ReflectionTestUtils.setField(templateShiftSchedule, "employeeId", employee.getEmployeeId());

        DailyShiftSchedule dailyShiftSchedule = new DailyShiftSchedule();
        dailyShiftSchedule.setTemplateShiftSchedule(templateShiftSchedule);
        dailyShiftSchedule.setStartDateTime(LocalDateTime.of(workDate, startTime));
        dailyShiftSchedule.setEndDateTime(
                LocalDateTime.of(workDate.plusDays(endTime.isBefore(startTime) ? 1 : 0), endTime)
        );
        return dailyShiftSchedule;
    }

    private LeaveRequest buildLeave(Employee employee, LocalDateTime startDateTime, LocalDateTime endDateTime) {
        LeaveRequest leaveRequest = new LeaveRequest();
        leaveRequest.setEmployee(employee);
        leaveRequest.setStartDateTime(startDateTime);
        leaveRequest.setEndDateTime(endDateTime);
        leaveRequest.setRequestStatus(Status.APPROVED);
        leaveRequest.setReason("Test leave");
        return leaveRequest;
    }

    private AttendanceLog buildAttendanceLog(Employee employee, LocalDate workDate, LocalTime time, String direction) {
        AttendanceLog attendanceLog = new AttendanceLog();
        attendanceLog.setEmployeeId(employee.getEmployeeId());
        attendanceLog.setTimestamp(workDate.atTime(time).atZone(ZONE).toInstant());
        attendanceLog.setDirection(direction);
        return attendanceLog;
    }
}
