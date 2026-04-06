package org.example.maridone.aspect;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.example.maridone.core.dto.CreateUserAccountDto;
import org.example.maridone.core.dto.EmployeeResponseDto;
import org.example.maridone.core.employee.Employee;
import org.example.maridone.core.employee.EmployeeRepository;
import org.example.maridone.core.user.UserAccount;
import org.example.maridone.enums.Activity;
import org.example.maridone.enums.Status;
import org.example.maridone.leave.dto.LeaveRequestDto;
import org.example.maridone.leave.dto.UpdateBalanceDto;
import org.example.maridone.leave.request.LeaveRequest;
import org.example.maridone.leave.request.LeaveRequestRepository;
import org.example.maridone.log.ActivityLogService;
import org.example.maridone.log.dto.ActivityRequestDto;
import org.example.maridone.overtime.OvertimeRequest;
import org.example.maridone.overtime.OvertimeRequestRepository;
import org.example.maridone.overtime.dto.OvertimeRequestDto;
import org.example.maridone.overtime.dto.OvertimeResponseDto;
import org.example.maridone.overtime.dto.OvertimeUpdateDto;
import org.example.maridone.payroll.dispute.DisputeRequest;
import org.example.maridone.payroll.dispute.DisputeRequestRepository;
import org.example.maridone.payroll.dto.DisputeActionDto;
import org.example.maridone.payroll.dto.DisputeRequestDto;
import org.example.maridone.payroll.dto.DisputeResponseDto;
import org.example.maridone.payroll.dto.ItemDetailsDto;
import org.example.maridone.payroll.dto.PayrollItemDto;
import org.example.maridone.payroll.dto.PayrollRunViewDto;
import org.example.maridone.payroll.dto.RunCreateDto;
import org.example.maridone.payroll.dto.RunResponseDto;
import org.example.maridone.payroll.item.PayrollItem;
import org.example.maridone.payroll.run.PayrollItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Aspect
@Order(2)
public class LoggingAspect {
    private static final Logger logger = LoggerFactory.getLogger(LoggingAspect.class);
    private static final DateTimeFormatter AUDIT_DATE_FORMAT = DateTimeFormatter.ofPattern("MMMM d, uuuu");
    private static final DateTimeFormatter AUDIT_TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm a");
    private static final Pattern EMPLOYEE_ENDPOINT_PATTERN = Pattern.compile("^/api/employees(?:/status)?/(?<employeeId>\\d+)\\b");
    private static final Pattern USER_ACCOUNT_ENDPOINT_PATTERN = Pattern.compile("^/api/user/(?<employeeId>\\d+)\\b");
    private static final Pattern LEAVE_BALANCE_ENDPOINT_PATTERN = Pattern.compile("^/api/leave/balance(?:/update)?/(?<employeeId>\\d+)\\b");
    private static final Pattern LEAVE_CREATE_ENDPOINT_PATTERN = Pattern.compile("^/api/leave/create/(?<employeeId>\\d+)\\b");
    private static final Pattern LEAVE_REQUEST_ENDPOINT_PATTERN = Pattern.compile("^/api/leave/update/(?<requestId>\\d+)\\b");
    private static final Pattern LEAVE_CANCEL_ENDPOINT_PATTERN = Pattern.compile("^/api/leave/request/cancel/(?<requestId>\\d+)\\b");
    private static final Pattern OVERTIME_CANCEL_ENDPOINT_PATTERN = Pattern.compile("^/api/overtime/cancel/(?<overtimeId>\\d+)\\b");
    private static final Pattern DISPUTE_CREATE_ENDPOINT_PATTERN = Pattern.compile("^/api/payroll/item/dispute/create/(?<itemId>\\d+)\\b");
    private static final Pattern DISPUTE_ENDPOINT_PATTERN = Pattern.compile("^/api/payroll/item/dispute/update/(?<disputeId>\\d+)\\b");
    private static final Pattern DISPUTE_CANCEL_ENDPOINT_PATTERN = Pattern.compile("^/api/payroll/item/dispute/cancel/(?<disputeId>\\d+)\\b");

    private final ActivityLogService activityLogService;
    private final EmployeeRepository employeeRepository;
    private final OvertimeRequestRepository overtimeRequestRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final DisputeRequestRepository disputeRequestRepository;
    private final PayrollItemRepository payrollItemRepository;

    public LoggingAspect(ActivityLogService activityLogService,
                         EmployeeRepository employeeRepository,
                         OvertimeRequestRepository overtimeRequestRepository,
                         LeaveRequestRepository leaveRequestRepository,
                         DisputeRequestRepository disputeRequestRepository,
                         PayrollItemRepository payrollItemRepository) {
        this.activityLogService = activityLogService;
        this.employeeRepository = employeeRepository;
        this.overtimeRequestRepository = overtimeRequestRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.disputeRequestRepository = disputeRequestRepository;
        this.payrollItemRepository = payrollItemRepository;
    }

    @Pointcut("@annotation(org.example.maridone.annotation.AuditLog)")
    public void auditLog() {}

    @Pointcut("@annotation(org.example.maridone.annotation.AutoScheduled)")
    public void auditScheduling() {}

    @AfterReturning(pointcut = "auditLog()", returning = "result")
    public void logToDatabase(JoinPoint joinPoint, Object result) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        String httpMethod = attributes != null ? attributes.getRequest().getMethod() : "unknownHttpMethod";
        String requestURI = attributes != null ? attributes.getRequest().getRequestURI() : "unknownURI";
        String username = resolveUsername();
        Activity activity = resolveActivity(httpMethod);
        String message = buildAuditMessage(username, activity, requestURI, joinPoint.getArgs(), result);
        logger.info("Inserting to database: {}", message);
        activityLogService.activity(new ActivityRequestDto(username, activity, message));
    }

    @AfterReturning("auditScheduling()")
    public void logSystemRuns(JoinPoint joinPoint) {
        String message = "Scheduled by System -> "
                + joinPoint.getSignature().getDeclaringType().getSimpleName()
                + " -> " + joinPoint.getSignature().getName();
        logger.info("Scheduled System run inserted to database: {}", message);
        activityLogService.activity(new ActivityRequestDto("System SUCCESS",  Activity.SYSTEM, message));
    }

    @AfterThrowing(value = "auditScheduling()", throwing = "error")
    public void logSystemThrows(JoinPoint joinPoint, Throwable error) {

        String rawErrorMessage = error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName();

        int maxLength = 100;
        if (rawErrorMessage.length() > maxLength) {
            rawErrorMessage = rawErrorMessage.substring(0, maxLength) + "...";
        }
        String message = "Scheduled by System -> "
                + joinPoint.getSignature().getDeclaringType().getSimpleName()
                + " -> " + joinPoint.getSignature().getName() + " ERROR: " + rawErrorMessage;
        logger.error("Scheduled System run failed: {}", message);
        activityLogService.activity(new ActivityRequestDto("System FAILURE",  Activity.SYSTEM, message));
    }

    private String resolveUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !auth.getPrincipal().equals("anonymousUser")) {
            return auth.getName();
        }
        return "unknownUser";
    }

    private Activity resolveActivity(String httpMethod) {
        return switch (httpMethod) {
            case "POST" -> Activity.CREATE;
            case "GET" -> Activity.READ;
            case "PUT", "PATCH" -> Activity.MODIFY;
            case "DELETE" -> Activity.DELETE;
            default -> Activity.UNKNOWN;
        };
    }

    private String buildAuditMessage(String username, Activity activity, String requestURI, Object[] args, Object result) {
        StringBuilder message = new StringBuilder();
        appendSentence(message, "Username: " + username, false);
        appendSentence(message, "Activity: " + activity, false);
        appendSentence(message, "Endpoint: " + requestURI, false);

        String detail = resolveAuditDetail(requestURI, args, result);
        if (detail != null && !detail.isBlank()) {
            appendSentence(message, detail, true);
        }

        String resolvedMessage = message.toString();
        return resolvedMessage.length() <= 255 ? resolvedMessage : resolvedMessage.substring(0, 252) + "...";
    }

    private String resolveAuditDetail(String requestURI, Object[] args, Object result) {
        if (requestURI == null || requestURI.isBlank()) {
            return null;
        }

        if ("/api/employees/create".equalsIgnoreCase(requestURI)) {
            EmployeeResponseDto response = extractBody(result, EmployeeResponseDto.class);
            if (response != null && response.getId() != null) {
                return "Employee record for " + formatEmployeeReference(
                        response.getId(),
                        response.getFirstName(),
                        response.getLastName()
                );
            }
        }

        if ("/api/user/create".equalsIgnoreCase(requestURI)) {
            UserAccount userAccount = extractBody(result, UserAccount.class);
            CreateUserAccountDto payload = findArg(args, CreateUserAccountDto.class);
            if (userAccount != null && userAccount.getEmployeeId() != null) {
                return describeUserAccountTarget(
                        userAccount.getUsername(),
                        userAccount.getEmployeeId(),
                        userAccount.getEmployee() != null ? userAccount.getEmployee().getFirstName() : null,
                        userAccount.getEmployee() != null ? userAccount.getEmployee().getLastName() : null
                );
            }
            if (payload != null && payload.getEmployeeId() != null) {
                return describeUserAccountTarget(payload.getUsername(), payload.getEmployeeId(), null, null);
            }
        }

        Matcher userAccountMatcher = USER_ACCOUNT_ENDPOINT_PATTERN.matcher(requestURI);
        if (userAccountMatcher.find()) {
            Long employeeId = Long.valueOf(userAccountMatcher.group("employeeId"));
            UserAccount userAccount = extractBody(result, UserAccount.class);
            CreateUserAccountDto payload = findArg(args, CreateUserAccountDto.class);
            return describeUserAccountTarget(
                    userAccount != null ? userAccount.getUsername() : payload != null ? payload.getUsername() : null,
                    employeeId,
                    userAccount != null && userAccount.getEmployee() != null ? userAccount.getEmployee().getFirstName() : null,
                    userAccount != null && userAccount.getEmployee() != null ? userAccount.getEmployee().getLastName() : null
            );
        }

        Matcher employeeMatcher = EMPLOYEE_ENDPOINT_PATTERN.matcher(requestURI);
        if (employeeMatcher.find()) {
            Long employeeId = Long.valueOf(employeeMatcher.group("employeeId"));
            if (requestURI.toLowerCase().startsWith("/api/employees/status/")) {
                return "Employment status for " + formatEmployeeReference(employeeId, null, null);
            }
            return "Employee record for " + formatEmployeeReference(employeeId, null, null);
        }

        Matcher leaveBalanceMatcher = LEAVE_BALANCE_ENDPOINT_PATTERN.matcher(requestURI);
        if (leaveBalanceMatcher.find()) {
            Long employeeId = Long.valueOf(leaveBalanceMatcher.group("employeeId"));
            UpdateBalanceDto balancePayload = findArg(args, UpdateBalanceDto.class);
            String detail = "Leave balance for " + formatEmployeeReference(employeeId, null, null);
            if (balancePayload != null && balancePayload.getLeaveType() != null) {
                return detail + ". Leave type: " + humanizeToken(balancePayload.getLeaveType().name());
            }
            return detail;
        }

        if ("/api/payroll/run/process".equalsIgnoreCase(requestURI)) {
            PayrollRunViewDto run = extractBody(result, PayrollRunViewDto.class);
            if (run != null) {
                return formatProcessedPayrollRunDetail(
                        run.getPeriodStart(),
                        run.getPeriodEnd(),
                        run.getRunType() != null ? run.getRunType().name() : null
                );
            }

            RunCreateDto payload = findArg(args, RunCreateDto.class);
            if (payload != null) {
                return formatProcessedPayrollRunDetail(
                        payload.getPeriodStart(),
                        payload.getPeriodEnd(),
                        payload.getRunType() != null ? payload.getRunType().name() : null
                );
            }
        }

        if ("/api/payroll/run/create".equalsIgnoreCase(requestURI)) {
            PayrollRunViewDto run = extractBody(result, PayrollRunViewDto.class);
            if (run != null) {
                return "Created " + formatPayrollRunReference(
                        run.getPayId(),
                        run.getPeriodStart(),
                        run.getPeriodEnd(),
                        run.getPeriodDescription(),
                        run.getRunType() != null ? run.getRunType().name() : null
                );
            }

            RunCreateDto payload = findArg(args, RunCreateDto.class);
            if (payload != null) {
                return "Created " + formatPayrollRunReference(
                        null,
                        payload.getPeriodStart(),
                        payload.getPeriodEnd(),
                        payload.getPeriodDescription(),
                        payload.getRunType() != null ? payload.getRunType().name() : null
                );
            }
        }

        if ("/api/payroll/item/create".equalsIgnoreCase(requestURI)) {
            return describePayrollItem(extractBody(result, ItemDetailsDto.class), findArg(args, PayrollItemDto.class));
        }

        if ("/api/overtime/create".equalsIgnoreCase(requestURI)) {
            return describeOvertimeRequest(
                    extractBody(result, OvertimeRequest.class),
                    findArg(args, OvertimeRequestDto.class),
                    null
            );
        }

        if ("/api/overtime/update".equalsIgnoreCase(requestURI)) {
            return describeOvertimeResponse(
                    extractBody(result, OvertimeResponseDto.class),
                    findArg(args, OvertimeUpdateDto.class)
            );
        }

        Matcher overtimeCancelMatcher = OVERTIME_CANCEL_ENDPOINT_PATTERN.matcher(requestURI);
        if (overtimeCancelMatcher.find()) {
            Long overtimeId = Long.valueOf(overtimeCancelMatcher.group("overtimeId"));
            return describeOvertimeRequest(
                    overtimeRequestRepository.findById(overtimeId).orElse(null),
                    null,
                    Status.CANCELLED
            );
        }

        Matcher leaveCreateMatcher = LEAVE_CREATE_ENDPOINT_PATTERN.matcher(requestURI);
        if (leaveCreateMatcher.find()) {
            Long employeeId = Long.valueOf(leaveCreateMatcher.group("employeeId"));
            return describeLeaveCreation(
                    employeeId,
                    extractBodyList(result, LeaveRequest.class),
                    findArg(args, LeaveRequestDto.class)
            );
        }

        Matcher leaveRequestMatcher = LEAVE_REQUEST_ENDPOINT_PATTERN.matcher(requestURI);
        if (leaveRequestMatcher.find()) {
            Long requestId = Long.valueOf(leaveRequestMatcher.group("requestId"));
            return describeLeaveRequest(extractBody(result, LeaveRequest.class), requestId, null);
        }

        Matcher leaveCancelMatcher = LEAVE_CANCEL_ENDPOINT_PATTERN.matcher(requestURI);
        if (leaveCancelMatcher.find()) {
            Long requestId = Long.valueOf(leaveCancelMatcher.group("requestId"));
            return describeLeaveRequest(
                    leaveRequestRepository.findById(requestId).orElse(null),
                    requestId,
                    Status.CANCELLED
            );
        }

        Matcher disputeCreateMatcher = DISPUTE_CREATE_ENDPOINT_PATTERN.matcher(requestURI);
        if (disputeCreateMatcher.find()) {
            Long itemId = Long.valueOf(disputeCreateMatcher.group("itemId"));
            return describeDisputeRequest(
                    extractBody(result, DisputeRequest.class),
                    findArg(args, DisputeRequestDto.class),
                    itemId,
                    null
            );
        }

        Matcher disputeMatcher = DISPUTE_ENDPOINT_PATTERN.matcher(requestURI);
        if (disputeMatcher.find()) {
            Long disputeId = Long.valueOf(disputeMatcher.group("disputeId"));
            DisputeResponseDto response = extractBody(result, DisputeResponseDto.class);
            DisputeActionDto payload = findArg(args, DisputeActionDto.class);
            if (response != null) {
                return describeDisputeResponse(response, payload);
            }
            return describeDisputeRequest(
                    disputeRequestRepository.findByIdWithEmployee(disputeId).orElse(null),
                    null,
                    null,
                    payload != null ? payload.getStatus() : null
            );
        }

        Matcher disputeCancelMatcher = DISPUTE_CANCEL_ENDPOINT_PATTERN.matcher(requestURI);
        if (disputeCancelMatcher.find()) {
            Long disputeId = Long.valueOf(disputeCancelMatcher.group("disputeId"));
            return describeDisputeRequest(
                    disputeRequestRepository.findByIdWithEmployee(disputeId).orElse(null),
                    null,
                    null,
                    Status.CANCELLED
            );
        }

        return null;
    }

    private void appendSentence(StringBuilder message, String segment, boolean preservePunctuation) {
        if (segment == null) {
            return;
        }

        String normalized = segment.trim();
        if (normalized.isBlank()) {
            return;
        }

        if (message.length() > 0) {
            message.append(' ');
        }

        message.append(normalized);
        if (!preservePunctuation || !endsWithTerminalPunctuation(normalized)) {
            message.append('.');
        }
    }

    private boolean endsWithTerminalPunctuation(String value) {
        return value.endsWith(".") || value.endsWith("!") || value.endsWith("?");
    }

    private String describeUserAccountTarget(String username, Long employeeId, String firstName, String lastName) {
        String employeeReference = formatEmployeeReference(employeeId, firstName, lastName);
        if (username != null && !username.isBlank()) {
            return "User account @" + username.trim() + " for " + employeeReference;
        }
        return "User account for " + employeeReference;
    }

    private String describePayrollItem(ItemDetailsDto item, PayrollItemDto payload) {
        if (item == null && payload == null) {
            return null;
        }

        Long employeeId = payload != null ? payload.getEmpId() : null;
        String employeeReference = employeeId != null
                ? formatEmployeeReference(employeeId, null, null)
                : "an employee";

        Long itemId = item != null ? item.getId() : null;
        RunResponseDto runDetails = item != null ? item.getRunDetails() : null;
        Long payId = firstNonNull(
                runDetails != null ? runDetails.getId() : null,
                payload != null ? payload.getPayId() : null
        );

        StringBuilder detail = new StringBuilder("Payroll item");
        if (itemId != null) {
            detail.append(" #").append(itemId);
        }
        detail.append(" for ").append(employeeReference);

        if (payId != null || runDetails != null) {
            detail.append(" in ").append(formatPayrollRunReference(
                    payId,
                    runDetails != null ? runDetails.getPeriodStart() : null,
                    runDetails != null ? runDetails.getPeriodEnd() : null,
                    null,
                    runDetails != null && runDetails.getRunType() != null ? runDetails.getRunType().name() : null
            ));
        }

        return detail.toString();
    }

    private String describeOvertimeRequest(OvertimeRequest request, OvertimeRequestDto payload, Status fallbackStatus) {
        if (request == null && payload == null) {
            return null;
        }

        Employee employee = request != null ? request.getEmployee() : null;
        return buildOvertimeDetail(
                firstNonNull(
                        request != null ? request.getEmployeeId() : null,
                        employee != null ? employee.getEmployeeId() : null
                ),
                employee != null ? employee.getFirstName() : null,
                employee != null ? employee.getLastName() : null,
                firstNonNull(
                        request != null ? request.getWorkDate() : null,
                        payload != null ? payload.getWorkDate() : null
                ),
                firstNonNull(
                        request != null ? request.getStartTime() : null,
                        payload != null ? payload.getStartTime() : null
                ),
                firstNonNull(
                        request != null ? request.getEndTime() : null,
                        payload != null ? payload.getEndTime() : null
                )
        );
    }

    private String describeOvertimeResponse(OvertimeResponseDto response, OvertimeUpdateDto payload) {
        if (response == null && payload == null) {
            return null;
        }

        return buildOvertimeDetail(
                response != null ? response.getEmployeeId() : null,
                response != null ? response.getFirstName() : null,
                response != null ? response.getLastName() : null,
                response != null ? response.getWorkDate() : null,
                response != null ? response.getStartTime() : null,
                response != null ? response.getEndTime() : null
        );
    }

    private String describeLeaveCreation(Long employeeId, List<LeaveRequest> requests, LeaveRequestDto payload) {
        if ((requests == null || requests.isEmpty()) && payload == null && employeeId == null) {
            return null;
        }

        LeaveRequest firstRequest = requests != null && !requests.isEmpty() ? requests.get(0) : null;
        Employee employee = firstRequest != null ? firstRequest.getEmployee() : null;
        int requestCount = requests != null ? requests.size() : 0;
        return buildLeaveDetail(
                requestCount > 1 ? requestCount + " leave requests" : "Leave request",
                firstNonNull(
                        employee != null ? employee.getEmployeeId() : null,
                        employeeId
                ),
                employee != null ? employee.getFirstName() : null,
                employee != null ? employee.getLastName() : null,
                firstNonNull(
                        firstRequest != null && firstRequest.getStartDateTime() != null ? firstRequest.getStartDateTime().toLocalDate() : null,
                        payload != null && payload.getStartDateTime() != null ? payload.getStartDateTime().toLocalDate() : null
                ),
                firstNonNull(
                        firstRequest != null && firstRequest.getEndDateTime() != null ? firstRequest.getEndDateTime().toLocalDate() : null,
                        payload != null && payload.getEndDateTime() != null ? payload.getEndDateTime().toLocalDate() : null
                )
        );
    }

    private String describeLeaveRequest(LeaveRequest request, Long requestId, Status fallbackStatus) {
        if (request == null && requestId == null) {
            return null;
        }

        Employee employee = request != null ? request.getEmployee() : null;
        Long resolvedRequestId = firstNonNull(request != null ? request.getRequestId() : null, requestId);
        return buildLeaveDetail(
                "Leave request",
                firstNonNull(
                        employee != null ? employee.getEmployeeId() : null,
                        resolveLeaveEmployeeId(request, resolvedRequestId)
                ),
                employee != null ? employee.getFirstName() : null,
                employee != null ? employee.getLastName() : null,
                request != null && request.getStartDateTime() != null ? request.getStartDateTime().toLocalDate() : null,
                request != null && request.getEndDateTime() != null ? request.getEndDateTime().toLocalDate() : null
        );
    }

    private String describeDisputeRequest(DisputeRequest request, DisputeRequestDto payload, Long fallbackItemId, Status fallbackStatus) {
        Long itemId = firstNonNull(
                request != null && request.getPayrollItem() != null ? request.getPayrollItem().getItemId() : null,
                fallbackItemId
        );
        Employee employee = request != null && request.getPayrollItem() != null
                ? request.getPayrollItem().getEmployee()
                : resolvePayrollItemEmployee(itemId);
        return buildDisputeDetail(
                itemId,
                employee != null ? employee.getEmployeeId() : null,
                employee != null ? employee.getFirstName() : null,
                employee != null ? employee.getLastName() : null,
                firstNonNull(
                        request != null ? request.getSubject() : null,
                        payload != null ? payload.getSubject() : null
                )
        );
    }

    private String describeDisputeResponse(DisputeResponseDto response, DisputeActionDto payload) {
        if (response == null && payload == null) {
            return null;
        }

        return buildDisputeDetail(
                response != null ? response.getItemId() : null,
                response != null ? response.getEmployeeId() : null,
                response != null ? response.getEmployeeName() : null,
                null,
                response != null ? response.getSubject() : null
        );
    }

    private String buildOvertimeDetail(Long employeeId,
                                       String firstName,
                                       String lastName,
                                       LocalDate workDate,
                                       LocalDateTime startTime,
                                       LocalDateTime endTime) {
        StringBuilder detail = new StringBuilder("Overtime request");
        detail.append(" for ").append(formatEmployeeReference(employeeId, firstName, lastName));

        if (workDate != null) {
            detail.append(" on ").append(AUDIT_DATE_FORMAT.format(workDate));
        }

        String timeWindow = formatTimeWindow(startTime, endTime);
        if (timeWindow != null) {
            detail.append(" from ").append(timeWindow);
        }

        return detail.toString();
    }

    private String buildLeaveDetail(String label,
                                    Long employeeId,
                                    String firstName,
                                    String lastName,
                                    LocalDate startDate,
                                    LocalDate endDate) {
        StringBuilder detail = new StringBuilder(label);
        detail.append(" for ").append(formatEmployeeReference(employeeId, firstName, lastName));

        String dateRange = formatDateRange(startDate, endDate);
        if (dateRange != null) {
            detail.append(" for ").append(dateRange);
        }

        return detail.toString();
    }

    private String buildDisputeDetail(Long itemId,
                                      Long employeeId,
                                      String firstName,
                                      String lastName,
                                      String subject) {
        StringBuilder detail = new StringBuilder("Dispute request");
        detail.append(" for ").append(formatEmployeeReference(employeeId, firstName, lastName));
        if (itemId != null) {
            detail.append(", payroll item #").append(itemId);
        }
        if (subject != null && !subject.isBlank()) {
            detail.append(". Subject: ").append(abbreviate(subject, 70));
        }

        return detail.toString();
    }

    private Employee resolvePayrollItemEmployee(Long itemId) {
        if (itemId == null) {
            return null;
        }
        PayrollItem item = payrollItemRepository.findById(itemId).orElse(null);
        return item != null ? item.getEmployee() : null;
    }

    private Long resolveLeaveEmployeeId(LeaveRequest request, Long requestId) {
        if (request != null && request.getEmployee() != null && request.getEmployee().getEmployeeId() != 0L) {
            return request.getEmployee().getEmployeeId();
        }
        if (requestId == null) {
            return null;
        }
        return leaveRequestRepository.findEmployeeIdByRequestId(requestId).orElse(null);
    }

    private String formatEmployeeReference(Long employeeId, String firstName, String lastName) {
        String resolvedFirstName = firstName;
        String resolvedLastName = lastName;

        if ((resolvedFirstName == null || resolvedFirstName.isBlank())
                && (resolvedLastName == null || resolvedLastName.isBlank())
                && employeeId != null) {
            Optional<Employee> employee = employeeRepository.findById(employeeId);
            if (employee.isPresent()) {
                resolvedFirstName = employee.get().getFirstName();
                resolvedLastName = employee.get().getLastName();
            }
        }

        String fullName = ((resolvedFirstName == null ? "" : resolvedFirstName.trim()) + " "
                + (resolvedLastName == null ? "" : resolvedLastName.trim())).trim();

        if (!fullName.isBlank() && employeeId != null) {
            return fullName + " (Employee #" + employeeId + ")";
        }
        if (!fullName.isBlank()) {
            return fullName;
        }
        if (employeeId != null) {
            return "Employee #" + employeeId;
        }
        return "employee";
    }

    private String formatDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null) {
            if (startDate.equals(endDate)) {
                return AUDIT_DATE_FORMAT.format(startDate);
            }
            return AUDIT_DATE_FORMAT.format(startDate) + " to " + AUDIT_DATE_FORMAT.format(endDate);
        }
        if (startDate != null) {
            return AUDIT_DATE_FORMAT.format(startDate);
        }
        if (endDate != null) {
            return AUDIT_DATE_FORMAT.format(endDate);
        }
        return null;
    }

    private String formatTimeWindow(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime == null || endTime == null) {
            return null;
        }
        return AUDIT_TIME_FORMAT.format(startTime) + " to " + AUDIT_TIME_FORMAT.format(endTime);
    }

    private String humanizeToken(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        String[] parts = value.trim().replace('-', '_').split("_+");
        StringBuilder formatted = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (formatted.length() > 0) {
                formatted.append(' ');
            }
            String lower = part.toLowerCase();
            formatted.append(Character.toUpperCase(lower.charAt(0)));
            if (lower.length() > 1) {
                formatted.append(lower.substring(1));
            }
        }
        return formatted.toString();
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        if (maxLength <= 3) {
            return normalized.substring(0, Math.max(0, maxLength));
        }
        return normalized.substring(0, maxLength - 3) + "...";
    }

    private String formatPayrollRunReference(Long payId,
                                             LocalDate periodStart,
                                             LocalDate periodEnd,
                                             String periodDescription,
                                             String runType)
    {
        String normalizedRunType = runType == null ? "" : runType.trim().toUpperCase();
        String runLabel = "ADJUSTMENT".equals(normalizedRunType) ? "adjustment payroll run" : "payroll run";

        StringBuilder detail = new StringBuilder(runLabel);
        if (payId != null) {
            detail.append(" #").append(payId);
        }

        String periodSummary = formatPayrollPeriod(periodStart, periodEnd, periodDescription);
        if (periodSummary != null) {
            detail.append(" for ").append(periodSummary);
        }

        return detail.toString();
    }

    private String formatProcessedPayrollRunDetail(LocalDate periodStart,
                                                   LocalDate periodEnd,
                                                   String runType) {
        String normalizedRunType = runType == null ? "" : runType.trim().toUpperCase();
        String runLabel = "ADJUSTMENT".equals(normalizedRunType) ? "Processed adjustment payroll run"
                : "Processed payroll run";

        String periodSummary = formatPayrollPeriod(periodStart, periodEnd, null);
        if (periodSummary != null) {
            return runLabel + " for " + periodSummary;
        }
        return runLabel;
    }

    private String formatPayrollPeriod(LocalDate periodStart, LocalDate periodEnd, String periodDescription) {
        String cleanedDescription = periodDescription == null ? "" : periodDescription.trim();
        String dateRange = null;

        if (periodStart != null && periodEnd != null) {
            dateRange = AUDIT_DATE_FORMAT.format(periodStart) + " to " + AUDIT_DATE_FORMAT.format(periodEnd);
        } else if (periodStart != null) {
            dateRange = AUDIT_DATE_FORMAT.format(periodStart);
        } else if (periodEnd != null) {
            dateRange = AUDIT_DATE_FORMAT.format(periodEnd);
        }

        if (!cleanedDescription.isBlank() && dateRange != null) {
            return cleanedDescription + " (" + dateRange + ")";
        }
        if (!cleanedDescription.isBlank()) {
            return cleanedDescription;
        }
        if (dateRange != null) {
            return dateRange;
        }
        return null;
    }

    private <T> T extractBody(Object result, Class<T> type) {
        Object body = result instanceof ResponseEntity<?> responseEntity ? responseEntity.getBody() : result;
        return type.isInstance(body) ? type.cast(body) : null;
    }

    private <T> List<T> extractBodyList(Object result, Class<T> type) {
        Object body = result instanceof ResponseEntity<?> responseEntity ? responseEntity.getBody() : result;
        if (!(body instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(type::isInstance)
                .map(type::cast)
                .toList();
    }

    private <T> T findArg(Object[] args, Class<T> type) {
        if (args == null) {
            return null;
        }
        for (Object arg : args) {
            if (type.isInstance(arg)) {
                return type.cast(arg);
            }
        }
        return null;
    }

    @SafeVarargs
    private static <T> T firstNonNull(T... values) {
        if (values == null) {
            return null;
        }
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}
