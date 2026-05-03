package org.example.maridone.payroll.dispute;

import java.util.Optional;

import org.example.maridone.core.employee.Employee;
import org.example.maridone.core.employee.EmployeeRepository;
import org.example.maridone.core.user.UserAccountRepository;
import org.example.maridone.document.path.DocumentPathRepository;
import org.example.maridone.document.DocumentService;
import org.example.maridone.enums.EmploymentStatus;
import org.example.maridone.enums.Position;
import org.example.maridone.enums.Status;
import org.example.maridone.exception.notfound.ItemNotFoundException;
import org.example.maridone.notification.NotificationService;
import org.example.maridone.notification.WorkflowEmailService;
import org.example.maridone.payroll.dto.DisputeResponseDto;
import org.example.maridone.payroll.item.PayrollItem;
import org.example.maridone.payroll.dto.DisputeRequestDto;
import org.example.maridone.payroll.mapper.PayrollMapper;
import org.example.maridone.payroll.run.PayrollItemRepository;
import org.example.maridone.payroll.run.PayrollRun;
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
    @Mock private EmployeeRepository employeeRepository;
    @Mock private UserAccountRepository userAccountRepository;
    @Mock private DocumentPathRepository documentPathRepository;
    @Mock private DocumentService documentService;
    @Mock private PayrollMapper payrollMapper;
    @Mock private NotificationService notificationService;
    @Mock private WorkflowEmailService workflowEmailService;

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

        DisputeRequest result = disputeService.createDisputeRequest(itemId, dto, null);

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
            disputeService.createDisputeRequest(itemId, dto, null);
        });
    }

    @Test
    void getActiveDisputeRequests_ShouldPopulateEmployeeId() {
        DisputeRequest disputeRequest = new DisputeRequest();
        DisputeResponseDto dto = new DisputeResponseDto();
        dto.setEmployeeId(6L);
        dto.setEmployeeName("Juan Dela Cruz");

        when(disputeRepository.findAllByStatus(Status.PENDING, Pageable.unpaged()))
                .thenReturn(new PageImpl<>(java.util.List.of(disputeRequest)));
        when(payrollMapper.toResponseDto(disputeRequest)).thenReturn(dto);

        Page<DisputeResponseDto> result = disputeService.getActiveDisputeRequests(Pageable.unpaged());

        Assertions.assertEquals(1, result.getTotalElements());
        Assertions.assertEquals(6L, result.getContent().get(0).getEmployeeId());
        Assertions.assertEquals("Juan Dela Cruz", result.getContent().get(0).getEmployeeName());
        verify(payrollMapper).toResponseDto(disputeRequest);
    }

    @Test
    void cancelRequest_ShouldNotifyHr_WithDetailedPayrollPeriod() {
        Employee employee = new Employee();
        employee.setFirstName("Niko");
        employee.setLastName("Santos");

        PayrollRun payrollRun = new PayrollRun();
        payrollRun.setPeriodStart(java.time.LocalDate.of(2026, 4, 1));
        payrollRun.setPeriodEnd(java.time.LocalDate.of(2026, 4, 15));

        PayrollItem payrollItem = new PayrollItem();
        payrollItem.setEmployee(employee);
        payrollItem.setPayrollRun(payrollRun);

        Employee hr = new Employee();
        hr.setFirstName("Pat");
        hr.setLastName("Reyes");

        DisputeRequest disputeRequest = new DisputeRequest();
        disputeRequest.setPayrollItem(payrollItem);
        disputeRequest.setStatus(Status.PENDING);

        when(disputeRepository.findByIdWithEmployeeForUpdate(9L)).thenReturn(Optional.of(disputeRequest));
        when(disputeRepository.save(any(DisputeRequest.class))).thenReturn(disputeRequest);
        when(employeeRepository.findEmployeeIdsByPosition(Position.HR, EmploymentStatus.TERMINATED)).thenReturn(java.util.List.of(5L));
        when(employeeRepository.getReferenceById(5L)).thenReturn(hr);

        disputeService.cancelRequest(9L);

        Assertions.assertEquals(Status.CANCELLED, disputeRequest.getStatus());
        verify(notificationService).createNotification(
                hr,
                "Dispute request from Niko Santos for April 1, 2026 to April 15, 2026 was cancelled.",
                "HIGH");
    }
}
