package org.example.maridone.schedule.dto;

import org.example.maridone.enums.EarningsType;

import java.time.DayOfWeek;
import java.time.LocalTime;

public class ShiftResponseDto {
    private Long shiftId;
    private Long employeeId;
    private LocalTime startTime;
    private LocalTime endTime;
    private DayOfWeek dayOfWeek;
    private EarningsType earningsType;

    public Long getShiftId() {
        return shiftId;
    }

    public void setShiftId(Long shiftId) {
        this.shiftId = shiftId;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(Long employeeId) {
        this.employeeId = employeeId;
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

    public DayOfWeek getDayOfWeek() {
        return dayOfWeek;
    }

    public void setDayOfWeek(DayOfWeek dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
    }

    public EarningsType getEarningsType() {
        return earningsType;
    }

    public void setEarningsType(EarningsType earningsType) {
        this.earningsType = earningsType;
    }
}
