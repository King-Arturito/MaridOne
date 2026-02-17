package org.example.maridone.overtime.dto;

import jakarta.persistence.*;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.example.maridone.enums.EarningsType;
import org.example.maridone.enums.Status;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class OvertimeRequestDto {
    private final Status requestStatus = Status.PENDING;
    private final Instant requestAt =  Instant.now();
    @FutureOrPresent
    private LocalDate workDate;
    @NotNull
    private LocalDateTime startTime;
    @NotNull
    private LocalDateTime endTime;
    @NotNull
    private EarningsType overtimeType;
    @NotNull
    @Size(min = 5, max = 255)
    private String reason;

    public Status getRequestStatus() {
        return requestStatus;
    }

    public Instant getRequestAt() {
        return requestAt;
    }

    public LocalDate getWorkDate() {
        return workDate;
    }

    public void setWorkDate(LocalDate workDate) {
        this.workDate = workDate;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
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
}
