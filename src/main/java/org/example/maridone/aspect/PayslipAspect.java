package org.example.maridone.aspect;

import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.example.maridone.payroll.payslip.PayslipService;
import org.springframework.core.annotation.Order;

@Aspect
@Order
public class PayslipAspect {
    private final PayslipService payslipService;

    public PayslipAspect(PayslipService payslipService) {
        this.payslipService = payslipService;
    }

    @Pointcut("@annotation(org.example.maridone.annotation.GeneratePayslip) && args(payId,..)")
    public void generate(Long payId) {}

    @AfterReturning("generate(payId)")
    public void generatePayslip(Long payId) {
        payslipService.generatePayslip(payId);
    }
}
