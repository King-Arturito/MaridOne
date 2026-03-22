package org.example.maridone.document.mapper;

import org.example.maridone.document.dto.DocumentResponseDto;
import org.example.maridone.document.path.DocumentPath;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface DocumentMapper {

    @Mapping(target = "username", source = "username.username")
    DocumentResponseDto toDocumentResponseDto(DocumentPath documentPath);
}
