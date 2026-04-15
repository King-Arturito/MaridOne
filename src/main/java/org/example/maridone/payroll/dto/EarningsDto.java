package org.example.maridone.payroll.dto;

import org.example.maridone.enums.EarningsType;

import java.math.BigDecimal;
import java.time.Instant;

public class EarningsDto {
    private Long earningsId;
    private String description;
    private EarningsType earningsType;
    private BigDecimal hours;
    private BigDecimal rate;
    private BigDecimal amount;
    private BigDecimal taxableAmount;
    private BigDecimal nonTaxableAmount;
    private BigDecimal originalAmount;
    private String adjustedBy;
    private Instant adjustedAt;
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

    public EarningsType getEarningsType() {
        return earningsType;
    }

    public void setEarningsType(EarningsType earningsType) {
        this.earningsType = earningsType;
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

    public BigDecimal getTaxableAmount() {
        return taxableAmount;
    }

    public void setTaxableAmount(BigDecimal taxableAmount) {
        this.taxableAmount = taxableAmount;
    }

    public BigDecimal getNonTaxableAmount() {
        return nonTaxableAmount;
    }

    public void setNonTaxableAmount(BigDecimal nonTaxableAmount) {
        this.nonTaxableAmount = nonTaxableAmount;
    }

    public BigDecimal getOriginalAmount() {
        return originalAmount;
    }

    public void setOriginalAmount(BigDecimal originalAmount) {
        this.originalAmount = originalAmount;
    }

    public String getAdjustedBy() {
        return adjustedBy;
    }

    public void setAdjustedBy(String adjustedBy) {
        this.adjustedBy = adjustedBy;
    }

    public Instant getAdjustedAt() {
        return adjustedAt;
    }

    public void setAdjustedAt(Instant adjustedAt) {
        this.adjustedAt = adjustedAt;
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
