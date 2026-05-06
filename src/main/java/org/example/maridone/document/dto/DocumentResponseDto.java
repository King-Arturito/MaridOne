package org.example.maridone.document.dto;

import java.time.Instant;

public record DocumentResponseDto(
        Long pathId,
        String fileName,
        String fileType,
        Long fileSizeBytes,
        String connectedEntity,
        String connectedIds,
        Instant createdAt,
        Instant updatedAt,
        Boolean isPublic
) {}
