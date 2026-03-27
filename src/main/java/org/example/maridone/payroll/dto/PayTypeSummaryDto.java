package org.example.maridone.payroll.dto;

import org.example.maridone.enums.DeductionType;

import java.math.BigDecimal;

public class PayTypeSummaryDto {
    private String payType;
    private BigDecimal total;

    public PayTypeSummaryDto() {

    }

    public PayTypeSummaryDto(String payType, BigDecimal total) {
        this.payType = payType;
        this.total = total;
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
