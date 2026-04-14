package org.example.maridone.leave.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import org.example.maridone.core.employee.Employee;
import org.example.maridone.enums.LeaveType;
import org.example.maridone.enums.Status;
import org.hibernate.annotations.Check;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "leave_request")
@Check(constraints = "start_date_time < end_date_time")
public class LeaveRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "request_id", nullable = false)
    private Long requestId;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "emp_id", nullable = false)
    private Employee employee;

    @Column(name = "start_date_time", nullable = false)
    private LocalDateTime startDateTime;

    @Column(name = "end_date_time", nullable = false)
    private LocalDateTime endDateTime;

    @Column(name = "approver", nullable = true)
    private String approver;

    @Column(name = "request_status", nullable = false)
    @Enumerated(EnumType.STRING)
    private Status requestStatus;

    @Column(name = "reason", nullable = false)
    private String reason;

    @Column(name = "leave_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private LeaveType leaveType;

    @Column(name = "approver_reason", nullable = true)
    private String approverReason;

    @Column(name = "approved_at", nullable = true)
    private Instant approvedAt;

    @Column(name = "manager_approver", nullable = true)
    private String managerApprover;

    @Column(name = "manager_approver_reason", nullable = true)
    private String managerApproverReason;

    @Column(name = "manager_approved_at", nullable = true)
    private Instant managerApprovedAt;

    @Column(name = "is_paid",  nullable = false)
    private Boolean isPaid = true;

    @Column(name = "suggested_paid", nullable = false)
    private Boolean suggestedPaid = true;

    @Column(name = "paid_override_reason", nullable = true)
    private String paidOverrideReason;

    public Long getRequestId() {
        return requestId;
    }

    public Employee getEmployee() {
        return employee;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    public LocalDateTime getStartDateTime() {
        return startDateTime;
    }

    public void setStartDateTime(LocalDateTime startDateTime) {
        this.startDateTime = startDateTime;
    }

    public LocalDateTime getEndDateTime() {
        return endDateTime;
    }

    public void setEndDateTime(LocalDateTime endDateTime) {
        this.endDateTime = endDateTime;
    }

    public String getApprover() {
        return approver;
    }

    public void setApprover(String approver) {
        this.approver = approver;
    }

    public Status getRequestStatus() {
        return requestStatus;
    }

    public void setRequestStatus(Status requestStatus) {
        this.requestStatus = requestStatus;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public LeaveType getLeaveType() {
        return leaveType;
    }

    public void setLeaveType(LeaveType leaveType) {
        this.leaveType = leaveType;
    }

    public String getApproverReason() {
        return approverReason;
    }

    public void setApproverReason(String approverReason) {
        this.approverReason = approverReason;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(Instant approvedAt) {
        this.approvedAt = approvedAt;
    }

    public String getManagerApprover() {
        return managerApprover;
    }

    public void setManagerApprover(String managerApprover) {
        this.managerApprover = managerApprover;
    }

    public String getManagerApproverReason() {
        return managerApproverReason;
    }

    public void setManagerApproverReason(String managerApproverReason) {
        this.managerApproverReason = managerApproverReason;
    }

    public Instant getManagerApprovedAt() {
        return managerApprovedAt;
    }

    public void setManagerApprovedAt(Instant managerApprovedAt) {
        this.managerApprovedAt = managerApprovedAt;
    }

    public Boolean getPaid() {
        return isPaid;
    }

    public void setPaid(Boolean paid) {
        isPaid = paid;
    }

    public Boolean getSuggestedPaid() {
        return suggestedPaid;
    }

    public void setSuggestedPaid(Boolean suggestedPaid) {
        this.suggestedPaid = suggestedPaid;
    }

    public String getPaidOverrideReason() {
        return paidOverrideReason;
    }

    public void setPaidOverrideReason(String paidOverrideReason) {
        this.paidOverrideReason = paidOverrideReason;
    }

    public boolean coversDate(LocalDate targetDate) {
        if (targetDate == null || startDateTime == null || endDateTime == null) {
            return false;
        }

        LocalDate startDate = startDateTime.toLocalDate();
        LocalDate endDate = endDateTime.toLocalDate();

        return !targetDate.isBefore(startDate) && !targetDate.isAfter(endDate);
    }



}
