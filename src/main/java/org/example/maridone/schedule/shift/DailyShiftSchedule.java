package org.example.maridone.schedule.shift;

import jakarta.persistence.*;
import org.example.maridone.enums.EarningsType;
import java.time.LocalDate;
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

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "scheduled_date", nullable = false)
    private LocalDate scheduledDate;

    public Long getDailyShiftId() {
        return dailyShiftId;
    }

    public TemplateShiftSchedule getTemplateShiftSchedule() {
        return templateShiftSchedule;
    }

    public void setTemplateShiftSchedule(TemplateShiftSchedule templateShiftSchedule) {
        this.templateShiftSchedule = templateShiftSchedule;
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

    public LocalDate getScheduledDate() {
        return scheduledDate;
    }

    public void setScheduledDate(LocalDate scheduledDate) {
        this.scheduledDate = scheduledDate;
    }
}
