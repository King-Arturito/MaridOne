package org.example.maridone.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Aspect
@Order(1)
public class TrackingAspect {
    private static final Logger logger = LoggerFactory.getLogger(TrackingAspect.class);

    @Pointcut("@annotation(org.example.maridone.annotation.ExecutionTime)")
    public void timer() {}

    @Around("timer()")
    public Object trackExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();

        Object proceed = joinPoint.proceed();

        long timeTaken = System.currentTimeMillis() - start;
        logger.info("{} took {} ms", joinPoint.getSignature().getName(), timeTaken);
        return proceed;
    }


}
