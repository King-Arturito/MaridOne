package org.example.maridone.leave.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.example.maridone.enums.LeaveType;

import java.math.BigDecimal;

public class UpdateBalanceDto {

    @NotNull
    @Positive(message = "enter positive balance hours")
    private BigDecimal balanceHours;
    @NotNull
    private LeaveType leaveType;
    @NotBlank(message = "type must be add/subtract")
    private String type;

    public UpdateBalanceDto(BigDecimal balanceHours, LeaveType leaveType, String type) {
        this.balanceHours = balanceHours;
        this.leaveType = leaveType;
        this.type = type;
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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
