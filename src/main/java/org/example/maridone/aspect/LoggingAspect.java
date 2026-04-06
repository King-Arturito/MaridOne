package org.example.maridone.aspect;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.example.maridone.enums.Activity;
import org.example.maridone.core.dto.CreateUserAccountDto;
import org.example.maridone.core.dto.EmployeeResponseDto;
import org.example.maridone.core.employee.Employee;
import org.example.maridone.core.employee.EmployeeRepository;
import org.example.maridone.core.user.UserAccount;
import org.example.maridone.leave.dto.UpdateBalanceDto;
import org.example.maridone.log.LogService;
import org.example.maridone.log.dto.ActivityRequestDto;
import org.example.maridone.overtime.dto.OvertimeUpdateDto;
import org.example.maridone.payroll.dto.PayrollRunViewDto;
import org.example.maridone.payroll.dto.RunCreateDto;
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
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Aspect
@Order(2)
public class LoggingAspect {
    private static final Logger logger = LoggerFactory.getLogger(LoggingAspect.class);
    private static final DateTimeFormatter AUDIT_DATE_FORMAT = DateTimeFormatter.ofPattern("MMMM d, uuuu");
    private static final Pattern EMPLOYEE_ENDPOINT_PATTERN = Pattern.compile("^/api/employees(?:/status)?/(?<employeeId>\\d+)\\b");
    private static final Pattern USER_ACCOUNT_ENDPOINT_PATTERN = Pattern.compile("^/api/user/(?<employeeId>\\d+)\\b");
    private static final Pattern LEAVE_BALANCE_ENDPOINT_PATTERN = Pattern.compile("^/api/leave/balance(?:/update)?/(?<employeeId>\\d+)\\b");
    private static final Pattern LEAVE_REQUEST_ENDPOINT_PATTERN = Pattern.compile("^/api/leave/update/(?<requestId>\\d+)\\b");
    private static final Pattern DISPUTE_ENDPOINT_PATTERN = Pattern.compile("^/api/payroll/item/dispute/update/(?<disputeId>\\d+)\\b");

    private final LogService logService;
    private final EmployeeRepository employeeRepository;

    public LoggingAspect(LogService logService, EmployeeRepository employeeRepository) {
        this.logService = logService;
        this.employeeRepository = employeeRepository;
    }

    @Pointcut("@annotation(org.example.maridone.annotation.AuditLog)")
    public void auditLog() {}

    @Pointcut("@annotation(org.example.maridone.annotation.AutoScheduled)")
    public void auditScheduling() {}

    @AfterReturning(pointcut = "auditLog()", returning = "result")
    public void logToDatabase(JoinPoint joinPoint, Object result) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        String httpMethod = "unknownHttpMethod";
        String requestURI = "unknownURI";
        String username = "unknownUser";
        Activity activity;

        if (attributes != null) {
            httpMethod = attributes.getRequest().getMethod();
            requestURI = attributes.getRequest().getRequestURI();
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !auth.getPrincipal().equals("anonymousUser")) {
            username = auth.getName();
        }

        switch (httpMethod) {
            case "POST": {
                activity = Activity.CREATE;
                break;
            }
            case "GET": {
                activity = Activity.READ;
                break;
            }
            case "PUT":
            case "PATCH": {
                activity = Activity.MODIFY;
                break;
            }
            case "DELETE": {
                activity = Activity.DELETE;
                break;
            }
            default: {
                activity = Activity.UNKNOWN;
                break;
            }
        }
        String message = buildAuditMessage(username, activity, requestURI, joinPoint.getArgs(), result);
        logger.info("Inserting to database: {}", message);
        logService.activity(new ActivityRequestDto(username, activity, message));
    }

    @AfterReturning("auditScheduling()")
    public void logSystemRuns(JoinPoint joinPoint) {
        String message = "Scheduled by System -> "
                + joinPoint.getSignature().getDeclaringType().getSimpleName()
                + " -> " + joinPoint.getSignature().getName();
        logger.info("Scheduled System run inserted to database: {}", message);
        logService.activity(new ActivityRequestDto("System SUCCESS",  Activity.SYSTEM, message));
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
                + " -> " + joinPoint.getSignature().getName() + "ERROR: " + rawErrorMessage;
        logger.error("Scheduled System run failed: {}", message);
        logService.activity(new ActivityRequestDto("System FAILURE",  Activity.SYSTEM, message));
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
            EmployeeResponseDto response = extractEmployeeResponse(result);
            if (response != null && response.getId() != null) {
                return "Employee record for " + formatEmployeeReference(
                        response.getId(),
                        response.getFirstName(),
                        response.getLastName()
                );
            }
        }

        if ("/api/user/create".equalsIgnoreCase(requestURI)) {
            UserAccount userAccount = extractUserAccount(result);
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
            UserAccount userAccount = extractUserAccount(result);
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
                return detail + ". Leave type: " + balancePayload.getLeaveType();
            }
            return detail;
        }

        if ("/api/payroll/run/process".equalsIgnoreCase(requestURI)) {
            PayrollRunViewDto run = extractPayrollRun(result);
            if (run != null) {
                return "Processed " + formatPayrollRunReference(
                        run.getPayId(),
                        run.getPeriodStart(),
                        run.getPeriodEnd(),
                        run.getPeriodDescription(),
                        run.getRunType() != null ? run.getRunType().name() : null
                );
            }

            RunCreateDto payload = findArg(args, RunCreateDto.class);
            if (payload != null) {
                return "Processed " + formatPayrollRunReference(
                        null,
                        payload.getPeriodStart(),
                        payload.getPeriodEnd(),
                        payload.getPeriodDescription(),
                        payload.getRunType() != null ? payload.getRunType().name() : null
                );
            }
        }

        if ("/api/payroll/run/create".equalsIgnoreCase(requestURI)) {
            PayrollRunViewDto run = extractPayrollRun(result);
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

        Matcher leaveRequestMatcher = LEAVE_REQUEST_ENDPOINT_PATTERN.matcher(requestURI);
        if (leaveRequestMatcher.find()) {
            return "Leave request: #" + leaveRequestMatcher.group("requestId");
        }

        if ("/api/overtime/update".equalsIgnoreCase(requestURI)) {
            OvertimeUpdateDto overtimeUpdateDto = findArg(args, OvertimeUpdateDto.class);
            if (overtimeUpdateDto != null && overtimeUpdateDto.getOvertimeId() != null) {
                return "Overtime request: #" + overtimeUpdateDto.getOvertimeId();
            }
        }

        Matcher disputeMatcher = DISPUTE_ENDPOINT_PATTERN.matcher(requestURI);
        if (disputeMatcher.find()) {
            return "Dispute request: #" + disputeMatcher.group("disputeId");
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

    private String formatEmployeeReference(Long employeeId, String firstName, String lastName) {
        String resolvedFirstName = firstName;
        String resolvedLastName = lastName;

        if ((resolvedFirstName == null || resolvedFirstName.isBlank())
                && (resolvedLastName == null || resolvedLastName.isBlank())) {
            Optional<Employee> employee = employeeRepository.findById(employeeId);
            if (employee.isPresent()) {
                resolvedFirstName = employee.get().getFirstName();
                resolvedLastName = employee.get().getLastName();
            }
        }

        String fullName = ((resolvedFirstName == null ? "" : resolvedFirstName.trim()) + " "
                + (resolvedLastName == null ? "" : resolvedLastName.trim())).trim();

        if (!fullName.isBlank()) {
            return fullName + " (Employee #" + employeeId + ")";
        }
        return "Employee #" + employeeId;
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

    private EmployeeResponseDto extractEmployeeResponse(Object result) {
        if (result instanceof EmployeeResponseDto employeeResponseDto) {
            return employeeResponseDto;
        }
        if (result instanceof ResponseEntity<?> responseEntity && responseEntity.getBody() instanceof EmployeeResponseDto employeeResponseDto) {
            return employeeResponseDto;
        }
        return null;
    }

    private UserAccount extractUserAccount(Object result) {
        if (result instanceof UserAccount userAccount) {
            return userAccount;
        }
        if (result instanceof ResponseEntity<?> responseEntity && responseEntity.getBody() instanceof UserAccount userAccount) {
            return userAccount;
        }
        return null;
    }

    private PayrollRunViewDto extractPayrollRun(Object result) {
        if (result instanceof PayrollRunViewDto payrollRun) {
            return payrollRun;
        }
        if (result instanceof ResponseEntity<?> responseEntity && responseEntity.getBody() instanceof PayrollRunViewDto payrollRun) {
            return payrollRun;
        }
        return null;
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
}
