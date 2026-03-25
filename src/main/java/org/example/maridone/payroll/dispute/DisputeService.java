package org.example.maridone.payroll.dispute;

import org.example.maridone.annotation.BulkNotify;
import org.example.maridone.annotation.ExecutionTime;
import org.example.maridone.annotation.Notify;
import org.example.maridone.common.CommonSpecs;
import org.example.maridone.core.employee.Employee;
import org.example.maridone.enums.Position;
import org.example.maridone.enums.Status;
import org.example.maridone.exception.unauthorized.DuplicateDisputeException;
import org.example.maridone.exception.unauthorized.InvalidActionException;
import org.example.maridone.exception.notfound.ItemNotFoundException;
import org.example.maridone.exception.notfound.RequestNotFoundException;
import org.example.maridone.payroll.item.PayrollItem;
import org.example.maridone.payroll.dto.DisputeActionDto;
import org.example.maridone.payroll.dto.DisputeRequestDto;
import org.example.maridone.payroll.dto.DisputeResponseDto;
import org.example.maridone.payroll.mapper.PayrollMapper;
import org.example.maridone.payroll.run.PayrollItemRepository;
import org.example.maridone.payroll.spec.DisputeSpecs;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

@Service
public class DisputeService {
    private final DisputeRequestRepository disputeRepository;
    private final PayrollItemRepository payrollItemRepository;
    private final PayrollMapper payrollMapper;
    public DisputeService
            (DisputeRequestRepository disputeRepository,
             PayrollItemRepository payrollItemRepository,
             PayrollMapper payrollMapper) {
        this.disputeRepository = disputeRepository;
        this.payrollItemRepository = payrollItemRepository;
        this.payrollMapper = payrollMapper;
    }


    @Transactional
    @ExecutionTime
    @BulkNotify(message = "Dispute Requested", targetRole = Position.HR, importance = "HIGH")
    public DisputeRequest createDisputeRequest(Long itemId, DisputeRequestDto payload) {
        PayrollItem item =  payrollItemRepository.findById(itemId).orElse(null);
        if (item == null) {
            throw new ItemNotFoundException(itemId);
        }

        DisputeRequest checkRecent = disputeRepository.findTopByPayrollItem_ItemIdOrderByDisputeIdDesc(itemId);
        if (checkRecent != null && checkRecent.getStatus().equals(Status.PENDING)) {
            throw new DuplicateDisputeException(itemId, "Your dispute is still pending. Please ask for an update from the HR.");
        }

        DisputeRequest disputeRequest = new DisputeRequest();
        disputeRequest.setPayrollItem(item);
        disputeRequest.setCreatedAt(Instant.now());
        disputeRequest.setSubject(payload.getSubject());
        disputeRequest.setReason(payload.getReason());
        disputeRequest.setStatus(Status.PENDING);
        return disputeRepository.save(disputeRequest);
    }

    @Transactional
    @ExecutionTime
    @Notify(message = "Your Dispute Request has been  #{#result.status}", targetEmployee = "#result.employeeId",importance = "HIGH")
    public DisputeResponseDto updateDisputeStatus(Long disputeId, DisputeActionDto payload) {
        DisputeRequest disputeRequest = disputeRepository.findByIdWithEmployee(disputeId)
                .orElseThrow(() -> new RequestNotFoundException("Dispute Request",disputeId));
        if (disputeRequest.getStatus().equals(Status.APPROVED)) {
            throw new InvalidActionException("Dispute ID: " + disputeId + " has been approved already.");
        }
        disputeRequest.setStatus(payload.getStatus());
        disputeRequest.setStatusReason(payload.getStatusReason());
        disputeRequest.setUpdatedAt(Instant.now());
        disputeRepository.save(disputeRequest);
        DisputeResponseDto responseDto = payrollMapper.toResponseDto(disputeRequest);

        responseDto.setEmployeeId(disputeRequest.getPayrollItem().getEmployee().getEmployeeId());
        return responseDto;
    }


    @ExecutionTime
    public Page<DisputeResponseDto> getActiveDisputeRequests(Pageable paging) {
            Page<DisputeRequest> entityPage = disputeRepository.findAllByStatus(Status.PENDING, paging);
            return entityPage.map(payrollMapper::toResponseDto);
    }


    @Transactional(readOnly = true)
    @ExecutionTime
    public Page<DisputeResponseDto> getMyDisputeRequests(Long empId, Status status, Pageable pageable) {
        Specification<DisputeRequest> spec = Specification.allOf(
                CommonSpecs.fieldEquals("status", status),
                DisputeSpecs.hasEmployeeId(empId)
                );
        Page<DisputeRequest> entityPage = disputeRepository.findAll(spec, pageable);
        return entityPage.map(payrollMapper::toResponseDto);
    }

    @ExecutionTime
    @Transactional
    @BulkNotify(message = "Dispute Request Cancelled.", targetRole = Position.HR)
    public void cancelRequest(Long disputeId) {
        DisputeRequest disputeRequest = disputeRepository.findById(disputeId).orElseThrow(() -> new RequestNotFoundException("Dispute Request",disputeId));
        if (disputeRequest.getStatus().equals(Status.APPROVED)) {
            throw new IllegalStateException("Cannot cancel. Dispute Request has been approved already.");
        }
        disputeRequest.setStatus(Status.CANCELLED);
        disputeRepository.save(disputeRequest);
    }
}
