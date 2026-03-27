package org.example.maridone.payroll.dto;


import java.math.BigDecimal;

public class ItemViewDto {
    private Long empId;
    private String firstName;
    private String lastName;
    private Long itemId;
    private BigDecimal grossPay;
    private BigDecimal netPay;

    public ItemViewDto(Long empId, String firstName, String lastName, Long itemId, BigDecimal grossPay, BigDecimal netPay) {
        this.empId = empId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.itemId = itemId;
        this.grossPay = grossPay;
        this.netPay = netPay;
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

    public Long getItemId() {
        return itemId;
    }

    public void setItemId(Long itemId) {
        this.itemId = itemId;
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
