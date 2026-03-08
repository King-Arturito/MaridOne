package org.example.maridone.payroll.dispute;

import java.util.Optional;

import org.example.maridone.enums.Status;
import org.example.maridone.exception.notfound.ItemNotFoundException;
import org.example.maridone.payroll.item.PayrollItem;
import org.example.maridone.payroll.dto.DisputeRequestDto;
import org.example.maridone.payroll.run.PayrollItemRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DisputeServiceTest {

    @Mock private DisputeRequestRepository disputeRepository;
    @Mock private PayrollItemRepository payrollItemRepository;

    @InjectMocks
    private DisputeService disputeService;

    @Test
    void createDisputeRequest_ShouldSave_WhenItemExists() {
        Long itemId = 50L;
        DisputeRequestDto dto = new DisputeRequestDto();
        dto.setSubject("Wrong Tax");
        dto.setReason("Too high");

        PayrollItem item = new PayrollItem();
        Pageable pageable = Pageable.unpaged();
        ReflectionTestUtils.setField(item, "itemId", itemId);

        when(payrollItemRepository.findById(itemId)).thenReturn(Optional.of(item));
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

        when(payrollItemRepository.findById(itemId)).thenReturn(Optional.empty());

        Assertions.assertThrows(ItemNotFoundException.class, () -> {
            disputeService.createDisputeRequest(itemId, dto);
        });
    }
}