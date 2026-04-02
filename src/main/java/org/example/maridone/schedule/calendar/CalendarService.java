package org.example.maridone.schedule.calendar;

import org.example.maridone.annotation.ExecutionTime;
import org.example.maridone.common.CommonSpecs;
import org.example.maridone.core.user.UserAccount;
import org.example.maridone.core.user.UserAccountRepository;
import org.example.maridone.exception.notfound.AccountNotFoundException;
import org.example.maridone.exception.notfound.CalendarEventNotFound;
import org.example.maridone.schedule.dto.CalendarDto;
import org.example.maridone.schedule.mapper.CalendarMapper;
import org.example.maridone.schedule.spec.CalendarSpecs;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class CalendarService {

    private final CalendarRepository calendarRepository;
    private final CalendarMapper calendarMapper;
    private final UserAccountRepository userAccountRepository;

    public CalendarService(
            CalendarRepository calendarRepository,
            CalendarMapper calendarMapper,
            UserAccountRepository userAccountRepository)
    {
        this.calendarRepository = calendarRepository;
        this.calendarMapper = calendarMapper;
        this.userAccountRepository = userAccountRepository;
    }

    @Transactional
    public CompanyCalendar createEvent(CalendarDto calendar, String username) {
        if (calendar.getIsActive() == null) {
            calendar.setIsActive(Boolean.TRUE);
        }

        UserAccount userAccount = userAccountRepository.findByUsername(username).orElseThrow(
                () -> new AccountNotFoundException("username of: " + username)
        );

        CompanyCalendar companyCalendar = calendarMapper.toCompanyCalendar(calendar);
        companyCalendar.setIsActive(calendar.getIsActive());
        companyCalendar.setEmployee(userAccount.getEmployee());
        companyCalendar.setCreatedAt(Instant.now());
        return calendarRepository.save(companyCalendar);
    }


    @Transactional
    @ExecutionTime
    public CompanyCalendar updateEvent(CalendarDto calendar) {
        CompanyCalendar cal = calendarRepository.findById(calendar.getCalendarId()).orElseThrow(
                () -> new CalendarEventNotFound("Event of ID:" + calendar.getCalendarId() + " not found."));
        cal.setTitle(calendar.getTitle());
        cal.setDescription(calendar.getDescription());
        cal.setStartDate(calendar.getStartDate());
        cal.setEndDate(calendar.getEndDate());
        cal.setIsActive(calendar.getIsActive());
        return calendarRepository.save(cal);
    }

    @Transactional
    @ExecutionTime
    public CompanyCalendar deleteEvent(Long calendarId) {
        CompanyCalendar cal = calendarRepository.findById(calendarId).orElseThrow(
                () -> new CalendarEventNotFound("Event of ID:" + calendarId + " not found.")
        );
        cal.setIsActive(false);
        return calendarRepository.save(cal);
    }

    @ExecutionTime
    public List<CompanyCalendar> getAllEvents(int month, int year) {
        Specification<CompanyCalendar> spec = Specification.allOf(
                CalendarSpecs.isWithinMonth(month, year),
                CommonSpecs.fieldEquals("isActive", Boolean.TRUE)
        );
        return calendarRepository.findAll(spec);
    }
}
