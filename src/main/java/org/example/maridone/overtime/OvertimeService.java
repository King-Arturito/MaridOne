package org.example.maridone.overtime;

import org.example.maridone.annotation.ExecutionTime;
import org.example.maridone.common.CommonSpecs;
import org.example.maridone.config.DefaultConfig;
import org.example.maridone.core.employee.Employee;
import org.example.maridone.core.employee.EmployeeRepository;
import org.example.maridone.core.spec.EmployeeSpecs;
import org.example.maridone.core.user.UserAccount;
import org.example.maridone.core.user.UserAccountRepository;
import org.example.maridone.enums.Position;
import org.example.maridone.enums.Status;
import org.example.maridone.exception.conflict.OverlappingDatesException;
import org.example.maridone.exception.notfound.AccountNotFoundException;
import org.example.maridone.exception.notfound.EmployeeNotFoundException;
import org.example.maridone.exception.badrequest.InvalidActionException;
import org.example.maridone.exception.unauthorized.OvertimeException;
import org.example.maridone.leave.request.LeaveRequestRepository;
import org.example.maridone.notification.NotificationMessageFormatter;
import org.example.maridone.notification.WorkflowEmailService;
import org.example.maridone.notification.event.BulkNotificationCreatedEvent;
import org.example.maridone.notification.event.NotificationCreatedEvent;
import org.example.maridone.overtime.dto.OvertimeRequestDto;
import org.example.maridone.overtime.dto.OvertimeResponseDto;
import org.example.maridone.overtime.dto.OvertimeUpdateDto;
import org.example.maridone.overtime.mapper.OvertimeMapper;
import org.example.maridone.overtime.spec.OvertimeSpecs;
import org.example.maridone.schedule.shift.TemplateShiftRepository;
import org.example.maridone.schedule.shift.TemplateShiftSchedule;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

@Service
public class OvertimeService {

    private final OvertimeRequestRepository overtimeRequestRepository;
    private final EmployeeRepository employeeRepository;
    private final OvertimeMapper overtimeMapper;
    private final UserAccountRepository userAccountRepository;
    private final TemplateShiftRepository templateShiftRepository;
    private final DefaultConfig defaultConfig;
    private final LeaveRequestRepository leaveRequestRepository;
    private final WorkflowEmailService workflowEmailService;
    private final ApplicationEventPublisher eventPublisher;

    public OvertimeService(OvertimeRequestRepository overtimeRequestRepository,
                           OvertimeMapper overtimeMapper,
                           EmployeeRepository employeeRepository,
                           UserAccountRepository userAccountRepository,
                           TemplateShiftRepository templateShiftRepository,
                           DefaultConfig defaultConfig,
                           LeaveRequestRepository leaveRequestRepository,
                           WorkflowEmailService workflowEmailService,
                           ApplicationEventPublisher eventPublisher) {
        this.overtimeRequestRepository = overtimeRequestRepository;
        this.overtimeMapper = overtimeMapper;
        this.employeeRepository = employeeRepository;
        this.userAccountRepository = userAccountRepository;
        this.templateShiftRepository = templateShiftRepository;
        this.defaultConfig = defaultConfig;
        this.leaveRequestRepository = leaveRequestRepository;
        this.workflowEmailService = workflowEmailService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    @ExecutionTime
    public OvertimeRequest createOvertimeRequest(OvertimeRequestDto requestDto, String username) {
        Specification<Employee> spec = Specification.allOf(
                EmployeeSpecs.hasUserAccount(username)
        );
        Employee emp = employeeRepository.findOne(spec).orElseThrow(
                () -> new AccountNotFoundException("UserAccount with: " + username + " not found."));
        validateRequestWindow(requestDto);
        validateNoLeaveConflict(emp, requestDto);
        validateOutsideShiftSchedule(emp, requestDto.getStartTime(), requestDto.getEndTime());

        Specification<OvertimeRequest> checkConflicts = Specification.allOf(
                CommonSpecs.fieldEquals("employee", emp),
                CommonSpecs.fieldNotEquals("requestStatus", Status.CANCELLED),
                CommonSpecs.fieldNotEquals("requestStatus", Status.REJECTED),
                OvertimeSpecs.checkOverlaps(requestDto.getStartTime(), requestDto.getEndTime())
        );
        if (overtimeRequestRepository.count(checkConflicts) > 0) {
            throw new OverlappingDatesException("Conflicting Schedules found.");
        }
        OvertimeRequest request = overtimeMapper.toOvertimeRequest(requestDto);
        request.setEmployee(emp);
        request.setRequestStatus(Status.PENDING_MANAGER);
        request.setSuggestedPaid(true);
        request.setPaid(true);

        OvertimeRequest savedRequest = overtimeRequestRepository.save(request);
        if (emp.getManager() != null) {
            notifyManager(
                    emp,
                    "New team overtime request from " + emp.getFirstName() + " " + emp.getLastName()
                            + " for " + requestDto.getWorkDate()
            );
            workflowEmailService.sendOvertimeSubmittedToManager(savedRequest);
        } else {
            notifyRole("New overtime request from " + emp.getFirstName() + " " + emp.getLastName()
                    + " for " + requestDto.getWorkDate() + " is waiting for management review.",
                    Position.MANAGEMENT);
            workflowEmailService.sendOvertimeSubmittedToManagement(savedRequest);
        }
        return savedRequest;
    }

    @Transactional
    @ExecutionTime
    public OvertimeResponseDto updateOvertimeRequest(OvertimeUpdateDto updateDto, String approverUsername) {
        OvertimeRequest req = overtimeRequestRepository.findByIdForUpdate(updateDto.getOvertimeId())
                .orElseThrow(() -> new OvertimeException("Overtime Not Found"));
        Employee emp = userAccountRepository.findByUsername(approverUsername)
                .map(UserAccount::getEmployee)
                .orElseThrow(() -> new AccountNotFoundException("Approver's UserAccount with: " + approverUsername + " not found."));

        Long requestEmployeeId = req.getEmployeeId() != null
                ? req.getEmployeeId()
                : req.getEmployee() != null
                    ? req.getEmployee().getEmployeeId()
                    : null;

        validateApproverAccess(emp, approverUsername, requestEmployeeId);

        if (emp.getPosition() == Position.MANAGER) {
            return handleManagerOvertimeReview(req, updateDto, emp);
        }
        if (emp.getPosition() == Position.MANAGEMENT) {
            return handleManagerOvertimeReview(req, updateDto, emp);
        }
        if (emp.getPosition() == Position.HR) {
            return handleHrOvertimeReview(req, updateDto, emp);
        }
        throw new InvalidActionException("Only managers, management, and HR can review overtime requests.");
    }

    @ExecutionTime
    public Page<OvertimeResponseDto> getAllOvertimeRequest(OvertimeFilter filter, Pageable pageable) {
        Specification<OvertimeRequest> specs =  Specification.allOf(
                OvertimeSpecs.filtered(filter)
        );
        Page<OvertimeRequest> requests = overtimeRequestRepository.findAll(specs, pageable);
        return requests.map(overtimeMapper::toOvertimeResponseDto);
    }

    @ExecutionTime
    public Page<OvertimeResponseDto> getTeamOvertimeRequests(OvertimeFilter filter, String username, Pageable pageable) {
        List<Long> handledEmployeeIds = resolveHandledEmployeeIds(username);
        if (handledEmployeeIds.isEmpty()) {
            return Page.empty(pageable);
        }

        Specification<OvertimeRequest> specs = Specification.allOf(
                OvertimeSpecs.filtered(filter),
                OvertimeSpecs.hasEmployeeIds(handledEmployeeIds)
        );
        return overtimeRequestRepository.findAll(specs, pageable).map(overtimeMapper::toOvertimeResponseDto);
    }


    @ExecutionTime
    public Page<OvertimeResponseDto> getMyOvertimeRequests(Long empId, Pageable pageable) {
        Specification<OvertimeRequest> specs =  Specification.allOf(
                OvertimeSpecs.hasEmployeeId(empId)
        );
        Page<OvertimeRequest> myRequests = overtimeRequestRepository.findAll(specs, pageable);
        return myRequests.map(overtimeMapper::toOvertimeResponseDto);
    }

    @Transactional
    @ExecutionTime
    public void cancelRequest(Long overtimeId) {
        OvertimeRequest overtimeRequest = overtimeRequestRepository.findByIdForUpdate(overtimeId)
                .orElseThrow(() -> new OvertimeException("Overtime Not Found"));

        Status requestStatus = overtimeRequest.getRequestStatus();
        boolean isPendingRequest =
                requestStatus == Status.PENDING
                        || requestStatus == Status.PENDING_MANAGER
                        || requestStatus == Status.PENDING_HR;

        if (!isPendingRequest && requestStatus != Status.APPROVED) {
            throw new InvalidActionException("Only pending or approved overtime requests can be cancelled.");
        }

        if (requestStatus == Status.APPROVED && overtimeRequest.getStartTime().isBefore(currentDateTime().plusHours(1))) {
            throw new InvalidActionException("Overtime request can only be cancelled at least 1 hour before the start time.");
        }

        overtimeRequest.setRequestStatus(Status.CANCELLED);
        overtimeRequestRepository.save(overtimeRequest);
        String employeeName = NotificationMessageFormatter.formatEmployeeName(overtimeRequest.getEmployee());
        String workDate = NotificationMessageFormatter.formatDate(overtimeRequest.getWorkDate());
        notifyRole("Overtime request from " + employeeName + " for " + workDate + " was cancelled.", Position.HR);
        notifyManager(
                overtimeRequest.getEmployee(),
                "Team overtime request from " + employeeName + " for " + workDate + " was cancelled."
        );

    }

    private List<Long> resolveHandledEmployeeIds(String username) {
        Long employeeId = userAccountRepository.findEmployeeIdByUsername(username);
        if (employeeId == null) {
            throw new AccountNotFoundException("UserAccount with: " + username + " not found.");
        }
        return employeeRepository.findHandledEmployeeIds(employeeId);
    }

    private void validateApproverAccess(Employee approver, String username, Long requestEmployeeId) {
        if (approver.getPosition() == Position.HR) {
            return;
        }

        if (approver.getPosition() == Position.MANAGEMENT) {
            if (isSameEmployee(approver, requestEmployeeId)) {
                return;
            }
            if (requestEmployeeId == null) {
                throw new InvalidActionException("The overtime request is missing its employee reference.");
            }

            Employee requestEmployee = employeeRepository.findById(requestEmployeeId)
                    .orElseThrow(() -> new EmployeeNotFoundException(requestEmployeeId));
            if (requestEmployee.getManager() != null) {
                throw new InvalidActionException("Management can only review overtime requests from employees without a manager.");
            }
            return;
        }

        if (approver.getPosition() != Position.MANAGER) {
            throw new InvalidActionException("Only managers, management, and HR can review overtime requests.");
        }

        if (requestEmployeeId == null) {
            throw new InvalidActionException("The overtime request is missing its employee reference.");
        }

        if (isSameEmployee(approver, requestEmployeeId)) {
            throw new InvalidActionException("Managers cannot review their own overtime requests.");
        }

        List<Long> handledEmployeeIds = resolveHandledEmployeeIds(username);
        if (!handledEmployeeIds.contains(requestEmployeeId)) {
            throw new InvalidActionException("Managers can only review overtime requests from their own team.");
        }
    }

    private boolean isSameEmployee(Employee employee, Long requestEmployeeId) {
        return employee != null
                && employee.getEmployeeId() != null
                && employee.getEmployeeId().equals(requestEmployeeId);
    }

    private void notifyManager(Employee employee, String message) {
        Employee manager = employee.getManager();
        if (manager == null || manager.getPosition() != Position.MANAGER || manager.getEmployeeId() == null) {
            return;
        }
        eventPublisher.publishEvent(new NotificationCreatedEvent(List.of(manager.getEmployeeId()), message, "HIGH"));
    }

    private void notifyRole(String message, Position position) {
        eventPublisher.publishEvent(new BulkNotificationCreatedEvent(message, "HIGH", position));
    }


    private OvertimeResponseDto handleManagerOvertimeReview(
            OvertimeRequest request,
            OvertimeUpdateDto updateDto,
            Employee manager
    ) {
        if (request.getRequestStatus() != Status.PENDING_MANAGER && request.getRequestStatus() != Status.PENDING) {
            throw new OvertimeException("This overtime request is no longer waiting for manager review.");
        }

        Status decision = validateReviewDecision(updateDto.getUpdateStatus());
        String reason = validateDecisionReason(updateDto.getApproveReason(), "Please enter at least 5 characters for the manager review.");
        String approverName = manager.getLastName() + ", " + manager.getFirstName();
        String reviewerLabel = resolveFirstLevelReviewerLabel(manager.getPosition());
        Instant decisionTime = Instant.now();

        request.setManagerApprover(approverName);
        request.setManagerApproveReason(reason);
        request.setManagerApprovedAt(decisionTime);

        if (decision == Status.APPROVED) {
            request.setRequestStatus(Status.PENDING_HR);
            request.setApprover(null);
            request.setApproveReason(null);
            request.setApprovedAt(null);
            overtimeRequestRepository.save(request);
            String employeeName = NotificationMessageFormatter.formatEmployeeName(request.getEmployee());
            String workDate = NotificationMessageFormatter.formatDate(request.getWorkDate());
            notifyRole("Overtime request from " + employeeName + " for " + workDate + " is ready for HR review.", Position.HR);
            notifyEmployee(
                    request.getEmployee(),
                    "Your overtime request for " + workDate + " was endorsed by " + reviewerLabel + " and is now waiting for HR review."
            );
            workflowEmailService.sendOvertimeForwardedToHr(request);
            workflowEmailService.sendOvertimeForwardedToEmployee(request);
        } else {
            request.setRequestStatus(Status.REJECTED);
            request.setApprover(approverName);
            request.setApproveReason(reason);
            request.setApprovedAt(decisionTime);
            overtimeRequestRepository.save(request);
            notifyEmployee(
                    request.getEmployee(),
                    "Your overtime request for " + NotificationMessageFormatter.formatDate(request.getWorkDate()) + " was denied by " + reviewerLabel + "."
            );
            workflowEmailService.sendOvertimeManagerDecisionToEmployee(request);
        }

        OvertimeResponseDto response = overtimeMapper.toOvertimeResponseDto(request);
        response.setEmployeeId(request.getEmployeeId());
        return response;
    }

    private OvertimeResponseDto handleHrOvertimeReview(
            OvertimeRequest request,
            OvertimeUpdateDto updateDto,
            Employee hr
    ) {
        if (request.getRequestStatus() != Status.PENDING_HR) {
            throw new OvertimeException("This overtime request is no longer waiting for HR review.");
        }

        Status decision = validateHrFinalizeDecision(updateDto.getUpdateStatus());
        String reason = validateDecisionReason(updateDto.getApproveReason(), "Please enter at least 5 characters for the HR decision.");
        String approverName = hr.getLastName() + ", " + hr.getFirstName();
        Instant decisionTime = Instant.now();

        if (decision == Status.APPROVED) {
            boolean finalPaid = resolveFinalPaidValue(updateDto.getPaid(), request.getPaid());
            boolean paidOverrideApplied = !Boolean.valueOf(finalPaid).equals(request.getSuggestedPaid());
            String paidOverrideReason = validatePaidOverrideReason(
                    paidOverrideApplied,
                    updateDto.getPaidOverrideReason()
            );
            request.setPaid(finalPaid);
            request.setPaidOverrideReason(paidOverrideReason);
        }

        request.setApprover(approverName);
        request.setRequestStatus(decision);
        request.setApprovedAt(decisionTime);
        request.setApproveReason(reason);
        overtimeRequestRepository.save(request);

        String workDate = NotificationMessageFormatter.formatDate(request.getWorkDate());
        if (decision == Status.APPROVED) {
            String payLabel = Boolean.TRUE.equals(request.getPaid()) ? "paid" : "unpaid";
            notifyEmployee(
                    request.getEmployee(),
                    "Your overtime request for " + workDate + " was approved by HR as " + payLabel + " overtime."
            );
            workflowEmailService.sendOvertimeDecisionToEmployee(request);
        }

        OvertimeResponseDto response = overtimeMapper.toOvertimeResponseDto(request);
        response.setEmployeeId(request.getEmployeeId());
        return response;
    }

    private Status validateReviewDecision(Status decision) {
        if (decision != Status.APPROVED && decision != Status.REJECTED) {
            throw new InvalidActionException("Only approve or reject decisions are allowed here.");
        }
        return decision;
    }

    private Status validateHrFinalizeDecision(Status decision) {
        if (decision != Status.APPROVED) {
            throw new InvalidActionException("HR can only finalize requests as approved.");
        }
        return decision;
    }

    private String resolveFirstLevelReviewerLabel(Position position) {
        return position == Position.MANAGEMENT ? "management" : "your manager";
    }

    private String validateDecisionReason(String reason, String message) {
        String trimmed = reason == null ? "" : reason.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String validatePaidOverrideReason(boolean overrideApplied, String reason) {
        if (!overrideApplied) {
            return null;
        }
        String trimmed = reason == null ? "" : reason.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean resolveFinalPaidValue(Boolean payloadPaid, Boolean currentPaid) {
        if (payloadPaid != null) {
            return payloadPaid;
        }
        return Boolean.TRUE.equals(currentPaid);
    }

    private void notifyEmployee(Employee employee, String message) {
        if (employee == null) {
            return;
        }
        eventPublisher.publishEvent(new NotificationCreatedEvent(List.of(employee.getEmployeeId()), message, "HIGH"));
    }

    private void validateRequestWindow(OvertimeRequestDto requestDto) {
        LocalDate workDate = requestDto.getWorkDate();
        LocalDateTime startTime = requestDto.getStartTime();
        LocalDateTime endTime = requestDto.getEndTime();

        if (workDate == null || startTime == null || endTime == null) {
            throw new OvertimeException("Overtime date and time window are required.");
        }
        if (!startTime.toLocalDate().equals(workDate)) {
            throw new OvertimeException("The overtime start date must match the selected work date.");
        }
        if (!endTime.isAfter(startTime)) {
            throw new OvertimeException("End date and time must be later than the start date and time.");
        }
        if (workDate.isBefore(currentDateTime().toLocalDate())) {
            throw new OvertimeException("Overtime cannot be requested for past dates.");
        }
        if (!startTime.isAfter(currentDateTime())) {
            throw new OvertimeException("Overtime cannot be requested for a time window that has already started or passed.");
        }

        LocalDate maxEndDate = startTime.toLocalDate().plusDays(1);
        if (endTime.toLocalDate().isAfter(maxEndDate)) {
            throw new OvertimeException("End date can only be up to the day after the selected start date.");
        }
        if (endTime.toLocalDate().isEqual(maxEndDate) && endTime.toLocalTime().isAfter(LocalTime.of(6, 0))) {
            throw new OvertimeException("If overtime ends the next day, the latest allowed end time is 6:00 AM.");
        }
    }

    private void validateOutsideShiftSchedule(Employee employee, LocalDateTime requestStart, LocalDateTime requestEnd) {
        TemplateShiftSchedule shiftSchedule = templateShiftRepository.findAllByEmployeeId(employee.getEmployeeId())
                .stream()
                .filter(schedule -> !schedule.isDayOff())
                .filter(schedule -> schedule.getDayOfWeek() == requestStart.getDayOfWeek())
                .findFirst()
                .orElseThrow(() -> new OvertimeException("Your work schedule is not set up yet, so overtime cannot be processed."));

        LocalDate shiftDate = requestStart.toLocalDate();
        LocalDateTime shiftStart = LocalDateTime.of(shiftDate, shiftSchedule.getStartTime());
        LocalDateTime shiftEnd = shiftSchedule.getEndTime().isAfter(shiftSchedule.getStartTime())
                || shiftSchedule.getEndTime().equals(shiftSchedule.getStartTime())
                ? LocalDateTime.of(shiftDate, shiftSchedule.getEndTime())
                : LocalDateTime.of(shiftDate.plusDays(1), shiftSchedule.getEndTime());

        boolean isOutsideShift = !requestEnd.isAfter(shiftStart) || !requestStart.isBefore(shiftEnd);
        if (!isOutsideShift) {
            throw new OvertimeException("Overtime can only be requested outside your scheduled shift.");
        }
    }

    private void validateNoLeaveConflict(Employee employee, OvertimeRequestDto requestDto) {
        if (employee == null || employee.getEmployeeId() == null || requestDto.getWorkDate() == null || requestDto.getEndTime() == null) {
            return;
        }

        boolean hasConflict = leaveRequestRepository.existsActiveOnDateRange(
                employee.getEmployeeId(),
                requestDto.getWorkDate().atStartOfDay(),
                requestDto.getEndTime().toLocalDate().plusDays(1).atStartOfDay()
        );

        if (hasConflict) {
            throw new OverlappingDatesException("Leave and overtime requests cannot be filed on the same day.");
        }
    }

    private LocalDateTime currentDateTime() {
        ZoneId zoneId = defaultConfig.getTimeZone();
        return LocalDateTime.now(defaultConfig.getTimeZone());
    }
}
