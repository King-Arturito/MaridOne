package org.example.maridone.leave.dto;

import org.example.maridone.enums.Status;

import java.time.LocalDateTime;

public class LeaveResponseDto {
    private Long requestId;
    private Long empId;
    private LocalDateTime startDateTime;
    private LocalDateTime endDateTime;
    private Status requestStatus;
    private String reason;
    private Boolean isPaid;

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

    public Boolean getPaid() {
        return isPaid;
    }

    public void setPaid(Boolean paid) {
        isPaid = paid;
    }
}
