package org.example.maridone.payroll;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.example.maridone.config.PayrollConfig;
import org.example.maridone.core.employee.Employee;
import org.example.maridone.enums.DeductionType;
import org.example.maridone.enums.ExemptionStatus;
import org.example.maridone.payroll.item.PayrollItem;
import org.example.maridone.payroll.item.component.DeductionsLine;
import org.example.maridone.payroll.item.component.DeductionsRepository;
import org.example.maridone.payroll.item.component.EarningsLine;
import org.example.maridone.payroll.item.component.EarningsRepository;
import org.example.maridone.payroll.run.PayrollRun;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import static org.mockito.Mockito.verifyNoInteractions;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PayrollCalculatorTest {

    @Mock
    private EarningsRepository earningsRepository;
    @Mock
    private DeductionsRepository deductionsRepository;
    @Mock
    private PayrollConfig payrollConfig;
    @InjectMocks
    private PayrollCalculator payrollCalculator;

    @Spy
    private BracketService bracketService = new BracketService(payrollConfig);

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
        verifyNoInteractions(earningsRepository, deductionsRepository);
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
        verifyNoInteractions(earningsRepository, deductionsRepository);
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
        verifyNoInteractions(earningsRepository, deductionsRepository);
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
        verifyNoInteractions(earningsRepository, deductionsRepository);
    }

    @Test
    void setDeductions_ShouldReturnExpectedLines_WithAttendanceDeductions() {
        Employee emp = buildEmployee(ExemptionStatus.NON_EXEMPT, "624000.00");
        PayrollItem item = buildItem("26000.00", LocalDate.of(2026, 4, 1));

        List<DeductionsLine> deductions = payrollCalculator.setDeductions(
                emp,
                item,
                new BigDecimal("500.25"),
                new BigDecimal("120.75")
        );

        Assertions.assertEquals(6, deductions.size());
        assertDeduction(deductions, DeductionType.SSS, "875.00");
        assertDeduction(deductions, DeductionType.PHILHEALTH, "650.00");
        assertDeduction(deductions, DeductionType.PAGIBIG, "100.00");
        assertDeduction(deductions, DeductionType.BRACKET_LEVEL_THREE, "2354.90");
        assertDeduction(deductions, DeductionType.ABSENT_DEDUCTION, "500.25");
        assertDeduction(deductions, DeductionType.LATE_PENALTY, "120.75");
        deductions.forEach(line -> Assertions.assertSame(item, line.getPayrollItem()));
        verifyNoInteractions(earningsRepository, deductionsRepository);
    }

    @Test
    void setDeductions_ShouldReturnExpectedLines_ForExemptEmployee() {
        Employee emp = buildEmployee(ExemptionStatus.EXEMPT, "624000.00");
        PayrollItem item = buildItem("26000.00", LocalDate.of(2026, 4, 1));

        List<DeductionsLine> deductions = payrollCalculator.setDeductions(
                emp,
                item,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );

        Assertions.assertEquals(4, deductions.size());
        assertDeduction(deductions, DeductionType.SSS, "875.00");
        assertDeduction(deductions, DeductionType.PHILHEALTH, "650.00");
        assertDeduction(deductions, DeductionType.PAGIBIG, "100.00");
        assertDeduction(deductions, DeductionType.BRACKET_LEVEL_THREE, "2479.10");
        deductions.forEach(line -> Assertions.assertSame(item, line.getPayrollItem()));
        verifyNoInteractions(earningsRepository, deductionsRepository);
    }

    @Test
    void setDeductions_ShouldResolveBracketUsingTaxableIncome() {
        Employee emp = buildEmployee(ExemptionStatus.NON_EXEMPT, "820000.00");
        PayrollItem item = buildItem("34166.67", LocalDate.of(2026, 4, 1));

        List<DeductionsLine> deductions = payrollCalculator.setDeductions(
                emp,
                item,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );

        assertDeduction(deductions, DeductionType.BRACKET_LEVEL_THREE, "4071.60");
        Assertions.assertTrue(deductions.stream().noneMatch(d -> d.getDeductionType() == DeductionType.BRACKET_LEVEL_FOUR));
        verifyNoInteractions(earningsRepository, deductionsRepository);
    }

    @Test
    void setDeductions_ShouldReturnEmpty_WhenYearlySalaryIsZeroOrLess() {
        Employee emp = buildEmployee(ExemptionStatus.NON_EXEMPT, "0.00");
        PayrollItem item = buildItem("0.00", LocalDate.of(2026, 4, 1));

        List<DeductionsLine> deductions = payrollCalculator.setDeductions(
                emp,
                item,
                new BigDecimal("500.25"),
                new BigDecimal("120.75")
        );

        Assertions.assertTrue(deductions.isEmpty());
        verifyNoInteractions(earningsRepository, deductionsRepository);
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

    private void assertBigDecimalEquals(String expected, BigDecimal actual) {
        Assertions.assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
