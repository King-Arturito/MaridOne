package org.example.maridone.schedule.dto;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import org.example.maridone.enums.EarningsType;
import org.example.maridone.marker.OnCreate;
import org.example.maridone.marker.OnUpdate;

import java.time.DayOfWeek;
import java.time.LocalTime;

public class ShiftRequestDto {
    @NotNull(groups = OnCreate.class)
    @Null(groups = OnUpdate.class)
    private Long empId;
    @NotNull(groups = OnUpdate.class)
    @Null(groups = OnCreate.class)
    private Long shiftId;
    @NotNull(groups = {OnCreate.class,  OnUpdate.class})
    private LocalTime startTime;
    @NotNull(groups = {OnCreate.class,  OnUpdate.class})
    private LocalTime endTime;
    @NotNull(groups = {OnCreate.class,  OnUpdate.class})
    private DayOfWeek dayOfWeek;

    public Long getEmpId() {
        return empId;
    }

    public Long getShiftId() {
        return shiftId;
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

}
