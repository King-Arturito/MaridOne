package org.example.maridone.schedule.mapper;


import org.example.maridone.schedule.dto.ShiftResponseDto;
import org.example.maridone.schedule.shift.TemplateShiftSchedule;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ScheduleMapper {

    @Mapping(source = "employeeId", target = "employeeId")
    ShiftResponseDto toResponseDto(TemplateShiftSchedule templateShiftSchedule);

    List<ShiftResponseDto> toResponseDtos(List<TemplateShiftSchedule> templateShiftSchedules);
}
