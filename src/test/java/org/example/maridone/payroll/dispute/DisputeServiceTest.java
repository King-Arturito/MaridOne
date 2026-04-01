package org.example.maridone.payroll.dispute;

import java.util.Optional;

import org.example.maridone.core.employee.Employee;
import org.example.maridone.enums.Status;
import org.example.maridone.exception.notfound.ItemNotFoundException;
import org.example.maridone.payroll.dto.DisputeResponseDto;
import org.example.maridone.payroll.item.PayrollItem;
import org.example.maridone.payroll.dto.DisputeRequestDto;
import org.example.maridone.payroll.mapper.PayrollMapper;
import org.example.maridone.payroll.run.PayrollItemRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DisputeServiceTest {

    @Mock private DisputeRequestRepository disputeRepository;
    @Mock private PayrollItemRepository payrollItemRepository;
    @Mock private PayrollMapper payrollMapper;

    @InjectMocks
    private DisputeService disputeService;

    @Test
    void createDisputeRequest_ShouldSave_WhenItemExists() {
        Long itemId = 50L;
        DisputeRequestDto dto = new DisputeRequestDto();
        dto.setSubject("Wrong Tax");
        dto.setReason("Too high");

        PayrollItem item = new PayrollItem();
        ReflectionTestUtils.setField(item, "itemId", itemId);

        when(payrollItemRepository.findByItemIdAndPayrollRun_PayrollStatus(itemId, org.example.maridone.enums.PayrollStatus.POSTED))
                .thenReturn(Optional.of(item));
        when(disputeRepository.save(any(DisputeRequest.class))).thenAnswer(i -> i.getArgument(0));

        DisputeRequest result = disputeService.createDisputeRequest(itemId, dto);

        Assertions.assertNotNull(result);
        Assertions.assertEquals(Status.PENDING, result.getStatus());
        Assertions.assertEquals("Wrong Tax", result.getSubject());
        Assertions.assertNotNull(result.getCreatedAt());
    }

    @Test
    void createDisputeRequest_ShouldThrow_WhenItemNotFound() {
        Long itemId = 50L;
        DisputeRequestDto dto = new DisputeRequestDto();

        when(payrollItemRepository.findByItemIdAndPayrollRun_PayrollStatus(itemId, org.example.maridone.enums.PayrollStatus.POSTED))
                .thenReturn(Optional.empty());

        Assertions.assertThrows(ItemNotFoundException.class, () -> {
            disputeService.createDisputeRequest(itemId, dto);
        });
    }

    @Test
    void getActiveDisputeRequests_ShouldPopulateEmployeeId() {
        Long employeeId = 6L;
        Employee employee = new Employee();
        PayrollItem item = new PayrollItem();
        DisputeRequest disputeRequest = new DisputeRequest();
        DisputeResponseDto dto = new DisputeResponseDto();

        ReflectionTestUtils.setField(employee, "employeeId", employeeId);
        employee.setFirstName("Juan");
        employee.setLastName("Dela Cruz");
        item.setEmployee(employee);
        disputeRequest.setPayrollItem(item);

        when(disputeRepository.findAllByStatus(Status.PENDING, Pageable.unpaged()))
                .thenReturn(new PageImpl<>(java.util.List.of(disputeRequest)));
        when(payrollMapper.toResponseDto(disputeRequest)).thenReturn(dto);

        Page<DisputeResponseDto> result = disputeService.getActiveDisputeRequests(Pageable.unpaged());

        Assertions.assertEquals(1, result.getTotalElements());
        Assertions.assertEquals(employeeId, result.getContent().get(0).getEmployeeId());
        Assertions.assertEquals("Juan Dela Cruz", result.getContent().get(0).getEmployeeName());
        verify(payrollMapper).toResponseDto(disputeRequest);
    }
}
