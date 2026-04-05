package org.example.maridone.leave;

import org.example.maridone.annotation.*;
import org.example.maridone.common.CommonCalculator;
import org.example.maridone.common.CommonSpecs;
import org.example.maridone.config.PayrollConfig;
import org.example.maridone.document.DocumentPathRepository;
import org.example.maridone.document.DocumentService;
import org.example.maridone.document.path.DocumentPath;
import org.example.maridone.document.spec.DocumentSpecs;
import org.example.maridone.enums.*;
import org.example.maridone.core.employee.Employee;
import org.example.maridone.core.employee.EmployeeRepository;
import org.example.maridone.core.user.UserAccount;
import org.example.maridone.core.user.UserAccountRepository;
import org.example.maridone.exception.conflict.OverlappingDatesException;
import org.example.maridone.exception.notfound.*;
import org.example.maridone.exception.toolarge.FileTooLargeException;
import org.example.maridone.exception.unauthorized.DuplicateLeaveException;
import org.example.maridone.exception.unauthorized.InvalidActionException;
import org.example.maridone.exception.unauthorized.InsufficientBalanceException;
import org.example.maridone.leave.balance.*;
import org.example.maridone.leave.dto.*;
import org.example.maridone.leave.request.LeaveRequest;
import org.example.maridone.leave.request.LeaveRequestRepository;
import org.example.maridone.leave.spec.LeaveSpecs;
import org.example.maridone.notification.NotificationService;
import org.example.maridone.schedule.shift.TemplateShiftRepository;
import org.example.maridone.schedule.shift.TemplateShiftSchedule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class LeaveService {

    private final LeaveBalanceRepository leaveBalanceRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final EmployeeRepository employeeRepository;
    private final UserAccountRepository userAccountRepository;
    private final TemplateShiftRepository templateShiftRepository;
    private final DocumentPathRepository documentPathRepository;
    private final LeaveMapper leaveMapper;
    private final DocumentService documentService;
    private final PayrollConfig payrollConfig;
    private final NotificationService notificationService;

    public LeaveService(
            LeaveBalanceRepository leaveBalanceRepository,
            LeaveRequestRepository leaveRequestRepository,
            EmployeeRepository employeeRepository,
            UserAccountRepository userAccountRepository,
            TemplateShiftRepository templateShiftRepository,
            DocumentPathRepository documentPathRepository,
            LeaveMapper leaveMapper,
            DocumentService documentService,
            PayrollConfig payrollConfig,
            NotificationService notificationService) {
        this.leaveBalanceRepository = leaveBalanceRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.employeeRepository = employeeRepository;
        this.userAccountRepository = userAccountRepository;
        this.templateShiftRepository = templateShiftRepository;
        this.documentPathRepository = documentPathRepository;
        this.leaveMapper = leaveMapper;
        this.documentService = documentService;
        this.payrollConfig = payrollConfig;
        this.notificationService = notificationService;
    }

    @Transactional
    @ExecutionTime
    public LeaveBalance createLeaveBalance(Long empId, BalanceRequestDto payload) {
        Specification<LeaveBalance> spec = Specification.allOf(
                LeaveSpecs.hasEmployeeId(empId),
                CommonSpecs.fieldEquals("leaveType", payload.getLeaveType())
        );
        Optional<LeaveBalance> check = leaveBalanceRepository.findOne(spec);
        Employee emp = employeeRepository.findById(empId).orElseThrow(() -> new EmployeeNotFoundException(empId));
        if (check.isPresent()) {
            throw new DuplicateLeaveException(empId, payload.getLeaveType());
        }

        LeaveBalance create = leaveBalanceRepository.save(leaveMapper.toEntity(payload, emp));
        return create;
    }

    @ExecutionTime
    public List<BalanceResponseDto> getBalance(Long empId) {
        return leaveMapper.toBalanceResponsesDto(leaveBalanceRepository.findByEmployee_EmployeeId(empId));
    }

    @ExecutionTime
    public Page<LeaveResponseDto> getLeaveRequests(LeaveFilter filter, Pageable pageable) {
        Specification<LeaveRequest> spec = Specification.allOf(
                LeaveSpecs.hasFilters(filter),
                CommonSpecs.fieldEquals("requestId", filter.requestId())
        );

        Page<LeaveRequest> entityPage = leaveRequestRepository.findAll(spec, pageable);
        return entityPage.map(leaveMapper::toLeaveResponseDto);
    }

    @ExecutionTime
    public Page<LeaveResponseDto> getTeamLeaveRequests(LeaveFilter filter, String username, Pageable pageable) {
        List<Long> handledEmployeeIds = resolveHandledEmployeeIds(username);
        if (handledEmployeeIds.isEmpty()) {
            return Page.empty(pageable);
        }

        Specification<LeaveRequest> spec = Specification.allOf(
                LeaveSpecs.hasFilters(filter),
                CommonSpecs.fieldEquals("requestId", filter.requestId()),
                LeaveSpecs.hasEmployeeIds(handledEmployeeIds)
        );

        return leaveRequestRepository.findAll(spec, pageable).map(leaveMapper::toLeaveResponseDto);
    }

    @ExecutionTime
    @Transactional
    @AuditLog
    @NotifyRole(message = "An employee cancelled a leave request.", targetRole = Position.HR)
    public void cancelRequest(Long requestId) {
        LeaveRequest request = leaveRequestRepository.findById(requestId).orElseThrow(() -> new RequestNotFoundException("Leave Request", requestId));
        if (request.getRequestStatus().equals(Status.APPROVED)) {
            throw new IllegalStateException("Cannot cancel. Dispute Request has been approved already.");
        }
        request.setRequestStatus(Status.CANCELLED);
        leaveRequestRepository.save(request);
    }

    @ExecutionTime
    public Page<LeaveResponseDto> getMyLeaveRequests(Long empId, Pageable pageable) {
        Page<LeaveRequest> leaveRequests = leaveRequestRepository.findByEmployee_EmployeeId(empId,pageable);
        return leaveRequests.map(leaveMapper::toLeaveResponseDto);
    }


    @Transactional
    @ExecutionTime
    public void updateLeaveBalance(Long empId, UpdateBalanceDto payload) {
        Specification<LeaveBalance> spec = Specification.allOf(
                LeaveSpecs.hasEmployeeId(empId),
                CommonSpecs.fieldEquals("leaveType",  payload.getLeaveType())
        );
        LeaveBalance lb = leaveBalanceRepository.findOne(spec).orElseThrow(() -> new LeaveNotFoundException
                (
                "Employee Id: " + empId + " has no Leave Balance of type: " + payload.getLeaveType()
                        + " Please create a leave type " + payload.getLeaveType() + " for Employee " + empId
                )
        );

        if (payload.getType().equalsIgnoreCase("add")) {
            lb.setBalanceHours(lb.getBalanceHours().add(payload.getBalanceHours()));
        } else {
            if (lb.getBalanceHours().compareTo(payload.getBalanceHours()) < 0) {
                String message = "Balance hours for Employee Id: " + empId + " is insufficient."
                        + " Remaining Balance = " + lb.getBalanceHours() + ", "
                        + "Requested = " +  payload.getBalanceHours();
                throw new InsufficientBalanceException(message);
            }
            lb.setBalanceHours(lb.getBalanceHours().subtract(payload.getBalanceHours()));
        }
        leaveBalanceRepository.save(lb);
    }


    //used by BalanceUpdaterTask
    @Transactional
    @AutoScheduled
    public void updateYearlyBalance() {
        List<EmploymentStatus> blacklistedStatuses = List.of(
                EmploymentStatus.TERMINATED,
                EmploymentStatus.SUSPENDED
        );

        int updateSickLeaves = leaveBalanceRepository.updateActiveEmployeeLeaveBalances(
                payrollConfig.getSickLeaveHours(),
                List.of(LeaveType.SICK_LEAVE),
                blacklistedStatuses
        );

        int updateVacationLeaves = leaveBalanceRepository.updateActiveEmployeeLeaveBalances(
                payrollConfig.getVacationLeaveHours(),
                List.of(LeaveType.VACATION_LEAVE),
                blacklistedStatuses
        );

        System.out.println("Updated Sick Leaves = " + updateSickLeaves);
        System.out.println("Updated Vacation Leaves = " + updateVacationLeaves);
    }

    @Transactional
    @ExecutionTime
    @NotifyRole(
            message = "New leave request from #{#result[0].employee.firstName} #{#result[0].employee.lastName} "
                    + "(#{#result[0].startDateTime.toLocalDate()} to #{#result[0].endDateTime.toLocalDate()})",
            targetRole = Position.HR,
            importance = "HIGH")
    public List<LeaveRequest> createLeaveRequest(LeaveRequestDto payload, Long empId, MultipartFile file) {
        Employee emp =  employeeRepository.findById(empId).orElseThrow(() -> new EmployeeNotFoundException(empId));
        List<LeaveRequest> requests = new ArrayList<>();

        BigDecimal remainingHours = leaveBalanceRepository
                .findByLeaveTypeAndEmployee_EmployeeId(payload.getLeaveType(), empId)
                .orElseThrow(() -> new BalanceNotFoundException("Balance of Type:  " + payload.getLeaveType() +  " for Employee ID: " + empId + " not found."))
                .getBalanceHours();

        //throw error if there is a pending/approved leave for your new specified leave request,
        //REGARDLESS of leave type
        Specification<LeaveRequest> spec = Specification.allOf(
          CommonSpecs.fieldGreaterThanOrEqual("endDateTime", payload.getStartDateTime()),
          CommonSpecs.fieldLessThanOrEqual("startDateTime", payload.getEndDateTime()),
          CommonSpecs.fieldEquals("employee", emp),
          CommonSpecs.fieldNotEquals("requestStatus", Status.CANCELLED),
          CommonSpecs.fieldNotEquals("requestStatus", Status.REJECTED)

        );
        if (leaveRequestRepository.exists(spec)) {
            throw new OverlappingDatesException("unable to process your request. There is an overlapping pending/approved request");
        }

        List<LeaveWindow> leaveWindows = resolveLeaveWindows(payload, empId);
        BigDecimal requestHoursTotal = calculateRequestHours(leaveWindows);

        boolean hasEnoughBalance = requestHoursTotal.compareTo(remainingHours) <= 0;
        if (hasEnoughBalance) {
            LeaveRequest request = createLeave(payload, emp, hasEnoughBalance);
            requests.add(request);
        } else {
            LocalDateTime splitPoint = resolvePaidCoverageEnd(payload, leaveWindows, remainingHours);

            if (payload.getStartDateTime().isBefore(splitPoint)) {
                LeaveRequest paidRequest = createLeave(
                        payload.getStartDateTime(),
                        splitPoint,
                        emp,
                        payload.getLeaveType(),
                        payload.getReason(),
                        true
                );
                requests.add(paidRequest);
            }

            if (splitPoint.isBefore(payload.getEndDateTime())) {
                LeaveRequest unpaidRequest = createLeave(
                        splitPoint,
                        payload.getEndDateTime(),
                        emp,
                        payload.getLeaveType(),
                        payload.getReason(),
                        false
                );
                requests.add(unpaidRequest);
            }
        }
        if (file != null) {
            //max file size = 10mb
            if (file.getSize() > (10*1024*1024)) {
                throw new FileTooLargeException("File size exceeded 10MB.");
            }
            documentService.upload(file, emp.getUserAccount().getUsername(), Directories.LEAVE_REQUEST.getDisplayName());
        }
        leaveRequestRepository.saveAll(requests);
        notifyManagerAboutLeaveRequest(emp, payload);
        return requests;
    }

    @Transactional
    @ExecutionTime
    @Notify(
            message = "Your leave request for #{#result.startDateTime.toLocalDate()} was #{#result.requestStatus}",
            importance = "HIGH",
            targetEmployee = "#result.employee")
    public LeaveRequest updateLeaveRequest(LeaveRequestDto payload, Long requestId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserAccount user = userAccountRepository.findByUsername(authentication.getName()).orElseThrow(
                () -> new AccountNotFoundException(authentication.getName()));

        LeaveRequest request = leaveRequestRepository.findById(requestId).orElseThrow(
                () -> new LeaveNotFoundException("Leave Request of Id: " + requestId + " is not found."));

        Long requestEmpId  = leaveRequestRepository.findEmployeeIdByRequestId(requestId).orElse(null);
        if (requestEmpId == null) {
            throw new IllegalStateException("Employee ID connected to request not found.");
        }

        validateApproverAccess(user, requestEmpId);

        if (request.getRequestStatus() != Status.PENDING)  {
            throw new IllegalStateException("This Request has been processed already.");
        }

        //update leave balance:
        if (request.getPaid() && payload.getRequestStatus().equals(Status.APPROVED)) {
            LeaveType leaveType = Optional.ofNullable(request.getLeaveType()).orElse(payload.getLeaveType());
            if (leaveType == null) {
                throw new IllegalStateException("Leave type is required before approving this request.");
            }
            updateLeaveBalance(requestEmpId,
                    new UpdateBalanceDto(
                            calculateRequestHours(request.getStartDateTime(),
                                    request.getEndDateTime(),
                                    requestEmpId),
                            leaveType,
                            "subtract"));
        }

        request.setApproverReason(payload.getApproverReason());
        request.setRequestStatus(payload.getRequestStatus());
        request.setApprover(user.getEmployee().getLastName() + ", " + user.getEmployee().getFirstName());
        request.setApprovedAt(Instant.now());
        leaveRequestRepository.save(request);
        return request;
    }




    //HELPER METHODS
    public BigDecimal calculateRequestHours(LeaveRequestDto payload, Long empId) {
        return calculateRequestHours(resolveLeaveWindows(payload, empId));
    }

    public BigDecimal calculateRequestHours(LocalDateTime start, LocalDateTime end, Long empId) {
        return calculateRequestHours(new LeaveRequestDto(start,end), empId);
    }

    public LeaveRequest createLeave(LeaveRequestDto payload, Employee emp, boolean isPaid) {
        return createLeave(
                payload.getStartDateTime(),
                payload.getEndDateTime(),
                emp,
                payload.getLeaveType(),
                payload.getReason(),
                isPaid
        );
    }

    private LeaveRequest createLeave(
            LocalDateTime startDateTime,
            LocalDateTime endDateTime,
            Employee emp,
            LeaveType leaveType,
            String reason,
            boolean isPaid
    ) {
        LeaveRequest request = new LeaveRequest();
        request.setRequestStatus(Status.PENDING);
        request.setStartDateTime(startDateTime);
        request.setEndDateTime(endDateTime);
        request.setEmployee(emp);
        request.setLeaveType(leaveType);
        request.setReason(reason);
        request.setPaid(isPaid);
        return request;
    }

    private List<LeaveWindow> resolveLeaveWindows(LeaveRequestDto payload, Long empId) {
        List<TemplateShiftSchedule> shiftSchedules = templateShiftRepository.findAllByEmployeeId(empId);
        if (shiftSchedules.isEmpty()) {
            throw new ShiftsNotFoundException("No Shift Schedule for Employee ID: " + empId);
        }

        Map<DayOfWeek, TemplateShiftSchedule> scheduleMap = shiftSchedules.stream()
                .collect(Collectors.toMap(TemplateShiftSchedule::getDayOfWeek, s -> s));

        List<LeaveWindow> windows = new ArrayList<>();
        LocalDate current = payload.getStartDateTime().toLocalDate();
        LocalDate endDate = payload.getEndDateTime().toLocalDate();
        LocalDateTime payloadStart = payload.getStartDateTime();
        LocalDateTime payloadEnd = payload.getEndDateTime();

        while (!current.isAfter(endDate)) {
            TemplateShiftSchedule schedule = scheduleMap.get(current.getDayOfWeek());

            if (schedule != null) {
                LocalDateTime shiftStart = LocalDateTime.of(current, schedule.getStartTime());
                LocalDateTime shiftEnd = LocalDateTime.of(current, schedule.getEndTime());

                if (!shiftEnd.isAfter(shiftStart)) {
                    shiftEnd = shiftEnd.plusDays(1);
                }

                LocalDateTime overlapStart = shiftStart.isBefore(payloadStart) ? payloadStart : shiftStart;
                LocalDateTime overlapEnd = shiftEnd.isAfter(payloadEnd) ? payloadEnd : shiftEnd;

                if (overlapStart.isBefore(overlapEnd)) {
                    windows.add(new LeaveWindow(overlapStart, overlapEnd));
                }
            }

            current = current.plusDays(1);
        }

        return windows;
    }

    private BigDecimal calculateRequestHours(List<LeaveWindow> leaveWindows) {
        BigDecimal hours = BigDecimal.ZERO;
        for (LeaveWindow window : leaveWindows) {
            hours = hours.add(CommonCalculator.calculateHours(window.startDateTime(), window.endDateTime()));
        }
        return hours;
    }

    private LocalDateTime resolvePaidCoverageEnd(
            LeaveRequestDto payload,
            List<LeaveWindow> leaveWindows,
            BigDecimal remainingHours
    ) {
        if (remainingHours == null || remainingHours.compareTo(BigDecimal.ZERO) <= 0) {
            return payload.getStartDateTime();
        }

        long remainingMinutes = remainingHours
                .multiply(BigDecimal.valueOf(60))
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();

        for (LeaveWindow window : leaveWindows) {
            long windowMinutes = Duration.between(window.startDateTime(), window.endDateTime()).toMinutes();
            if (remainingMinutes >= windowMinutes) {
                remainingMinutes -= windowMinutes;
                if (remainingMinutes == 0) {
                    return window.endDateTime();
                }
                continue;
            }

            return window.startDateTime().plusMinutes(remainingMinutes);
        }

        return payload.getEndDateTime();
    }

    private List<Long> resolveHandledEmployeeIds(String username) {
        Long employeeId = userAccountRepository.findEmployeeIdByUsername(username);
        if (employeeId == null) {
            throw new AccountNotFoundException(username);
        }
        return employeeRepository.findHandledEmployeeIds(employeeId);
    }

    private void validateApproverAccess(UserAccount user, Long requestEmpId) {
        Position position = Optional.ofNullable(user.getEmployee())
                .map(Employee::getPosition)
                .orElse(Position.UNKNOWN);

        if (position != Position.MANAGER) {
            return;
        }

        List<Long> handledEmployeeIds = resolveHandledEmployeeIds(user.getUsername());
        if (!handledEmployeeIds.contains(requestEmpId)) {
            throw new InvalidActionException("Managers can only review leave requests from their own team.");
        }
    }

    private void notifyManagerAboutLeaveRequest(Employee employee, LeaveRequestDto payload) {
        Employee manager = employee.getManager();
        if (manager == null || manager.getPosition() != Position.MANAGER) {
            return;
        }

        String message = "New team leave request from " + employee.getFirstName() + " " + employee.getLastName()
                + " (" + payload.getStartDateTime().toLocalDate() + " to " + payload.getEndDateTime().toLocalDate() + ")";
        notificationService.createNotification(manager, message, "HIGH");
    }


    public LeaveDetails getLeaveRequestDetails(Long requestId) {
        LeaveDetails details = leaveRequestRepository.getLeaveRequestDetails(requestId);
        Specification<DocumentPath> spec = Specification.allOf(
                CommonSpecs.fieldLike("filePath", "leave-request"),
                DocumentSpecs.hasUsername(details.getUsername())
        );
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


    private record LeaveWindow(LocalDateTime startDateTime, LocalDateTime endDateTime) {}
}
