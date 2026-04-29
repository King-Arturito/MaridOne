package org.example.maridone.schedule.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public class DailyShiftOverrideRequestDto {

    @NotNull
    private Long empId;

    @NotNull
    private LocalDate workDate;

    private LocalTime startTime;

    private LocalTime endTime;

    private Boolean dayOff = false;

    private List<DailyShiftSegmentDto> segments;

    public Long getEmpId() {
        return empId;
    }

    public void setEmpId(Long empId) {
        this.empId = empId;
    }

    public LocalDate getWorkDate() {
        return workDate;
    }

    public void setWorkDate(LocalDate workDate) {
        this.workDate = workDate;
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

    public boolean isDayOff() {
        return Boolean.TRUE.equals(dayOff);
    }

    public void setDayOff(Boolean dayOff) {
        this.dayOff = dayOff;
    }

    public List<DailyShiftSegmentDto> getSegments() {
        return segments;
    }

    public void setSegments(List<DailyShiftSegmentDto> segments) {
        this.segments = segments;
    }
}
