package org.example.maridone.compliance.dto;

import org.example.maridone.document.dto.DocumentLinkDto;

import java.util.List;

public record ComplianceDocumentDto(
        Long pathId,
        String fileName,
        String fileType,
        Long fileSizeBytes,
        List<DocumentLinkDto> dto
) {}
