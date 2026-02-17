package org.example.maridone.schedule.calendar;

import org.example.maridone.common.CommonSpecs;
import org.example.maridone.exception.CalendarEventNotFound;
import org.example.maridone.schedule.dto.CalendarDto;
import org.example.maridone.schedule.mapper.CalendarMapper;
import org.example.maridone.schedule.spec.CalendarSpecs;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CalendarService {

    private final CalendarRepository calendarRepository;
    private final CalendarMapper calendarMapper;

    public CalendarService(
            CalendarRepository calendarRepository,
            CalendarMapper calendarMapper)
    {
        this.calendarRepository = calendarRepository;
        this.calendarMapper = calendarMapper;
    }

        @Transactional
        public CompanyCalendar createEvent(CalendarDto calendar) {
            if (calendar.getIsActive() == null) {
                calendar.setIsActive(Boolean.TRUE);
            }
            return calendarRepository.save(calendarMapper.toCompanyCalendar(calendar));
        }


    @Transactional
    public CompanyCalendar updateEvent(CalendarDto calendar) {
        CompanyCalendar cal = calendarRepository.findById(calendar.getCalendarId()).orElseThrow(
                () -> new CalendarEventNotFound("Event of ID:" + calendar.getCalendarId() + " not found."));
        return calendarRepository.save(calendarMapper.toCompanyCalendar(calendar));
    }

    @Transactional
    public CompanyCalendar deleteEvent(Long calendarId) {
        CompanyCalendar cal = calendarRepository.findById(calendarId).orElseThrow(
                () -> new CalendarEventNotFound("Event of ID:" + calendarId + " not found.")
        );
        cal.setIsActive(false);
        return calendarRepository.save(cal);
    }

    public List<CompanyCalendar> getAllEvents(int month, int year) {
        Specification<CompanyCalendar> spec = Specification.allOf(
                CalendarSpecs.isWithinMonth(month, year),
                CommonSpecs.fieldEquals("isActive", Boolean.TRUE)
        );
        return calendarRepository.findAll(spec);
    }
}
