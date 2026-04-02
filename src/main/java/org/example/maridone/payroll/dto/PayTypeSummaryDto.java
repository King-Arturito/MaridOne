package org.example.maridone.payroll.dto;

import org.example.maridone.enums.AdjustmentKind;

import java.math.BigDecimal;

public class PayTypeSummaryDto {
    private AdjustmentKind adjustment;
    private String payType;
    private BigDecimal total;

    public PayTypeSummaryDto() {

    }

    public PayTypeSummaryDto(AdjustmentKind adjustmentKind, String payType, BigDecimal total) {
        this.adjustment = adjustmentKind;
        this.payType = payType;
        this.total = total;
    }

    public AdjustmentKind getAdjustment() {
        return adjustment;
    }

    public void setAdjustment(AdjustmentKind adjustment) {
        this.adjustment = adjustment;
    }

    public String getPayType() {
        return payType;
    }

    public void setPayType(String payType) {
        this.payType = payType;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }
}
