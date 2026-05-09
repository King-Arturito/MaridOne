package org.example.maridone.leave.dto;

import org.example.maridone.enums.Status;

import java.time.LocalDateTime;
import java.util.List;

public class LeaveDetails {

    private Long requestId;
    private String firstName;
    private String lastName;
    private String username;
    private LocalDateTime startDateTime;
    private LocalDateTime endDateTime;
    private Status requestStatus;
    private String reason;
    private String approverReason;
    private Boolean isPaid;
    //if it has document
    private List<Long> pathId;

    public LeaveDetails() {}

    public LeaveDetails(Long requestId, String firstName, String lastName, String username,
                        LocalDateTime startDateTime, LocalDateTime endDateTime,
                        Status requestStatus, String reason, String approverReason, Boolean isPaid) {
        this.requestId = requestId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.username = username;
        this.startDateTime = startDateTime;
        this.endDateTime = endDateTime;
        this.requestStatus = requestStatus;
        this.reason = reason;
        this.approverReason = approverReason;
        this.isPaid = isPaid;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Long getRequestId() {
        return requestId;
    }

    public void setRequestId(Long requestId) {
        this.requestId = requestId;
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

    public String getApproverReason() {
        return approverReason;
    }

    public void setApproverReason(String approverReason) {
        this.approverReason = approverReason;
    }

    public Boolean getPaid() {
        return isPaid;
    }

    public void setPaid(Boolean paid) {
        isPaid = paid;
    }

    public List<Long> getPathId() {
        return pathId;
    }

    public void setPathId(List<Long> pathId) {
        this.pathId = pathId;
    }
}
