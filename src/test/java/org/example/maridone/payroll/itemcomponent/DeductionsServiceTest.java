package org.example.maridone.payroll.itemcomponent;

import org.example.maridone.enums.DeductionType;
import org.example.maridone.payroll.dto.DeductionsDto;
import org.example.maridone.payroll.item.component.DeductionsLine;
import org.example.maridone.payroll.item.component.DeductionsRepository;
import org.example.maridone.payroll.item.component.DeductionsService;
import org.example.maridone.payroll.mapper.PayrollMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeductionsServiceTest {

    @Mock
    private DeductionsRepository deductionsRepository;

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
}
