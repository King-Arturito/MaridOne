package org.example.maridone.schedule.calendar;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.example.maridone.schedule.dto.CalendarDto;
import org.example.maridone.schedule.mapper.CalendarMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CalendarServiceTest {

    @Mock private CalendarRepository calendarRepository;
    @Mock private CalendarMapper calendarMapper;

    @InjectMocks
    private CalendarService calendarService;

    @Test
    void createEvent_ShouldSave() {
        CalendarDto dto = new CalendarDto();
        dto.setTitle("Holiday");
        
        CompanyCalendar entity = new CompanyCalendar();
        entity.setTitle("Holiday");

        when(calendarMapper.toCompanyCalendar(dto)).thenReturn(entity);
        when(calendarRepository.save(any(CompanyCalendar.class))).thenReturn(entity);

        CompanyCalendar result = calendarService.createEvent(dto);
        
        Assertions.assertEquals("Holiday", result.getTitle());
    }

    @Test
    void updateEvent_ShouldUpdateFields() {
        Long id = 10L;
        CalendarDto dto = new CalendarDto();
        dto.setCalendarId(id);
        dto.setTitle("New Title");
        dto.setIsActive(true);
        dto.setStartDate(Instant.now());
        dto.setEndDate(Instant.now().plus(5, ChronoUnit.DAYS));

        CompanyCalendar existing = new CompanyCalendar();
        ReflectionTestUtils.setField(existing, "calendarId", id);
        existing.setTitle(dto.getTitle());
        existing.setIsActive(true);
        existing.setStartDate(dto.getStartDate());
        existing.setEndDate(dto.getStartDate());


        when(calendarRepository.findById(id)).thenReturn(Optional.of(existing));
        when(calendarRepository.save(any(CompanyCalendar.class))).thenReturn(existing);
        when(calendarMapper.toCompanyCalendar(dto)).thenReturn(existing);
        CompanyCalendar result = calendarService.updateEvent(dto);

        Assertions.assertEquals("New Title", result.getTitle());
    }
}