package org.example.maridone.schedule.calendar;

import org.example.maridone.annotation.ExecutionTime;
import org.example.maridone.config.DefaultConfig;
import org.example.maridone.core.user.UserAccount;
import org.example.maridone.core.user.UserAccountRepository;
import org.example.maridone.exception.notfound.AccountNotFoundException;
import org.example.maridone.exception.notfound.CalendarEventNotFound;
import org.example.maridone.schedule.dto.CalendarDto;
import org.example.maridone.schedule.dto.CalendarEventResponseDto;
import org.example.maridone.schedule.mapper.CalendarMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.YearMonth;
import java.util.List;

@Service
public class CalendarService {

    private final CalendarRepository calendarRepository;
    private final CalendarMapper calendarMapper;
    private final UserAccountRepository userAccountRepository;
    private final DefaultConfig defaultConfig;

    public CalendarService(
            CalendarRepository calendarRepository,
            CalendarMapper calendarMapper,
            UserAccountRepository userAccountRepository,
            DefaultConfig defaultConfig)
    {
        this.calendarRepository = calendarRepository;
        this.calendarMapper = calendarMapper;
        this.userAccountRepository = userAccountRepository;
        this.defaultConfig = defaultConfig;
    }

    @Transactional
    public CalendarEventResponseDto createEvent(CalendarDto calendar, String username) {
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
        CompanyCalendar savedCalendar = calendarRepository.save(companyCalendar);
        return calendarMapper.toCalendarEventResponseDto(savedCalendar);
    }


    @Transactional
    @ExecutionTime
    public CalendarEventResponseDto updateEvent(CalendarDto calendar) {
        CompanyCalendar cal = calendarRepository.findByIdWithEmployee(calendar.getCalendarId()).orElseThrow(
                () -> new CalendarEventNotFound("Event of ID:" + calendar.getCalendarId() + " not found."));
        cal.setTitle(calendar.getTitle());
        cal.setDescription(calendar.getDescription());
        cal.setStartDate(calendar.getStartDate());
        cal.setEndDate(calendar.getEndDate());
        cal.setIsActive(calendar.getIsActive());
        CompanyCalendar savedCalendar = calendarRepository.save(cal);
        return calendarMapper.toCalendarEventResponseDto(savedCalendar);
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
    @Transactional(readOnly = true)
    public List<CalendarEventResponseDto> getAllEvents(int month, int year) {
        YearMonth selectedMonth = YearMonth.of(year, month);
        Instant periodStart = selectedMonth.atDay(1).atStartOfDay(defaultConfig.getTimeZone()).toInstant();
        Instant periodEnd = selectedMonth.atEndOfMonth().plusDays(1).atStartOfDay(defaultConfig.getTimeZone()).minusNanos(1).toInstant();

        return calendarMapper.toCalendarEventResponseDtos(
                calendarRepository.findActiveEventsOverlappingPeriod(periodStart, periodEnd)
        );
    }
}
