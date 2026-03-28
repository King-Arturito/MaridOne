package org.example.maridone.payroll.dto;

import java.math.BigDecimal;

public class EarningsDto {
    private Long earningsId;
    private String description;
    private BigDecimal hours;
    private BigDecimal rate;
    private BigDecimal amount;
    private boolean isOvertime;
    private Long overtimeId;

    public Long getEarningsId() {
        return earningsId;
    }

    public void setEarningsId(Long earningsId) {
        this.earningsId = earningsId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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

    public boolean isOvertime() {
        return isOvertime;
    }

    public void setOvertime(boolean overtime) {
        isOvertime = overtime;
    }

    public Long getOvertimeId() {
        return overtimeId;
    }

    public void setOvertimeId(Long overtimeId) {
        this.overtimeId = overtimeId;
    }
}
