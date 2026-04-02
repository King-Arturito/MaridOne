package org.example.maridone.schedule.mapper;

import org.example.maridone.core.employee.Employee;
import org.example.maridone.schedule.calendar.CompanyCalendar;
import org.example.maridone.schedule.dto.CalendarDto;
import org.example.maridone.schedule.dto.CalendarEventResponseDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

@Mapper(componentModel = "spring")
public interface CalendarMapper {

    @Mapping(source = "isActive", target = "isActive")
    @Mapping(target = "employee", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    CompanyCalendar toCompanyCalendar(CalendarDto calendarDto);

    @Mapping(source = "employee.employeeId", target = "employeeId")
    @Mapping(source = "employee", target = "employeeName", qualifiedByName = "employeeDisplayName")
    @Mapping(source = "startDate", target = "day", qualifiedByName = "dayOfMonth")
    CalendarEventResponseDto toCalendarEventResponseDto(CompanyCalendar companyCalendar);

    List<CalendarEventResponseDto> toCalendarEventResponseDtos(List<CompanyCalendar> companyCalendars);

    @Named("employeeDisplayName")
    default String employeeDisplayName(Employee employee) {
        if (employee == null) {
            return "MaridOne Staff";
        }

        String fullName = String.join(
                " ",
                safe(employee.getFirstName()),
                safe(employee.getMiddleName()),
                safe(employee.getLastName())
        ).trim().replaceAll("\\s+", " ");

        return fullName.isEmpty() ? "MaridOne Staff" : fullName;
    }

    @Named("dayOfMonth")
    default Integer dayOfMonth(Instant startDate) {
        if (startDate == null) {
            return null;
        }

        return startDate.atZone(ZoneOffset.UTC).getDayOfMonth();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
