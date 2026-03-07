package org.example.maridone.leave.balance;


import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import org.example.maridone.core.employee.Employee;
import org.example.maridone.enums.LeaveType;

import java.math.BigDecimal;

@Entity
@Table(name = "leave_balance")
public class LeaveBalance {

    @Id
    @Column(name = "leave_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long leaveId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "emp_id", nullable = false)
    @JsonIgnore
    private Employee employee;

    @Column(name ="balanceHours", nullable = false)
    private BigDecimal balanceHours;

    @Column(name = "leave_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private LeaveType leaveType;

    public Long getLeaveId() {
        return leaveId;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    public Employee getEmployee() {
        return employee;
    }

    public BigDecimal getBalanceHours() {
        return balanceHours;
    }

    public void setBalanceHours(BigDecimal balanceRemaining) {
        this.balanceHours = balanceRemaining;
    }

    public LeaveType getLeaveType() {
        return leaveType;
    }

    public void setLeaveType(LeaveType leaveType) {
        this.leaveType = leaveType;
    }
}
