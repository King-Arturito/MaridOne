package org.example.maridone.payroll.run;

import jakarta.persistence.*;
import org.example.maridone.enums.PayrollStatus;
import org.example.maridone.enums.RunType;
import org.example.maridone.payroll.PayrollItem;

import java.util.List;

import java.time.LocalDate;

@Entity
@Table(name = "payroll_run")
public class PayrollRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pay_id", nullable = false)
    private Long payId;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "period_description", nullable = true)
    private String periodDescription;

    @Column(name = "payroll_status", nullable = false)
    @Enumerated(EnumType.STRING)
    private PayrollStatus payrollStatus;

    @Column(name = "run_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private RunType runType;

    @OneToMany(mappedBy = "payrollRun", fetch = FetchType.LAZY)
    private List<PayrollItem> items;

    public Long getPayId() {
        return payId;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public void setPeriodStart(LocalDate periodStart) {
        this.periodStart = periodStart;
    }

    public PayrollStatus getPayrollStatus() {
        return payrollStatus;
    }

    public void setPayrollStatus(PayrollStatus payrollStatus) {
        this.payrollStatus = payrollStatus;
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

    public RunType getRunType() {
        return runType;
    }

    public void setRunType(RunType runType) {
        this.runType = runType;
    }

    public List<PayrollItem> getItems() {
        return items;
    }

    public void setItems(List<PayrollItem> items) {
        this.items = items;
    }
}
