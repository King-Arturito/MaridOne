package org.example.maridone.payroll.itemcomponent;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import org.example.maridone.overtime.OvertimeRequest;
import org.example.maridone.payroll.PayrollItem;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "earnings_line")
public class EarningsLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "earnings_id", nullable = false)
    private Long earningsId;

    @ManyToOne
    @JoinColumn(name = "item_id", nullable = false)
    @JsonIgnore
    private PayrollItem payrollItem;

    @Column(name = "hours", nullable = true)
    private BigDecimal hours;

    // employee_rate * this.rate
    @Column(name = "rate", nullable = true)
    private BigDecimal rate;

    // employee_rate * this.rate * hours
    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "earnings_date", nullable = false)
    private LocalDate earningsDate;

    @Column(name = "is_overtime", nullable = false)
    private Boolean isOvertime;

    @OneToOne
    @JoinColumn(name = "earningsLine", nullable = true)
    @JsonIgnore
    private OvertimeRequest overtimeRequest;

    public long getEarningsId() {
        return earningsId;
    }

    public PayrollItem getPayrollItem() {
        return payrollItem;
    }

    public void setPayrollItem(PayrollItem payrollItem) {
        this.payrollItem = payrollItem;
    }

    public BigDecimal getHours() {
        return hours;
    }

    public void setHours(BigDecimal hours) {
        this.hours = hours;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public void setRate(BigDecimal rate) {
        this.rate = rate;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public LocalDate getEarningsDate() {
        return earningsDate;
    }

    public void setEarningsDate(LocalDate earningsDate) {
        this.earningsDate = earningsDate;
    }

    public Boolean getOvertime() {
        return isOvertime;
    }

    public void setOvertime(Boolean overtime) {
        isOvertime = overtime;
    }

    public OvertimeRequest getOvertimeRequest() {
        return overtimeRequest;
    }

    public void setOvertimeRequest(OvertimeRequest overtimeRequest) {
        this.overtimeRequest = overtimeRequest;
    }
}
