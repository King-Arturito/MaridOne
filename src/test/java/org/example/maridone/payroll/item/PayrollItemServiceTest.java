package org.example.maridone.payroll.item;

import org.example.maridone.core.employee.Employee;
import org.example.maridone.core.employee.EmployeeRepository;
import org.example.maridone.enums.Activity;
import org.example.maridone.enums.AdjustmentKind;
import org.example.maridone.enums.DeductionType;
import org.example.maridone.enums.EarningsType;
import org.example.maridone.enums.PaySummaryKind;
import org.example.maridone.enums.PayrollStatus;
import org.example.maridone.enums.RunType;
import org.example.maridone.exception.badrequest.InvalidActionException;
import org.example.maridone.log.ActivityLogRepository;
import org.example.maridone.log.activity.ActivityLog;
import org.example.maridone.payroll.dto.EarningsDto;
import org.example.maridone.payroll.dto.ItemDetailsDto;
import org.example.maridone.payroll.dto.PayTypeSummaryDto;
import org.example.maridone.payroll.dto.PayrollItemDto;
import org.example.maridone.payroll.dto.PayrollLineCorrectionDto;
import org.example.maridone.payroll.item.component.DeductionsLine;
import org.example.maridone.payroll.item.component.DeductionsRepository;
import org.example.maridone.payroll.item.component.DeductionsService;
import org.example.maridone.payroll.item.component.EarningsLine;
import org.example.maridone.payroll.item.component.EarningsRepository;
import org.example.maridone.payroll.item.component.EarningsService;
import org.example.maridone.payroll.item.component.EmployerContributionRepository;
import org.example.maridone.payroll.item.component.EmployerContributionService;
import org.example.maridone.payroll.item.component.PayrollItemTotalsService;
import org.example.maridone.payroll.mapper.PayrollMapper;
import org.example.maridone.payroll.projection.PayTypeSummaryProjection;
import org.example.maridone.payroll.run.PayrollItemRepository;
import org.example.maridone.payroll.run.PayrollRun;
import org.example.maridone.payroll.run.PayrollRunRepository;
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
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class PayrollItemServiceTest {

    @Mock
    private PayrollItemRepository payrollItemRepository;
    @Mock
    private PayrollMapper payrollMapper;
    @Mock
    private PayrollRunRepository payrollRunRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private DeductionsRepository deductionsRepository;
    @Mock
    private EarningsRepository earningsRepository;
    @Mock
    private EarningsService earningsService;
    @Mock
    private DeductionsService deductionsService;
    @Mock
    private EmployerContributionRepository employerContributionRepository;
    @Mock
    private EmployerContributionService employerContributionService;
    @Mock
    private ActivityLogRepository activityLogRepository;
    @Mock
    private PayrollItemTotalsService payrollItemTotalsService;

    @InjectMocks
    private PayrollItemService payrollItemService;

    @Test
    void getItems_ShouldReturnDtos() {
        Long empId = 1L;
        PayrollItem item = new PayrollItem();
        ReflectionTestUtils.setField(item, "itemId", 14L);
        ItemDetailsDto dto = new ItemDetailsDto();
        EarningsDto earningsDto = new EarningsDto();
        earningsDto.setAmount(new BigDecimal("1200.00"));
        org.example.maridone.payroll.dto.DeductionsDto deductionsDto = new org.example.maridone.payroll.dto.DeductionsDto();
        deductionsDto.setAmount(new BigDecimal("200.00"));

        when(payrollItemRepository.findByEmployee_EmployeeIdAndPayrollRun_PayrollStatusOrderByPayrollRun_PeriodEndDescItemIdDesc(
                empId,
                PayrollStatus.POSTED
        )).thenReturn(List.of(item));
        when(payrollMapper.toItemDetailsDto(item)).thenReturn(dto);
        when(earningsService.getEarnings(14L)).thenReturn(List.of(earningsDto));
        when(deductionsService.getDeductions(14L)).thenReturn(List.of(deductionsDto));

        List<ItemDetailsDto> result = payrollItemService.getItems(empId);

        Assertions.assertFalse(result.isEmpty());
        Assertions.assertEquals(new BigDecimal("1200.00"), result.get(0).getGrossPay());
        Assertions.assertEquals(new BigDecimal("1000.00"), result.get(0).getNetPay());
    }

    @Test
    void createItem_ShouldRejectPostedRun() {
        PayrollRun postedRun = new PayrollRun();
        ReflectionTestUtils.setField(postedRun, "payId", 5L);
        postedRun.setPayrollStatus(PayrollStatus.POSTED);

        PayrollItemDto payload = new PayrollItemDto();
        payload.setPayId(5L);
        payload.setEmpId(10L);

        when(payrollRunRepository.findById(5L)).thenReturn(Optional.of(postedRun));

        Assertions.assertThrows(InvalidActionException.class, () -> payrollItemService.createItem(payload));
    }

    @Test
    void getSummarizedRunItems_ShouldIncludeEmployerContributionsAsSeparateSummaryKind() {
        when(deductionsRepository.getSummarizedDeductions(12L)).thenReturn(List.of(
                summaryProjection("SSS", new BigDecimal("20000.00"))
        ));
        when(earningsRepository.getSummarizedEarnings(12L)).thenReturn(List.of(
                summaryProjection("BASIC", new BigDecimal("50000.00"))
        ));
        when(employerContributionRepository.getSummarizedEmployerContributions(12L)).thenReturn(List.of(
                summaryProjection("PHILHEALTH", new BigDecimal("1300.00"))
        ));

        List<PayTypeSummaryDto> result = payrollItemService.getSummarizedRunItems(12L);

        Assertions.assertEquals(3, result.size());
        Assertions.assertEquals("SSS", result.get(0).getPayType());
        Assertions.assertEquals(AdjustmentKind.DEDUCTION, result.get(0).getAdjustment());
        Assertions.assertEquals(PaySummaryKind.DEDUCTION, result.get(0).getSummaryKind());
        Assertions.assertEquals(new BigDecimal("20000.00"), result.get(0).getTotal());
        Assertions.assertEquals("BASIC", result.get(1).getPayType());
        Assertions.assertEquals(AdjustmentKind.EARNING, result.get(1).getAdjustment());
        Assertions.assertEquals(PaySummaryKind.EARNING, result.get(1).getSummaryKind());
        Assertions.assertEquals(new BigDecimal("50000.00"), result.get(1).getTotal());
        Assertions.assertEquals("PHILHEALTH", result.get(2).getPayType());
        Assertions.assertNull(result.get(2).getAdjustment());
        Assertions.assertEquals(PaySummaryKind.EMPLOYER_CONTRIBUTION, result.get(2).getSummaryKind());
        Assertions.assertEquals(new BigDecimal("1300.00"), result.get(2).getTotal());
    }

    @Test
    void addLineCorrection_ShouldCreateDeductionLineInsideAdjustmentRun() {
        PayrollRun targetRun = new PayrollRun();
        ReflectionTestUtils.setField(targetRun, "payId", 5L);
        targetRun.setRunType(RunType.ADJUSTMENT);
        targetRun.setPayrollStatus(PayrollStatus.IN_PROGRESS);
        targetRun.setPeriodEnd(LocalDate.of(2026, 3, 28));

        PayrollRun sourceRun = new PayrollRun();
        sourceRun.setRunType(RunType.REGULAR);
        sourceRun.setPayrollStatus(PayrollStatus.POSTED);

        Employee employee = new Employee();
        ReflectionTestUtils.setField(employee, "employeeId", 10L);

        PayrollItem sourceItem = new PayrollItem();
        ReflectionTestUtils.setField(sourceItem, "itemId", 21L);
        sourceItem.setEmployee(employee);
        sourceItem.setPayrollRun(sourceRun);
        sourceItem.setGrossPay(new BigDecimal("800.00"));
        sourceItem.setNetPay(new BigDecimal("800.00"));

        EarningsLine sourceLine = new EarningsLine();
        ReflectionTestUtils.setField(sourceLine, "earningsId", 77L);
        sourceLine.setPayrollItem(sourceItem);
        sourceLine.setAmount(new BigDecimal("800.00"));
        sourceLine.setHours(BigDecimal.ZERO);
        sourceLine.setRate(BigDecimal.ZERO);
        sourceLine.setOvertime(false);

        EarningsDto sourceLineDto = new EarningsDto();
        sourceLineDto.setEarningsId(77L);
        sourceLineDto.setDescription("Basic Pay");

        PayrollLineCorrectionDto payload = new PayrollLineCorrectionDto();
        payload.setSourceItemId(21L);
        payload.setSourceLineType(AdjustmentKind.EARNING);
        payload.setSourceLineId(77L);
        payload.setCorrectedAmount(new BigDecimal("650.00"));

        ItemDetailsDto dto = new ItemDetailsDto();
        AtomicReference<PayrollItem> savedAdjustmentItem = new AtomicReference<>();
        AtomicReference<DeductionsLine> savedAdjustmentLine = new AtomicReference<>();

        when(payrollRunRepository.findById(5L)).thenReturn(Optional.of(targetRun));
        when(payrollItemRepository.findByItemIdAndPayrollRun_PayrollStatusAndPayrollRun_RunTypeNot(
                21L,
                PayrollStatus.POSTED,
                RunType.ADJUSTMENT
        )).thenReturn(Optional.of(sourceItem));
        when(payrollItemRepository.findByPayrollRun_PayIdAndEmployee_EmployeeId(5L, 10L)).thenReturn(Optional.empty());
        when(payrollItemRepository.save(any(PayrollItem.class))).thenAnswer(invocation -> {
            PayrollItem item = invocation.getArgument(0);
            if (ReflectionTestUtils.getField(item, "itemId") == null) {
                ReflectionTestUtils.setField(item, "itemId", 31L);
            }
            savedAdjustmentItem.set(item);
            return item;
        });
        when(earningsRepository.findById(77L)).thenReturn(Optional.of(sourceLine));
        when(earningsService.getOriginalEarnings(21L)).thenReturn(List.of(sourceLineDto));
        when(deductionsRepository.save(any(DeductionsLine.class))).thenAnswer(invocation -> {
            DeductionsLine line = invocation.getArgument(0);
            if (ReflectionTestUtils.getField(line, "deductionsId") == null) {
                ReflectionTestUtils.setField(line, "deductionsId", 55L);
            }
            savedAdjustmentLine.set(line);
            return line;
        });
        when(activityLogRepository.save(any(ActivityLog.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(payrollMapper.toItemDetailsDto(any(PayrollItem.class))).thenReturn(dto);
        doAnswer(invocation -> {
            PayrollItem adjustmentItem = invocation.getArgument(0);
            adjustmentItem.setGrossPay(new BigDecimal("0.00"));
            adjustmentItem.setNetPay(new BigDecimal("-150.00"));
            return null;
        }).when(payrollItemTotalsService).refreshItem(any(PayrollItem.class));

        ItemDetailsDto result = payrollItemService.addLineCorrection(5L, payload);

        Assertions.assertSame(dto, result);
        Assertions.assertEquals(new BigDecimal("800.00"), sourceLine.getAmount());
        Assertions.assertNull(sourceLine.getOriginalAmount());
        Assertions.assertNull(sourceLine.getAdjustedBy());
        Assertions.assertNull(sourceLine.getAdjustedAt());
        Assertions.assertNotNull(savedAdjustmentLine.get());
        Assertions.assertEquals(DeductionType.MANUAL_ADJUSTMENT, savedAdjustmentLine.get().getDeductionType());
        Assertions.assertEquals(new BigDecimal("150.00"), savedAdjustmentLine.get().getAmount());
        Assertions.assertNotNull(savedAdjustmentItem.get());
        Assertions.assertEquals(new BigDecimal("0.00"), savedAdjustmentItem.get().getGrossPay());
        Assertions.assertEquals(new BigDecimal("-150.00"), savedAdjustmentItem.get().getNetPay());
        verify(activityLogRepository).save(argThat(log ->
                log != null
                        && log.getActivityType() == Activity.MODIFY
                        && log.getMessage() != null
                        && !log.getMessage().startsWith("PAYCORR|")
                        && log.getMessage().contains("emp #10 item #21 Basic Pay line #77 800.00->650.00 in adj run #5")
        ));
    }

    @Test
    void addLineCorrection_ShouldCreateManualEarningLineWithFixedType() {
        PayrollRun targetRun = new PayrollRun();
        ReflectionTestUtils.setField(targetRun, "payId", 7L);
        targetRun.setRunType(RunType.ADJUSTMENT);
        targetRun.setPayrollStatus(PayrollStatus.IN_PROGRESS);
        targetRun.setPeriodEnd(LocalDate.of(2026, 8, 15));

        PayrollRun sourceRun = new PayrollRun();
        sourceRun.setRunType(RunType.REGULAR);
        sourceRun.setPayrollStatus(PayrollStatus.POSTED);

        Employee employee = new Employee();
        ReflectionTestUtils.setField(employee, "employeeId", 12L);

        PayrollItem sourceItem = new PayrollItem();
        ReflectionTestUtils.setField(sourceItem, "itemId", 25L);
        sourceItem.setEmployee(employee);
        sourceItem.setPayrollRun(sourceRun);

        DeductionsLine sourceLine = new DeductionsLine();
        ReflectionTestUtils.setField(sourceLine, "deductionsId", 88L);
        sourceLine.setPayrollItem(sourceItem);
        sourceLine.setDeductionType(DeductionType.MANUAL_ADJUSTMENT);
        sourceLine.setAmount(new BigDecimal("400.00"));

        org.example.maridone.payroll.dto.DeductionsDto sourceLineDto = new org.example.maridone.payroll.dto.DeductionsDto();
        sourceLineDto.setDeductionsId(88L);
        sourceLineDto.setDescription("Manual Adjustment");

        PayrollLineCorrectionDto payload = new PayrollLineCorrectionDto();
        payload.setSourceItemId(25L);
        payload.setSourceLineType(AdjustmentKind.DEDUCTION);
        payload.setSourceLineId(88L);
        payload.setCorrectedAmount(new BigDecimal("250.00"));

        AtomicReference<EarningsLine> savedAdjustmentLine = new AtomicReference<>();

        when(payrollRunRepository.findById(7L)).thenReturn(Optional.of(targetRun));
        when(payrollItemRepository.findByItemIdAndPayrollRun_PayrollStatusAndPayrollRun_RunTypeNot(
                25L,
                PayrollStatus.POSTED,
                RunType.ADJUSTMENT
        )).thenReturn(Optional.of(sourceItem));
        when(payrollItemRepository.findByPayrollRun_PayIdAndEmployee_EmployeeId(7L, 12L)).thenReturn(Optional.empty());
        when(payrollItemRepository.save(any(PayrollItem.class))).thenAnswer(invocation -> {
            PayrollItem item = invocation.getArgument(0);
            if (ReflectionTestUtils.getField(item, "itemId") == null) {
                ReflectionTestUtils.setField(item, "itemId", 44L);
            }
            return item;
        });
        when(deductionsRepository.findById(88L)).thenReturn(Optional.of(sourceLine));
        when(deductionsService.getOriginalDeductions(25L)).thenReturn(List.of(sourceLineDto));
        when(earningsRepository.save(any(EarningsLine.class))).thenAnswer(invocation -> {
            EarningsLine line = invocation.getArgument(0);
            savedAdjustmentLine.set(line);
            return line;
        });
        when(activityLogRepository.save(any(ActivityLog.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(payrollMapper.toItemDetailsDto(any(PayrollItem.class))).thenReturn(new ItemDetailsDto());
        doAnswer(invocation -> null).when(payrollItemTotalsService).refreshItem(any(PayrollItem.class));

        payrollItemService.addLineCorrection(7L, payload);

        Assertions.assertNotNull(savedAdjustmentLine.get());
        Assertions.assertEquals(EarningsType.FIXED, savedAdjustmentLine.get().getEarningsType());
        Assertions.assertEquals(new BigDecimal("150.00"), savedAdjustmentLine.get().getAmount());
        Assertions.assertEquals(new BigDecimal("150.00"), savedAdjustmentLine.get().getTaxableAmount());
        Assertions.assertEquals(new BigDecimal("0.00"), savedAdjustmentLine.get().getNonTaxableAmount());
    }

    private PayTypeSummaryProjection summaryProjection(String type, BigDecimal totalAmount) {
        return new PayTypeSummaryProjection() {
            @Override
            public String getType() {
                return type;
            }

            @Override
            public BigDecimal getTotalAmount() {
                return totalAmount;
            }
        };
    }
}
