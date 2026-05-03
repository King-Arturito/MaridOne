package org.example.maridone.payroll.dispute;

import org.example.maridone.annotation.NotifyRole;
import org.example.maridone.annotation.ExecutionTime;
import org.example.maridone.annotation.Notify;
import org.example.maridone.common.CommonSpecs;
import org.example.maridone.common.FileValidation;
import org.example.maridone.config.DocumentConfig;
import org.example.maridone.core.employee.EmployeeRepository;
import org.example.maridone.core.user.UserAccount;
import org.example.maridone.core.user.UserAccountRepository;
import org.example.maridone.document.path.DocumentPathRepository;
import org.example.maridone.document.DocumentService;
import org.example.maridone.document.path.DocumentPath;
import org.example.maridone.document.spec.DocumentSpecs;
import org.example.maridone.enums.Directories;
import org.example.maridone.enums.EmploymentStatus;
import org.example.maridone.enums.PayrollStatus;
import org.example.maridone.enums.Position;
import org.example.maridone.enums.Status;
import org.example.maridone.exception.badrequest.MaxFileUploadExceededException;
import org.example.maridone.exception.notfound.AccountNotFoundException;
import org.example.maridone.exception.toolarge.FileTooLargeException;
import org.example.maridone.exception.unauthorized.DuplicateDisputeException;
import org.example.maridone.exception.unauthorized.InvalidActionException;
import org.example.maridone.exception.notfound.ItemNotFoundException;
import org.example.maridone.exception.notfound.RequestNotFoundException;
import org.example.maridone.payroll.dto.DisputeDetails;
import org.example.maridone.payroll.item.PayrollItem;
import org.example.maridone.payroll.dto.DisputeActionDto;
import org.example.maridone.payroll.dto.DisputeRequestDto;
import org.example.maridone.payroll.dto.DisputeResponseDto;
import org.example.maridone.payroll.mapper.PayrollMapper;
import org.example.maridone.notification.NotificationMessageFormatter;
import org.example.maridone.notification.NotificationService;
import org.example.maridone.notification.WorkflowEmailService;
import org.example.maridone.payroll.run.PayrollItemRepository;
import org.example.maridone.payroll.spec.DisputeSpecs;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;

@Service
public class DisputeService {
    private final DisputeRequestRepository disputeRepository;
    private final PayrollItemRepository payrollItemRepository;
    private final EmployeeRepository employeeRepository;
    private final UserAccountRepository userAccountRepository;
    private final DocumentPathRepository documentPathRepository;
    //todo: change to cloud
    private final DocumentService documentService;
    private final PayrollMapper payrollMapper;
    private final NotificationService notificationService;
    private final WorkflowEmailService workflowEmailService;
    private final DocumentConfig documentConfig;
    public DisputeService
            (DisputeRequestRepository disputeRepository,
             PayrollItemRepository payrollItemRepository,
             EmployeeRepository employeeRepository,
             UserAccountRepository userAccountRepository,
             DocumentPathRepository documentPathRepository,
             DocumentService documentService,
             PayrollMapper payrollMapper,
             NotificationService notificationService,
             WorkflowEmailService workflowEmailService,
             DocumentConfig documentConfig) {
        this.disputeRepository = disputeRepository;
        this.payrollItemRepository = payrollItemRepository;
        this.employeeRepository = employeeRepository;
        this.userAccountRepository = userAccountRepository;
        this.documentPathRepository = documentPathRepository;
        this.documentService = documentService;
        this.payrollMapper = payrollMapper;
        this.notificationService = notificationService;
        this.workflowEmailService = workflowEmailService;
        this.documentConfig = documentConfig;
    }


    @Transactional
    @ExecutionTime
    @NotifyRole(
            message = "New payroll dispute from #{#result.payrollItem.employee.firstName} "
                    + "#{#result.payrollItem.employee.lastName}: #{#result.subject}",
            targetRole = Position.HR,
            importance = "HIGH")
    public DisputeRequest createDisputeRequest(Long itemId, DisputeRequestDto payload, List<MultipartFile> files) {
        if (FileValidation.exceedsFileSizeLimit(files, documentConfig.getMaxFileSize().toBytes())) {
            throw new FileTooLargeException("One or more files exceeds file size limit: " + documentConfig.getMaxFileSize().toMegabytes() + "MB");
        }
        if (FileValidation.exceedsFileUploadLimit(files.size(), documentConfig.getMaxFileUpload())) {
            throw new MaxFileUploadExceededException("files to upload exceeded. Limit: " + documentConfig.getMaxFileUpload() + " files");
        }
        PayrollItem item = payrollItemRepository
                .findByItemIdAndPayrollRun_PayrollStatus(itemId, PayrollStatus.POSTED)
                .orElse(null);
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

        if (files != null) {
            documentService.batchUploadPrivateDocuments(files, item.getEmployee().getUserAccount().getUsername(), Directories.DISPUTE, List.of(itemId));
        }
        DisputeRequest savedRequest = disputeRepository.save(disputeRequest);
        workflowEmailService.sendDisputeSubmittedToHr(savedRequest);
        return savedRequest;
    }

    @Transactional
    @ExecutionTime
    @Notify(
            message = "Your dispute about #{#result.subject} was #{#result.status}",
            targetEmployee = "#result.employeeId",
            importance = "HIGH")
    public DisputeResponseDto updateDisputeStatus(Long disputeId, DisputeActionDto payload, String approverUsername) {
        DisputeRequest disputeRequest = disputeRepository.findByIdWithEmployeeForUpdate(disputeId)
                .orElseThrow(() -> new RequestNotFoundException("Dispute Request",disputeId));


        if (disputeRequest.getStatus() != Status.PENDING) {
            throw new InvalidActionException("Dispute ID: " + disputeId + " has been processed already.");
        }
        UserAccount approver = userAccountRepository.findByUsername(approverUsername)
                .orElseThrow(() -> new AccountNotFoundException("Approver's UserAccount with: " + approverUsername + " not found."));

        Long empId = disputeRepository.findEmployeeIdByDisputeRequestId(disputeId);
        if ( empId !=  null && empId.equals(approver.getEmployeeId())) {
            throw new InvalidActionException("You cannot approve your own request");
        }
        disputeRequest.setStatus(payload.getStatus());
        disputeRequest.setStatusReason(payload.getStatusReason());
        if (approver.getEmployee() != null) {
            disputeRequest.setApprover(
                    approver.getEmployee().getLastName() + ", " + approver.getEmployee().getFirstName()
            );
        } else {
            disputeRequest.setApprover(approverUsername);
        }
        disputeRequest.setUpdatedAt(Instant.now());
        disputeRepository.save(disputeRequest);
        workflowEmailService.sendDisputeDecisionToEmployee(disputeRequest);
        return payrollMapper.toResponseDto(disputeRequest);
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

    public DisputeDetails getDisputeRequestDetails(long disputeId) {
        DisputeRequest request = disputeRepository.findByIdWithEmployee(disputeId)
                .orElseThrow(() -> new RequestNotFoundException("Dispute Request", disputeId));
        DisputeResponseDto responseDto = payrollMapper.toResponseDto(request);

        String username = request.getPayrollItem().getEmployee().getUserAccount().getUsername();

        Specification<DocumentPath> spec = Specification.allOf(
                CommonSpecs.fieldLike("filePath", Directories.DISPUTE.getDisplayName()),
                DocumentSpecs.hasUsername(username)
        );

        DisputeDetails details = new DisputeDetails();
        details.setId(responseDto.getId());
        details.setSubject(request.getSubject());
        details.setStatus(responseDto.getStatus());
        details.setUpdatedAt(responseDto.getUpdatedAt());
        details.setCreatedAt(responseDto.getCreatedAt());
        details.setStatusReason(responseDto.getStatusReason());
        details.setItemId(responseDto.getItemId());
        details.setEmployeeId(responseDto.getEmployeeId());
        details.setEmployeeName(responseDto.getEmployeeName());

        Page<DocumentPath> result = documentPathRepository.findAll(
                spec,
                PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "pathId"))
        );

        details.setPathId(result.stream()
                .findFirst()
                .map(DocumentPath::getPathId)
                .orElse(null));
        return details;
    }

    @ExecutionTime
    @Transactional
    public void cancelRequest(Long disputeId) {
        DisputeRequest disputeRequest = disputeRepository.findByIdWithEmployeeForUpdate(disputeId)
                .orElseThrow(() -> new RequestNotFoundException("Dispute Request",disputeId));
        if (disputeRequest.getStatus().equals(Status.APPROVED)) {
            throw new IllegalStateException("Cannot cancel. Dispute Request has been approved already.");
        }
        disputeRequest.setStatus(Status.CANCELLED);
        disputeRepository.save(disputeRequest);
        String employeeName = NotificationMessageFormatter.formatEmployeeName(disputeRequest.getPayrollItem().getEmployee());
        String payrollPeriod = NotificationMessageFormatter.formatPayrollPeriod(
                disputeRequest.getPayrollItem().getPayrollRun()
        );
        notifyHr("Dispute request from " + employeeName + " for " + payrollPeriod + " was cancelled.");
    }

    private void notifyHr(String message) {
        for (Long employeeId : employeeRepository.findEmployeeIdsByPosition(Position.HR, EmploymentStatus.TERMINATED)) {
            notificationService.createNotification(employeeRepository.getReferenceById(employeeId), message, "HIGH");
        }
    }
}
