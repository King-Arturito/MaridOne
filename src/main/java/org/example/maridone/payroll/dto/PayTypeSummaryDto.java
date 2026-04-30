package org.example.maridone.payroll.dto;

import org.example.maridone.enums.AdjustmentKind;
import org.example.maridone.enums.PaySummaryKind;

import java.math.BigDecimal;

public class PayTypeSummaryDto {
    private AdjustmentKind adjustment;
    private PaySummaryKind summaryKind;
    private String payType;
    private BigDecimal total;

    public PayTypeSummaryDto() {

    }

    public PayTypeSummaryDto(PaySummaryKind summaryKind, String payType, BigDecimal total) {
        this.summaryKind = summaryKind;
        this.adjustment = toAdjustmentKind(summaryKind);
        this.payType = payType;
        this.total = total;
    }

    public AdjustmentKind getAdjustment() {
        return adjustment;
    }

    public void setAdjustment(AdjustmentKind adjustment) {
        this.adjustment = adjustment;
        this.summaryKind = adjustment == null ? this.summaryKind : PaySummaryKind.valueOf(adjustment.name());
    }

    public PaySummaryKind getSummaryKind() {
        return summaryKind;
    }

    public void setSummaryKind(PaySummaryKind summaryKind) {
        this.summaryKind = summaryKind;
        this.adjustment = toAdjustmentKind(summaryKind);
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

    private AdjustmentKind toAdjustmentKind(PaySummaryKind summaryKind) {
        if (summaryKind == null || summaryKind == PaySummaryKind.EMPLOYER_CONTRIBUTION) {
            return null;
        }
        return AdjustmentKind.valueOf(summaryKind.name());
    }
}
