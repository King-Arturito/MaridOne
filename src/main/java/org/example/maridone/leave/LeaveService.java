package org.example.maridone.leave;

import org.example.maridone.annotation.*;
import org.example.maridone.common.CommonCalculator;
import org.example.maridone.common.CommonSpecs;
import org.example.maridone.config.DefaultConfig;
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
import org.example.maridone.holiday.HolidayLookup;
import org.example.maridone.holiday.HolidayService;
import org.example.maridone.leave.balance.*;
import org.example.maridone.leave.dto.*;
import org.example.maridone.leave.request.LeaveRequest;
import org.example.maridone.leave.request.LeaveRequestRepository;
import org.example.maridone.leave.spec.LeaveSpecs;
import org.example.maridone.notification.NotificationMessageFormatter;
import org.example.maridone.notification.NotificationService;
import org.example.maridone.notification.WorkflowEmailService;
import org.example.maridone.overtime.OvertimeRequestRepository;
import org.example.maridone.schedule.shift.DailyShiftRepository;
import org.example.maridone.schedule.shift.DailyShiftSchedule;
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
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class LeaveService {
    private static final int NON_SICK_LEAVE_NOTICE_DAYS = 7;
    private static final int MAX_ADVANCE_LEAVE_MONTHS = 2;

    private final LeaveBalanceRepository leaveBalanceRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final EmployeeRepository employeeRepository;
    private final UserAccountRepository userAccountRepository;
    private final DailyShiftRepository dailyShiftRepository;
    private final TemplateShiftRepository templateShiftRepository;
    private final DocumentPathRepository documentPathRepository;
    private final LeaveMapper leaveMapper;
    private final DocumentService documentService;
    private final PayrollConfig payrollConfig;
    private final DefaultConfig defaultConfig;
    private final NotificationService notificationService;
    private final OvertimeRequestRepository overtimeRequestRepository;
    private final WorkflowEmailService workflowEmailService;
    private final HolidayService holidayService;

    public LeaveService(
            LeaveBalanceRepository leaveBalanceRepository,
            LeaveRequestRepository leaveRequestRepository,
            EmployeeRepository employeeRepository,
            UserAccountRepository userAccountRepository,
            DailyShiftRepository dailyShiftRepository,
            TemplateShiftRepository templateShiftRepository,
            DocumentPathRepository documentPathRepository,
            LeaveMapper leaveMapper,
            DocumentService documentService,
            PayrollConfig payrollConfig,
            DefaultConfig defaultConfig,
            NotificationService notificationService,
            OvertimeRequestRepository overtimeRequestRepository,
            WorkflowEmailService workflowEmailService,
            HolidayService holidayService) {
        this.leaveBalanceRepository = leaveBalanceRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.employeeRepository = employeeRepository;
        this.userAccountRepository = userAccountRepository;
        this.dailyShiftRepository = dailyShiftRepository;
        this.templateShiftRepository = templateShiftRepository;
        this.documentPathRepository = documentPathRepository;
        this.leaveMapper = leaveMapper;
        this.documentService = documentService;
        this.payrollConfig = payrollConfig;
        this.defaultConfig = defaultConfig;
        this.notificationService = notificationService;
        this.overtimeRequestRepository = overtimeRequestRepository;
        this.workflowEmailService = workflowEmailService;
        this.holidayService = holidayService;
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
    public void cancelRequest(Long requestId) {
        LeaveRequest request = leaveRequestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new RequestNotFoundException("Leave Request", requestId));

        Status requestStatus = request.getRequestStatus();
        boolean isPendingRequest =
                requestStatus == Status.PENDING
                        || requestStatus == Status.PENDING_MANAGER
                        || requestStatus == Status.PENDING_HR;

        if (!isPendingRequest && requestStatus != Status.APPROVED) {
            throw new InvalidActionException("Only pending or approved leave requests can be cancelled.");
        }

        if (requestStatus == Status.APPROVED
                && request.getStartDateTime().toLocalDate().isBefore(LocalDate.now(defaultConfig.getTimeZone()).plusDays(2))) {
            throw new InvalidActionException("Leave request can only be cancelled at least 2 days before the start date.");
        }

        request.setRequestStatus(Status.CANCELLED);
        leaveRequestRepository.save(request);
        String employeeName = NotificationMessageFormatter.formatEmployeeName(request.getEmployee());
        String leaveDates = NotificationMessageFormatter.formatDateRange(
                request.getStartDateTime().toLocalDate(),
                request.getEndDateTime().toLocalDate()
        );
        notifyHr("Leave request from " + employeeName + " (" + leaveDates + ") was cancelled.");
        notifyManager(
                request.getEmployee(),
                "Team leave request from " + employeeName + " (" + leaveDates + ") was cancelled."
        );
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
    public List<LeaveRequest> createLeaveRequest(LeaveRequestDto payload, Long empId, MultipartFile file) {
        Employee emp =  employeeRepository.findById(empId).orElseThrow(() -> new EmployeeNotFoundException(empId));
        validateRequestWindow(payload);
        validateAdvanceNotice(payload);
        validateNoOvertimeConflict(empId, payload.getStartDateTime(), payload.getEndDateTime());

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

        List<LeaveRequest> requests = new ArrayList<>();

        if (payload.getLeaveType() == LeaveType.EMERGENCY_LEAVE) {
            LeaveRequest request = createLeave(payload, emp, false);
            requests.add(request);
        } else {
            BigDecimal remainingHours = leaveBalanceRepository
                    .findByLeaveTypeAndEmployee_EmployeeId(payload.getLeaveType(), empId)
                    .orElseThrow(() -> new BalanceNotFoundException("Balance of Type:  " + payload.getLeaveType() + " for Employee ID: " + empId + " not found."))
                    .getBalanceHours();

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

                LocalDateTime unpaidStart = resolveUnpaidCoverageStart(splitPoint, leaveWindows, payload.getEndDateTime());
                if (unpaidStart.isBefore(payload.getEndDateTime())) {
                    LeaveRequest unpaidRequest = createLeave(
                            unpaidStart,
                            payload.getEndDateTime(),
                            emp,
                            payload.getLeaveType(),
                            payload.getReason(),
                            false
                    );
                    requests.add(unpaidRequest);
                }
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

        if (emp.getManager() != null) {
            notifyManager(
                    emp,
                    "New team leave request from " + emp.getFirstName() + " " + emp.getLastName()
                            + " (" + payload.getStartDateTime().toLocalDate() + " to " + payload.getEndDateTime().toLocalDate() + ")"
            );
            workflowEmailService.sendLeaveSubmittedToManager(emp, payload);
        } else {
            notifyManagement("New leave request from " + emp.getFirstName() + " " + emp.getLastName()
                    + " (" + payload.getStartDateTime().toLocalDate() + " to " + payload.getEndDateTime().toLocalDate() + ") is waiting for management review.");
            workflowEmailService.sendLeaveSubmittedToManagement(emp, payload);
        }
        return requests;
    }

    private void validateNoOvertimeConflict(Long employeeId, LocalDateTime leaveStart, LocalDateTime leaveEnd) {
        if (employeeId == null || leaveStart == null || leaveEnd == null) {
            return;
        }

        boolean hasConflict = overtimeRequestRepository.existsActiveOnWorkDateRange(
                employeeId,
                leaveStart.toLocalDate(),
                leaveEnd.toLocalDate()
        );

        if (hasConflict) {
            throw new OverlappingDatesException("Leave and overtime requests cannot be filed on the same day.");
        }
    }

    @Transactional
    @ExecutionTime
    public LeaveRequest updateLeaveRequest(LeaveRequestDto payload, Long requestId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserAccount user = userAccountRepository.findByUsername(authentication.getName()).orElseThrow(
                () -> new AccountNotFoundException(authentication.getName()));

        LeaveRequest request = leaveRequestRepository.findByIdForUpdate(requestId).orElseThrow(
                () -> new LeaveNotFoundException("Leave Request of Id: " + requestId + " is not found."));


        Long requestEmpId  = leaveRequestRepository.findEmployeeIdByRequestId(requestId).orElse(null);
        if (requestEmpId == null) {
            throw new IllegalStateException("Employee ID connected to request not found.");
        }

        if  (requestEmpId.equals(verifyOwnEmployeeId(authentication.getName()))) {
            throw new InvalidActionException("You cannot approve your own request.");
        }

        validateApproverAccess(user, requestEmpId);

        return switch (resolveApproverPosition(user)) {
            case MANAGER -> handleManagerLeaveReview(request, payload, user);
            case MANAGEMENT -> handleManagerLeaveReview(request, payload, user);
            case HR -> handleHrLeaveReview(request, payload, user, requestEmpId);
            default -> throw new InvalidActionException("Only managers, management, and HR can review leave requests.");
        };
    }

    private Long verifyOwnEmployeeId(String name) {
        UserAccount userAccount = userAccountRepository.findByUsername(name).orElseThrow((
                () -> new AccountNotFoundException(name)
                ));
        return userAccount.getEmployeeId();
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
        request.setRequestStatus(Status.PENDING_MANAGER);
        request.setStartDateTime(startDateTime);
        request.setEndDateTime(endDateTime);
        request.setEmployee(emp);
        request.setLeaveType(leaveType);
        request.setReason(reason);
        request.setPaid(isPaid);
        request.setSuggestedPaid(isPaid);
        return request;
    }

    private void validateAdvanceNotice(LeaveRequestDto payload) {
        if (payload.getStartDateTime() == null || payload.getLeaveType() == null) {
            return;
        }

        LocalDate requestedStartDate = payload.getStartDateTime().toLocalDate();
        LocalDate today = LocalDate.now(defaultConfig.getTimeZone());

        if (isAdvanceBookingRestricted(payload.getLeaveType())) {
            LocalDate latestAllowedAdvanceDate = today.plusMonths(MAX_ADVANCE_LEAVE_MONTHS);
            if (!requestedStartDate.isBefore(latestAllowedAdvanceDate)) {
                throw new InvalidActionException("Leave requests cannot be filed 2 or more months in advance for this leave type.");
            }
        }

        LocalDate earliestAllowedDate = today.plusDays(NON_SICK_LEAVE_NOTICE_DAYS);
        if (payload.getLeaveType() != LeaveType.EMERGENCY_LEAVE && payload.getLeaveType() != LeaveType.SICK_LEAVE && requestedStartDate.isBefore(earliestAllowedDate)) {
            throw new InvalidActionException(
                    "Non-sick leave requests must be filed at least " + NON_SICK_LEAVE_NOTICE_DAYS + " days before the leave date."
            );
        }
    }

    private boolean isAdvanceBookingRestricted(LeaveType leaveType) {
        return leaveType == LeaveType.SICK_LEAVE ||
               leaveType == LeaveType.EMERGENCY_LEAVE ||
               leaveType == LeaveType.MAGNA_CARTA ||
               leaveType == LeaveType.SOLO_PARENT_LEAVE ||
               leaveType == LeaveType.RA9262;
    }

    private void validateRequestWindow(LeaveRequestDto payload) {
        if (payload.getStartDateTime() == null || payload.getEndDateTime() == null) {
            throw new InvalidActionException("Leave start and end date-time are required.");
        }

        if (!payload.getEndDateTime().isAfter(payload.getStartDateTime())) {
            throw new InvalidActionException("Leave end date-time must be later than the start date-time.");
        }

        LocalDateTime now = LocalDateTime.now(defaultConfig.getTimeZone());
        if (!payload.getStartDateTime().isAfter(now)) {
            throw new InvalidActionException("Leave cannot be filed for a time window that has already started or passed.");
        }
    }

    private List<LeaveWindow> resolveLeaveWindows(LeaveRequestDto payload, Long empId) {
        LocalDate startDate = payload.getStartDateTime().toLocalDate();
        LocalDate endDate = payload.getEndDateTime().toLocalDate();
        List<TemplateShiftSchedule> shiftSchedules = templateShiftRepository.findAllByEmployeeId(empId);
        List<DailyShiftSchedule> dailyShifts = Optional.ofNullable(dailyShiftRepository.findAllByEmployeeIdAndPeriod(
                empId,
                startDate.atStartOfDay(),
                endDate.plusDays(1).atStartOfDay()
        )).orElse(List.of());

        if (shiftSchedules.isEmpty() && dailyShifts.isEmpty()) {
            throw new ShiftsNotFoundException("No Shift Schedule for Employee ID: " + empId);
        }

        Map<DayOfWeek, TemplateShiftSchedule> scheduleMap = shiftSchedules.stream()
                .collect(Collectors.toMap(TemplateShiftSchedule::getDayOfWeek, s -> s));
        Map<LocalDate, List<DailyShiftSchedule>> dailyShiftMap = dailyShifts.stream()
                .filter(Objects::nonNull)
                .filter(shift -> shift.getWorkDate() != null)
                .collect(Collectors.groupingBy(DailyShiftSchedule::getWorkDate));

        List<LeaveWindow> windows = new ArrayList<>();
        LocalDate current = startDate;
        LocalDateTime payloadStart = payload.getStartDateTime();
        LocalDateTime payloadEnd = payload.getEndDateTime();
        HolidayLookup holidayLookup = safeHolidayLookup(startDate, endDate);
        java.util.Set<LocalDate> holidayDates = holidayLookup.holidayDates();

        while (!current.isAfter(endDate)) {
            if (holidayDates.contains(current)) {
                current = current.plusDays(1);
                continue;
            }

            for (LeaveWindow effectiveWindow : resolveEffectiveShiftWindows(current, scheduleMap, dailyShiftMap)) {
                LocalDateTime overlapStart = effectiveWindow.startDateTime().isBefore(payloadStart)
                        ? payloadStart
                        : effectiveWindow.startDateTime();
                LocalDateTime overlapEnd = effectiveWindow.endDateTime().isAfter(payloadEnd)
                        ? payloadEnd
                        : effectiveWindow.endDateTime();

                if (overlapStart.isBefore(overlapEnd)) {
                    windows.add(new LeaveWindow(overlapStart, overlapEnd));
                }
            }

            current = current.plusDays(1);
        }

        return windows;
    }

    private List<LeaveWindow> resolveEffectiveShiftWindows(
            LocalDate workDate,
            Map<DayOfWeek, TemplateShiftSchedule> scheduleMap,
            Map<LocalDate, List<DailyShiftSchedule>> dailyShiftMap
    ) {
        List<DailyShiftSchedule> dailyShifts = dailyShiftMap.get(workDate);
        if (dailyShifts != null && !dailyShifts.isEmpty()) {
            if (dailyShifts.stream().anyMatch(DailyShiftSchedule::isDayOff)) {
                return List.of();
            }

            return dailyShifts.stream()
                    .map(this::toLeaveWindow)
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(LeaveWindow::startDateTime))
                    .toList();
        }

        TemplateShiftSchedule schedule = scheduleMap.get(workDate.getDayOfWeek());
        if (schedule == null) {
            return List.of();
        }

        return List.of(toLeaveWindow(workDate, schedule));
    }

    private LeaveWindow toLeaveWindow(DailyShiftSchedule shift) {
        if (shift == null || shift.getStartDateTime() == null || shift.getEndDateTime() == null) {
            return null;
        }

        if (!shift.getEndDateTime().isAfter(shift.getStartDateTime())) {
            return null;
        }

        return new LeaveWindow(shift.getStartDateTime(), shift.getEndDateTime());
    }

    private LeaveWindow toLeaveWindow(LocalDate workDate, TemplateShiftSchedule schedule) {
        LocalDateTime shiftStart = LocalDateTime.of(workDate, schedule.getStartTime());
        LocalDateTime shiftEnd = LocalDateTime.of(workDate, schedule.getEndTime());

        if (!shiftEnd.isAfter(shiftStart)) {
            shiftEnd = shiftEnd.plusDays(1);
        }

        return new LeaveWindow(shiftStart, shiftEnd);
    }

    private BigDecimal calculateRequestHours(List<LeaveWindow> leaveWindows) {
        BigDecimal hours = BigDecimal.ZERO;
        for (LeaveWindow window : leaveWindows) {
            BigDecimal rawHours = CommonCalculator.calculateHours(window.startDateTime(), window.endDateTime());
            hours = hours.add(CommonCalculator.deductUnpaidLunchHour(rawHours));
        }
        return hours;
    }

    private HolidayLookup safeHolidayLookup(LocalDate startDate, LocalDate endDate) {
        try {
            HolidayLookup lookup = holidayService.getHolidayLookup(startDate, endDate);
            return lookup == null ? HolidayLookup.empty() : lookup;
        } catch (RuntimeException exception) {
            return HolidayLookup.empty();
        }
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
            long rawWindowMinutes = Duration.between(window.startDateTime(), window.endDateTime()).toMinutes();
            // If the window is > 5 hours, 1 hour (60 mins) is unpaid lunch and doesn't count towards the balance deduction.
            long deductibleMinutes = rawWindowMinutes > 300 ? rawWindowMinutes - 60 : rawWindowMinutes;

            if (remainingMinutes >= deductibleMinutes) {
                remainingMinutes -= deductibleMinutes;
                if (remainingMinutes == 0) {
                    return window.endDateTime();
                }
                continue;
            }

            // The remaining balance ends within this window.
            long minutesToAdd = remainingMinutes;
            // If the partial leave being consumed in this window exceeds 5 hours, skip the lunch hour.
            if (minutesToAdd > 300) {
                minutesToAdd += 60;
            }

            return window.startDateTime().plusMinutes(minutesToAdd);
        }

        return payload.getEndDateTime();
    }

    private LocalDateTime resolveUnpaidCoverageStart(
            LocalDateTime splitPoint,
            List<LeaveWindow> leaveWindows,
            LocalDateTime fallbackEnd
    ) {
        if (splitPoint == null || leaveWindows == null || leaveWindows.isEmpty()) {
            return splitPoint == null ? fallbackEnd : splitPoint;
        }

        for (int index = 0; index < leaveWindows.size(); index++) {
            LeaveWindow window = leaveWindows.get(index);

            if (splitPoint.isBefore(window.startDateTime())) {
                return window.startDateTime();
            }

            if (!splitPoint.isBefore(window.startDateTime()) && splitPoint.isBefore(window.endDateTime())) {
                return splitPoint;
            }

            if (splitPoint.equals(window.endDateTime())) {
                if (index + 1 < leaveWindows.size()) {
                    return leaveWindows.get(index + 1).startDateTime();
                }
                return fallbackEnd;
            }
        }

        return fallbackEnd;
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

        if (position == Position.HR) {
            return;
        }

        if (position == Position.MANAGEMENT) {
            Employee requestEmployee = employeeRepository.findById(requestEmpId)
                    .orElseThrow(() -> new EmployeeNotFoundException(requestEmpId));
            if (requestEmployee.getManager() != null) {
                throw new InvalidActionException("Management can only review leave requests from employees without a manager.");
            }
            return;
        }

        if (position != Position.MANAGER) {
            throw new InvalidActionException("Only managers, management, and HR can review leave requests.");
        }

        List<Long> handledEmployeeIds = resolveHandledEmployeeIds(user.getUsername());
        if (!handledEmployeeIds.contains(requestEmpId)) {
            throw new InvalidActionException("Managers can only review leave requests from their own team.");
        }
    }

    private void notifyManager(Employee employee, String message) {
        Employee manager = employee.getManager();
        if (manager == null || manager.getPosition() != Position.MANAGER) {
            return;
        }
        notificationService.createNotification(manager, message, "HIGH");
    }

    private void notifyHr(String message) {
        List<Long> hrEmployeeIds = employeeRepository.findEmployeeIdsByPosition(Position.HR, EmploymentStatus.TERMINATED);
        for (Long employeeId : hrEmployeeIds) {
            notificationService.createNotification(employeeRepository.getReferenceById(employeeId), message, "HIGH");
        }
    }

    private void notifyManagement(String message) {
        List<Long> managementEmployeeIds = employeeRepository.findEmployeeIdsByPosition(Position.MANAGEMENT, EmploymentStatus.TERMINATED);
        for (Long employeeId : managementEmployeeIds) {
            notificationService.createNotification(employeeRepository.getReferenceById(employeeId), message, "HIGH");
        }
    }

    private LeaveRequest handleManagerLeaveReview(LeaveRequest request, LeaveRequestDto payload, UserAccount user) {
        if (request.getRequestStatus() != Status.PENDING_MANAGER && request.getRequestStatus() != Status.PENDING) {
            throw new IllegalStateException("This leave request is no longer waiting for manager review.");
        }

        Status decision = validateReviewDecision(payload.getRequestStatus());
        String reason = validateDecisionReason(payload.getApproverReason(), "Please enter at least 5 characters for the manager review.");
        String approverName = formatApproverName(user);
        String reviewerLabel = resolveFirstLevelReviewerLabel(resolveApproverPosition(user));
        Instant decisionTime = Instant.now();

        request.setManagerApprover(approverName);
        request.setManagerApproverReason(reason);
        request.setManagerApprovedAt(decisionTime);

        if (decision == Status.APPROVED) {
            request.setRequestStatus(Status.PENDING_HR);
            request.setApprover(null);
            request.setApproverReason(null);
            request.setApprovedAt(null);
            leaveRequestRepository.save(request);

            String employeeName = NotificationMessageFormatter.formatEmployeeName(request.getEmployee());
            String leaveDates = NotificationMessageFormatter.formatDateRange(
                    request.getStartDateTime().toLocalDate(),
                    request.getEndDateTime().toLocalDate()
            );
            notifyHr("Leave request from " + employeeName + " (" + leaveDates + ") is ready for HR review.");
            notifyEmployee(
                    request.getEmployee(),
                    "Your leave request (" + leaveDates + ") was endorsed by " + reviewerLabel + " and is now waiting for HR review."
            );
            workflowEmailService.sendLeaveForwardedToHr(request);
            workflowEmailService.sendLeaveForwardedToEmployee(request);
            return request;
        }

        request.setRequestStatus(Status.REJECTED);
        request.setApprover(approverName);
        request.setApproverReason(reason);
        request.setApprovedAt(decisionTime);
        leaveRequestRepository.save(request);
        notifyEmployee(
                request.getEmployee(),
                "Your leave request (" + NotificationMessageFormatter.formatDateRange(
                        request.getStartDateTime().toLocalDate(),
                        request.getEndDateTime().toLocalDate()
                ) + ") was denied by " + reviewerLabel + "."
        );
        workflowEmailService.sendLeaveManagerDecisionToEmployee(request);
        return request;
    }

    private LeaveRequest handleHrLeaveReview(
            LeaveRequest request,
            LeaveRequestDto payload,
            UserAccount user,
            Long requestEmpId
    ) {
        if (request.getRequestStatus() != Status.PENDING_HR) {
            throw new IllegalStateException("This leave request is no longer waiting for HR review.");
        }

        Status decision = validateHrFinalizeDecision(payload.getRequestStatus());
        String reason = validateDecisionReason(payload.getApproverReason(), "Please enter at least 5 characters for the HR decision.");
        String approverName = formatApproverName(user);
        Instant decisionTime = Instant.now();

        if (decision == Status.APPROVED) {
            boolean finalPaid = resolveFinalPaidValue(payload.getPaid(), request.getPaid());
            boolean paidOverrideApplied = !Boolean.valueOf(finalPaid).equals(request.getSuggestedPaid());
            String paidOverrideReason = validatePaidOverrideReason(
                    paidOverrideApplied,
                    payload.getPaidOverrideReason()
            );

            if (Boolean.TRUE.equals(request.getSuggestedPaid()) && finalPaid && request.getLeaveType() != LeaveType.EMERGENCY_LEAVE) {
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

            request.setPaid(finalPaid);
            request.setPaidOverrideReason(paidOverrideReason);
        }

        request.setApproverReason(reason);
        request.setRequestStatus(decision);
        request.setApprover(approverName);
        request.setApprovedAt(decisionTime);
        leaveRequestRepository.save(request);

        String leaveDates = NotificationMessageFormatter.formatDateRange(
                request.getStartDateTime().toLocalDate(),
                request.getEndDateTime().toLocalDate()
        );
        if (decision == Status.APPROVED) {
            String payLabel = Boolean.TRUE.equals(request.getPaid()) ? "paid" : "unpaid";
            notifyEmployee(
                    request.getEmployee(),
                    "Your leave request (" + leaveDates + ") was approved by HR as " + payLabel + " leave."
            );
            workflowEmailService.sendLeaveDecisionToEmployee(request);
        }
        return request;
    }

    private Position resolveApproverPosition(UserAccount user) {
        return Optional.ofNullable(user.getEmployee())
                .map(Employee::getPosition)
                .orElse(Position.UNKNOWN);
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
        if (trimmed.length() < 5) {
            throw new InvalidActionException(message);
        }
        return trimmed;
    }

    private String validatePaidOverrideReason(boolean overrideApplied, String reason) {
        if (!overrideApplied) {
            return null;
        }
        String trimmed = reason == null ? "" : reason.trim();
        if (trimmed.length() < 5) {
            throw new InvalidActionException("Please explain why HR is overriding the paid or unpaid leave treatment.");
        }
        return trimmed;
    }

    private boolean resolveFinalPaidValue(Boolean payloadPaid, Boolean currentPaid) {
        if (payloadPaid != null) {
            return payloadPaid;
        }
        return Boolean.TRUE.equals(currentPaid);
    }

    private String formatApproverName(UserAccount user) {
        Employee employee = Optional.ofNullable(user.getEmployee())
                .orElseThrow(() -> new AccountNotFoundException(user.getUsername()));
        return employee.getLastName() + ", " + employee.getFirstName();
    }

    private void notifyEmployee(Employee employee, String message) {
        if (employee == null) {
            return;
        }
        notificationService.createNotification(employee, message, "HIGH");
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
