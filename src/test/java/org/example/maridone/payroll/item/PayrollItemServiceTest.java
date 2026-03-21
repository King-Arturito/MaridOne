package org.example.maridone.payroll.item;

import org.example.maridone.payroll.dto.ItemDetailsDto;
import org.example.maridone.payroll.mapper.PayrollMapper;
import org.example.maridone.payroll.run.PayrollItemRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class PayrollItemServiceTest {

    @Mock
    private PayrollItemRepository payrollItemRepository;
    @Mock
    private PayrollMapper payrollMapper;

    @InjectMocks
    private PayrollItemService payrollItemService;

    @Test
    void getItems_ShouldReturnDtos() {
        Long empId = 1L;
        PayrollItem item = new PayrollItem();
        ItemDetailsDto dto = new ItemDetailsDto();

        when(payrollItemRepository.findAll(any(Specification.class))).thenReturn(List.of(item));
        when(payrollMapper.toItemDetailsDtos(anyList())).thenReturn(List.of(dto));

        List<ItemDetailsDto> result = payrollItemService.getItems(empId);

        Assertions.assertFalse(result.isEmpty());
    }
}
