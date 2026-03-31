package org.example.maridone.aspect;

import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.example.maridone.payroll.payslip.PayslipService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect
@Order
@Component
@Profile("dev")
public class PayslipAspect {
    private final PayslipService payslipService;
    private static final Logger logger =  LoggerFactory.getLogger(PayslipAspect.class);

    public PayslipAspect(PayslipService payslipService) {
        this.payslipService = payslipService;
    }

    @Pointcut("@annotation(org.example.maridone.annotation.GeneratePayslip) && args(payId,..)")
    public void generate(Long payId) {}

    @AfterReturning(value = "generate(payId)", argNames = "payId")
    public void generatePayslip(Long payId) {

        payslipService.generatePayslip(payId);
        logger.info("Payslip has been generated");
    }
}
