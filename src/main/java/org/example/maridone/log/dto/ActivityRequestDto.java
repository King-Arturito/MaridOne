package org.example.maridone.log.dto;

import org.example.maridone.enums.Activity;

public class ActivityRequestDto {
    private Long employeeId;
    private Activity activityType;
    private String message;

    public Long getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(Long employeeId) {
        this.employeeId = employeeId;
    }

    public Activity getActivityType() {
        return activityType;
    }

    public void setActivityType(Activity activityType) {
        this.activityType = activityType;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
