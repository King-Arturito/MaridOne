package org.example.maridone.compliance.dto;

public record ComplianceDocumentDto(
        Long pathId,
        String fileName,
        String fileType,
        Long fileSizeBytes,
        String connectedEntity,
        String connectedId
) {}
