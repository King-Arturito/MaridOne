package org.example.maridone.overtime;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import org.example.maridone.core.employee.Employee;
import org.example.maridone.enums.EarningsType;
import org.example.maridone.enums.Status;
import org.example.maridone.payroll.itemcomponent.EarningsLine;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "overtime_request")
public class OvertimeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "overtime_id", nullable = false)
    private Long overtimeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "emp_id", nullable = false)
    @JsonIgnore
    private Employee employee;

    @Column(name = "emp_id", updatable = false, insertable = false)
    private Long employeeId;

    @OneToOne(mappedBy = "overtimeRequest", cascade = CascadeType.ALL)
    private EarningsLine earningsLine;

    @Column(name = "request_status", nullable = false)
    @Enumerated(EnumType.STRING)
    private Status requestStatus;

    @Column(name = "request_at", nullable = false)
    private Instant requestAt;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;
    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "overtime_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private EarningsType overtimeType;

    @Column(name = "reason", nullable = false)
    private String reason;

    @Column(name = "approver", nullable = true)
    private String approver;

    @Column(name = "approved_at", nullable = true)
    private Instant approvedAt;

    @Column(name = "approve_reason", nullable = true)
    private String approveReason;

    public Long getOvertimeId() {
        return overtimeId;
    }

    public Employee getEmployee() {
        return employee;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public Status getRequestStatus() {
        return requestStatus;
    }

    public void setRequestStatus(Status requestStatus) {
        this.requestStatus = requestStatus;
    }

    public LocalDate getWorkDate() {
        return workDate;
    }

    public void setWorkDate(LocalDate workDate) {
        this.workDate = workDate;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }

    public EarningsType getOvertimeType() {
        return overtimeType;
    }

    public void setOvertimeType(EarningsType overtimeType) {
        this.overtimeType = overtimeType;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getApprover() {
        return approver;
    }

    public void setApprover(String approver) {
        this.approver = approver;
    }

    public Instant getRequestAt() {
        return requestAt;
    }

    public void setRequestAt(Instant requestAt) {
        this.requestAt = requestAt;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(Instant approvedAt) {
        this.approvedAt = approvedAt;
    }

    public String getApproveReason() {
        return approveReason;
    }

    public void setApproveReason(String approveReason) {
        this.approveReason = approveReason;
    }

    public EarningsLine getEarningsLine() {
        return earningsLine;
    }

    public void setEarningsLine(EarningsLine earningsLine) {
        this.earningsLine = earningsLine;
    }
}
