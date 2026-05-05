package org.example.maridone.schedule.calendar;

import org.example.maridone.annotation.ExecutionTime;
import org.example.maridone.config.DefaultConfig;
import org.example.maridone.core.employee.Employee;
import org.example.maridone.core.employee.EmployeeRepository;
import org.example.maridone.core.user.UserAccount;
import org.example.maridone.core.user.UserAccountRepository;
import org.example.maridone.enums.EmploymentStatus;
import org.example.maridone.enums.Position;
import org.example.maridone.exception.badrequest.InvalidRangeException;
import org.example.maridone.exception.notfound.AccountNotFoundException;
import org.example.maridone.exception.notfound.CalendarEventNotFound;
import org.example.maridone.notification.NotificationMessageFormatter;
import org.example.maridone.notification.NotificationService;
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
    private final EmployeeRepository employeeRepository;
    private final NotificationService notificationService;

    public CalendarService(
            CalendarRepository calendarRepository,
            CalendarMapper calendarMapper,
            UserAccountRepository userAccountRepository,
            DefaultConfig defaultConfig,
            EmployeeRepository employeeRepository,
            NotificationService notificationService)
    {
        this.calendarRepository = calendarRepository;
        this.calendarMapper = calendarMapper;
        this.userAccountRepository = userAccountRepository;
        this.defaultConfig = defaultConfig;
        this.employeeRepository = employeeRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    public CalendarEventResponseDto createEvent(CalendarDto calendar, String username) {
        if (calendar.getIsActive() == null) {
            calendar.setIsActive(Boolean.TRUE);
        }

        validateEventDateRange(calendar);

        UserAccount userAccount = userAccountRepository.findByUsername(username).orElseThrow(
                () -> new AccountNotFoundException("UserAccount with: " + username + " not found.")
        );

        CompanyCalendar companyCalendar = calendarMapper.toCompanyCalendar(calendar);
        companyCalendar.setIsActive(calendar.getIsActive());
        companyCalendar.setEmployee(userAccount.getEmployee());
        companyCalendar.setCreatedAt(Instant.now());
        CompanyCalendar savedCalendar = calendarRepository.save(companyCalendar);
        notifyAnnouncementRecipients(savedCalendar);
        return calendarMapper.toCalendarEventResponseDto(savedCalendar);
    }


    @Transactional
    @ExecutionTime
    public CalendarEventResponseDto updateEvent(CalendarDto calendar) {
        validateEventDateRange(calendar);

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

    private void validateEventDateRange(CalendarDto calendar) {
        if (calendar == null || calendar.getStartDate() == null || calendar.getEndDate() == null) {
            return;
        }

        if (calendar.getEndDate().isBefore(calendar.getStartDate())) {
            throw new InvalidRangeException("End date and time must be after the start date and time.");
        }
    }

    private void notifyAnnouncementRecipients(CompanyCalendar companyCalendar) {
        if (companyCalendar == null || companyCalendar.getEmployee() == null) {
            return;
        }

        Employee author = companyCalendar.getEmployee();
        Long authorId = author.getEmployeeId();
        String message = NotificationMessageFormatter.formatAnnouncementMessage(
                author,
                companyCalendar.getStartDate(),
                companyCalendar.getEndDate(),
                companyCalendar.getTitle(),
                defaultConfig.getTimeZone()
        );

        for (Employee employee : employeeRepository.findByEmploymentStatusNotOrderByLastNameAscFirstNameAsc(EmploymentStatus.TERMINATED)) {
            if (employee == null
                    || employee.getUserAccount() == null
                    || employee.getEmploymentStatus() == EmploymentStatus.SUSPENDED
                    || employee.getPosition() == Position.ACCOUNTING
                    || (authorId != null && authorId.equals(employee.getEmployeeId()))) {
                continue;
            }

            notificationService.createNotification(employee, message, "HIGH");
        }
    }
}
