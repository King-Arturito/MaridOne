package org.example.maridone.payroll.run;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.example.maridone.config.DefaultConfig;
import org.example.maridone.config.PayrollConfig;
import org.example.maridone.core.employee.Employee;
import org.example.maridone.core.employee.EmployeeRepository;
import org.example.maridone.enums.DeductionType;
import org.example.maridone.enums.EarningsType;
import org.example.maridone.enums.EmployerContributionType;
import org.example.maridone.enums.ExemptionStatus;
import org.example.maridone.enums.PayrollStatus;
import org.example.maridone.enums.Position;
import org.example.maridone.enums.RunType;
import org.example.maridone.enums.Status;
import org.example.maridone.exception.badrequest.InvalidActionException;
import org.example.maridone.holiday.HolidayLookup;
import org.example.maridone.holiday.HolidayService;
import org.example.maridone.leave.request.LeaveRequest;
import org.example.maridone.leave.request.LeaveRequestRepository;
import org.example.maridone.log.AttendanceLogRepository;
import org.example.maridone.log.attendance.AttendanceLog;
import org.example.maridone.overtime.OvertimeRequest;
import org.example.maridone.overtime.OvertimeRequestRepository;
import org.example.maridone.payroll.BracketService;
import org.example.maridone.payroll.PayrollCalculator;
import org.example.maridone.payroll.PayrollService;
import org.example.maridone.payroll.dto.PayrollRunViewDto;
import org.example.maridone.payroll.dto.RunCreateDto;
import org.example.maridone.payroll.item.PayrollItem;
import org.example.maridone.payroll.item.PayrollItemService;
import org.example.maridone.payroll.item.component.DeductionsLine;
import org.example.maridone.payroll.item.component.EarningsLine;
import org.example.maridone.payroll.item.component.EmployerContributionLine;
import org.example.maridone.schedule.shift.DailyShiftRepository;
import org.example.maridone.schedule.shift.DailyShiftSchedule;
import org.example.maridone.schedule.shift.TemplateShiftRepository;
import org.example.maridone.schedule.shift.TemplateShiftSchedule;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayrollServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Manila");

    @Mock private PayrollRunRepository payrollRunRepository;
    @Mock private PayrollItemRepository payrollItemRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private AttendanceLogRepository attendanceLogRepository;
    @Mock private DailyShiftRepository dailyShiftRepository;
    @Mock private TemplateShiftRepository templateShiftRepository;
    @Mock private OvertimeRequestRepository overtimeRequestRepository;
    @Mock private LeaveRequestRepository leaveRequestRepository;
    @Mock private HolidayService holidayService;
    @Mock private DefaultConfig defaultConfig;
    @Mock private PayrollItemService payrollItemService;

    private PayrollService payrollService;
    private PayrollCalculator payrollCalculator;

    @BeforeEach
    void setUp() {
        PayrollConfig payrollConfig = new PayrollConfig();
        BracketService bracketService = new BracketService(payrollConfig);
        payrollCalculator = new PayrollCalculator(
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
                templateShiftRepository,
                overtimeRequestRepository,
                leaveRequestRepository,
                holidayService,
                payrollCalculator,
                defaultConfig,
                payrollConfig,
                payrollItemService
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
        payrollService.processPayrollNonExempt(run, items, List.of(employee), Map.of());

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
        payrollService.processPayrollNonExempt(run, items, List.of(employee), Map.of());

        Assertions.assertEquals(1, items.size());
        Assertions.assertTrue(items.get(0).getDeductions().stream()
                .noneMatch(line -> line.getDeductionType() == DeductionType.ABSENT_DEDUCTION));
        Assertions.assertTrue(items.get(0).getDeductions().stream()
                .noneMatch(line -> line.getDeductionType() == DeductionType.LATE_PENALTY));
    }

    @Test
    void processPayrollNonExempt_BrokenShiftMissingOneHour_ShouldOnlyDeductOneHour() {
        LocalDate workDate = LocalDate.of(2026, 3, 25);
        PayrollRun run = buildSingleDayRun(workDate);
        Employee employee = buildEmployee(21L, ExemptionStatus.NON_EXEMPT, "624000.00");
        DailyShiftSchedule morningShift = buildDailyShift(employee, workDate, LocalTime.of(8, 0), LocalTime.of(12, 0));
        DailyShiftSchedule afternoonShift = buildDailyShift(employee, workDate, LocalTime.of(13, 0), LocalTime.of(17, 0));

        List<AttendanceLog> attendanceLogs = List.of(
                buildAttendanceLog(employee, workDate, LocalTime.of(8, 0), "IN"),
                buildAttendanceLog(employee, workDate, LocalTime.of(12, 0), "OUT"),
                buildAttendanceLog(employee, workDate, LocalTime.of(13, 0), "IN"),
                buildAttendanceLog(employee, workDate, LocalTime.of(16, 0), "OUT")
        );

        when(defaultConfig.getTimeZone()).thenReturn(ZONE);
        when(attendanceLogRepository.findByEmployeeIdInAndTimestampBetween(anyList(), any(Instant.class), any(Instant.class), any()))
                .thenReturn(attendanceLogs);
        when(overtimeRequestRepository.findAll(any(Specification.class)))
                .thenReturn(List.of());
        when(dailyShiftRepository.findAllByEmployeeIdsAndPeriod(anyList(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(morningShift, afternoonShift));
        when(leaveRequestRepository.findApprovedLeavesForPeriod(anyList(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(holidayService.getHolidayLookup(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(HolidayLookup.empty());

        List<PayrollItem> items = new ArrayList<>();
        payrollService.processPayrollNonExempt(run, items, List.of(employee), Map.of());

        Assertions.assertEquals(1, items.size());
        BigDecimal latePenaltyAmount = items.get(0).getDeductions().stream()
                .filter(line -> line.getDeductionType() == DeductionType.LATE_PENALTY)
                .map(line -> line.getAmount())
                .findFirst()
                .orElse(BigDecimal.ZERO);

        BigDecimal expectedOneHourDeduction = payrollCalculator.computeHourlyRate(employee.getYearlySalary())
                .setScale(2, java.math.RoundingMode.HALF_UP);
        assertBigDecimalEquals(expectedOneHourDeduction.toPlainString(), latePenaltyAmount);
        Assertions.assertTrue(items.get(0).getDeductions().stream()
                .noneMatch(line -> line.getDeductionType() == DeductionType.ABSENT_DEDUCTION));
    }

    @Test
    void processPayrollNonExempt_SmallLateArrivalShouldCreateLatePenalty() {
        LocalDate workDate = LocalDate.of(2026, 3, 3);
        PayrollRun run = buildSingleDayRun(workDate);
        Employee employee = buildEmployee(19L, ExemptionStatus.NON_EXEMPT, "624000.00");
        DailyShiftSchedule dailyShift = buildDailyShift(employee, workDate, LocalTime.of(8, 0), LocalTime.of(17, 0));
        List<AttendanceLog> attendanceLogs = List.of(
                buildAttendanceLog(employee, workDate, LocalTime.of(8, 10), "IN"),
                buildAttendanceLog(employee, workDate, LocalTime.of(17, 0), "OUT")
        );

        when(defaultConfig.getTimeZone()).thenReturn(ZONE);
        when(attendanceLogRepository.findByEmployeeIdInAndTimestampBetween(anyList(), any(Instant.class), any(Instant.class), any()))
                .thenReturn(attendanceLogs);
        when(overtimeRequestRepository.findAll(any(Specification.class)))
                .thenReturn(List.of());
        when(dailyShiftRepository.findAllByEmployeeIdsAndPeriod(anyList(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(dailyShift));
        when(leaveRequestRepository.findApprovedLeavesForPeriod(anyList(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(holidayService.getHolidayLookup(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(HolidayLookup.empty());

        List<PayrollItem> items = new ArrayList<>();
        payrollService.processPayrollNonExempt(run, items, List.of(employee), Map.of());

        Assertions.assertEquals(1, items.size());
        BigDecimal latePenaltyAmount = items.get(0).getDeductions().stream()
                .filter(line -> line.getDeductionType() == DeductionType.LATE_PENALTY)
                .map(line -> line.getAmount())
                .findFirst()
                .orElse(BigDecimal.ZERO);

        Assertions.assertTrue(latePenaltyAmount.compareTo(BigDecimal.ZERO) > 0);
        Assertions.assertTrue(items.get(0).getDeductions().stream()
                .noneMatch(line -> line.getDeductionType() == DeductionType.ABSENT_DEDUCTION));
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

        List<PayrollItem> items = payrollService.processPayrollExempt(run, List.of(employee), Map.of());

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

        List<PayrollItem> items = payrollService.processPayrollExempt(run, List.of(employee), Map.of());

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

        List<PayrollItem> items = payrollService.processPayrollExempt(run, List.of(employee), Map.of());

        Assertions.assertEquals(1, items.size());
        Assertions.assertTrue(items.get(0).getDeductions().stream()
                .noneMatch(line -> line.getDeductionType() == DeductionType.ABSENT_DEDUCTION));
    }

    @Test
    void processPayrollExempt_UnpaidOverlappingLeave_ShouldStillCreateAbsentDeduction() {
        LocalDate workDate = LocalDate.of(2026, 3, 21);
        PayrollRun run = buildSingleDayRun(workDate);
        Employee employee = buildEmployee(11L, ExemptionStatus.EXEMPT, "624000.00");
        DailyShiftSchedule dailyShift = buildDailyShift(employee, workDate, LocalTime.of(8, 0), LocalTime.of(17, 0));
        LeaveRequest unpaidLeave = buildLeave(employee, workDate.atTime(8, 0), workDate.atTime(17, 0), false);

        when(defaultConfig.getTimeZone()).thenReturn(ZONE);
        when(attendanceLogRepository.findByEmployeeIdInAndTimestampBetween(anyList(), any(Instant.class), any(Instant.class), any()))
                .thenReturn(List.of());
        when(dailyShiftRepository.findAllByEmployeeIdsAndPeriod(anyList(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(dailyShift));
        when(leaveRequestRepository.findApprovedLeavesForPeriod(anyList(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(unpaidLeave));
        when(holidayService.getHolidayLookup(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(HolidayLookup.empty());

        List<PayrollItem> items = payrollService.processPayrollExempt(run, List.of(employee), Map.of());

        Assertions.assertEquals(1, items.size());
        Assertions.assertTrue(items.get(0).getDeductions().stream()
                .anyMatch(line -> line.getDeductionType() == DeductionType.ABSENT_DEDUCTION));
    }

    @Test
    void processPayrollNonExempt_PartialPaidLeaveWithoutAttendance_ShouldOnlyDeductUncoveredHours() {
        LocalDate workDate = LocalDate.of(2026, 3, 26);
        PayrollRun run = buildSingleDayRun(workDate);
        Employee employee = buildEmployee(31L, ExemptionStatus.NON_EXEMPT, "624000.00");
        DailyShiftSchedule dailyShift = buildDailyShift(employee, workDate, LocalTime.of(8, 0), LocalTime.of(17, 0));
        LeaveRequest partialLeave = buildLeave(employee, workDate.atTime(8, 0), workDate.atTime(12, 0));

        when(defaultConfig.getTimeZone()).thenReturn(ZONE);
        when(attendanceLogRepository.findByEmployeeIdInAndTimestampBetween(anyList(), any(Instant.class), any(Instant.class), any()))
                .thenReturn(List.of());
        when(overtimeRequestRepository.findAll(any(Specification.class)))
                .thenReturn(List.of());
        when(dailyShiftRepository.findAllByEmployeeIdsAndPeriod(anyList(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(dailyShift));
        when(leaveRequestRepository.findApprovedLeavesForPeriod(anyList(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(partialLeave));
        when(holidayService.getHolidayLookup(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(HolidayLookup.empty());

        List<PayrollItem> items = new ArrayList<>();
        payrollService.processPayrollNonExempt(run, items, List.of(employee), Map.of());

        Assertions.assertEquals(1, items.size());
        BigDecimal latePenaltyAmount = items.get(0).getDeductions().stream()
                .filter(line -> line.getDeductionType() == DeductionType.LATE_PENALTY)
                .map(DeductionsLine::getAmount)
                .findFirst()
                .orElse(BigDecimal.ZERO);

        BigDecimal expectedFourHourDeduction = payrollCalculator.computeHourlyRate(employee.getYearlySalary())
                .multiply(new BigDecimal("4.00"))
                .setScale(2, java.math.RoundingMode.HALF_UP);
        assertBigDecimalEquals(expectedFourHourDeduction.toPlainString(), latePenaltyAmount);
        Assertions.assertTrue(items.get(0).getDeductions().stream()
                .noneMatch(line -> line.getDeductionType() == DeductionType.ABSENT_DEDUCTION));
    }

    @Test
    void processPayrollNonExempt_PartialPaidLeavePlusAttendance_ShouldNotCreateAttendanceDeductions() {
        LocalDate workDate = LocalDate.of(2026, 3, 27);
        PayrollRun run = buildSingleDayRun(workDate);
        Employee employee = buildEmployee(32L, ExemptionStatus.NON_EXEMPT, "624000.00");
        DailyShiftSchedule dailyShift = buildDailyShift(employee, workDate, LocalTime.of(8, 0), LocalTime.of(17, 0));
        LeaveRequest morningLeave = buildLeave(employee, workDate.atTime(8, 0), workDate.atTime(12, 0));
        List<AttendanceLog> attendanceLogs = List.of(
                buildAttendanceLog(employee, workDate, LocalTime.of(13, 0), "IN"),
                buildAttendanceLog(employee, workDate, LocalTime.of(17, 0), "OUT")
        );

        when(defaultConfig.getTimeZone()).thenReturn(ZONE);
        when(attendanceLogRepository.findByEmployeeIdInAndTimestampBetween(anyList(), any(Instant.class), any(Instant.class), any()))
                .thenReturn(attendanceLogs);
        when(overtimeRequestRepository.findAll(any(Specification.class)))
                .thenReturn(List.of());
        when(dailyShiftRepository.findAllByEmployeeIdsAndPeriod(anyList(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(dailyShift));
        when(leaveRequestRepository.findApprovedLeavesForPeriod(anyList(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(morningLeave));
        when(holidayService.getHolidayLookup(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(HolidayLookup.empty());

        List<PayrollItem> items = new ArrayList<>();
        payrollService.processPayrollNonExempt(run, items, List.of(employee), Map.of());

        Assertions.assertEquals(1, items.size());
        Assertions.assertTrue(items.get(0).getDeductions().stream()
                .noneMatch(line -> line.getDeductionType() == DeductionType.ABSENT_DEDUCTION));
        Assertions.assertTrue(items.get(0).getDeductions().stream()
                .noneMatch(line -> line.getDeductionType() == DeductionType.LATE_PENALTY));
    }

    @Test
    void processPayrollNonExempt_ApprovedOvertimeWithoutAttendanceOverlap_ShouldNotCreateOvertimeEarnings() {
        LocalDate workDate = LocalDate.of(2026, 3, 22);
        PayrollRun run = buildSingleDayRun(workDate);
        Employee employee = buildEmployee(9L, ExemptionStatus.NON_EXEMPT, "624000.00");
        DailyShiftSchedule dailyShift = buildDailyShift(employee, workDate, LocalTime.of(8, 0), LocalTime.of(17, 0));
        OvertimeRequest overtimeRequest = buildOvertimeRequest(
                employee,
                workDate,
                workDate.atTime(17, 0),
                workDate.atTime(19, 0)
        );
        List<AttendanceLog> attendanceLogs = List.of(
                buildAttendanceLog(employee, workDate, LocalTime.of(8, 0), "IN"),
                buildAttendanceLog(employee, workDate, LocalTime.of(17, 0), "OUT")
        );

        when(defaultConfig.getTimeZone()).thenReturn(ZONE);
        when(attendanceLogRepository.findByEmployeeIdInAndTimestampBetween(anyList(), any(Instant.class), any(Instant.class), any()))
                .thenReturn(attendanceLogs);
        when(overtimeRequestRepository.findAll(any(Specification.class)))
                .thenReturn(List.of(overtimeRequest));
        when(dailyShiftRepository.findAllByEmployeeIdsAndPeriod(anyList(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(dailyShift));
        when(leaveRequestRepository.findApprovedLeavesForPeriod(anyList(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(holidayService.getHolidayLookup(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(HolidayLookup.empty());

        List<PayrollItem> items = new ArrayList<>();
        payrollService.processPayrollNonExempt(run, items, List.of(employee), Map.of());

        Assertions.assertEquals(1, items.size());
        PayrollItem item = items.get(0);
        Assertions.assertTrue(item.getEarnings().stream().noneMatch(line -> Boolean.TRUE.equals(line.getOvertime())));
        assertBigDecimalEquals("26000.00", item.getGrossPay());
    }

    @Test
    void processPayrollNonExempt_UnpaidApprovedOvertimeShouldNotCreateOvertimeEarnings() {
        LocalDate workDate = LocalDate.of(2026, 3, 29);
        PayrollRun run = buildSingleDayRun(workDate);
        Employee employee = buildEmployee(33L, ExemptionStatus.NON_EXEMPT, "624000.00");
        DailyShiftSchedule dailyShift = buildDailyShift(employee, workDate, LocalTime.of(8, 0), LocalTime.of(17, 0));
        OvertimeRequest overtimeRequest = buildOvertimeRequest(
                employee,
                workDate,
                workDate.atTime(17, 0),
                workDate.atTime(19, 0),
                EarningsType.OVERTIME,
                false
        );
        List<AttendanceLog> attendanceLogs = List.of(
                buildAttendanceLog(employee, workDate, LocalTime.of(8, 0), "IN"),
                buildAttendanceLog(employee, workDate, LocalTime.of(19, 0), "OUT")
        );

        when(defaultConfig.getTimeZone()).thenReturn(ZONE);
        when(attendanceLogRepository.findByEmployeeIdInAndTimestampBetween(anyList(), any(Instant.class), any(Instant.class), any()))
                .thenReturn(attendanceLogs);
        when(overtimeRequestRepository.findAll(any(Specification.class)))
                .thenReturn(List.of(overtimeRequest));
        when(dailyShiftRepository.findAllByEmployeeIdsAndPeriod(anyList(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(dailyShift));
        when(leaveRequestRepository.findApprovedLeavesForPeriod(anyList(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(holidayService.getHolidayLookup(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(HolidayLookup.empty());

        List<PayrollItem> items = new ArrayList<>();
        payrollService.processPayrollNonExempt(run, items, List.of(employee), Map.of());

        Assertions.assertEquals(1, items.size());
        Assertions.assertTrue(items.get(0).getEarnings().stream()
                .noneMatch(line -> Boolean.TRUE.equals(line.getOvertime())));
    }

    @Test
    void processPayrollNonExempt_UnpaidLeaveShouldNotSuppressAbsentDeduction() {
        LocalDate workDate = LocalDate.of(2026, 3, 24);
        PayrollRun run = buildSingleDayRun(workDate);
        Employee employee = buildEmployee(12L, ExemptionStatus.NON_EXEMPT, "624000.00");
        DailyShiftSchedule dailyShift = buildDailyShift(employee, workDate, LocalTime.of(8, 0), LocalTime.of(17, 0));
        LeaveRequest unpaidLeave = buildLeave(employee, workDate.atTime(8, 0), workDate.atTime(17, 0), false);

        when(defaultConfig.getTimeZone()).thenReturn(ZONE);
        when(attendanceLogRepository.findByEmployeeIdInAndTimestampBetween(anyList(), any(Instant.class), any(Instant.class), any()))
                .thenReturn(List.of());
        when(overtimeRequestRepository.findAll(any(Specification.class)))
                .thenReturn(List.of());
        when(dailyShiftRepository.findAllByEmployeeIdsAndPeriod(anyList(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(dailyShift));
        when(leaveRequestRepository.findApprovedLeavesForPeriod(anyList(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(unpaidLeave));
        when(holidayService.getHolidayLookup(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(HolidayLookup.empty());

        List<PayrollItem> items = new ArrayList<>();
        payrollService.processPayrollNonExempt(run, items, List.of(employee), Map.of());

        Assertions.assertEquals(1, items.size());
        Assertions.assertTrue(items.get(0).getDeductions().stream()
                .anyMatch(line -> line.getDeductionType() == DeductionType.ABSENT_DEDUCTION));
    }

    @Test
    void processPayrollNonExempt_MidPeriodHireShouldProrateBasicPay() {
        LocalDate periodStart = LocalDate.of(2026, 4, 1);
        LocalDate periodEnd = LocalDate.of(2026, 4, 15);
        PayrollRun run = new PayrollRun();
        run.setPeriodStart(periodStart);
        run.setPeriodEnd(periodEnd);

        Employee employee = buildEmployee(17L, ExemptionStatus.NON_EXEMPT, "624000.00");
        employee.setEmploymentDateStart(LocalDate.of(2026, 4, 10));

        when(defaultConfig.getTimeZone()).thenReturn(ZONE);
        when(attendanceLogRepository.findByEmployeeIdInAndTimestampBetween(anyList(), any(Instant.class), any(Instant.class), any()))
                .thenReturn(List.of());
        when(overtimeRequestRepository.findAll(any(Specification.class))).thenReturn(List.of());
        when(dailyShiftRepository.findAllByEmployeeIdsAndPeriod(anyList(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(leaveRequestRepository.findApprovedLeavesForPeriod(anyList(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(holidayService.getHolidayLookup(periodStart, periodEnd)).thenReturn(HolidayLookup.empty());

        List<PayrollItem> items = new ArrayList<>();
        payrollService.processPayrollNonExempt(run, items, List.of(employee), buildTemplateScheduleMap(employee));

        Assertions.assertEquals(1, items.size());
        assertBigDecimalEquals("10400.00", items.get(0).getGrossPay());
    }

    @Test
    void processPayrollExempt_MidPeriodTerminationShouldProrateBasicPay() {
        LocalDate periodStart = LocalDate.of(2026, 4, 1);
        LocalDate periodEnd = LocalDate.of(2026, 4, 15);
        PayrollRun run = new PayrollRun();
        run.setPeriodStart(periodStart);
        run.setPeriodEnd(periodEnd);

        Employee employee = buildEmployee(18L, ExemptionStatus.EXEMPT, "624000.00");
        employee.setEmploymentDateEnd(LocalDate.of(2026, 4, 5));

        when(defaultConfig.getTimeZone()).thenReturn(ZONE);
        when(attendanceLogRepository.findByEmployeeIdInAndTimestampBetween(anyList(), any(Instant.class), any(Instant.class), any()))
                .thenReturn(List.of());
        when(dailyShiftRepository.findAllByEmployeeIdsAndPeriod(anyList(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(leaveRequestRepository.findApprovedLeavesForPeriod(anyList(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(holidayService.getHolidayLookup(periodStart, periodEnd)).thenReturn(HolidayLookup.empty());

        List<PayrollItem> items = payrollService.processPayrollExempt(run, List.of(employee), buildTemplateScheduleMap(employee));

        Assertions.assertEquals(1, items.size());
        assertBigDecimalEquals("8666.66", items.get(0).getGrossPay());
    }

    @Test
    void processPayrollNonExempt_NightOvertimeShouldCompoundNightDifferentialRate() {
        LocalDate workDate = LocalDate.of(2026, 3, 23);
        PayrollRun run = buildSingleDayRun(workDate);
        Employee employee = buildEmployee(10L, ExemptionStatus.NON_EXEMPT, "624000.00");
        DailyShiftSchedule dailyShift = buildDailyShift(employee, workDate, LocalTime.of(14, 0), LocalTime.of(22, 0));
        OvertimeRequest overtimeRequest = buildOvertimeRequest(
                employee,
                workDate,
                workDate.atTime(22, 0),
                workDate.plusDays(1).atTime(0, 0)
        );
        List<AttendanceLog> attendanceLogs = List.of(
                buildAttendanceLog(employee, workDate, LocalTime.of(14, 0), "IN"),
                buildAttendanceLog(employee, workDate.plusDays(1), LocalTime.of(0, 0), "OUT")
        );

        when(defaultConfig.getTimeZone()).thenReturn(ZONE);
        when(attendanceLogRepository.findByEmployeeIdInAndTimestampBetween(anyList(), any(Instant.class), any(Instant.class), any()))
                .thenReturn(attendanceLogs);
        when(overtimeRequestRepository.findAll(any(Specification.class)))
                .thenReturn(List.of(overtimeRequest));
        when(dailyShiftRepository.findAllByEmployeeIdsAndPeriod(anyList(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(dailyShift));
        when(leaveRequestRepository.findApprovedLeavesForPeriod(anyList(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(holidayService.getHolidayLookup(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(HolidayLookup.empty());

        List<PayrollItem> items = new ArrayList<>();
        payrollService.processPayrollNonExempt(run, items, List.of(employee), Map.of());

        Assertions.assertEquals(1, items.size());
        PayrollItem item = items.get(0);

        EarningsLine overtimeLine = item.getEarnings().stream()
                .filter(line -> Boolean.TRUE.equals(line.getOvertime()))
                .findFirst()
                .orElseThrow();
        assertBigDecimalEquals("2.00", overtimeLine.getHours());
        assertBigDecimalEquals("625.00", overtimeLine.getAmount());

        EarningsLine overtimeNightDiffLine = item.getEarnings().stream()
                .filter(line -> !Boolean.TRUE.equals(line.getOvertime()))
                .filter(line -> line.getHours() != null && line.getHours().compareTo(BigDecimal.ZERO) > 0)
                .findFirst()
                .orElseThrow();
        assertBigDecimalEquals("2.00", overtimeNightDiffLine.getHours());
        assertBigDecimalEquals("62.50", overtimeNightDiffLine.getAmount());
        assertBigDecimalEquals("26687.50", item.getGrossPay());
    }

    @Test
    void processPayrollNonExempt_DoubleNightOvertimeShouldUseDoubleOvertimeRates() {
        LocalDate workDate = LocalDate.of(2026, 3, 24);
        PayrollRun run = buildSingleDayRun(workDate);
        Employee employee = buildEmployee(17L, ExemptionStatus.NON_EXEMPT, "624000.00");
        DailyShiftSchedule dailyShift = buildDailyShift(employee, workDate, LocalTime.of(14, 0), LocalTime.of(22, 0));
        OvertimeRequest overtimeRequest = buildOvertimeRequest(
                employee,
                workDate,
                workDate.atTime(22, 0),
                workDate.plusDays(1).atTime(0, 0),
                EarningsType.DOUBLE_OVERTIME
        );
        List<AttendanceLog> attendanceLogs = List.of(
                buildAttendanceLog(employee, workDate, LocalTime.of(14, 0), "IN"),
                buildAttendanceLog(employee, workDate.plusDays(1), LocalTime.of(0, 0), "OUT")
        );

        when(defaultConfig.getTimeZone()).thenReturn(ZONE);
        when(attendanceLogRepository.findByEmployeeIdInAndTimestampBetween(anyList(), any(Instant.class), any(Instant.class), any()))
                .thenReturn(attendanceLogs);
        when(overtimeRequestRepository.findAll(any(Specification.class)))
                .thenReturn(List.of(overtimeRequest));
        when(dailyShiftRepository.findAllByEmployeeIdsAndPeriod(anyList(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(dailyShift));
        when(leaveRequestRepository.findApprovedLeavesForPeriod(anyList(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(holidayService.getHolidayLookup(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(HolidayLookup.empty());

        List<PayrollItem> items = new ArrayList<>();
        payrollService.processPayrollNonExempt(run, items, List.of(employee), Map.of());

        Assertions.assertEquals(1, items.size());
        PayrollItem item = items.get(0);

        EarningsLine overtimeLine = item.getEarnings().stream()
                .filter(line -> Boolean.TRUE.equals(line.getOvertime()))
                .findFirst()
                .orElseThrow();
        assertBigDecimalEquals("2.00", overtimeLine.getHours());
        assertBigDecimalEquals("1000.00", overtimeLine.getAmount());

        EarningsLine overtimeNightDiffLine = item.getEarnings().stream()
                .filter(line -> !Boolean.TRUE.equals(line.getOvertime()))
                .filter(line -> line.getHours() != null && line.getHours().compareTo(BigDecimal.ZERO) > 0)
                .findFirst()
                .orElseThrow();
        assertBigDecimalEquals("2.00", overtimeNightDiffLine.getHours());
        assertBigDecimalEquals("100.00", overtimeNightDiffLine.getAmount());
        assertBigDecimalEquals("27100.00", item.getGrossPay());
    }

    @Test
    void processPayroll_RegularRunShouldReturnGeneratedRunView() {
        LocalDate workDate = LocalDate.of(2026, 3, 25);
        RunCreateDto payload = buildRunPayload(workDate, workDate, RunType.REGULAR);
        PayrollRunViewDto viewDto = new PayrollRunViewDto(
                77L,
                workDate,
                workDate,
                workDate,
                "Test run",
                PayrollStatus.IN_PROGRESS,
                RunType.REGULAR,
                0L
        );

        when(payrollRunRepository.existsByPeriodStartAndPeriodEndAndRunType(workDate, workDate, RunType.REGULAR))
                .thenReturn(false);
        when(payrollRunRepository.save(any(PayrollRun.class))).thenAnswer(invocation -> {
            PayrollRun run = invocation.getArgument(0);
            ReflectionTestUtils.setField(run, "payId", 77L);
            return run;
        });
        when(employeeRepository.findPayrollEligibleEmployees(workDate, workDate, org.example.maridone.enums.EmploymentStatus.TERMINATED))
                .thenReturn(List.of());
        when(payrollItemRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(payrollRunRepository.findRunViewByPayId(77L)).thenReturn(java.util.Optional.of(viewDto));

        PayrollRunViewDto result = payrollService.processPayroll(payload);

        Assertions.assertEquals(77L, result.getPayId());
        Assertions.assertEquals(0L, result.getItemCount());
        verify(payrollItemRepository).saveAll(anyList());
    }

    @Test
    void processPayroll_ShouldQueryOnlyEmployeesWhoseEmploymentOverlapsPayrollPeriod() {
        LocalDate periodStart = LocalDate.of(2026, 2, 1);
        LocalDate periodEnd = LocalDate.of(2026, 2, 15);
        RunCreateDto payload = buildRunPayload(periodStart, periodEnd, RunType.REGULAR);
        PayrollRunViewDto viewDto = new PayrollRunViewDto(
                78L,
                periodStart,
                periodEnd,
                periodEnd,
                "Test run",
                PayrollStatus.IN_PROGRESS,
                RunType.REGULAR,
                1L
        );
        Employee eligibleEmployee = buildEmployee(13L, ExemptionStatus.NON_EXEMPT, "624000.00");
        eligibleEmployee.setEmploymentDateStart(LocalDate.of(2026, 1, 15));
        Employee newlyAddedEmployee = buildEmployee(14L, ExemptionStatus.NON_EXEMPT, "624000.00");
        newlyAddedEmployee.setEmploymentDateStart(LocalDate.of(2026, 3, 1));
        DailyShiftSchedule eligibleShift = buildDailyShift(
                eligibleEmployee,
                periodStart,
                LocalTime.of(8, 0),
                LocalTime.of(17, 0)
        );

        when(payrollRunRepository.existsByPeriodStartAndPeriodEndAndRunType(periodStart, periodEnd, RunType.REGULAR))
                .thenReturn(false);
        when(payrollRunRepository.save(any(PayrollRun.class))).thenAnswer(invocation -> {
            PayrollRun run = invocation.getArgument(0);
            ReflectionTestUtils.setField(run, "payId", 78L);
            return run;
        });
        when(employeeRepository.findPayrollEligibleEmployees(
                periodStart,
                periodEnd,
                org.example.maridone.enums.EmploymentStatus.TERMINATED
        )).thenReturn(List.of(eligibleEmployee));
        when(defaultConfig.getTimeZone()).thenReturn(ZONE);
        when(attendanceLogRepository.findByEmployeeIdInAndTimestampBetween(anyList(), any(Instant.class), any(Instant.class), any()))
                .thenReturn(List.of());
        when(overtimeRequestRepository.findAll(any(Specification.class))).thenReturn(List.of());
        when(dailyShiftRepository.findAllByEmployeeIdsAndPeriod(anyList(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(eligibleShift));
        when(leaveRequestRepository.findApprovedLeavesForPeriod(anyList(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(holidayService.getHolidayLookup(periodStart, periodEnd)).thenReturn(HolidayLookup.empty());
        when(payrollItemRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(payrollRunRepository.findRunViewByPayId(78L)).thenReturn(java.util.Optional.of(viewDto));

        PayrollRunViewDto result = payrollService.processPayroll(payload);

        Assertions.assertEquals(78L, result.getPayId());
        verify(employeeRepository).findPayrollEligibleEmployees(
                periodStart,
                periodEnd,
                org.example.maridone.enums.EmploymentStatus.TERMINATED
        );
        ArgumentCaptor<Iterable<PayrollItem>> savedItemsCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(payrollItemRepository).saveAll(savedItemsCaptor.capture());

        List<PayrollItem> savedItems = new ArrayList<>();
        savedItemsCaptor.getValue().forEach(savedItems::add);
        Assertions.assertEquals(1, savedItems.size());
        Assertions.assertEquals(eligibleEmployee.getEmployeeId(), savedItems.get(0).getEmployee().getEmployeeId());
        Assertions.assertTrue(savedItems.stream().noneMatch(item ->
                item.getEmployee() != null
                        && item.getEmployee().getEmployeeId().equals(newlyAddedEmployee.getEmployeeId())
        ));
    }

    @Test
    void processPayroll_ShouldExcludeEmployeeWithoutDailyShiftsOrPaidLeave() {
        LocalDate periodStart = LocalDate.of(2026, 4, 5);
        LocalDate periodEnd = LocalDate.of(2026, 4, 5);
        RunCreateDto payload = buildRunPayload(periodStart, periodEnd, RunType.REGULAR);
        PayrollRunViewDto viewDto = new PayrollRunViewDto(
                79L,
                periodStart,
                periodEnd,
                periodEnd,
                "Test run",
                PayrollStatus.IN_PROGRESS,
                RunType.REGULAR,
                0L
        );
        Employee employee = buildEmployee(15L, ExemptionStatus.NON_EXEMPT, "624000.00");
        employee.setEmploymentDateStart(LocalDate.of(2026, 1, 1));

        when(payrollRunRepository.existsByPeriodStartAndPeriodEndAndRunType(periodStart, periodEnd, RunType.REGULAR))
                .thenReturn(false);
        when(payrollRunRepository.save(any(PayrollRun.class))).thenAnswer(invocation -> {
            PayrollRun run = invocation.getArgument(0);
            ReflectionTestUtils.setField(run, "payId", 79L);
            return run;
        });
        when(employeeRepository.findPayrollEligibleEmployees(
                periodStart,
                periodEnd,
                org.example.maridone.enums.EmploymentStatus.TERMINATED
        )).thenReturn(List.of(employee));
        when(dailyShiftRepository.findAllByEmployeeIdsAndPeriod(anyList(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(leaveRequestRepository.findApprovedLeavesForPeriod(anyList(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(payrollItemRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(payrollRunRepository.findRunViewByPayId(79L)).thenReturn(java.util.Optional.of(viewDto));

        PayrollRunViewDto result = payrollService.processPayroll(payload);

        Assertions.assertEquals(79L, result.getPayId());
        ArgumentCaptor<Iterable<PayrollItem>> savedItemsCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(payrollItemRepository).saveAll(savedItemsCaptor.capture());

        List<PayrollItem> savedItems = new ArrayList<>();
        savedItemsCaptor.getValue().forEach(savedItems::add);
        Assertions.assertTrue(savedItems.isEmpty());
    }

    @Test
    void processPayroll_ShouldExcludeAccountingEmployeesEvenWhenTheyHavePayrollInputs() {
        LocalDate periodStart = LocalDate.of(2026, 4, 6);
        LocalDate periodEnd = LocalDate.of(2026, 4, 6);
        RunCreateDto payload = buildRunPayload(periodStart, periodEnd, RunType.REGULAR);
        PayrollRunViewDto viewDto = new PayrollRunViewDto(
                81L,
                periodStart,
                periodEnd,
                periodEnd,
                "Test run",
                PayrollStatus.IN_PROGRESS,
                RunType.REGULAR,
                1L
        );
        Employee regularEmployee = buildEmployee(21L, ExemptionStatus.NON_EXEMPT, "624000.00");
        Employee accountingEmployee = buildEmployee(22L, ExemptionStatus.NON_EXEMPT, "624000.00");
        accountingEmployee.setPosition(Position.ACCOUNTING);

        DailyShiftSchedule regularShift = buildDailyShift(regularEmployee, periodStart, LocalTime.of(8, 0), LocalTime.of(17, 0));
        DailyShiftSchedule accountingShift = buildDailyShift(accountingEmployee, periodStart, LocalTime.of(8, 0), LocalTime.of(17, 0));

        when(payrollRunRepository.existsByPeriodStartAndPeriodEndAndRunType(periodStart, periodEnd, RunType.REGULAR))
                .thenReturn(false);
        when(payrollRunRepository.save(any(PayrollRun.class))).thenAnswer(invocation -> {
            PayrollRun run = invocation.getArgument(0);
            ReflectionTestUtils.setField(run, "payId", 81L);
            return run;
        });
        when(employeeRepository.findPayrollEligibleEmployees(
                periodStart,
                periodEnd,
                org.example.maridone.enums.EmploymentStatus.TERMINATED
        )).thenReturn(List.of(regularEmployee, accountingEmployee));
        when(defaultConfig.getTimeZone()).thenReturn(ZONE);
        when(attendanceLogRepository.findByEmployeeIdInAndTimestampBetween(anyList(), any(Instant.class), any(Instant.class), any()))
                .thenReturn(List.of());
        when(overtimeRequestRepository.findAll(any(Specification.class))).thenReturn(List.of());
        when(dailyShiftRepository.findAllByEmployeeIdsAndPeriod(anyList(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(regularShift, accountingShift));
        when(leaveRequestRepository.findApprovedLeavesForPeriod(anyList(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(holidayService.getHolidayLookup(periodStart, periodEnd)).thenReturn(HolidayLookup.empty());
        when(payrollItemRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(payrollRunRepository.findRunViewByPayId(81L)).thenReturn(java.util.Optional.of(viewDto));

        PayrollRunViewDto result = payrollService.processPayroll(payload);

        Assertions.assertEquals(81L, result.getPayId());
        ArgumentCaptor<Iterable<PayrollItem>> savedItemsCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(payrollItemRepository).saveAll(savedItemsCaptor.capture());

        List<PayrollItem> savedItems = new ArrayList<>();
        savedItemsCaptor.getValue().forEach(savedItems::add);
        Assertions.assertEquals(1, savedItems.size());
        Assertions.assertEquals(regularEmployee.getEmployeeId(), savedItems.get(0).getEmployee().getEmployeeId());
        Assertions.assertTrue(savedItems.stream().noneMatch(item ->
                item.getEmployee() != null
                        && item.getEmployee().getEmployeeId().equals(accountingEmployee.getEmployeeId())
        ));
    }

    @Test
    void processPayroll_ShouldKeepEmployeeWithPaidLeaveEvenWithoutDailyShifts() {
        LocalDate periodStart = LocalDate.of(2026, 4, 5);
        LocalDate periodEnd = LocalDate.of(2026, 4, 5);
        RunCreateDto payload = buildRunPayload(periodStart, periodEnd, RunType.REGULAR);
        PayrollRunViewDto viewDto = new PayrollRunViewDto(
                80L,
                periodStart,
                periodEnd,
                periodEnd,
                "Test run",
                PayrollStatus.IN_PROGRESS,
                RunType.REGULAR,
                1L
        );
        Employee employee = buildEmployee(16L, ExemptionStatus.EXEMPT, "624000.00");
        employee.setEmploymentDateStart(LocalDate.of(2026, 1, 1));
        LeaveRequest paidLeave = buildLeave(
                employee,
                periodStart.atStartOfDay(),
                periodEnd.plusDays(1).atStartOfDay(),
                true
        );

        when(payrollRunRepository.existsByPeriodStartAndPeriodEndAndRunType(periodStart, periodEnd, RunType.REGULAR))
                .thenReturn(false);
        when(payrollRunRepository.save(any(PayrollRun.class))).thenAnswer(invocation -> {
            PayrollRun run = invocation.getArgument(0);
            ReflectionTestUtils.setField(run, "payId", 80L);
            return run;
        });
        when(employeeRepository.findPayrollEligibleEmployees(
                periodStart,
                periodEnd,
                org.example.maridone.enums.EmploymentStatus.TERMINATED
        )).thenReturn(List.of(employee));
        when(defaultConfig.getTimeZone()).thenReturn(ZONE);
        when(attendanceLogRepository.findByEmployeeIdInAndTimestampBetween(anyList(), any(Instant.class), any(Instant.class), any()))
                .thenReturn(List.of());
        when(dailyShiftRepository.findAllByEmployeeIdsAndPeriod(anyList(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(leaveRequestRepository.findApprovedLeavesForPeriod(anyList(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(paidLeave));
        when(holidayService.getHolidayLookup(periodStart, periodEnd)).thenReturn(HolidayLookup.empty());
        when(payrollItemRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(payrollRunRepository.findRunViewByPayId(80L)).thenReturn(java.util.Optional.of(viewDto));

        PayrollRunViewDto result = payrollService.processPayroll(payload);

        Assertions.assertEquals(80L, result.getPayId());
        ArgumentCaptor<Iterable<PayrollItem>> savedItemsCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(payrollItemRepository).saveAll(savedItemsCaptor.capture());

        List<PayrollItem> savedItems = new ArrayList<>();
        savedItemsCaptor.getValue().forEach(savedItems::add);
        Assertions.assertEquals(1, savedItems.size());
        Assertions.assertEquals(employee.getEmployeeId(), savedItems.get(0).getEmployee().getEmployeeId());
    }

    @Test
    void processPayroll_AdjustmentRunShouldRejectAutomaticGeneration() {
        LocalDate workDate = LocalDate.of(2026, 3, 26);
        RunCreateDto payload = buildRunPayload(workDate, workDate, RunType.ADJUSTMENT);

        Assertions.assertThrows(InvalidActionException.class, () -> payrollService.processPayroll(payload));
    }

    @Test
    void processPayroll_FuturePeriodShouldRejectGeneration() {
        when(defaultConfig.getTimeZone()).thenReturn(ZONE);
        LocalDate tomorrow = LocalDate.now(ZONE).plusDays(1);
        RunCreateDto payload = buildRunPayload(tomorrow, tomorrow, RunType.REGULAR);

        Assertions.assertThrows(InvalidActionException.class, () -> payrollService.processPayroll(payload));
    }

    @Test
    void createRun_FuturePeriodShouldRejectCreation() {
        when(defaultConfig.getTimeZone()).thenReturn(ZONE);
        LocalDate tomorrow = LocalDate.now(ZONE).plusDays(1);
        RunCreateDto payload = buildRunPayload(tomorrow, tomorrow, RunType.ADJUSTMENT);

        Assertions.assertThrows(InvalidActionException.class, () -> payrollService.createRun(payload));
    }

    @Test
    void createRun_ShouldAlwaysStartInProgress() {
        LocalDate workDate = LocalDate.of(2026, 3, 27);
        RunCreateDto payload = buildRunPayload(workDate, workDate, RunType.REGULAR);
        payload.setPayrollStatus(PayrollStatus.POSTED);

        when(payrollRunRepository.save(any(PayrollRun.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PayrollRun result = payrollService.createRun(payload);

        Assertions.assertEquals(PayrollStatus.IN_PROGRESS, result.getPayrollStatus());
    }

    @Test
    void postRun_WithoutPayrollItemsShouldRejectPosting() {
        PayrollRun run = buildSingleDayRun(LocalDate.of(2026, 3, 27));
        ReflectionTestUtils.setField(run, "payId", 88L);

        when(payrollRunRepository.findById(88L)).thenReturn(java.util.Optional.of(run));
        when(payrollItemRepository.countByPayrollRun_PayId(88L)).thenReturn(0L);

        Assertions.assertThrows(InvalidActionException.class, () -> payrollService.postRun(88L));
    }

    @Test
    void postRun_AdjustmentRunShouldPostWithoutUsingLegacyCorrectionService() {
        PayrollRun run = buildSingleDayRun(LocalDate.of(2026, 3, 28));
        ReflectionTestUtils.setField(run, "payId", 89L);
        run.setRunType(RunType.ADJUSTMENT);

        PayrollRunViewDto viewDto = new PayrollRunViewDto(
                89L,
                run.getPeriodStart(),
                run.getPeriodEnd(),
                run.getPeriodEnd(),
                "Adjustment run",
                PayrollStatus.POSTED,
                RunType.ADJUSTMENT,
                1L
        );

        when(payrollRunRepository.findById(89L)).thenReturn(java.util.Optional.of(run));
        when(payrollItemRepository.countByPayrollRun_PayId(89L)).thenReturn(1L);
        when(payrollRunRepository.save(any(PayrollRun.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(payrollRunRepository.findRunViewByPayId(89L)).thenReturn(java.util.Optional.of(viewDto));

        PayrollRunViewDto result = payrollService.postRun(89L);

        Assertions.assertEquals(PayrollStatus.POSTED, run.getPayrollStatus());
        Assertions.assertEquals(89L, result.getPayId());
        verifyNoInteractions(payrollItemService);
    }

    @Test
    void processPayrollExempt_30thShouldUsePriorRegularCutoffForMonthlyTaxes() {
        LocalDate workDate = LocalDate.of(2026, 4, 30);
        PayrollRun run = buildSingleDayRun(workDate);
        Employee employee = buildEmployee(31L, ExemptionStatus.EXEMPT, "624000.00");
        DailyShiftSchedule dailyShift = buildDailyShift(employee, workDate, LocalTime.of(8, 0), LocalTime.of(17, 0));
        LeaveRequest paidLeave = buildLeave(employee, workDate.atTime(8, 0), workDate.atTime(17, 0), true);

        PayrollItem priorItem = new PayrollItem();
        priorItem.setEmployee(employee);
        priorItem.setGrossPay(new BigDecimal("26000.00"));
        priorItem.setEarnings(List.of(
                buildEarningLine(priorItem, EarningsType.BASIC, "26000.00")
        ));
        priorItem.setDeductions(List.of(
                buildDeductionLine(priorItem, DeductionType.BRACKET_LEVEL_THREE, "2479.10")
        ));

        PayrollRun priorRun = new PayrollRun();
        priorRun.setRunType(RunType.REGULAR);
        priorRun.setPayrollStatus(PayrollStatus.POSTED);
        priorRun.setPaymentDate(LocalDate.of(2026, 4, 15));
        priorRun.setPeriodStart(LocalDate.of(2026, 4, 1));
        priorRun.setPeriodEnd(LocalDate.of(2026, 4, 15));
        priorItem.setPayrollRun(priorRun);

        when(defaultConfig.getTimeZone()).thenReturn(ZONE);
        when(attendanceLogRepository.findByEmployeeIdInAndTimestampBetween(anyList(), any(Instant.class), any(Instant.class), any()))
                .thenReturn(List.of());
        when(dailyShiftRepository.findAllByEmployeeIdsAndPeriod(anyList(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(dailyShift));
        when(leaveRequestRepository.findApprovedLeavesForPeriod(anyList(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(paidLeave));
        when(holidayService.getHolidayLookup(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(HolidayLookup.empty());
        when(payrollItemRepository.findByEmployee_EmployeeIdAndPayrollRun_RunTypeOrderByPayrollRun_PaymentDateDescPayrollRun_PeriodEndDescItemIdDesc(
                employee.getEmployeeId(),
                RunType.REGULAR
        )).thenReturn(List.of(priorItem));

        List<PayrollItem> items = payrollService.processPayrollExempt(run, List.of(employee), Map.of());

        Assertions.assertEquals(1, items.size());
        assertDeduction(items.get(0), DeductionType.SSS, "1750.00");
        assertDeduction(items.get(0), DeductionType.PHILHEALTH, "1300.00");
        assertDeduction(items.get(0), DeductionType.PAGIBIG, "200.00");
        assertDeduction(items.get(0), DeductionType.BRACKET_LEVEL_THREE, "2479.10");
        assertEmployerContribution(items.get(0), EmployerContributionType.SSS, "3530.00");
        assertEmployerContribution(items.get(0), EmployerContributionType.PHILHEALTH, "1300.00");
        assertEmployerContribution(items.get(0), EmployerContributionType.PAGIBIG, "200.00");
    }

    @Test
    void processPayrollExempt_15thShouldNotCreateEmployerContributions() {
        LocalDate workDate = LocalDate.of(2026, 4, 15);
        PayrollRun run = buildSingleDayRun(workDate);
        Employee employee = buildEmployee(32L, ExemptionStatus.EXEMPT, "624000.00");
        DailyShiftSchedule dailyShift = buildDailyShift(employee, workDate, LocalTime.of(8, 0), LocalTime.of(17, 0));
        LeaveRequest paidLeave = buildLeave(employee, workDate.atTime(8, 0), workDate.atTime(17, 0), true);

        when(defaultConfig.getTimeZone()).thenReturn(ZONE);
        when(attendanceLogRepository.findByEmployeeIdInAndTimestampBetween(anyList(), any(Instant.class), any(Instant.class), any()))
                .thenReturn(List.of());
        when(dailyShiftRepository.findAllByEmployeeIdsAndPeriod(anyList(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(dailyShift));
        when(leaveRequestRepository.findApprovedLeavesForPeriod(anyList(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(paidLeave));
        when(holidayService.getHolidayLookup(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(HolidayLookup.empty());

        List<PayrollItem> items = payrollService.processPayrollExempt(run, List.of(employee), Map.of());

        Assertions.assertEquals(1, items.size());
        Assertions.assertTrue(items.get(0).getEmployerContributions().isEmpty());
    }

    @Test
    void processPayrollExempt_AdjustmentRunShouldNotCreateEmployerContributions() {
        LocalDate workDate = LocalDate.of(2026, 4, 30);
        PayrollRun run = buildSingleDayRun(workDate);
        run.setRunType(RunType.ADJUSTMENT);
        Employee employee = buildEmployee(33L, ExemptionStatus.EXEMPT, "624000.00");
        DailyShiftSchedule dailyShift = buildDailyShift(employee, workDate, LocalTime.of(8, 0), LocalTime.of(17, 0));
        LeaveRequest paidLeave = buildLeave(employee, workDate.atTime(8, 0), workDate.atTime(17, 0), true);

        when(defaultConfig.getTimeZone()).thenReturn(ZONE);
        when(attendanceLogRepository.findByEmployeeIdInAndTimestampBetween(anyList(), any(Instant.class), any(Instant.class), any()))
                .thenReturn(List.of());
        when(dailyShiftRepository.findAllByEmployeeIdsAndPeriod(anyList(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(dailyShift));
        when(leaveRequestRepository.findApprovedLeavesForPeriod(anyList(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(paidLeave));
        when(holidayService.getHolidayLookup(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(HolidayLookup.empty());

        List<PayrollItem> items = payrollService.processPayrollExempt(run, List.of(employee), Map.of());

        Assertions.assertEquals(1, items.size());
        Assertions.assertTrue(items.get(0).getEmployerContributions().isEmpty());
    }

    private PayrollRun buildSingleDayRun(LocalDate date) {
        PayrollRun run = new PayrollRun();
        run.setPeriodStart(date);
        run.setPeriodEnd(date);
        run.setPaymentDate(date);
        run.setRunType(RunType.REGULAR);
        return run;
    }

    private RunCreateDto buildRunPayload(LocalDate start, LocalDate end, RunType runType) {
        RunCreateDto payload = new RunCreateDto();
        payload.setPeriodStart(start);
        payload.setPeriodEnd(end);
        payload.setPeriodDescription("Test run");
        payload.setPayrollStatus(PayrollStatus.IN_PROGRESS);
        payload.setRunType(runType);
        return payload;
    }

    private Employee buildEmployee(Long employeeId, ExemptionStatus exemptionStatus, String yearlySalary) {
        Employee employee = new Employee();
        ReflectionTestUtils.setField(employee, "employeeId", employeeId);
        employee.setExemptionStatus(exemptionStatus);
        employee.setYearlySalary(new BigDecimal(yearlySalary));
        employee.setEmploymentDateStart(LocalDate.of(2026, 1, 1));
        employee.setPosition(Position.EMPLOYEE);
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
        return buildLeave(employee, startDateTime, endDateTime, true);
    }

    private LeaveRequest buildLeave(
            Employee employee,
            LocalDateTime startDateTime,
            LocalDateTime endDateTime,
            boolean paid
    ) {
        LeaveRequest leaveRequest = new LeaveRequest();
        leaveRequest.setEmployee(employee);
        leaveRequest.setStartDateTime(startDateTime);
        leaveRequest.setEndDateTime(endDateTime);
        leaveRequest.setRequestStatus(Status.APPROVED);
        leaveRequest.setReason("Test leave");
        leaveRequest.setPaid(paid);
        return leaveRequest;
    }

    private OvertimeRequest buildOvertimeRequest(
            Employee employee,
            LocalDate workDate,
            LocalDateTime startDateTime,
            LocalDateTime endDateTime
    ) {
        return buildOvertimeRequest(employee, workDate, startDateTime, endDateTime, EarningsType.OVERTIME);
    }

    private OvertimeRequest buildOvertimeRequest(
            Employee employee,
            LocalDate workDate,
            LocalDateTime startDateTime,
            LocalDateTime endDateTime,
            EarningsType overtimeType
    ) {
        return buildOvertimeRequest(employee, workDate, startDateTime, endDateTime, overtimeType, true);
    }

    private OvertimeRequest buildOvertimeRequest(
            Employee employee,
            LocalDate workDate,
            LocalDateTime startDateTime,
            LocalDateTime endDateTime,
            EarningsType overtimeType,
            boolean paid
    ) {
        OvertimeRequest overtimeRequest = new OvertimeRequest();
        overtimeRequest.setEmployee(employee);
        overtimeRequest.setWorkDate(workDate);
        overtimeRequest.setStartTime(startDateTime);
        overtimeRequest.setEndTime(endDateTime);
        overtimeRequest.setOvertimeType(overtimeType);
        overtimeRequest.setRequestStatus(Status.APPROVED);
        overtimeRequest.setReason("Test overtime");
        overtimeRequest.setPaid(paid);
        ReflectionTestUtils.setField(overtimeRequest, "employeeId", employee.getEmployeeId());
        return overtimeRequest;
    }

    private AttendanceLog buildAttendanceLog(Employee employee, LocalDate workDate, LocalTime time, String direction) {
        AttendanceLog attendanceLog = new AttendanceLog();
        attendanceLog.setEmployeeId(employee.getEmployeeId());
        attendanceLog.setTimestamp(workDate.atTime(time).atZone(ZONE).toInstant());
        attendanceLog.setDirection(direction);
        return attendanceLog;
    }

    private Map<Long, Map<java.time.DayOfWeek, List<TemplateShiftSchedule>>> buildTemplateScheduleMap(Employee employee) {
        Map<java.time.DayOfWeek, List<TemplateShiftSchedule>> schedulesByDay = java.util.Arrays.stream(java.time.DayOfWeek.values())
                .collect(java.util.stream.Collectors.toMap(
                        dayOfWeek -> dayOfWeek,
                        dayOfWeek -> List.of(buildTemplateShift(employee, dayOfWeek))
                ));
        return Map.of(employee.getEmployeeId(), schedulesByDay);
    }

    private TemplateShiftSchedule buildTemplateShift(Employee employee, java.time.DayOfWeek dayOfWeek) {
        TemplateShiftSchedule templateShiftSchedule = new TemplateShiftSchedule();
        templateShiftSchedule.setEmployee(employee);
        templateShiftSchedule.setDayOfWeek(dayOfWeek);
        templateShiftSchedule.setStartTime(LocalTime.of(8, 0));
        templateShiftSchedule.setEndTime(LocalTime.of(17, 0));
        return templateShiftSchedule;
    }

    private DeductionsLine buildDeductionLine(PayrollItem item, DeductionType type, String amount) {
        DeductionsLine line = new DeductionsLine();
        line.setPayrollItem(item);
        line.setDeductionType(type);
        line.setAmount(new BigDecimal(amount));
        return line;
    }

    private EarningsLine buildEarningLine(PayrollItem item, EarningsType type, String amount) {
        EarningsLine line = new EarningsLine();
        line.setPayrollItem(item);
        line.setEarningsType(type);
        line.setAmount(new BigDecimal(amount));
        line.setHours(BigDecimal.ZERO);
        line.setRate(BigDecimal.ZERO);
        line.setOvertime(false);
        return line;
    }

    private void assertDeduction(PayrollItem item, DeductionType type, String expectedAmount) {
        DeductionsLine line = item.getDeductions().stream()
                .filter(deduction -> deduction.getDeductionType() == type)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing deduction type: " + type));
        assertBigDecimalEquals(expectedAmount, line.getAmount());
    }

    private void assertEmployerContribution(PayrollItem item, EmployerContributionType type, String expectedAmount) {
        EmployerContributionLine line = item.getEmployerContributions().stream()
                .filter(contribution -> contribution.getContributionType() == type)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing employer contribution type: " + type));
        assertBigDecimalEquals(expectedAmount, line.getAmount());
    }

    private void assertBigDecimalEquals(String expected, BigDecimal actual) {
        Assertions.assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
