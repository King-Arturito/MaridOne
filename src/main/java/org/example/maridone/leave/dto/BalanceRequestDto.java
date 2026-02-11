package org.example.maridone.leave.dto;

import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.example.maridone.enums.LeaveType;

import java.math.BigDecimal;

public class BalanceRequestDto {
    @NotNull
    @PositiveOrZero
    private Long empId;

    @NotNull
    @PositiveOrZero
    private BigDecimal balanceHours;

    @Enumerated(EnumType.STRING)
    private LeaveType leaveType;

    public Long getEmpId() {
        return empId;
    }

    public void setEmpId(Long empId) {
        this.empId = empId;
    }

    public BigDecimal getBalanceHours() {
        return balanceHours;
    }

    public void setBalanceHours(BigDecimal balanceHours) {
        this.balanceHours = balanceHours;
    }

    public LeaveType getLeaveType() {
        return leaveType;
    }

    public void setLeaveType(LeaveType leaveType) {
        this.leaveType = leaveType;
    }
}
