package org.example.maridone.payroll.run;

import java.math.BigDecimal;
import java.time.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.example.maridone.config.DefaultProperties;
import org.example.maridone.config.PayrollProperties;
import org.example.maridone.core.employee.Employee;
import org.example.maridone.enums.EarningsType;
import org.example.maridone.enums.DeductionType;
import org.example.maridone.core.employee.EmployeeRepository;
import org.example.maridone.enums.ExemptionStatus;
import org.example.maridone.holiday.HolidayLookup;
import org.example.maridone.holiday.HolidayService;
import org.example.maridone.leave.request.LeaveRequest;
import org.example.maridone.leave.request.LeaveRequestRepository;
import org.example.maridone.log.AttendanceLogRepository;
import org.example.maridone.log.attendance.AttendanceLog;
import org.example.maridone.overtime.OvertimeRequest;
import org.example.maridone.overtime.OvertimeRequestRepository;
import org.example.maridone.payroll.PayrollCalculator;
import org.example.maridone.payroll.PayrollService;
import org.example.maridone.payroll.item.PayrollItem;
import org.example.maridone.payroll.item.component.DeductionsLine;
import org.example.maridone.payroll.item.component.DeductionsRepository;
import org.example.maridone.payroll.item.component.EarningsLine;
import org.example.maridone.payroll.item.component.EarningsRepository;
import org.example.maridone.payroll.dto.ItemDetailsDto;
import org.example.maridone.payroll.mapper.PayrollMapper;
import org.example.maridone.schedule.shift.TemplateShiftRepository;
import org.example.maridone.schedule.shift.TemplateShiftSchedule;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PayrollServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Manila");

    @Mock private PayrollRunRepository payrollRunRepository;
    @Mock private PayrollItemRepository payrollItemRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private AttendanceLogRepository attendanceLogRepository;
    @Mock private TemplateShiftRepository templateShiftRepository;
    @Mock private OvertimeRequestRepository overtimeRequestRepository;
    @Mock private LeaveRequestRepository leaveRequestRepository;
    @Mock private HolidayService holidayService;
    @Mock private DefaultProperties defaultProperties;
    @Mock private EarningsRepository earningsRepository;
    @Mock private DeductionsRepository deductionsRepository;
    @Mock private PayrollMapper payrollMapper;

    private PayrollService payrollService;
    private PayrollProperties payrollProperties;

    @BeforeEach
    void setUp() {
        payrollProperties = new PayrollProperties();
        PayrollCalculator payrollCalculator = new PayrollCalculator(earningsRepository, deductionsRepository, payrollProperties);
        payrollService = new PayrollService(
                payrollRunRepository,
                payrollItemRepository,
                employeeRepository,
                attendanceLogRepository,
                templateShiftRepository,
                overtimeRequestRepository,
                leaveRequestRepository,
                holidayService,
                payrollCalculator,
                payrollMapper,
                defaultProperties,
                payrollProperties
        );
    }

    @Test
    void getItems_ShouldReturnDtos() {
        Long empId = 1L;
        PayrollItem item = new PayrollItem();
        ItemDetailsDto dto = new ItemDetailsDto();

        when(payrollItemRepository.findAll(any(Specification.class))).thenReturn(List.of(item));
        when(payrollMapper.toItemDetailsDtos(anyList())).thenReturn(List.of(dto));

        List<ItemDetailsDto> result = payrollService.getItems(empId);

        Assertions.assertFalse(result.isEmpty());
    }

    @Test
    void processPayrollNonExempt_RegularHoliday_ShouldApplyTwoHundredPercentPremium() {
        LocalDate workDate = LocalDate.of(2026, 3, 2);
        Employee emp = buildNonExemptEmployee(1L, new BigDecimal("624000.00"));
        PayrollRun run = buildSingleDayRun(workDate);
        TemplateShiftSchedule schedule = buildSchedule(emp, workDate, LocalTime.of(8, 0), LocalTime.of(17, 0));
        List<AttendanceLog> logs = List.of(
                buildAttendance(1L, workDate, LocalTime.of(8, 0), "IN"),
                buildAttendance(1L, workDate, LocalTime.of(17, 0), "OUT")
        );
        HolidayLookup holiday = new HolidayLookup(Set.of(workDate), Set.of(workDate));
        stubCommonDependencies(logs, schedule, holiday);

        List<PayrollItem> items = new ArrayList<>();

        payrollService.processPayrollNonExempt(run, items, List.of(emp));

        Assertions.assertEquals(1, items.size());
        PayrollItem item = items.get(0);
        Assertions.assertEquals(2, item.getEarnings().size());
        Assertions.assertEquals(4, item.getDeductions().size());
        Assertions.assertTrue(item.getDeductions().stream().anyMatch(d -> d.getDeductionType() == DeductionType.SSS));
        Assertions.assertTrue(item.getDeductions().stream().anyMatch(d -> d.getDeductionType() == DeductionType.PHILHEALTH));
        Assertions.assertTrue(item.getDeductions().stream().anyMatch(d -> d.getDeductionType() == DeductionType.PAGIBIG));
        Assertions.assertTrue(item.getDeductions().stream().anyMatch(d -> d.getDeductionType() == DeductionType.BRACKET_LEVEL_THREE));

        Assertions.assertTrue(item.getEarnings().stream().anyMatch(line -> new BigDecimal("26000.00").compareTo(line.getAmount()) == 0));
        EarningsLine holidayLine = item.getEarnings().stream()
                .filter(line -> new BigDecimal("2000.00").compareTo(line.getAmount()) == 0)
                .findFirst()
                .orElseThrow();
        assertBigDecimalEquals("8.00", holidayLine.getHours());
        assertBigDecimalEquals("250.00", holidayLine.getRate());
        assertBigDecimalEquals("2000.00", holidayLine.getAmount());
        Assertions.assertSame(item, holidayLine.getPayrollItem());

        assertBigDecimalEquals("28000.00", item.getGrossPay());
        assertBigDecimalEquals("23495.90", item.getNetPay());
    }

    @Test
    void processPayrollNonExempt_SpecialHoliday_ShouldApplyOneHundredThirtyPercentPremium() {
        LocalDate workDate = LocalDate.of(2026, 3, 2);
        Employee emp = buildNonExemptEmployee(1L, new BigDecimal("624000.00"));
        PayrollRun run = buildSingleDayRun(workDate);
        TemplateShiftSchedule schedule = buildSchedule(emp, workDate, LocalTime.of(8, 0), LocalTime.of(17, 0));
        List<AttendanceLog> logs = List.of(
                buildAttendance(1L, workDate, LocalTime.of(8, 0), "IN"),
                buildAttendance(1L, workDate, LocalTime.of(17, 0), "OUT")
        );
        HolidayLookup holiday = new HolidayLookup(Set.of(workDate), Set.of());
        stubCommonDependencies(logs, schedule, holiday);

        List<PayrollItem> items = new ArrayList<>();

        payrollService.processPayrollNonExempt(run, items, List.of(emp));

        Assertions.assertEquals(1, items.size());
        PayrollItem item = items.get(0);
        Assertions.assertEquals(2, item.getEarnings().size());
        Assertions.assertEquals(4, item.getDeductions().size());

        Assertions.assertTrue(item.getEarnings().stream().anyMatch(line -> new BigDecimal("26000.00").compareTo(line.getAmount()) == 0));
        EarningsLine holidayLine = item.getEarnings().stream()
                .filter(line -> new BigDecimal("600.00").compareTo(line.getAmount()) == 0)
                .findFirst()
                .orElseThrow();
        assertBigDecimalEquals("8.00", holidayLine.getHours());
        assertBigDecimalEquals("75.00", holidayLine.getRate());
        assertBigDecimalEquals("600.00", holidayLine.getAmount());

        assertBigDecimalEquals("26600.00", item.getGrossPay());
        assertBigDecimalEquals("22375.90", item.getNetPay());
    }

    @Test
    void processPayrollNonExempt_NonHolidayEventTitle_ShouldNotApplyHolidayPremium() {
        LocalDate workDate = LocalDate.of(2026, 3, 2);
        Employee emp = buildNonExemptEmployee(1L, new BigDecimal("624000.00"));
        PayrollRun run = buildSingleDayRun(workDate);
        TemplateShiftSchedule schedule = buildSchedule(emp, workDate, LocalTime.of(8, 0), LocalTime.of(17, 0));
        List<AttendanceLog> logs = List.of(
                buildAttendance(1L, workDate, LocalTime.of(8, 0), "IN"),
                buildAttendance(1L, workDate, LocalTime.of(17, 0), "OUT")
        );
        stubCommonDependencies(logs, schedule, HolidayLookup.empty());

        List<PayrollItem> items = new ArrayList<>();

        payrollService.processPayrollNonExempt(run, items, List.of(emp));

        Assertions.assertEquals(1, items.size());
        PayrollItem item = items.get(0);
        Assertions.assertEquals(1, item.getEarnings().size());
        assertBigDecimalEquals("26000.00", item.getEarnings().get(0).getAmount());
        Assertions.assertEquals(4, item.getDeductions().size());
        assertBigDecimalEquals("26000.00", item.getGrossPay());
        assertBigDecimalEquals("21895.90", item.getNetPay());
    }

    @Test
    void processPayrollNonExempt_NoLogs_ShouldApplyAbsentDeduction() {
        LocalDate workDate = LocalDate.of(2026, 3, 2);
        Employee emp = buildNonExemptEmployee(1L, new BigDecimal("624000.00"));
        PayrollRun run = buildSingleDayRun(workDate);
        TemplateShiftSchedule schedule = buildSchedule(emp, workDate, LocalTime.of(8, 0), LocalTime.of(17, 0));

        stubCommonDependencies(List.of(), schedule, HolidayLookup.empty(), List.of(), List.of());

        List<PayrollItem> items = new ArrayList<>();
        payrollService.processPayrollNonExempt(run, items, List.of(emp));

        Assertions.assertEquals(1, items.size());
        PayrollItem item = items.get(0);

        DeductionsLine absentLine = item.getDeductions().stream()
                .filter(d -> d.getDeductionType() == DeductionType.ABSENT_DEDUCTION)
                .findFirst()
                .orElseThrow();
        assertBigDecimalEquals("2000.00", absentLine.getAmount());
        assertBigDecimalEquals("26000.00", item.getGrossPay());
        assertBigDecimalEquals("20295.90", item.getNetPay());
    }

    @Test
    void processPayrollNonExempt_NoLogsWithPartialLeave_ShouldApplyAbsenceNotLatePenalty() {
        LocalDate workDate = LocalDate.of(2026, 3, 2);
        Employee emp = buildNonExemptEmployee(1L, new BigDecimal("624000.00"));
        PayrollRun run = buildSingleDayRun(workDate);
        TemplateShiftSchedule schedule = buildSchedule(emp, workDate, LocalTime.of(8, 0), LocalTime.of(17, 0));
        LeaveRequest leave = new LeaveRequest();
        leave.setEmployee(emp);
        leave.setStartDateTime(LocalDateTime.of(LocalDate.now(), LocalTime.of(80, 0)));
        leave.setStartDateTime(LocalDateTime.of(LocalDate.now(), LocalTime.of(10, 0)));

        stubCommonDependencies(List.of(), schedule, HolidayLookup.empty(), List.of(), List.of(leave));

        List<PayrollItem> items = new ArrayList<>();
        payrollService.processPayrollNonExempt(run, items, List.of(emp));

        Assertions.assertEquals(1, items.size());
        PayrollItem item = items.get(0);

        DeductionsLine absentLine = item.getDeductions().stream()
                .filter(d -> d.getDeductionType() == DeductionType.ABSENT_DEDUCTION)
                .findFirst()
                .orElseThrow();

        assertBigDecimalEquals("1500.00", absentLine.getAmount());
        Assertions.assertTrue(item.getDeductions().stream().noneMatch(d -> d.getDeductionType() == DeductionType.LATE_PENALTY));
        assertBigDecimalEquals("26000.00", item.getGrossPay());
        assertBigDecimalEquals("20695.90", item.getNetPay());
    }

    @Test
    void processPayrollNonExempt_FourteenScheduledDays_ShouldCapAttendanceDeductionsAtBasicPay() {
        Employee emp = buildNonExemptEmployee(1L, new BigDecimal("2000000.00"));
        PayrollRun run = new PayrollRun();
        run.setPeriodStart(LocalDate.of(2026, 3, 1));
        run.setPeriodEnd(LocalDate.of(2026, 3, 14));

        List<TemplateShiftSchedule> weeklySchedules = buildWeeklySchedules(emp, LocalTime.of(8, 0), LocalTime.of(17, 0));
        stubCommonDependencies(List.of(), weeklySchedules, HolidayLookup.empty(), List.of(), List.of());

        List<PayrollItem> items = new ArrayList<>();
        payrollService.processPayrollNonExempt(run, items, List.of(emp));

        Assertions.assertEquals(1, items.size());
        PayrollItem item = items.get(0);

        DeductionsLine absentLine = item.getDeductions().stream()
                .filter(d -> d.getDeductionType() == DeductionType.ABSENT_DEDUCTION)
                .findFirst()
                .orElseThrow();
        DeductionsLine sssLine = item.getDeductions().stream()
                .filter(d -> d.getDeductionType() == DeductionType.SSS)
                .findFirst()
                .orElseThrow();
        DeductionsLine philHealthLine = item.getDeductions().stream()
                .filter(d -> d.getDeductionType() == DeductionType.PHILHEALTH)
                .findFirst()
                .orElseThrow();
        DeductionsLine pagibigLine = item.getDeductions().stream()
                .filter(d -> d.getDeductionType() == DeductionType.PAGIBIG)
                .findFirst()
                .orElseThrow();

        assertBigDecimalEquals("83333.33", absentLine.getAmount());
        assertBigDecimalEquals("875.00", sssLine.getAmount());
        assertBigDecimalEquals("1250.00", philHealthLine.getAmount());
        assertBigDecimalEquals("100.00", pagibigLine.getAmount());
        Assertions.assertTrue(item.getDeductions().stream().noneMatch(d -> d.getDeductionType() == DeductionType.LATE_PENALTY));
        Assertions.assertTrue(item.getDeductions().stream().noneMatch(d -> d.getDeductionType() == DeductionType.BRACKET_LEVEL_FOUR));
        Assertions.assertTrue(absentLine.getAmount().compareTo(item.getGrossPay()) <= 0);
        assertBigDecimalEquals("-2225.00", item.getNetPay());
    }

    @Test
    void processPayrollNonExempt_LateOrEarlyOut_ShouldApplyLatePenalty() {
        LocalDate workDate = LocalDate.of(2026, 3, 2);
        Employee emp = buildNonExemptEmployee(1L, new BigDecimal("624000.00"));
        PayrollRun run = buildSingleDayRun(workDate);
        TemplateShiftSchedule schedule = buildSchedule(emp, workDate, LocalTime.of(8, 0), LocalTime.of(17, 0));
        List<AttendanceLog> logs = List.of(
                buildAttendance(1L, workDate, LocalTime.of(9, 0), "IN"),
                buildAttendance(1L, workDate, LocalTime.of(17, 0), "OUT")
        );

        stubCommonDependencies(logs, schedule, HolidayLookup.empty(), List.of(), List.of());

        List<PayrollItem> items = new ArrayList<>();
        payrollService.processPayrollNonExempt(run, items, List.of(emp));

        Assertions.assertEquals(1, items.size());
        PayrollItem item = items.get(0);

        DeductionsLine lateLine = item.getDeductions().stream()
                .filter(d -> d.getDeductionType() == DeductionType.LATE_PENALTY)
                .findFirst()
                .orElseThrow();
        assertBigDecimalEquals("187.50", lateLine.getAmount());
        assertBigDecimalEquals("26000.00", item.getGrossPay());
        assertBigDecimalEquals("21745.90", item.getNetPay());
    }

    @Test
    void processPayrollNonExempt_WithinGracePeriod_ShouldNotApplyLatePenalty() {
        LocalDate workDate = LocalDate.of(2026, 3, 2);
        Employee emp = buildNonExemptEmployee(1L, new BigDecimal("624000.00"));
        PayrollRun run = buildSingleDayRun(workDate);
        TemplateShiftSchedule schedule = buildSchedule(emp, workDate, LocalTime.of(8, 0), LocalTime.of(17, 0));
        List<AttendanceLog> logs = List.of(
                buildAttendance(1L, workDate, LocalTime.of(8, 15), "IN"),
                buildAttendance(1L, workDate, LocalTime.of(17, 0), "OUT")
        );

        stubCommonDependencies(logs, schedule, HolidayLookup.empty(), List.of(), List.of());

        List<PayrollItem> items = new ArrayList<>();
        payrollService.processPayrollNonExempt(run, items, List.of(emp));

        Assertions.assertEquals(1, items.size());
        PayrollItem item = items.get(0);

        Assertions.assertTrue(item.getDeductions().stream().noneMatch(d -> d.getDeductionType() == DeductionType.LATE_PENALTY));
        assertBigDecimalEquals("26000.00", item.getGrossPay());
        assertBigDecimalEquals("21895.90", item.getNetPay());
    }

    @Test
    void processPayrollNonExempt_TwentyMinuteLate_ShouldRoundChargeableMinutesToFive() {
        LocalDate workDate = LocalDate.of(2026, 3, 2);
        Employee emp = buildNonExemptEmployee(1L, new BigDecimal("624000.00"));
        PayrollRun run = buildSingleDayRun(workDate);
        TemplateShiftSchedule schedule = buildSchedule(emp, workDate, LocalTime.of(8, 0), LocalTime.of(17, 0));
        List<AttendanceLog> logs = List.of(
                buildAttendance(1L, workDate, LocalTime.of(8, 20), "IN"),
                buildAttendance(1L, workDate, LocalTime.of(17, 0), "OUT")
        );

        stubCommonDependencies(logs, schedule, HolidayLookup.empty(), List.of(), List.of());

        List<PayrollItem> items = new ArrayList<>();
        payrollService.processPayrollNonExempt(run, items, List.of(emp));

        Assertions.assertEquals(1, items.size());
        PayrollItem item = items.get(0);

        DeductionsLine lateLine = item.getDeductions().stream()
                .filter(d -> d.getDeductionType() == DeductionType.LATE_PENALTY)
                .findFirst()
                .orElseThrow();
        assertBigDecimalEquals("20.83", lateLine.getAmount());
        assertBigDecimalEquals("26000.00", item.getGrossPay());
        assertBigDecimalEquals("21879.24", item.getNetPay());
    }

    @Test
    void processPayrollNonExempt_GhostEmployee_ShouldKeepStatutoryLinesAndAllowNegativeNetPay() {
        Employee emp = buildNonExemptEmployee(1L, new BigDecimal("480000.00"));
        PayrollRun run = new PayrollRun();
        run.setPeriodStart(LocalDate.of(2026, 3, 1));
        run.setPeriodEnd(LocalDate.of(2026, 3, 14));

        List<TemplateShiftSchedule> weeklySchedules = buildWeeklySchedules(emp, LocalTime.of(8, 0), LocalTime.of(17, 0));
        stubCommonDependencies(List.of(), weeklySchedules, HolidayLookup.empty(), List.of(), List.of());

        List<PayrollItem> items = new ArrayList<>();
        payrollService.processPayrollNonExempt(run, items, List.of(emp));

        Assertions.assertEquals(1, items.size());
        PayrollItem item = items.get(0);

        DeductionsLine absentLine = item.getDeductions().stream()
                .filter(d -> d.getDeductionType() == DeductionType.ABSENT_DEDUCTION)
                .findFirst()
                .orElseThrow();
        DeductionsLine sssLine = item.getDeductions().stream()
                .filter(d -> d.getDeductionType() == DeductionType.SSS)
                .findFirst()
                .orElseThrow();
        DeductionsLine philHealthLine = item.getDeductions().stream()
                .filter(d -> d.getDeductionType() == DeductionType.PHILHEALTH)
                .findFirst()
                .orElseThrow();
        DeductionsLine pagibigLine = item.getDeductions().stream()
                .filter(d -> d.getDeductionType() == DeductionType.PAGIBIG)
                .findFirst()
                .orElseThrow();

        assertBigDecimalEquals("20000.00", item.getGrossPay());
        assertBigDecimalEquals("20000.00", absentLine.getAmount());
        assertBigDecimalEquals("875.00", sssLine.getAmount());
        assertBigDecimalEquals("500.00", philHealthLine.getAmount());
        assertBigDecimalEquals("100.00", pagibigLine.getAmount());
        Assertions.assertTrue(item.getDeductions().stream().noneMatch(d -> d.getDeductionType() == DeductionType.LATE_PENALTY));
        Assertions.assertTrue(item.getDeductions().stream().noneMatch(d -> d.getDeductionType() == DeductionType.BRACKET_LEVEL_ONE));
        Assertions.assertTrue(item.getDeductions().stream().noneMatch(d -> d.getDeductionType() == DeductionType.BRACKET_LEVEL_TWO));
        Assertions.assertTrue(item.getDeductions().stream().noneMatch(d -> d.getDeductionType() == DeductionType.BRACKET_LEVEL_THREE));
        Assertions.assertTrue(item.getDeductions().stream().noneMatch(d -> d.getDeductionType() == DeductionType.BRACKET_LEVEL_FOUR));
        Assertions.assertTrue(item.getDeductions().stream().noneMatch(d -> d.getDeductionType() == DeductionType.BRACKET_LEVEL_FIVE));
        Assertions.assertTrue(item.getDeductions().stream().noneMatch(d -> d.getDeductionType() == DeductionType.BRACKET_LEVEL_SIX));
        assertBigDecimalEquals("-1475.00", item.getNetPay());
    }

    @Test
    void processPayrollNonExempt_AlmostGhost_ShouldKeepFullStatutoryLinesAndAllowNegativeNetPay() {
        Employee emp = buildNonExemptEmployee(1L, new BigDecimal("360000.00"));
        PayrollRun run = new PayrollRun();
        run.setPeriodStart(LocalDate.of(2026, 3, 1));
        run.setPeriodEnd(LocalDate.of(2026, 3, 14));

        List<TemplateShiftSchedule> weeklySchedules = buildWeeklySchedulesWithoutSunday(emp, LocalTime.of(8, 0), LocalTime.of(17, 0));
        stubCommonDependencies(List.of(), weeklySchedules, HolidayLookup.empty(), List.of(), List.of());

        List<PayrollItem> items = new ArrayList<>();
        payrollService.processPayrollNonExempt(run, items, List.of(emp));

        Assertions.assertEquals(1, items.size());
        PayrollItem item = items.get(0);

        DeductionsLine absentLine = item.getDeductions().stream()
                .filter(d -> d.getDeductionType() == DeductionType.ABSENT_DEDUCTION)
                .findFirst()
                .orElseThrow();
        DeductionsLine sssLine = item.getDeductions().stream()
                .filter(d -> d.getDeductionType() == DeductionType.SSS)
                .findFirst()
                .orElseThrow();
        DeductionsLine philHealthLine = item.getDeductions().stream()
                .filter(d -> d.getDeductionType() == DeductionType.PHILHEALTH)
                .findFirst()
                .orElseThrow();
        DeductionsLine pagibigLine = item.getDeductions().stream()
                .filter(d -> d.getDeductionType() == DeductionType.PAGIBIG)
                .findFirst()
                .orElseThrow();

        assertBigDecimalEquals("15000.00", item.getGrossPay());
        assertBigDecimalEquals("13846.20", absentLine.getAmount());
        assertBigDecimalEquals("750.00", sssLine.getAmount());
        assertBigDecimalEquals("375.00", philHealthLine.getAmount());
        assertBigDecimalEquals("100.00", pagibigLine.getAmount());
        Assertions.assertTrue(item.getDeductions().stream().noneMatch(d -> d.getDeductionType() == DeductionType.BRACKET_LEVEL_ONE));
        Assertions.assertTrue(item.getDeductions().stream().noneMatch(d -> d.getDeductionType() == DeductionType.BRACKET_LEVEL_TWO));
        Assertions.assertTrue(item.getDeductions().stream().noneMatch(d -> d.getDeductionType() == DeductionType.BRACKET_LEVEL_THREE));
        Assertions.assertTrue(item.getDeductions().stream().noneMatch(d -> d.getDeductionType() == DeductionType.BRACKET_LEVEL_FOUR));
        Assertions.assertTrue(item.getDeductions().stream().noneMatch(d -> d.getDeductionType() == DeductionType.BRACKET_LEVEL_FIVE));
        Assertions.assertTrue(item.getDeductions().stream().noneMatch(d -> d.getDeductionType() == DeductionType.BRACKET_LEVEL_SIX));
        assertBigDecimalEquals("-71.20", item.getNetPay());
    }

    @Test
    void processPayrollNonExempt_MinimumWage_ShouldApplyStatutoryOnlyWithZeroWithholdingTax() {
        LocalDate workDate = LocalDate.of(2026, 3, 2);
        Employee emp = buildNonExemptEmployee(1L, new BigDecimal("192000.00"));
        PayrollRun run = buildSingleDayRun(workDate);

        stubCommonDependencies(List.of(), List.of(), HolidayLookup.empty(), List.of(), List.of());

        List<PayrollItem> items = new ArrayList<>();
        payrollService.processPayrollNonExempt(run, items, List.of(emp));

        Assertions.assertEquals(1, items.size());
        PayrollItem item = items.get(0);

        DeductionsLine sssLine = item.getDeductions().stream()
                .filter(d -> d.getDeductionType() == DeductionType.SSS)
                .findFirst()
                .orElseThrow();
        DeductionsLine philHealthLine = item.getDeductions().stream()
                .filter(d -> d.getDeductionType() == DeductionType.PHILHEALTH)
                .findFirst()
                .orElseThrow();
        DeductionsLine pagibigLine = item.getDeductions().stream()
                .filter(d -> d.getDeductionType() == DeductionType.PAGIBIG)
                .findFirst()
                .orElseThrow();

        assertBigDecimalEquals("8000.00", item.getGrossPay());
        assertBigDecimalEquals("400.00", sssLine.getAmount());
        assertBigDecimalEquals("200.00", philHealthLine.getAmount());
        assertBigDecimalEquals("100.00", pagibigLine.getAmount());
        Assertions.assertTrue(item.getDeductions().stream().noneMatch(d -> d.getDeductionType() == DeductionType.BRACKET_LEVEL_ONE));
        Assertions.assertTrue(item.getDeductions().stream().noneMatch(d -> d.getDeductionType() == DeductionType.BRACKET_LEVEL_TWO));
        Assertions.assertTrue(item.getDeductions().stream().noneMatch(d -> d.getDeductionType() == DeductionType.BRACKET_LEVEL_THREE));
        Assertions.assertTrue(item.getDeductions().stream().noneMatch(d -> d.getDeductionType() == DeductionType.BRACKET_LEVEL_FOUR));
        Assertions.assertTrue(item.getDeductions().stream().noneMatch(d -> d.getDeductionType() == DeductionType.BRACKET_LEVEL_FIVE));
        Assertions.assertTrue(item.getDeductions().stream().noneMatch(d -> d.getDeductionType() == DeductionType.BRACKET_LEVEL_SIX));
        assertBigDecimalEquals("7300.00", item.getNetPay());
    }

    @Test
    void processPayrollNonExempt_MessyTimesheet_ShouldApplyLateOvertimeAndBracketThreeTax() {
        LocalDate workDate = LocalDate.of(2026, 3, 2);
        Employee emp = buildNonExemptEmployee(1L, new BigDecimal("600000.00"));
        PayrollRun run = buildSingleDayRun(workDate);
        TemplateShiftSchedule schedule = buildSchedule(emp, workDate, LocalTime.of(8, 0), LocalTime.of(17, 0));
        List<AttendanceLog> logs = List.of(
                buildAttendance(1L, workDate, LocalTime.of(9, 0), "IN"),
                buildAttendance(1L, workDate, LocalTime.of(16, 0), "OUT")
        );
        OvertimeRequest ot = buildOvertime(1L, workDate, LocalTime.of(18, 0), LocalTime.of(22, 0));

        stubCommonDependencies(logs, schedule, HolidayLookup.empty(), List.of(ot), List.of());

        List<PayrollItem> items = new ArrayList<>();
        payrollService.processPayrollNonExempt(run, items, List.of(emp));

        Assertions.assertEquals(1, items.size());
        PayrollItem item = items.get(0);

        DeductionsLine lateLine = item.getDeductions().stream()
                .filter(d -> d.getDeductionType() == DeductionType.LATE_PENALTY)
                .findFirst()
                .orElseThrow();
        DeductionsLine bracketLine = item.getDeductions().stream()
                .filter(d -> d.getDeductionType() == DeductionType.BRACKET_LEVEL_THREE)
                .findFirst()
                .orElseThrow();

        assertBigDecimalEquals("420.67", lateLine.getAmount());
        assertBigDecimalEquals("2440.35", bracketLine.getAmount());
        assertBigDecimalEquals("26201.93", item.getGrossPay());
        assertBigDecimalEquals("21740.91", item.getNetPay());
    }

    @Test
    void processPayrollNonExempt_ExecutiveBracketJump_ShouldCapStatutoryAndUseBracketFiveTax() {
        Employee emp = buildNonExemptEmployee(1L, new BigDecimal("1440000.00"));
        PayrollRun run = new PayrollRun();
        run.setPeriodStart(LocalDate.of(2026, 3, 1));
        run.setPeriodEnd(LocalDate.of(2026, 3, 14));

        List<OvertimeRequest> overtimeRequests = buildDailyOvertime(emp.getEmployeeId(), run.getPeriodStart(), run.getPeriodEnd(), LocalTime.of(18, 0), LocalTime.of(22, 0));
        stubCommonDependencies(List.of(), List.of(), HolidayLookup.empty(), overtimeRequests, List.of());

        List<PayrollItem> items = new ArrayList<>();
        payrollService.processPayrollNonExempt(run, items, List.of(emp));

        Assertions.assertEquals(1, items.size());
        PayrollItem item = items.get(0);

        DeductionsLine philHealthLine = item.getDeductions().stream()
                .filter(d -> d.getDeductionType() == DeductionType.PHILHEALTH)
                .findFirst()
                .orElseThrow();
        DeductionsLine bracketLine = item.getDeductions().stream()
                .filter(d -> d.getDeductionType() == DeductionType.BRACKET_LEVEL_FIVE)
                .findFirst()
                .orElseThrow();

        assertBigDecimalEquals("1250.00", philHealthLine.getAmount());
        assertBigDecimalEquals("24853.42", bracketLine.getAmount());
        assertBigDecimalEquals("112499.95", item.getGrossPay());
        assertBigDecimalEquals("85421.53", item.getNetPay());
    }

    @Test
    void processPayrollExempt_ShouldSetNetPayAsGrossMinusDeductions() {
        Employee exempt = new Employee();
        ReflectionTestUtils.setField(exempt, "employeeId", 9L);
        exempt.setExemptionStatus(ExemptionStatus.EXEMPT);
        exempt.setYearlySalary(new BigDecimal("624000.00"));
        PayrollRun run = buildSingleDayRun(LocalDate.of(2026, 3, 2));

        stubExemptDependencies(List.of(), List.of(), List.of());

        List<PayrollItem> items = payrollService.processPayrollExempt(run, List.of(exempt));

        Assertions.assertEquals(1, items.size());
        PayrollItem item = items.get(0);
        Assertions.assertEquals(1, item.getEarnings().size());
        Assertions.assertEquals(4, item.getDeductions().size());
        assertBigDecimalEquals("26000.00", item.getEarnings().get(0).getAmount());
        Assertions.assertFalse(item.getEarnings().get(0).getOvertime());
        Assertions.assertTrue(item.getDeductions().stream().anyMatch(d -> d.getDeductionType() == DeductionType.SSS));
        Assertions.assertTrue(item.getDeductions().stream().anyMatch(d -> d.getDeductionType() == DeductionType.PHILHEALTH));
        Assertions.assertTrue(item.getDeductions().stream().anyMatch(d -> d.getDeductionType() == DeductionType.PAGIBIG));
        Assertions.assertTrue(item.getDeductions().stream().anyMatch(d -> d.getDeductionType() == DeductionType.BRACKET_LEVEL_THREE));
        assertBigDecimalEquals("26000.00", item.getGrossPay());
        assertBigDecimalEquals("21895.90", item.getNetPay());
        assertBigDecimalEquals("21895.90", item.getGrossPay().subtract(
                item.getDeductions().stream()
                        .map(DeductionsLine::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
        ));
    }

    @Test
    void processPayrollExempt_NoLogs_ShouldStoreAbsentDeductionLine() {
        LocalDate workDate = LocalDate.of(2026, 3, 2);
        Employee exempt = new Employee();
        ReflectionTestUtils.setField(exempt, "employeeId", 10L);
        exempt.setExemptionStatus(ExemptionStatus.EXEMPT);
        exempt.setYearlySalary(new BigDecimal("624000.00"));
        PayrollRun run = buildSingleDayRun(workDate);
        TemplateShiftSchedule schedule = buildSchedule(exempt, workDate, LocalTime.of(8, 0), LocalTime.of(17, 0));

        stubExemptDependencies(List.of(), List.of(schedule), List.of());

        List<PayrollItem> items = payrollService.processPayrollExempt(run, List.of(exempt));

        Assertions.assertEquals(1, items.size());
        PayrollItem item = items.get(0);

        DeductionsLine absentLine = item.getDeductions().stream()
                .filter(d -> d.getDeductionType() == DeductionType.ABSENT_DEDUCTION)
                .findFirst()
                .orElseThrow();

        assertBigDecimalEquals("2000.00", absentLine.getAmount());
        assertBigDecimalEquals("26000.00", item.getGrossPay());
        assertBigDecimalEquals("20295.90", item.getNetPay());
    }

    @Test
    void processPayrollExempt_LateArrival_ShouldNotStoreLatePenaltyLine() {
        LocalDate workDate = LocalDate.of(2026, 3, 2);
        Employee exempt = new Employee();
        ReflectionTestUtils.setField(exempt, "employeeId", 11L);
        exempt.setExemptionStatus(ExemptionStatus.EXEMPT);
        exempt.setYearlySalary(new BigDecimal("624000.00"));
        PayrollRun run = buildSingleDayRun(workDate);
        TemplateShiftSchedule schedule = buildSchedule(exempt, workDate, LocalTime.of(8, 0), LocalTime.of(17, 0));
        List<AttendanceLog> logs = List.of(
                buildAttendance(11L, workDate, LocalTime.of(9, 0), "IN"),
                buildAttendance(11L, workDate, LocalTime.of(17, 0), "OUT")
        );

        stubExemptDependencies(logs, List.of(schedule), List.of());

        List<PayrollItem> items = payrollService.processPayrollExempt(run, List.of(exempt));

        Assertions.assertEquals(1, items.size());
        PayrollItem item = items.get(0);

        Assertions.assertTrue(item.getDeductions().stream().noneMatch(d -> d.getDeductionType() == DeductionType.LATE_PENALTY));
        Assertions.assertTrue(item.getDeductions().stream().noneMatch(d -> d.getDeductionType() == DeductionType.ABSENT_DEDUCTION));
        assertBigDecimalEquals("26000.00", item.getGrossPay());
        assertBigDecimalEquals("21895.90", item.getNetPay());
    }

    @Test
    void processPayrollNonExempt_ApprovedOvertime_ShouldCreateOvertimeEarningsLine() {
        LocalDate workDate = LocalDate.of(2026, 3, 2);
        Employee emp = buildNonExemptEmployee(1L, new BigDecimal("624000.00"));
        PayrollRun run = buildSingleDayRun(workDate);
        TemplateShiftSchedule schedule = buildSchedule(emp, workDate, LocalTime.of(8, 0), LocalTime.of(17, 0));
        List<AttendanceLog> logs = List.of(
                buildAttendance(1L, workDate, LocalTime.of(8, 0), "IN"),
                buildAttendance(1L, workDate, LocalTime.of(17, 0), "OUT")
        );
        OvertimeRequest ot = buildOvertime(1L, workDate, LocalTime.of(18, 0), LocalTime.of(20, 0));

        stubCommonDependencies(logs, schedule, HolidayLookup.empty(), List.of(ot), List.of());

        List<PayrollItem> items = new ArrayList<>();
        payrollService.processPayrollNonExempt(run, items, List.of(emp));

        Assertions.assertEquals(1, items.size());
        PayrollItem item = items.get(0);
        EarningsLine overtimeLine = item.getEarnings().stream()
                .filter(EarningsLine::getOvertime)
                .findFirst()
                .orElseThrow();

        assertBigDecimalEquals("2.00", overtimeLine.getHours());
        assertBigDecimalEquals("312.50", overtimeLine.getRate());
        assertBigDecimalEquals("625.00", overtimeLine.getAmount());
        assertBigDecimalEquals("26625.00", item.getGrossPay());
        assertBigDecimalEquals("22395.90", item.getNetPay());
    }

    @Test
    void processPayrollNonExempt_RestDayOvertime_ShouldStackRestDayAndOvertimeMultipliers() {
        LocalDate workDate = LocalDate.of(2026, 3, 2);
        Employee emp = buildNonExemptEmployee(1L, new BigDecimal("624000.00"));
        PayrollRun run = buildSingleDayRun(workDate);
        List<AttendanceLog> logs = List.of(
                buildAttendance(1L, workDate, LocalTime.of(8, 0), "IN"),
                buildAttendance(1L, workDate, LocalTime.of(20, 0), "OUT")
        );
        OvertimeRequest ot = buildOvertime(1L, workDate, LocalTime.of(18, 0), LocalTime.of(20, 0));

        stubCommonDependencies(logs, List.of(), HolidayLookup.empty(), List.of(ot), List.of());

        List<PayrollItem> items = new ArrayList<>();
        payrollService.processPayrollNonExempt(run, items, List.of(emp));

        Assertions.assertEquals(1, items.size());
        PayrollItem item = items.get(0);
        EarningsLine overtimeLine = item.getEarnings().stream()
                .filter(EarningsLine::getOvertime)
                .findFirst()
                .orElseThrow();

        assertBigDecimalEquals("2.00", overtimeLine.getHours());
        assertBigDecimalEquals("406.25", overtimeLine.getRate());
        assertBigDecimalEquals("812.50", overtimeLine.getAmount());
    }

    private void assertBigDecimalEquals(String expected, BigDecimal actual) {
        Assertions.assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }

    private void stubCommonDependencies(
            List<AttendanceLog> logs,
            TemplateShiftSchedule schedule,
            HolidayLookup holidayLookup
    ) {
        stubCommonDependencies(logs, schedule, holidayLookup, List.of(), List.of());
    }

    private void stubCommonDependencies(
            List<AttendanceLog> logs,
            TemplateShiftSchedule schedule,
            HolidayLookup holidayLookup,
            List<OvertimeRequest> overtimeRequests,
            List<LeaveRequest> leaves
    ) {
        stubCommonDependencies(logs, List.of(schedule), holidayLookup, overtimeRequests, leaves);
    }

    private void stubCommonDependencies(
            List<AttendanceLog> logs,
            List<TemplateShiftSchedule> schedules,
            HolidayLookup holidayLookup,
            List<OvertimeRequest> overtimeRequests,
            List<LeaveRequest> leaves
    ) {
        when(defaultProperties.getTimeZone()).thenReturn(ZONE);
        when(attendanceLogRepository.findByEmployeeIdInAndTimestampBetween(anyList(), any(Instant.class), any(Instant.class), any()))
                .thenReturn(logs);
        when(overtimeRequestRepository.findAll(any(Specification.class)))
                .thenReturn(overtimeRequests);
        when(templateShiftRepository.findByEmployee_EmployeeIdIn(anyList()))
                .thenReturn(schedules);
        when(leaveRequestRepository.findApprovedLeavesForPeriod(anyList(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(leaves);
        when(holidayService.getHolidayLookup(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(holidayLookup);
    }

    private void stubExemptDependencies(
            List<AttendanceLog> logs,
            List<TemplateShiftSchedule> schedules,
            List<LeaveRequest> leaves
    ) {
        when(defaultProperties.getTimeZone()).thenReturn(ZONE);
        when(attendanceLogRepository.findByEmployeeIdInAndTimestampBetween(anyList(), any(Instant.class), any(Instant.class), any()))
                .thenReturn(logs);
        when(templateShiftRepository.findByEmployee_EmployeeIdIn(anyList()))
                .thenReturn(schedules);
        when(leaveRequestRepository.findApprovedLeavesForPeriod(anyList(), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(leaves);
        when(holidayService.getHolidayLookup(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(HolidayLookup.empty());
    }

    private PayrollRun buildSingleDayRun(LocalDate date) {
        PayrollRun run = new PayrollRun();
        run.setPeriodStart(date);
        run.setPeriodEnd(date);
        return run;
    }

    private Employee buildNonExemptEmployee(Long employeeId, BigDecimal yearlySalary) {
        Employee emp = new Employee();
        ReflectionTestUtils.setField(emp, "employeeId", employeeId);
        emp.setExemptionStatus(ExemptionStatus.NON_EXEMPT);
        emp.setYearlySalary(yearlySalary);
        return emp;
    }

    private TemplateShiftSchedule buildSchedule(Employee emp, LocalDate date, LocalTime start, LocalTime end) {
        TemplateShiftSchedule schedule = new TemplateShiftSchedule();
        schedule.setEmployee(emp);
        schedule.setDayOfWeek(date.getDayOfWeek());
        schedule.setStartTime(start);
        schedule.setEndTime(end);
        return schedule;
    }

    private List<TemplateShiftSchedule> buildWeeklySchedules(Employee emp, LocalTime start, LocalTime end) {
        return Arrays.stream(DayOfWeek.values())
                .map(day -> {
                    TemplateShiftSchedule schedule = new TemplateShiftSchedule();
                    schedule.setEmployee(emp);
                    schedule.setDayOfWeek(day);
                    schedule.setStartTime(start);
                    schedule.setEndTime(end);
                    return schedule;
                })
                .toList();
    }

    private List<TemplateShiftSchedule> buildWeeklySchedulesWithoutSunday(Employee emp, LocalTime start, LocalTime end) {
        return Arrays.stream(DayOfWeek.values())
                .filter(day -> day != DayOfWeek.SUNDAY)
                .map(day -> {
                    TemplateShiftSchedule schedule = new TemplateShiftSchedule();
                    schedule.setEmployee(emp);
                    schedule.setDayOfWeek(day);
                    schedule.setStartTime(start);
                    schedule.setEndTime(end);
                    return schedule;
                })
                .toList();
    }

    private AttendanceLog buildAttendance(Long empId, LocalDate date, LocalTime time, String direction) {
        AttendanceLog log = new AttendanceLog();
        log.setEmployeeId(empId);
        log.setDirection(direction);
        log.setTimestamp(date.atTime(time).atZone(ZONE).toInstant());
        return log;
    }

    private OvertimeRequest buildOvertime(
            Long empId,
            LocalDate workDate,
            LocalTime start,
            LocalTime end
    ) {
        OvertimeRequest request = new OvertimeRequest();
        ReflectionTestUtils.setField(request, "employeeId", empId);
        request.setWorkDate(workDate);
        request.setStartTime(workDate.atTime(start));
        request.setEndTime(workDate.atTime(end));
        request.setOvertimeType(EarningsType.OVERTIME);
        return request;
    }

    private List<OvertimeRequest> buildDailyOvertime(
            Long empId,
            LocalDate periodStart,
            LocalDate periodEnd,
            LocalTime start,
            LocalTime end
    ) {
        List<OvertimeRequest> requests = new ArrayList<>();
        LocalDate current = periodStart;
        while (!current.isAfter(periodEnd)) {
            requests.add(buildOvertime(empId, current, start, end));
            current = current.plusDays(1);
        }
        return requests;
    }

}
