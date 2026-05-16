package org.example.maridone.notification.event;

import org.example.maridone.enums.Position;

public record BulkNotificationCreatedEvent(
        String message,
        String importance,
        Position targetRole
)
{

}
