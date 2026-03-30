package org.example.maridone.aspect;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.example.maridone.annotation.NotifyPayRun;
import org.example.maridone.annotation.NotifyRole;
import org.example.maridone.annotation.Notify;
import org.example.maridone.core.employee.Employee;
import org.example.maridone.core.employee.EmployeeRepository;
import org.example.maridone.enums.EmploymentStatus;
import org.example.maridone.exception.notfound.EmployeeNotFoundException;
import org.example.maridone.notification.Notification;
import org.example.maridone.notification.NotificationRepository;
import org.example.maridone.payroll.run.PayrollItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Aspect
@Component
public class NotifyingAspect {
    private static final Logger logger = LoggerFactory.getLogger(NotifyingAspect.class);

    private final NotificationRepository notificationRepository;
    private final EmployeeRepository employeeRepository;
    private final PayrollItemRepository payrollItemRepository;

    private final ExpressionParser spelParser = new SpelExpressionParser();

    public NotifyingAspect(NotificationRepository notificationRepository,
                           PayrollItemRepository payrollItemRepository,
                           EmployeeRepository employeeRepository) {
        this.notificationRepository  = notificationRepository;
        this.payrollItemRepository = payrollItemRepository;
        this.employeeRepository = employeeRepository;
    }

    @Pointcut("@annotation(org.example.maridone.annotation.Notify)")
    public void notifyUser() {}

    @Pointcut("@annotation(org.example.maridone.annotation.NotifyRole)")
    public void notifyRoleBulk() {}

    @Pointcut("@annotation(org.example.maridone.annotation.NotifyPayRun) && args(payId,..)")
    public void notifyPayRun(Long payId) {}

    @AfterReturning("notifyRoleBulk()")
    public void addNotifications(JoinPoint joinPoint) {
        List<Notification> notifications = new ArrayList<>();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        NotifyRole notify = method.getAnnotation(NotifyRole.class);
        List<Long> employeeIds = employeeRepository.findEmployeeIdsByPosition(notify.targetRole(), EmploymentStatus.TERMINATED);

        for (Long employeeId : employeeIds) {
            Employee emp = employeeRepository.getReferenceById(employeeId);
            Notification notification = new Notification();
            notification.setCreatedAt(Instant.now());
            notification.setReadStatus(false);
            notification.setImportance(notify.importance());
            notification.setMessage(notify.message());
            notification.setEmployee(emp);
            notifications.add(notification);
        }
        notificationRepository.saveAll(notifications);
        logger.info("Notified: {}", notify.targetRole());
    }

    @Around("notifyUser()")
    public Object addNotification(ProceedingJoinPoint joinPoint) throws Throwable {
        Object result = joinPoint.proceed();

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Notify notify = method.getAnnotation(Notify.class);

        EvaluationContext context = buildContext(joinPoint, result);

        String message = resolveMessage(notify.message(), context);
        Object targetEmployeeRaw = spelParser
                .parseExpression(notify.targetEmployee())
                .getValue(context);

        Employee targetEmployee = resolveEmployee(targetEmployeeRaw);

        String importance = notify.importance();

        Notification notif = new Notification();
        notif.setReadStatus(false);
        notif.setCreatedAt(Instant.now());
        notif.setEmployee(targetEmployee);
        notif.setImportance(importance);
        notif.setMessage(message);

        notificationRepository.save(notif);

        logger.info("Notified: {} | Importance: {} | Target: {}", message, importance, targetEmployee);
        return result;
    }

    @Around("notifyPayRun(payId)")
    public Object addPayRunNotification(ProceedingJoinPoint proceedingJoinPoint, Long payId) throws Throwable {
        Object result = proceedingJoinPoint.proceed();

        List<Notification> notifications = new ArrayList<>();
        List<Long> empIds = payrollItemRepository.findEmployeeIdsByPayrollRun(payId);

        Instant createdAt = Instant.now();
        String message = "Pay Run Posted";
        for (Long employeeId : empIds) {
            Employee emp = employeeRepository.getReferenceById(employeeId);
            Notification notification = new Notification();
            notification.setCreatedAt(createdAt);
            notification.setEmployee(emp);
            notification.setReadStatus(false);
            notification.setMessage(message);
            notification.setImportance("HIGH");
            notifications.add(notification);
        }

        notificationRepository.saveAll(notifications);

        logger.info("Notifications for Payroll Run sent.");
        return result;
    }







    private Employee resolveEmployee(Object target) {
        if (target instanceof Number id) {
            return employeeRepository.findById(id.longValue()).orElseThrow(() -> new EmployeeNotFoundException(id.longValue()));
        }

        if (target instanceof String s) {
            try {
                return employeeRepository.findById(Long.parseLong(s)).orElseThrow(() -> new EmployeeNotFoundException(Long.parseLong(s)));
            } catch (NumberFormatException ignored) {}
        }

        if (target instanceof Employee employee) {
            return employee;
        }

        throw new IllegalArgumentException(
                "targetEmployee resolved to an unsupported type: " +
                        (target != null ? target.getClass().getName() : "null")
        );
    }

    private EvaluationContext buildContext(JoinPoint joinPoint, Object result) {
        StandardEvaluationContext context = new StandardEvaluationContext();

        Parameter[] parameters = ((MethodSignature) joinPoint.getSignature())
                .getMethod().getParameters();
        Object[] args = joinPoint.getArgs();
        for (int i = 0; i < parameters.length; i++) {
            context.setVariable(parameters[i].getName(), args[i]);
        }

        context.setVariable("result", result);

        return context;
    }

    private String resolveMessage(String template, EvaluationContext context) {
        Pattern pattern = Pattern.compile("#\\{([^}]+)}");
        Matcher matcher = pattern.matcher(template);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String expr = matcher.group(1);
            Object value = spelParser.parseExpression(expr).getValue(context);
            matcher.appendReplacement(result, value != null ? value.toString() : "");
        }

        matcher.appendTail(result);
        return result.toString();
    }
}