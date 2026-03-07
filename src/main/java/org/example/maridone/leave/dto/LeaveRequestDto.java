package org.example.maridone.leave.dto;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.validation.constraints.NotNull;
import org.example.maridone.enums.Status;
import org.example.maridone.marker.OnUpdate;

import java.time.LocalDate;

public class LeaveRequestDto {

    @NotNull
    private LocalDate leaveDate;
    @NotNull
    private String approver;
    @NotNull
    private String reason;
    @Enumerated(EnumType.STRING)
    @NotNull(groups =  OnUpdate.class)
    private Status requestStatus;
    @NotNull(groups = OnUpdate.class)
    private String approverReason;

    public LocalDate getLeaveDate() {
        return leaveDate;
    }

    public void setLeaveDate(LocalDate leaveDate) {
        this.leaveDate = leaveDate;
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

    public String getApproverReason() {
        return approverReason;
    }

    public void setApproverReason(String approverReason) {
        this.approverReason = approverReason;
    }
}
