package org.example.maridone.payroll.itemcomponent;

import java.util.List;

import org.example.maridone.payroll.dto.DeductionsDto;
import org.example.maridone.payroll.mapper.PayrollMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.anyList;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

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
        DeductionsDto dto = new DeductionsDto();

        when(deductionsRepository.findByPayrollItem_ItemId(itemId)).thenReturn(List.of(line));
        when(payrollMapper.toDeductionsDtos(anyList())).thenReturn(List.of(dto));

        List<DeductionsDto> result = deductionsService.getDeductions(itemId);

        Assertions.assertFalse(result.isEmpty());
    }
}