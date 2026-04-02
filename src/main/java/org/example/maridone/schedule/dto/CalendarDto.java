package org.example.maridone.schedule.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Size;
import org.example.maridone.marker.OnCreate;
import org.example.maridone.marker.OnUpdate;

import java.time.Instant;

public class CalendarDto {

    @Null(groups = OnCreate.class)
    @NotNull(groups = OnUpdate.class)
    private Long calendarId;
    @NotNull(groups = {OnCreate.class, OnUpdate.class})
    @Size(max = 120, groups = {OnCreate.class, OnUpdate.class})
    private String title;
    @Size(max = 500, groups = {OnCreate.class, OnUpdate.class})
    private String description;
    @NotNull(groups = {OnCreate.class, OnUpdate.class})
    private Instant startDate;
    @NotNull(groups = {OnCreate.class, OnUpdate.class})
    private Instant endDate;
    @NotNull(groups = {OnCreate.class, OnUpdate.class})
    private Boolean isActive;


    public Long getCalendarId() {
        return calendarId;
    }

    public void setCalendarId(Long calendarId) {
        this.calendarId = calendarId;
    }

    public Instant getStartDate() {
        return startDate;
    }

    public void setStartDate(Instant startDate) {
        this.startDate = startDate;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Instant getEndDate() {
        return endDate;
    }

    public void setEndDate(Instant endDate) {
        this.endDate = endDate;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean active) {
        isActive = active;
    }
}
