package org.example.maridone.schedule.shift;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "daily_shift_schedule")
public class DailyShiftSchedule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long dailyShiftId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shiftId")
    private TemplateShiftSchedule templateShiftSchedule;

    @Column(name = "start_date_time", nullable = false)
    private LocalDateTime startDateTime;

    @Column(name = "end_date_time", nullable = false)
    private LocalDateTime endDateTime;

    @Column(name = "is_day_off", nullable = false)
    private boolean dayOff;

    public Long getDailyShiftId() {
        return dailyShiftId;
    }

    public TemplateShiftSchedule getTemplateShiftSchedule() {
        return templateShiftSchedule;
    }

    public void setTemplateShiftSchedule(TemplateShiftSchedule templateShiftSchedule) {
        this.templateShiftSchedule = templateShiftSchedule;
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

    public boolean isDayOff() {
        return dayOff;
    }

    public void setDayOff(boolean dayOff) {
        this.dayOff = dayOff;
    }

    @Transient
    public Long getEmployeeId() {
        return templateShiftSchedule == null ? null : templateShiftSchedule.getEmployeeId();
    }

    @Transient
    public LocalDate getWorkDate() {
        return startDateTime == null ? null : startDateTime.toLocalDate();
    }

    @Transient
    public LocalTime getStartTime() {
        return startDateTime == null ? null : startDateTime.toLocalTime();
    }

    @Transient
    public LocalTime getEndTime() {
        return endDateTime == null ? null : endDateTime.toLocalTime();
    }
}
