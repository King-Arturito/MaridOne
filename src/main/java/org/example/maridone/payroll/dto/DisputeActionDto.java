package org.example.maridone.payroll.dto;

import jakarta.validation.constraints.NotNull;
import org.example.maridone.enums.Status;

public class DisputeActionDto {

    @NotNull
    private Status status;
    private String statusReason;

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getStatusReason() {
        return statusReason;
    }

    public void setStatusReason(String statusReason) {
        this.statusReason = statusReason;
    }
}
