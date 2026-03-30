package org.example.maridone.payroll.item;

import org.example.maridone.core.employee.Employee;
import org.example.maridone.core.employee.EmployeeRepository;
import org.example.maridone.enums.Activity;
import org.example.maridone.enums.AdjustmentKind;
import org.example.maridone.enums.PayrollStatus;
import org.example.maridone.enums.RunType;
import org.example.maridone.log.ActivityLogRepository;
import org.example.maridone.log.activity.ActivityLog;
import org.example.maridone.payroll.dto.EarningsDto;
import org.example.maridone.payroll.dto.ItemDetailsDto;
import org.example.maridone.payroll.dto.PayrollLineCorrectionDto;
import org.example.maridone.payroll.item.component.DeductionsRepository;
import org.example.maridone.payroll.item.component.DeductionsService;
import org.example.maridone.payroll.item.component.EarningsLine;
import org.example.maridone.payroll.item.component.EarningsRepository;
import org.example.maridone.payroll.item.component.EarningsService;
import org.example.maridone.payroll.mapper.PayrollMapper;
import org.example.maridone.payroll.run.PayrollItemRepository;
import org.example.maridone.payroll.run.PayrollRun;
import org.example.maridone.payroll.run.PayrollRunRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeast;
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
    private ActivityLogRepository activityLogRepository;

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

        when(payrollItemRepository.findByEmployee_EmployeeIdAndPayrollRun_PayrollStatusAndPayrollRun_RunTypeNotOrderByPayrollRun_PeriodEndDescItemIdDesc(
                empId,
                PayrollStatus.POSTED,
                RunType.ADJUSTMENT
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
    void addLineCorrection_ShouldStageAdjustmentInsideAdjustmentRun() {
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
        AtomicReference<EarningsLine> savedAdjustmentLine = new AtomicReference<>();

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
        when(activityLogRepository.findByActivityTypeAndMessageStartingWithOrderByActivityIdAsc(Activity.MODIFY, "PAYCORR|run=5|"))
                .thenReturn(List.of());
        when(earningsRepository.save(any(EarningsLine.class))).thenAnswer(invocation -> {
            EarningsLine line = invocation.getArgument(0);
            if (ReflectionTestUtils.getField(line, "earningsId") == null) {
                ReflectionTestUtils.setField(line, "earningsId", 44L);
            }
            savedAdjustmentLine.set(line);
            return line;
        });
        when(deductionsRepository.findByPayrollItem_ItemId(31L)).thenReturn(List.of());
        when(earningsRepository.findByPayrollItem_ItemId(31L)).thenAnswer(invocation ->
                savedAdjustmentLine.get() == null ? List.of() : List.of(savedAdjustmentLine.get())
        );
        when(activityLogRepository.save(any(ActivityLog.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(payrollMapper.toItemDetailsDto(any(PayrollItem.class))).thenReturn(dto);

        ItemDetailsDto result = payrollItemService.addLineCorrection(5L, payload);

        Assertions.assertSame(dto, result);
        Assertions.assertEquals(new BigDecimal("800.00"), sourceLine.getAmount());
        Assertions.assertNull(sourceLine.getOriginalAmount());
        Assertions.assertNull(sourceLine.getAdjustedBy());
        Assertions.assertNull(sourceLine.getAdjustedAt());
        Assertions.assertNotNull(savedAdjustmentLine.get());
        Assertions.assertEquals(new BigDecimal("-150.00"), savedAdjustmentLine.get().getAmount());
        Assertions.assertNotNull(savedAdjustmentItem.get());
        Assertions.assertEquals(new BigDecimal("-150.00"), savedAdjustmentItem.get().getGrossPay());
        Assertions.assertEquals(new BigDecimal("-150.00"), savedAdjustmentItem.get().getNetPay());
        verify(activityLogRepository).save(argThat(log ->
                log != null
                        && log.getMessage() != null
                        && log.getMessage().startsWith("PAYCORR|run=5|srcItem=21|srcType=EARNING|srcLine=77|corr=650.00|adjItem=31|adjType=EARNING|adjLine=44")
        ));
        verify(activityLogRepository, atLeast(2)).save(any(ActivityLog.class));
    }

    @Test
    void applyStagedCorrections_ShouldUpdateOriginalLineWhenAdjustmentRunPosts() {
        PayrollRun targetRun = new PayrollRun();
        ReflectionTestUtils.setField(targetRun, "payId", 5L);
        targetRun.setRunType(RunType.ADJUSTMENT);
        targetRun.setPayrollStatus(PayrollStatus.IN_PROGRESS);

        PayrollRun sourceRun = new PayrollRun();
        sourceRun.setRunType(RunType.REGULAR);
        sourceRun.setPayrollStatus(PayrollStatus.POSTED);

        Employee employee = new Employee();
        ReflectionTestUtils.setField(employee, "employeeId", 10L);

        PayrollItem sourceItem = new PayrollItem();
        ReflectionTestUtils.setField(sourceItem, "itemId", 21L);
        sourceItem.setEmployee(employee);
        sourceItem.setPayrollRun(sourceRun);

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

        ActivityLog stagedCorrection = new ActivityLog();
        stagedCorrection.setActivityType(Activity.MODIFY);
        stagedCorrection.setMessage("PAYCORR|run=5|srcItem=21|srcType=EARNING|srcLine=77|corr=650.00|adjItem=31|adjType=EARNING|adjLine=44");

        when(activityLogRepository.findByActivityTypeAndMessageStartingWithOrderByActivityIdAsc(Activity.MODIFY, "PAYCORR|run=5|"))
                .thenReturn(List.of(stagedCorrection));
        when(payrollItemRepository.findByItemIdAndPayrollRun_PayrollStatusAndPayrollRun_RunTypeNot(
                21L,
                PayrollStatus.POSTED,
                RunType.ADJUSTMENT
        )).thenReturn(Optional.of(sourceItem));
        when(earningsRepository.findById(77L)).thenReturn(Optional.of(sourceLine));
        when(earningsService.getOriginalEarnings(21L)).thenReturn(List.of(sourceLineDto));
        when(earningsRepository.save(any(EarningsLine.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(earningsRepository.findByPayrollItem_ItemId(21L)).thenReturn(List.of(sourceLine));
        when(deductionsRepository.findByPayrollItem_ItemId(21L)).thenReturn(List.of());
        when(payrollItemRepository.save(any(PayrollItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(activityLogRepository.save(any(ActivityLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("hr.payroll", "ignored", List.of())
        );
        try {
            payrollItemService.applyStagedCorrections(targetRun);
        } finally {
            SecurityContextHolder.clearContext();
        }

        Assertions.assertEquals(new BigDecimal("650.00"), sourceLine.getAmount());
        Assertions.assertEquals(new BigDecimal("800.00"), sourceLine.getOriginalAmount());
        Assertions.assertEquals("hr.payroll", sourceLine.getAdjustedBy());
        Assertions.assertNotNull(sourceLine.getAdjustedAt());
        Assertions.assertEquals(new BigDecimal("650.00"), sourceItem.getGrossPay());
        Assertions.assertEquals(new BigDecimal("650.00"), sourceItem.getNetPay());
        verify(activityLogRepository).save(argThat(log ->
                log != null
                        && log.getMessage() != null
                        && log.getMessage().contains("correction applied")
                        && log.getMessage().contains("800.00->650.00")
        ));
    }
}
