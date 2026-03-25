package org.example.maridone.leave.dto;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import org.example.maridone.enums.LeaveType;
import org.example.maridone.enums.Status;
import org.example.maridone.marker.OnCreate;
import org.example.maridone.marker.OnUpdate;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class LeaveRequestDto {

    @NotNull
    private LocalDateTime startDateTime;

    @NotNull
    private LocalDateTime endDateTime;

    @NotNull(groups = OnUpdate.class)
    private String approver;

    @NotNull
    private String reason;

    @NotNull
    private LeaveType leaveType;

    @Enumerated(EnumType.STRING)
    @NotNull(groups =  OnUpdate.class)
    private Status requestStatus;

    @NotNull(groups = OnUpdate.class)
    private String approverReason;


    @AssertTrue(message = "End date and time must be strictly after the start date and time")
    public boolean isEndDateAfterStartDate() {
        if (startDateTime == null || endDateTime == null) {
            return true;
        }

        return endDateTime.isAfter(startDateTime);
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

    public LeaveType getLeaveType() {
        return leaveType;
    }

    public void setLeaveType(LeaveType leaveType) {
        this.leaveType = leaveType;
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
