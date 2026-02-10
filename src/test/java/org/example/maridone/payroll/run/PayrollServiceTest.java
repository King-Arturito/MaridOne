package org.example.maridone.payroll.run;

import java.util.List;

import org.example.maridone.payroll.PayrollItem;
import org.example.maridone.payroll.dto.ItemDetailsDto;
import org.example.maridone.payroll.mapper.PayrollMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class PayrollServiceTest {

    @Mock private PayrollItemRepository payrollItemRepository;
    @Mock private PayrollMapper payrollMapper;

    @InjectMocks
    private PayrollService payrollService;

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
}