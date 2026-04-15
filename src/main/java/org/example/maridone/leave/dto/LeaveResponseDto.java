package org.example.maridone.leave.dto;

import org.example.maridone.enums.LeaveType;
import org.example.maridone.enums.Status;

import java.time.Instant;
import java.time.LocalDateTime;

public class LeaveResponseDto {
    private Long requestId;
    private Long empId;
    private String firstName;
    private String lastName;
    private LocalDateTime startDateTime;
    private LocalDateTime endDateTime;
    private Status requestStatus;
    private String reason;
    private LeaveType leaveType;
    private String approver;
    private String approverReason;
    private Instant approvedAt;
    private Boolean isPaid;
    private Boolean suggestedPaid;
    private String paidOverrideReason;
    private String managerApprover;
    private String managerApproverReason;
    private Instant managerApprovedAt;

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

    public Long getRequestId() {
        return requestId;
    }

    public void setRequestId(Long requestId) {
        this.requestId = requestId;
    }

    public Long getEmpId() {
        return empId;
    }

    public void setEmpId(Long empId) {
        this.empId = empId;
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

    public String getApprover() {
        return approver;
    }

    public void setApprover(String approver) {
        this.approver = approver;
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
}
