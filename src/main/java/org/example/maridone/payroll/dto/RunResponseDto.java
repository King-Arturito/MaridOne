package org.example.maridone.payroll.dto;

import org.example.maridone.enums.PayrollStatus;
import org.example.maridone.enums.RunType;

import java.time.LocalDate;

public class RunResponseDto {

    private Long id;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private PayrollStatus payrollStatus;
    private RunType runType;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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
}
