package org.example.maridone.aspect;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.example.maridone.annotation.Notify;
import org.example.maridone.core.employee.Employee;
import org.example.maridone.notification.Notification;
import org.example.maridone.notification.NotificationRepository;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Aspect
@Component
public class NotifyingAspect {
    private static final Logger logger = LoggerFactory.getLogger(NotifyingAspect.class);

    private final NotificationRepository notificationRepository;
    private final ExpressionParser spelParser = new SpelExpressionParser();

    public NotifyingAspect(NotificationRepository notificationRepository) {
        this.notificationRepository  = notificationRepository;
    }

    @Pointcut("@annotation(org.example.maridone.annotation.Notify)")
    public void notifyUser() {}

    @Around("notifyUser()")
    public Object addNotification(ProceedingJoinPoint joinPoint) throws Throwable {
        Object result = joinPoint.proceed();

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Notify notify = method.getAnnotation(Notify.class);

        EvaluationContext context = buildContext(joinPoint, result);

        String message = resolveMessage(notify.message(), context);
        Object targetEmployee = spelParser
                .parseExpression(notify.targetEmployee())
                .getValue(context);

        String importance = notify.importance();

        Notification notif = new Notification();
        notif.setReadStatus(false);
        notif.setCreatedAt(Instant.now());
        notif.setEmployee((Employee) targetEmployee);
        notif.setImportance(importance);
        notif.setMessage(message);

        notificationRepository.save(notif);

        logger.info("Notified: {} | Importance: {} | Target: {}", message, importance, targetEmployee);
        return result;
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
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String expr = matcher.group(1);
            Object value = spelParser.parseExpression(expr).getValue(context);
            matcher.appendReplacement(result, value != null ? value.toString() : "");
        }

        matcher.appendTail(result);
        return result.toString();
    }
}