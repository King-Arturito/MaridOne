package org.example.maridone.payroll.dto;

import org.example.maridone.enums.DeductionType;

import java.math.BigDecimal;

public class DeductionsDto {

    private Long deductionsId;
    private DeductionType deductionType;
    private String description;
    private BigDecimal amount;

    public Long getDeductionsId() {
        return deductionsId;
    }

    public void setDeductionsId(Long deductionsId) {
        this.deductionsId = deductionsId;
    }

    public DeductionType getDeductionType() {
        return deductionType;
    }

    public void setDeductionType(DeductionType deductionType) {
        this.deductionType = deductionType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
}
