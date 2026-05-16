package org.example.maridone.notification.event;

import java.util.List;

public record NotificationCreatedEvent(
        List<Long> employeeIds,
        String message,
        String importance

) {}
