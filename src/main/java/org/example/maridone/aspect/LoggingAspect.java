package org.example.maridone.aspect;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.example.maridone.enums.Activity;
import org.example.maridone.log.LogService;
import org.example.maridone.log.dto.ActivityRequestDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
@Aspect
@Order(2)
public class LoggingAspect {
    private static final Logger logger = LoggerFactory.getLogger(LoggingAspect.class);

    private final LogService logService;

    public LoggingAspect(LogService logService) {
        this.logService = logService;
    }

    @Pointcut("@annotation(org.example.maridone.annotation.AuditLog)")
    public void auditLog() {}

    @Pointcut("@annotation(org.example.maridone.annotation.AutoScheduled)")
    public void auditScheduling() {}

    @AfterReturning("auditLog()")
    public void logToDatabase() {
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
        String message = "Username: " + username + " | Activity: " + activity + " | Endpoint: " + requestURI;
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
}
