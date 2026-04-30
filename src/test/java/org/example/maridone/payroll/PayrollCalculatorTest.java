package org.example.maridone.payroll;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.example.maridone.config.DefaultConfig;
import org.example.maridone.config.PayrollConfig;
import org.example.maridone.core.employee.Employee;
import org.example.maridone.enums.DeductionType;
import org.example.maridone.enums.ExemptionStatus;
import org.example.maridone.payroll.item.PayrollItem;
import org.example.maridone.payroll.item.component.DeductionsLine;
import org.example.maridone.payroll.item.component.EarningsLine;
import org.example.maridone.payroll.run.PayrollRun;
import org.example.maridone.schedule.shift.DailyShiftSchedule;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayrollCalculatorTest {

    private static final java.time.ZoneId MANILA = java.time.ZoneId.of("Asia/Manila");

    @Mock
    private DefaultConfig defaultConfig;

    private PayrollCalculator payrollCalculator;
    private BracketService bracketService;

    @BeforeEach
    void setUp() {
        PayrollConfig payrollConfig = new PayrollConfig();
        bracketService = new BracketService(payrollConfig);
        payrollCalculator = new PayrollCalculator(
                bracketService,
                defaultConfig,
                payrollConfig
        );
        lenient().when(defaultConfig.getTimeZone()).thenReturn(MANILA);
    }

    @Test
    void setEarnings_ShouldCreateSingleLine_ForExemptEmployee() {
        Employee emp = buildEmployee(ExemptionStatus.EXEMPT, "624000.00");
        PayrollItem item = buildItem("26000.00", LocalDate.of(2026, 3, 15));

        List<EarningsLine> result = payrollCalculator.setEarnings(emp, item);

        Assertions.assertEquals(1, result.size());
        EarningsLine line = result.get(0);
        assertBigDecimalEquals("26000.00", line.getAmount());
        assertBigDecimalEquals("0.00", line.getHours());
        assertBigDecimalEquals("0.00", line.getRate());
        Assertions.assertFalse(line.getOvertime());
        Assertions.assertEquals(LocalDate.of(2026, 3, 15), line.getEarningsDate());
        Assertions.assertSame(item, line.getPayrollItem());
    }

    @Test
    void setEarnings_ShouldCreateSingleLine_ForNonExemptEmployee() {
        Employee emp = buildEmployee(ExemptionStatus.NON_EXEMPT, "624000.00");
        PayrollItem item = buildItem("26000.00", LocalDate.of(2026, 3, 30));

        List<EarningsLine> result = payrollCalculator.setEarnings(emp, item);

        Assertions.assertEquals(1, result.size());
        EarningsLine line = result.get(0);
        assertBigDecimalEquals("26000.00", line.getAmount());
        assertBigDecimalEquals("0.00", line.getHours());
        assertBigDecimalEquals("0.00", line.getRate());
        Assertions.assertFalse(line.getOvertime());
        Assertions.assertEquals(LocalDate.of(2026, 3, 30), line.getEarningsDate());
        Assertions.assertSame(item, line.getPayrollItem());
    }

    @Test
    void setEarnings_ShouldPopulateDefaults_ForPrecomputedLines() {
        Employee emp = buildEmployee(ExemptionStatus.NON_EXEMPT, "624000.00");
        PayrollItem item = buildItem("26000.00", LocalDate.of(2026, 4, 1));

        EarningsLine precomputed = new EarningsLine();
        precomputed.setAmount(new BigDecimal("555.50"));

        List<EarningsLine> result = payrollCalculator.setEarnings(emp, item, List.of(precomputed));

        Assertions.assertEquals(2, result.size());

        EarningsLine baseLine = result.stream()
                .filter(line -> new BigDecimal("26000.00").compareTo(line.getAmount()) == 0)
                .findFirst()
                .orElseThrow();
        assertBigDecimalEquals("26000.00", baseLine.getAmount());
        assertBigDecimalEquals("0.00", baseLine.getHours());
        assertBigDecimalEquals("0.00", baseLine.getRate());
        Assertions.assertFalse(baseLine.getOvertime());
        Assertions.assertEquals(LocalDate.of(2026, 4, 1), baseLine.getEarningsDate());
        Assertions.assertSame(item, baseLine.getPayrollItem());

        EarningsLine premiumLine = result.stream()
                .filter(line -> new BigDecimal("555.50").compareTo(line.getAmount()) == 0)
                .findFirst()
                .orElseThrow();
        assertBigDecimalEquals("555.50", premiumLine.getAmount());
        assertBigDecimalEquals("0.00", premiumLine.getHours());
        assertBigDecimalEquals("0.00", premiumLine.getRate());
        Assertions.assertFalse(premiumLine.getOvertime());
        Assertions.assertEquals(LocalDate.of(2026, 4, 1), premiumLine.getEarningsDate());
        Assertions.assertSame(item, premiumLine.getPayrollItem());
    }

    @Test
    void setEarnings_ShouldReturnBaseLine_WhenPrecomputedLinesAreNullOrEmptyForNonExempt() {
        Employee emp = buildEmployee(ExemptionStatus.NON_EXEMPT, "624000.00");
        PayrollItem item = buildItem("26000.00", LocalDate.of(2026, 4, 1));

        List<EarningsLine> nullResult = payrollCalculator.setEarnings(emp, item, null);
        List<EarningsLine> emptyResult = payrollCalculator.setEarnings(emp, item, List.of());

        Assertions.assertEquals(1, nullResult.size());
        Assertions.assertEquals(1, emptyResult.size());
        assertBigDecimalEquals("26000.00", nullResult.get(0).getAmount());
        assertBigDecimalEquals("26000.00", emptyResult.get(0).getAmount());
    }

    @Test
    void setDeductions_ShouldReturnFirstHalfWithholdingOnly_WithAttendanceDeductions() {
        Employee emp = buildEmployee(ExemptionStatus.NON_EXEMPT, "624000.00");
        PayrollItem item = buildItem("26000.00", LocalDate.of(2026, 4, 15));

        List<DeductionsLine> deductions = payrollCalculator.setDeductions(
                emp,
                item,
                new BigDecimal("500.25"),
                new BigDecimal("120.75")
        );

        Assertions.assertEquals(3, deductions.size());
        assertDeduction(deductions, DeductionType.BRACKET_LEVEL_THREE, "2679.90");
        assertDeduction(deductions, DeductionType.ABSENT_DEDUCTION, "500.25");
        assertDeduction(deductions, DeductionType.LATE_PENALTY, "120.75");
        Assertions.assertTrue(deductions.stream().noneMatch(d -> d.getDeductionType() == DeductionType.SSS));
        Assertions.assertTrue(deductions.stream().noneMatch(d -> d.getDeductionType() == DeductionType.PHILHEALTH));
        Assertions.assertTrue(deductions.stream().noneMatch(d -> d.getDeductionType() == DeductionType.PAGIBIG));
        deductions.forEach(line -> Assertions.assertSame(item, line.getPayrollItem()));
    }

    @Test
    void setDeductions_ShouldReturnMonthlyStatutoryAndSemimonthlyWithholding_OnSecondHalfPayroll() {
        Employee emp = buildEmployee(ExemptionStatus.EXEMPT, "624000.00");
        PayrollItem item = buildItem("26000.00", LocalDate.of(2026, 4, 30));

        List<DeductionsLine> deductions = payrollCalculator.setDeductions(
                emp,
                item,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new PayrollCalculator.RegularTaxContext(
                        new BigDecimal("52000.00"),
                        new BigDecimal("52000.00"),
                        new BigDecimal("26000.00"),
                        new BigDecimal("2479.10"),
                        true
                )
        );

        Assertions.assertEquals(4, deductions.size());
        assertDeduction(deductions, DeductionType.SSS, "1750.00");
        assertDeduction(deductions, DeductionType.PHILHEALTH, "1300.00");
        assertDeduction(deductions, DeductionType.PAGIBIG, "200.00");
        assertDeduction(deductions, DeductionType.BRACKET_LEVEL_THREE, "2479.10");
        deductions.forEach(line -> Assertions.assertSame(item, line.getPayrollItem()));
    }

    @Test
    void computeSssEmployerShareMonthly_ShouldApplyRegularWispAndEcComponents() {
        BigDecimal result = payrollCalculator.computeSssEmployerShareMonthly(new BigDecimal("25000.00"));

        assertBigDecimalEquals("2530.00", result);
    }

    @Test
    void computeSssEmployerShareMonthly_ShouldUseLowerEcBelowThreshold() {
        BigDecimal result = payrollCalculator.computeSssEmployerShareMonthly(new BigDecimal("14500.00"));

        assertBigDecimalEquals("1460.00", result);
    }

    @Test
    void computePhilHealthEmployerShareMonthly_ShouldRespectFloorAndCeiling() {
        assertBigDecimalEquals(
                "250.00",
                payrollCalculator.computePhilHealthEmployerShareMonthly(new BigDecimal("5000.00"))
        );
        assertBigDecimalEquals(
                "2500.00",
                payrollCalculator.computePhilHealthEmployerShareMonthly(new BigDecimal("150000.00"))
        );
    }

    @Test
    void computePagibigEmployerShareMonthly_ShouldCapAtTenThousandMfs() {
        BigDecimal result = payrollCalculator.computePagibigEmployerShareMonthly(new BigDecimal("15000.00"));

        assertBigDecimalEquals("200.00", result);
    }

    @Test
    void setDeductions_ShouldResolveBracketUsingSemimonthlyFirstHalfTaxableIncome() {
        Employee emp = buildEmployee(ExemptionStatus.NON_EXEMPT, "820000.00");
        PayrollItem item = buildItem("34166.67", LocalDate.of(2026, 4, 15));

        List<DeductionsLine> deductions = payrollCalculator.setDeductions(
                emp,
                item,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );

        assertDeduction(deductions, DeductionType.BRACKET_LEVEL_FOUR, "4479.25");
        Assertions.assertTrue(deductions.stream().noneMatch(d -> d.getDeductionType() == DeductionType.BRACKET_LEVEL_THREE));
    }

    @Test
    void setDeductions_ShouldSkipRegularTaxes_WhenPaymentDateIsNot15thOr30th() {
        Employee emp = buildEmployee(ExemptionStatus.NON_EXEMPT, "624000.00");
        PayrollItem item = buildItem("26000.00", LocalDate.of(2026, 4, 1));

        List<DeductionsLine> deductions = payrollCalculator.setDeductions(
                emp,
                item,
                new BigDecimal("500.25"),
                new BigDecimal("120.75")
        );

        Assertions.assertEquals(2, deductions.size());
        assertDeduction(deductions, DeductionType.ABSENT_DEDUCTION, "500.25");
        assertDeduction(deductions, DeductionType.LATE_PENALTY, "120.75");
        Assertions.assertTrue(deductions.stream().noneMatch(d -> d.getDeductionType() == DeductionType.SSS));
        Assertions.assertTrue(deductions.stream().noneMatch(d -> d.getDeductionType() == DeductionType.PHILHEALTH));
        Assertions.assertTrue(deductions.stream().noneMatch(d -> d.getDeductionType() == DeductionType.PAGIBIG));
    }

    @Test
    void setDeductions_ShouldReturnEmpty_WhenYearlySalaryIsZeroOrLess() {
        Employee emp = buildEmployee(ExemptionStatus.NON_EXEMPT, "0.00");
        PayrollItem item = buildItem("0.00", LocalDate.of(2026, 4, 15));

        List<DeductionsLine> deductions = payrollCalculator.setDeductions(
                emp,
                item,
                new BigDecimal("500.25"),
                new BigDecimal("120.75")
        );

        Assertions.assertTrue(deductions.isEmpty());
    }

    @Test
    void calculatePayableScheduledHours_ShouldNotDeductLunchAgain_ForBrokenShift() {
        List<DailyShiftSchedule> schedules = List.of(
                buildDailyShift(LocalDateTime.of(2026, 5, 4, 8, 0), LocalDateTime.of(2026, 5, 4, 12, 0)),
                buildDailyShift(LocalDateTime.of(2026, 5, 4, 13, 0), LocalDateTime.of(2026, 5, 4, 17, 0))
        );

        BigDecimal payableHours = payrollCalculator.calculatePayableScheduledHours(schedules);

        assertBigDecimalEquals("8.00", payableHours);
    }

    @Test
    void calculatePayableWorkedHoursWithinSchedules_ShouldNotDeductLunchAgain_ForBrokenShift() {
        List<DailyShiftSchedule> schedules = List.of(
                buildDailyShift(LocalDateTime.of(2026, 5, 4, 8, 0), LocalDateTime.of(2026, 5, 4, 12, 0)),
                buildDailyShift(LocalDateTime.of(2026, 5, 4, 13, 0), LocalDateTime.of(2026, 5, 4, 17, 0))
        );
        List<PayrollCalculator.AttendanceSession> sessions = List.of(
                buildAttendanceSession(
                        LocalDateTime.of(2026, 5, 4, 8, 0),
                        LocalDateTime.of(2026, 5, 4, 12, 0)
                ),
                buildAttendanceSession(
                        LocalDateTime.of(2026, 5, 4, 13, 0),
                        LocalDateTime.of(2026, 5, 4, 17, 0)
                )
        );

        BigDecimal payableHours = payrollCalculator.calculatePayableWorkedHoursWithinSchedules(schedules, sessions);

        assertBigDecimalEquals("8.00", payableHours);
    }

    private Employee buildEmployee(ExemptionStatus exemptionStatus, String yearlySalary) {
        Employee emp = new Employee();
        emp.setExemptionStatus(exemptionStatus);
        emp.setYearlySalary(new BigDecimal(yearlySalary));
        return emp;
    }

    private PayrollItem buildItem(String grossPay, LocalDate periodEnd) {
        PayrollRun run = new PayrollRun();
        run.setPeriodEnd(periodEnd);
        run.setPaymentDate(periodEnd);

        PayrollItem item = new PayrollItem();
        item.setGrossPay(new BigDecimal(grossPay));
        item.setPayrollRun(run);
        return item;
    }

    private void assertDeduction(List<DeductionsLine> deductions, DeductionType type, String expectedAmount) {
        DeductionsLine line = deductions.stream()
                .filter(d -> d.getDeductionType() == type)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing deduction type: " + type));
        assertBigDecimalEquals(expectedAmount, line.getAmount());
    }

    private DailyShiftSchedule buildDailyShift(LocalDateTime start, LocalDateTime end) {
        DailyShiftSchedule shift = new DailyShiftSchedule();
        shift.setStartDateTime(start);
        shift.setEndDateTime(end);
        return shift;
    }

    private PayrollCalculator.AttendanceSession buildAttendanceSession(LocalDateTime in, LocalDateTime out) {
        org.example.maridone.log.attendance.AttendanceLog inLog = new org.example.maridone.log.attendance.AttendanceLog();
        inLog.setTimestamp(in.atZone(MANILA).toInstant());
        inLog.setDirection("IN");

        org.example.maridone.log.attendance.AttendanceLog outLog = new org.example.maridone.log.attendance.AttendanceLog();
        outLog.setTimestamp(out.atZone(MANILA).toInstant());
        outLog.setDirection("OUT");

        return new PayrollCalculator.AttendanceSession(inLog, outLog);
    }

    private void assertBigDecimalEquals(String expected, BigDecimal actual) {
        Assertions.assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
