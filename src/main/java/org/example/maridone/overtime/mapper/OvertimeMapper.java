package org.example.maridone.overtime.mapper;

import org.example.maridone.overtime.OvertimeRequest;
import org.example.maridone.overtime.dto.OvertimeRequestDto;
import org.example.maridone.overtime.dto.OvertimeResponseDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface OvertimeMapper {
    OvertimeRequest toOvertimeRequest(OvertimeRequestDto overtimeRequestDto);

    @Mapping(source = "employeeId", target = "employeeId")
    @Mapping(source =  "employee.firstName", target = "firstName")
    @Mapping(source = "employee.lastName", target = "lastName")
    OvertimeResponseDto toOvertimeResponseDto(OvertimeRequest overtimeRequest);

    List<OvertimeResponseDto> toOvertimeResponsesDto(List<OvertimeRequest> overtimeRequests);
}
