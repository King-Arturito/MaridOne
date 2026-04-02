package org.example.maridone.payroll.dto;

import org.example.maridone.enums.Status;

import java.time.Instant;

public class DisputeDetails {
    private Long id;
    private String subject;
    private String reason;
    private Status status;
    private Instant createdAt;
    private Instant updatedAt;
    private String statusReason;
    private Long itemId;
    private Long employeeId;
    private String employeeName;
    private Long pathId;

    public DisputeDetails() {}

    public DisputeDetails(Long id, String subject, String reason,
                          Status status, Instant createdAt, Instant updatedAt,
                          String statusReason, Long itemId, Long employeeId,
                          String employeeName) {
        this.id = id;
        this.subject = subject;
        this.reason = reason;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.statusReason = statusReason;
        this.itemId = itemId;
        this.employeeId = employeeId;
        this.employeeName = employeeName;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getStatusReason() {
        return statusReason;
    }

    public void setStatusReason(String statusReason) {
        this.statusReason = statusReason;
    }

    public Long getItemId() {
        return itemId;
    }

    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(Long employeeId) {
        this.employeeId = employeeId;
    }

    public String getEmployeeName() {
        return employeeName;
    }

    public void setEmployeeName(String employeeName) {
        this.employeeName = employeeName;
    }

    public Long getPathId() {
        return pathId;
    }

    public void setPathId(Long pathId) {
        this.pathId = pathId;
    }
}
