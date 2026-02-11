package org.example.maridone.log.filter;

import org.example.maridone.enums.Activity;

import java.time.Instant;

public record ActivityLogFilter(
        Long activityId,
        Long employeeId,
        Activity activityType,
        String message,
        Instant timestamp
)
{}
