package org.example.maridone.leave;

import org.example.maridone.enums.Status;

import java.time.LocalDateTime;

public record LeaveFilter(
        Long requestId,
        LocalDateTime startDateTime,
        LocalDateTime endDateTime,
        Status requestStatus,
        Boolean isPaid) {
}
