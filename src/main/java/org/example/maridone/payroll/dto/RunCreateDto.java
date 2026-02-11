package org.example.maridone.payroll.dto;

import jakarta.validation.constraints.NotNull;
import org.example.maridone.enums.PayrollStatus;
import org.example.maridone.enums.RunType;

import java.time.LocalDate;

public class RunCreateDto {
    @NotNull
    private LocalDate periodStart;
    @NotNull
    private LocalDate periodEnd;
    @NotNull
    private String periodDescription;
    @NotNull
    private PayrollStatus payrollStatus;
    @NotNull
    private RunType runType;

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
}
