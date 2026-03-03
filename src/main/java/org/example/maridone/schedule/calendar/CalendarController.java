package org.example.maridone.schedule.calendar;

import org.example.maridone.annotation.AuditLog;
import org.example.maridone.marker.OnCreate;
import org.example.maridone.marker.OnUpdate;
import org.example.maridone.schedule.dto.CalendarDto;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/calendar/events")
public class CalendarController {

    private final CalendarService calendarService;
    public CalendarController(CalendarService calendarService) {
        this.calendarService = calendarService;
    }


    @PostMapping("/create")
    @PreAuthorize("hasRole('HR')")
    @AuditLog
    public ResponseEntity<CompanyCalendar> createEvent(
            @RequestBody @Validated(OnCreate.class) CalendarDto calendar) {
        CompanyCalendar savedCalendar = calendarService.createEvent(calendar);

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .build()
                .toUri();

        return ResponseEntity.created(location).body(savedCalendar);
    }

    @PutMapping("/update")
    @PreAuthorize("hasRole('HR')")
    @AuditLog
    public ResponseEntity<CompanyCalendar> updateEvent(
            @RequestBody @Validated(OnUpdate.class) CalendarDto calendar) {
        CompanyCalendar cal = calendarService.updateEvent(calendar);
        return ResponseEntity.ok(cal);
    }

    @PatchMapping("/delete/{calendarId}")
    @PreAuthorize("hasRole('HR')")
    @AuditLog
    public ResponseEntity<Void> deleteEvent(@PathVariable Long calendarId) {
        calendarService.deleteEvent(calendarId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<CompanyCalendar> getAllEvents(@RequestParam int month, @RequestParam int year) {
        return calendarService.getAllEvents(month, year);
    }

}
