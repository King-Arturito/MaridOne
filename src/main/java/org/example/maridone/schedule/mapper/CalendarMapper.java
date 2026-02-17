package org.example.maridone.schedule.mapper;

import org.example.maridone.schedule.calendar.CompanyCalendar;
import org.example.maridone.schedule.dto.CalendarDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CalendarMapper {

    @Mapping(source = "isActive", target = "isActive")
    CompanyCalendar toCompanyCalendar(CalendarDto calendarDto);
}
