package org.example.maridone.payroll.dto;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import org.example.maridone.marker.OnCreate;
import org.example.maridone.marker.OnUpdate;

import java.math.BigDecimal;

public class PayrollItemDto {
    @NotNull(groups = OnCreate.class)
    @Null(groups = OnUpdate.class)
    private Long empId;
    
    @NotNull(groups = OnCreate.class)
    private Long payId;
    
    @Null(groups = OnCreate.class)
    @NotNull(groups = OnUpdate.class)
    private BigDecimal grossPay;
    @Null(groups = OnCreate.class)
    @NotNull(groups = OnUpdate.class)
    private BigDecimal netPay;

    public Long getEmpId() {
        return empId;
    }

    public void setEmpId(Long empId) {
        this.empId = empId;
    }

    public Long getPayId() {
        return payId;
    }

    public void setPayId(Long payId) {
        this.payId = payId;
    }

    public BigDecimal getGrossPay() {
        return grossPay;
    }

    public void setGrossPay(BigDecimal grossPay) {
        this.grossPay = grossPay;
    }

    public BigDecimal getNetPay() {
        return netPay;
    }

    public void setNetPay(BigDecimal netPay) {
        this.netPay = netPay;
    }
}
