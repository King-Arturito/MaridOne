package org.example.maridone.payroll.dispute;

import org.example.maridone.common.CommonSpecs;
import org.example.maridone.enums.Status;
import org.example.maridone.exception.DuplicateDisputeException;
import org.example.maridone.exception.InvalidActionException;
import org.example.maridone.exception.ItemNotFoundException;
import org.example.maridone.exception.RequestNotFoundException;
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
    public DisputeRequest createDisputeRequest(Long itemId, DisputeRequestDto payload) {
        PayrollItem item =  payrollItemRepository.findById(itemId).orElse(null);
        if (item == null) {
            throw new ItemNotFoundException(itemId);
        }

        DisputeRequest checkRecent = disputeRepository.findTopByPayrollItem_ItemIdOrderByDisputeIdDesc(itemId);
        if (checkRecent != null && checkRecent.getStatus().equals(Status.PENDING)) {
            throw new DuplicateDisputeException(itemId, "Your dispute is still pending. Please for an update from the HR.");
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
    public void updateDisputeStatus(Long disputeId, DisputeActionDto payload) {
        DisputeRequest disputeRequest = disputeRepository.findById(disputeId).orElse(null);
        if (disputeRequest == null) {
            throw new RequestNotFoundException(disputeId);
        } else if (disputeRequest.getStatus().equals(Status.APPROVED)) {
            throw new InvalidActionException("Dispute ID: " + disputeId + "has been approved already.");
        }
        disputeRequest.setStatus(payload.getStatus());
        disputeRequest.setStatusReason(payload.getStatusReason());
        disputeRequest.setUpdatedAt(Instant.now());
        disputeRepository.save(disputeRequest);
    }


    public Page<DisputeResponseDto> getActiveDisputeRequests(Pageable paging) {
            Page<DisputeRequest> entityPage = disputeRepository.findAllByStatus(Status.PENDING, paging);
            return entityPage.map(payrollMapper::toResponseDto);
    }


    @Transactional(readOnly = true)
    public Page<DisputeResponseDto> getMyDisputeRequests(Long empId, Status status, Pageable pageable) {
        Specification<DisputeRequest> spec = Specification.allOf(
                CommonSpecs.fieldEquals("status", status),
                DisputeSpecs.hasEmployeeId(empId)
                );
        Page<DisputeRequest> entityPage = disputeRepository.findAll(spec, pageable);
        return entityPage.map(payrollMapper::toResponseDto);
    }
}
