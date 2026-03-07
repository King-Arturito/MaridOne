package org.example.maridone.log.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.example.maridone.enums.Activity;

public class ActivityRequestDto {

    @NotNull(message = "Username is required.")
    private String username;

    @NotNull(message = "Activity type is required.")
    private Activity activityType;

    @NotBlank(message = "Message cannot be empty or just whitespace.")
    @Size(max = 255, message = "Message must not exceed 255 characters.")
    private String message;

    public ActivityRequestDto(String username, Activity activityType, String message) {
        this.username = username;
        this.activityType = activityType;
        this.message = message;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
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