package org.example.maridone.payroll.dto;

import org.example.maridone.enums.PayrollStatus;
import org.example.maridone.enums.RunType;

import java.time.LocalDate;

public class PayrollRunViewDto {
    private Long payId;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private LocalDate paymentDate;
    private String periodDescription;
    private PayrollStatus payrollStatus;
    private RunType runType;
    private Long itemCount;

    public PayrollRunViewDto() {
    }

    public PayrollRunViewDto(
            Long payId,
            LocalDate periodStart,
            LocalDate periodEnd,
            LocalDate paymentDate,
            String periodDescription,
            PayrollStatus payrollStatus,
            RunType runType,
            Long itemCount
    ) {
        this.payId = payId;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.paymentDate = paymentDate;
        this.periodDescription = periodDescription;
        this.payrollStatus = payrollStatus;
        this.runType = runType;
        this.itemCount = itemCount;
    }

    public Long getPayId() {
        return payId;
    }

    public void setPayId(Long payId) {
        this.payId = payId;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public void setPeriodStart(LocalDate periodStart) {
        this.periodStart = periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public void setPeriodEnd(LocalDate periodEnd) {
        this.periodEnd = periodEnd;
    }

    public LocalDate getPaymentDate() {
        return paymentDate;
    }

    public void setPaymentDate(LocalDate paymentDate) {
        this.paymentDate = paymentDate;
    }

    public String getPeriodDescription() {
        return periodDescription;
    }

    public void setPeriodDescription(String periodDescription) {
        this.periodDescription = periodDescription;
    }

    public PayrollStatus getPayrollStatus() {
        return payrollStatus;
    }

    public void setPayrollStatus(PayrollStatus payrollStatus) {
        this.payrollStatus = payrollStatus;
    }

    public RunType getRunType() {
        return runType;
    }

    public void setRunType(RunType runType) {
        this.runType = runType;
    }

    public Long getItemCount() {
        return itemCount;
    }

    public void setItemCount(Long itemCount) {
        this.itemCount = itemCount;
    }
}
