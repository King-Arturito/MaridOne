package org.example.maridone.payroll.itemcomponent;

import java.util.List;

import org.example.maridone.payroll.dto.EarningsDto;
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
class EarningsServiceTest {

    @Mock private EarningsRepository earningsRepository;
    @Mock private PayrollMapper payrollMapper;

    @InjectMocks
    private EarningsService earningsService;

    @Test
    void getEarnings_ShouldReturnList() {
        Long itemId = 50L;
        EarningsLine line = new EarningsLine();
        EarningsDto dto = new EarningsDto();

        when(earningsRepository.findByPayrollItem_ItemId(itemId)).thenReturn(List.of(line));
        when(payrollMapper.toEarningsDtos(anyList())).thenReturn(List.of(dto));

        List<EarningsDto> result = earningsService.getEarnings(itemId);

        Assertions.assertFalse(result.isEmpty());
    }
}