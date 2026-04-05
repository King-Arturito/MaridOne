package org.example.maridone.aspect;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.example.maridone.enums.Activity;
import org.example.maridone.core.dto.EmployeeResponseDto;
import org.example.maridone.core.employee.Employee;
import org.example.maridone.core.employee.EmployeeRepository;
import org.example.maridone.core.user.UserAccount;
import org.example.maridone.leave.dto.UpdateBalanceDto;
import org.example.maridone.log.LogService;
import org.example.maridone.log.dto.ActivityRequestDto;
import org.example.maridone.overtime.dto.OvertimeUpdateDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Aspect
@Order(2)
public class LoggingAspect {
    private static final Logger logger = LoggerFactory.getLogger(LoggingAspect.class);
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
        StringJoiner joiner = new StringJoiner(" | ");
        joiner.add("Username: " + username);
        joiner.add("Activity: " + activity);
        joiner.add("Endpoint: " + requestURI);

        String detail = resolveAuditDetail(requestURI, args, result);
        if (detail != null && !detail.isBlank()) {
            joiner.add(detail);
        }

        String message = joiner.toString();
        return message.length() <= 255 ? message : message.substring(0, 252) + "...";
    }

    private String resolveAuditDetail(String requestURI, Object[] args, Object result) {
        if (requestURI == null || requestURI.isBlank()) {
            return null;
        }

        if ("/api/employees/create".equalsIgnoreCase(requestURI)) {
            EmployeeResponseDto response = extractEmployeeResponse(result);
            if (response != null && response.getId() != null) {
                return describeEmployeeTarget(response.getId(), response.getFirstName(), response.getLastName());
            }
        }

        if ("/api/user/create".equalsIgnoreCase(requestURI)) {
            UserAccount userAccount = extractUserAccount(result);
            if (userAccount != null && userAccount.getEmployeeId() != null) {
                return describeEmployeeTarget(
                        userAccount.getEmployeeId(),
                        userAccount.getEmployee() != null ? userAccount.getEmployee().getFirstName() : null,
                        userAccount.getEmployee() != null ? userAccount.getEmployee().getLastName() : null
                );
            }
        }

        String employeeDetail = resolveEmployeeDetailFromEndpoint(requestURI);
        if (employeeDetail != null) {
            UpdateBalanceDto balancePayload = findArg(args, UpdateBalanceDto.class);
            if (balancePayload != null && balancePayload.getLeaveType() != null) {
                return employeeDetail + " | Leave type: " + balancePayload.getLeaveType();
            }
            return employeeDetail;
        }

        Matcher leaveRequestMatcher = LEAVE_REQUEST_ENDPOINT_PATTERN.matcher(requestURI);
        if (leaveRequestMatcher.find()) {
            return "Target leave request: #" + leaveRequestMatcher.group("requestId");
        }

        if ("/api/overtime/update".equalsIgnoreCase(requestURI)) {
            OvertimeUpdateDto overtimeUpdateDto = findArg(args, OvertimeUpdateDto.class);
            if (overtimeUpdateDto != null && overtimeUpdateDto.getOvertimeId() != null) {
                return "Target overtime request: #" + overtimeUpdateDto.getOvertimeId();
            }
        }

        Matcher disputeMatcher = DISPUTE_ENDPOINT_PATTERN.matcher(requestURI);
        if (disputeMatcher.find()) {
            return "Target dispute request: #" + disputeMatcher.group("disputeId");
        }

        return null;
    }

    private String resolveEmployeeDetailFromEndpoint(String requestURI) {
        for (Pattern pattern : new Pattern[] {
                EMPLOYEE_ENDPOINT_PATTERN,
                USER_ACCOUNT_ENDPOINT_PATTERN,
                LEAVE_BALANCE_ENDPOINT_PATTERN
        }) {
            Matcher matcher = pattern.matcher(requestURI);
            if (matcher.find()) {
                Long employeeId = Long.valueOf(matcher.group("employeeId"));
                return describeEmployeeTarget(employeeId, null, null);
            }
        }
        return null;
    }

    private String describeEmployeeTarget(Long employeeId, String firstName, String lastName) {
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
            return "Target employee: " + fullName + " (Employee #" + employeeId + ")";
        }
        return "Target employee: Employee #" + employeeId;
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
