package org.example.maridone.schedule.calendar;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
public class CompanyCalendar {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "calendar_id", nullable = false)
    private Long calendarId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "start_date", nullable = false)
    private Instant startDate;

    @Column(name = "end_date", nullable = false)
    private Instant endDate;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    public Long getCalendarId() {
        return calendarId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Instant getStartDate() {
        return startDate;
    }

    public void setStartDate(Instant start) {
        this.startDate = start;
    }

    public Instant getEndDate() {
        return endDate;
    }

    public void setEndDate(Instant end) {
        this.endDate = end;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean active) {
        isActive = active;
    }
}
