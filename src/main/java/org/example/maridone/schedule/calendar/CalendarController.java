package org.example.maridone.schedule.calendar;

import org.example.maridone.annotation.AuditLog;
import org.example.maridone.marker.OnCreate;
import org.example.maridone.marker.OnUpdate;
import org.example.maridone.schedule.dto.CalendarDto;
import org.example.maridone.schedule.dto.CalendarEventResponseDto;
import org.example.maridone.schedule.mapper.CalendarMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/calendar/events")
public class CalendarController {

    private final CalendarService calendarService;
    private final CalendarMapper calendarMapper;

    public CalendarController(CalendarService calendarService, CalendarMapper calendarMapper) {
        this.calendarService = calendarService;
        this.calendarMapper = calendarMapper;
    }


    @PostMapping("/create")
    @PreAuthorize("hasAnyRole('HR', 'MANAGEMENT')")
    @AuditLog
    public ResponseEntity<CalendarEventResponseDto> createEvent(
            @RequestBody @Validated(OnCreate.class) CalendarDto calendar,
            Authentication authentication) {
        CompanyCalendar savedCalendar = calendarService.createEvent(calendar, authentication.getName());

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .build()
                .toUri();

        return ResponseEntity.created(location).body(calendarMapper.toCalendarEventResponseDto(savedCalendar));
    }

    @PutMapping("/update")
    @PreAuthorize("hasAnyRole('HR', 'MANAGEMENT')")
    @AuditLog
    public ResponseEntity<CalendarEventResponseDto> updateEvent(
            @RequestBody @Validated(OnUpdate.class) CalendarDto calendar) {
        CompanyCalendar cal = calendarService.updateEvent(calendar);
        return ResponseEntity.ok(calendarMapper.toCalendarEventResponseDto(cal));
    }

    @PatchMapping("/delete/{calendarId}")
    @PreAuthorize("hasAnyRole('HR', 'MANAGEMENT')")
    @AuditLog
    public ResponseEntity<Void> deleteEvent(@PathVariable Long calendarId) {
        calendarService.deleteEvent(calendarId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<CalendarEventResponseDto> getAllEvents(@RequestParam int month, @RequestParam int year) {
        return calendarMapper.toCalendarEventResponseDtos(calendarService.getAllEvents(month, year));
    }

}
