package org.example.maridone.payroll.itemcomponent;

import org.example.maridone.enums.DeductionType;
import org.example.maridone.enums.PayrollStatus;
import org.example.maridone.enums.RunType;
import org.example.maridone.exception.unauthorized.InvalidActionException;
import org.example.maridone.payroll.dto.DeductionsDto;
import org.example.maridone.payroll.item.PayrollItem;
import org.example.maridone.payroll.item.component.DeductionsLine;
import org.example.maridone.payroll.item.component.DeductionsRepository;
import org.example.maridone.payroll.item.component.DeductionsService;
import org.example.maridone.payroll.item.component.EarningsRepository;
import org.example.maridone.payroll.mapper.PayrollMapper;
import org.example.maridone.payroll.run.PayrollItemRepository;
import org.example.maridone.payroll.run.PayrollRun;
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
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeductionsServiceTest {

    @Mock
    private DeductionsRepository deductionsRepository;

    @Mock
    private EarningsRepository earningsRepository;

    @Mock
    private PayrollItemRepository payrollItemRepository;

    @Mock
    private PayrollMapper payrollMapper;

    @InjectMocks
    private DeductionsService deductionsService;

    @Test
    void getDeductions_ShouldReturnList() {
        Long itemId = 100L;
        DeductionsLine line = new DeductionsLine();
        line.setDeductionType(DeductionType.ABSENT_DEDUCTION);
        DeductionsDto dto = new DeductionsDto();

        when(deductionsRepository.findByPayrollItem_ItemId(itemId)).thenReturn(List.of(line));
        when(payrollMapper.toDeductionsDto(line)).thenReturn(dto);

        List<DeductionsDto> result = deductionsService.getDeductions(itemId);

        Assertions.assertFalse(result.isEmpty());
        Assertions.assertEquals("Absent Deduction", result.get(0).getDescription());
    }

    @Test
    void getDeductions_ShouldKeepBaseDescriptionAfterDirectCorrection() {
        Long itemId = 101L;
        DeductionsLine originalLine = new DeductionsLine();
        originalLine.setDeductionType(DeductionType.SSS);
        originalLine.setAmount(new BigDecimal("900.00"));

        DeductionsDto originalDto = new DeductionsDto();
        originalDto.setAmount(new BigDecimal("900.00"));

        when(deductionsRepository.findByPayrollItem_ItemId(itemId)).thenReturn(List.of(originalLine));
        when(payrollMapper.toDeductionsDto(originalLine)).thenReturn(originalDto);

        List<DeductionsDto> result = deductionsService.getDeductions(itemId);

        Assertions.assertEquals(1, result.size());
        Assertions.assertEquals("SSS Contribution", result.get(0).getDescription());
        Assertions.assertEquals(new BigDecimal("900.00"), result.get(0).getAmount());
    }

    @Test
    void updateDeduction_ShouldStampAdjustmentMetadataForRegularInProgressRun() {
        PayrollRun run = new PayrollRun();
        run.setRunType(RunType.REGULAR);
        run.setPayrollStatus(PayrollStatus.IN_PROGRESS);

        PayrollItem item = new PayrollItem();
        ReflectionTestUtils.setField(item, "itemId", 80L);
        item.setPayrollRun(run);

        DeductionsLine line = new DeductionsLine();
        ReflectionTestUtils.setField(line, "deductionsId", 14L);
        line.setPayrollItem(item);
        line.setDeductionType(DeductionType.SSS);
        line.setAmount(new BigDecimal("900.00"));

        DeductionsDto payload = new DeductionsDto();
        payload.setDeductionType(DeductionType.SSS);
        payload.setAmount(new BigDecimal("850.00"));

        when(deductionsRepository.findById(14L)).thenReturn(Optional.of(line));
        when(deductionsRepository.save(any(DeductionsLine.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(deductionsRepository.findByPayrollItem_ItemId(80L)).thenReturn(List.of(line));
        when(earningsRepository.findByPayrollItem_ItemId(80L)).thenReturn(List.of());
        when(payrollItemRepository.save(any(PayrollItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(payrollMapper.toDeductionsDto(any(DeductionsLine.class))).thenAnswer(invocation -> {
            DeductionsLine savedLine = invocation.getArgument(0);
            DeductionsDto dto = new DeductionsDto();
            dto.setAmount(savedLine.getAmount());
            dto.setOriginalAmount(savedLine.getOriginalAmount());
            dto.setAdjustedBy(savedLine.getAdjustedBy());
            dto.setAdjustedAt(savedLine.getAdjustedAt());
            dto.setDeductionType(savedLine.getDeductionType());
            return dto;
        });

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("hr.payroll", "ignored", List.of())
        );
        try {
            DeductionsDto result = deductionsService.updateDeduction(payload, 14L);

            Assertions.assertEquals(new BigDecimal("850.00"), line.getAmount());
            Assertions.assertEquals(new BigDecimal("900.00"), line.getOriginalAmount());
            Assertions.assertEquals("hr.payroll", line.getAdjustedBy());
            Assertions.assertNotNull(line.getAdjustedAt());
            Assertions.assertEquals(new BigDecimal("0.00"), item.getGrossPay());
            Assertions.assertEquals(new BigDecimal("-850.00"), item.getNetPay());
            Assertions.assertEquals(DeductionType.SSS, result.getDeductionType());
            Assertions.assertEquals("SSS Contribution", result.getDescription());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void updateDeduction_ShouldRejectPostedRun() {
        PayrollRun run = new PayrollRun();
        run.setRunType(RunType.REGULAR);
        run.setPayrollStatus(PayrollStatus.POSTED);

        PayrollItem item = new PayrollItem();
        item.setPayrollRun(run);

        DeductionsLine line = new DeductionsLine();
        ReflectionTestUtils.setField(line, "deductionsId", 15L);
        line.setPayrollItem(item);
        line.setDeductionType(DeductionType.SSS);
        line.setAmount(new BigDecimal("900.00"));

        DeductionsDto payload = new DeductionsDto();
        payload.setAmount(new BigDecimal("850.00"));

        when(deductionsRepository.findById(15L)).thenReturn(Optional.of(line));

        Assertions.assertThrows(InvalidActionException.class, () -> deductionsService.updateDeduction(payload, 15L));
    }
}
