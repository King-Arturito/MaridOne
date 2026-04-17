package org.example.maridone.payroll.itemcomponent;

import org.example.maridone.config.PayrollConfig;
import org.example.maridone.core.employee.Employee;
import org.example.maridone.enums.DeductionType;
import org.example.maridone.enums.EarningsType;
import org.example.maridone.enums.RunType;
import org.example.maridone.payroll.BracketService;
import org.example.maridone.payroll.item.PayrollItem;
import org.example.maridone.payroll.item.component.DeductionsLine;
import org.example.maridone.payroll.item.component.DeductionsRepository;
import org.example.maridone.payroll.item.component.EarningsLine;
import org.example.maridone.payroll.item.component.EarningsRepository;
import org.example.maridone.payroll.item.component.PayrollItemTotalsService;
import org.example.maridone.payroll.run.PayrollItemRepository;
import org.example.maridone.payroll.run.PayrollRun;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayrollItemTotalsServiceTest {

    @Mock
    private EarningsRepository earningsRepository;
    @Mock
    private DeductionsRepository deductionsRepository;
    @Mock
    private PayrollItemRepository payrollItemRepository;
    @Mock
    private BracketService bracketService;
    @Mock
    private PayrollConfig payrollConfig;

    @InjectMocks
    private PayrollItemTotalsService payrollItemTotalsService;

    @Test
    void syncBonusTaxationForEmployeeYear_ShouldSplitAnnualCapAcrossMultipleBonusLines() {
        Employee employee = new Employee();
        ReflectionTestUtils.setField(employee, "employeeId", 14L);

        PayrollItem firstItem = new PayrollItem();
        ReflectionTestUtils.setField(firstItem, "itemId", 501L);
        PayrollItem secondItem = new PayrollItem();
        ReflectionTestUtils.setField(secondItem, "itemId", 502L);

        EarningsLine firstBonus = new EarningsLine();
        ReflectionTestUtils.setField(firstBonus, "earningsId", 9001L);
        firstBonus.setPayrollItem(firstItem);
        firstBonus.setAmount(new BigDecimal("50000.00"));
        firstBonus.setEarningsType(EarningsType.BONUS);
        firstBonus.setEarningsDate(LocalDate.of(2026, 3, 15));

        EarningsLine secondBonus = new EarningsLine();
        ReflectionTestUtils.setField(secondBonus, "earningsId", 9002L);
        secondBonus.setPayrollItem(secondItem);
        secondBonus.setAmount(new BigDecimal("50000.00"));
        secondBonus.setEarningsType(EarningsType.BONUS);
        secondBonus.setEarningsDate(LocalDate.of(2026, 8, 15));

        when(payrollConfig.getTaxFreeBonusAnnualCap()).thenReturn(new BigDecimal("90000.00"));
        when(earningsRepository.findByPayrollItem_Employee_EmployeeIdAndEarningsTypeAndEarningsDateBetweenOrderByEarningsDateAscEarningsIdAsc(
                14L,
                EarningsType.BONUS,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31)
        )).thenReturn(List.of(firstBonus, secondBonus));
        when(earningsRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(payrollItemRepository.findById(501L)).thenReturn(Optional.empty());
        when(payrollItemRepository.findById(502L)).thenReturn(Optional.empty());

        payrollItemTotalsService.syncBonusTaxationForEmployeeYear(employee, LocalDate.of(2026, 8, 15));

        Assertions.assertEquals(new BigDecimal("50000.00"), firstBonus.getNonTaxableAmount());
        Assertions.assertEquals(new BigDecimal("0.00"), firstBonus.getTaxableAmount());
        Assertions.assertEquals(new BigDecimal("40000.00"), secondBonus.getNonTaxableAmount());
        Assertions.assertEquals(new BigDecimal("10000.00"), secondBonus.getTaxableAmount());
    }

    @Test
    void refreshItem_ShouldCreateWithholdingDeductionForTaxableBonusInAdjustmentRun() {
        PayrollRun run = new PayrollRun();
        run.setRunType(RunType.ADJUSTMENT);

        PayrollItem item = new PayrollItem();
        ReflectionTestUtils.setField(item, "itemId", 601L);
        item.setPayrollRun(run);

        EarningsLine bonusLine = new EarningsLine();
        bonusLine.setAmount(new BigDecimal("100000.00"));
        bonusLine.setEarningsType(EarningsType.BONUS);
        bonusLine.setTaxableAmount(new BigDecimal("10000.00"));
        bonusLine.setNonTaxableAmount(new BigDecimal("90000.00"));

        DeductionsLine manualDeduction = new DeductionsLine();
        manualDeduction.setDeductionType(DeductionType.MANUAL_ADJUSTMENT);
        manualDeduction.setAmount(new BigDecimal("500.00"));

        when(earningsRepository.findByPayrollItem_ItemId(601L)).thenReturn(List.of(bonusLine));
        when(deductionsRepository.findByPayrollItem_ItemId(601L)).thenReturn(List.of(manualDeduction));
        when(bracketService.computeWithholdingTaxPerCutoff(new BigDecimal("10000.00"))).thenReturn(new BigDecimal("1200.00"));
        when(bracketService.resolveWithholdingDeductionType(new BigDecimal("10000.00"))).thenReturn(DeductionType.BRACKET_LEVEL_TWO);
        when(deductionsRepository.save(any(DeductionsLine.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(payrollItemRepository.save(any(PayrollItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        payrollItemTotalsService.refreshItem(item);

        Assertions.assertEquals(new BigDecimal("100000.00"), item.getGrossPay());
        Assertions.assertEquals(new BigDecimal("98300.00"), item.getNetPay());
        Assertions.assertEquals(2, item.getDeductions().size());
        Assertions.assertTrue(item.getDeductions().stream().anyMatch(line ->
                line.getDeductionType() == DeductionType.BRACKET_LEVEL_TWO
                        && new BigDecimal("1200.00").compareTo(line.getAmount()) == 0
        ));
    }

    @Test
    void refreshItem_ShouldCapNetPayAtZeroWhenDeductionsExceedEarnings() {
        PayrollRun run = new PayrollRun();
        run.setRunType(RunType.ADJUSTMENT);

        PayrollItem item = new PayrollItem();
        ReflectionTestUtils.setField(item, "itemId", 602L);
        item.setPayrollRun(run);

        EarningsLine earningLine = new EarningsLine();
        earningLine.setAmount(new BigDecimal("500.00"));
        earningLine.setEarningsType(EarningsType.FIXED);
        earningLine.setTaxableAmount(new BigDecimal("500.00"));
        earningLine.setNonTaxableAmount(BigDecimal.ZERO.setScale(2));

        DeductionsLine manualDeduction = new DeductionsLine();
        manualDeduction.setDeductionType(DeductionType.MANUAL_ADJUSTMENT);
        manualDeduction.setAmount(new BigDecimal("650.00"));

        when(earningsRepository.findByPayrollItem_ItemId(602L)).thenReturn(List.of(earningLine));
        when(deductionsRepository.findByPayrollItem_ItemId(602L)).thenReturn(List.of(manualDeduction));
        when(payrollItemRepository.save(any(PayrollItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        payrollItemTotalsService.refreshItem(item);

        Assertions.assertEquals(new BigDecimal("500.00"), item.getGrossPay());
        Assertions.assertEquals(new BigDecimal("0.00"), item.getNetPay());
    }
}
