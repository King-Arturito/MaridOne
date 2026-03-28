package org.example.maridone.payroll.dto;

import java.math.BigDecimal;

public class CorrectionSourceItemDto {
    private Long itemId;
    private Long empId;
    private String firstName;
    private String lastName;
    private RunResponseDto runDetails;
    private BigDecimal grossPay;
    private BigDecimal netPay;

    public Long getItemId() {
        return itemId;
    }

    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }

    public Long getEmpId() {
        return empId;
    }

    public void setEmpId(Long empId) {
        this.empId = empId;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public RunResponseDto getRunDetails() {
        return runDetails;
    }

    public void setRunDetails(RunResponseDto runDetails) {
        this.runDetails = runDetails;
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
