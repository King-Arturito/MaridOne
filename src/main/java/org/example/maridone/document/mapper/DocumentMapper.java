package org.example.maridone.document.mapper;

import org.example.maridone.document.dto.DocumentResponseDto;
import org.example.maridone.document.path.DocumentPath;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface DocumentMapper {

    DocumentResponseDto toDocumentResponseDto(DocumentPath documentPath);

    List<DocumentResponseDto> toDocumentResponseDtos(List<DocumentPath> documentPaths);
}
