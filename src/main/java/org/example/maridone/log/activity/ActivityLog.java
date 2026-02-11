package org.example.maridone.log.activity;

import jakarta.persistence.*;
import org.example.maridone.enums.Activity;

import java.time.Instant;

@Entity
public class ActivityLog {
    @Id
    @Column(name = "activity_id")
    private Long activityId;

    @Column(name = "emp_id", nullable = false)
    private Long employeeId;

    @Column(name = "activity_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private Activity activityType;

    @Column(name = "message", nullable = false)
    private String message;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    public Long getActivityId() {
        return activityId;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(Long employeeId) {
        this.employeeId = employeeId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Activity getActivityType() {
        return activityType;
    }

    public void setActivityType(Activity activityType) {
        this.activityType = activityType;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}
