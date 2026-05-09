package org.example.maridone.schedule.calendar;

import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import org.example.maridone.config.DefaultConfig;
import org.example.maridone.core.employee.Employee;
import org.example.maridone.core.employee.EmployeeRepository;
import org.example.maridone.core.user.UserAccount;
import org.example.maridone.core.user.UserAccountRepository;
import org.example.maridone.enums.EmploymentStatus;
import org.example.maridone.enums.Position;
import org.example.maridone.exception.badrequest.InvalidRangeException;
import org.example.maridone.notification.NotificationService;
import org.example.maridone.schedule.dto.CalendarDto;
import org.example.maridone.schedule.dto.CalendarEventResponseDto;
import org.example.maridone.schedule.mapper.CalendarMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CalendarServiceTest {

    @Mock private CalendarRepository calendarRepository;
    @Mock private CalendarMapper calendarMapper;
    @Mock private UserAccountRepository userAccountRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private NotificationService notificationService;
    @Mock private DefaultConfig defaultConfig;

    @InjectMocks
    private CalendarService calendarService;

    @Test
    void createEvent_ShouldSaveCreatorMetadata() {
        CalendarDto dto = new CalendarDto();
        dto.setTitle("Holiday");
        dto.setDescription("Holiday description");
        dto.setStartDate(Instant.now());
        dto.setEndDate(Instant.now().plus(1, ChronoUnit.DAYS));

        CompanyCalendar entity = new CompanyCalendar();
        entity.setTitle("Holiday");
        entity.setDescription("Holiday description");
        entity.setStartDate(dto.getStartDate());
        entity.setEndDate(dto.getEndDate());

        Employee employee = new Employee();
        ReflectionTestUtils.setField(employee, "employeeId", 2L);
        employee.setFirstName("HR");
        employee.setLastName("Poster");

        UserAccount userAccount = new UserAccount();
        userAccount.setUsername("userName2");
        userAccount.setEmployee(employee);
        CalendarEventResponseDto responseDto = new CalendarEventResponseDto();
        responseDto.setTitle("Holiday");
        responseDto.setDescription("Holiday description");
        responseDto.setEmployeeId(2L);
        responseDto.setEmployeeName("HR Poster");

        when(calendarMapper.toCompanyCalendar(dto)).thenReturn(entity);
        when(userAccountRepository.findByUsername("userName2")).thenReturn(Optional.of(userAccount));
        when(calendarRepository.save(any(CompanyCalendar.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(calendarMapper.toCalendarEventResponseDto(any(CompanyCalendar.class))).thenReturn(responseDto);
        when(defaultConfig.getTimeZone()).thenReturn(ZoneId.of("Asia/Manila"));
        when(employeeRepository.findByEmploymentStatusNotOrderByLastNameAscFirstNameAsc(EmploymentStatus.TERMINATED))
                .thenReturn(List.of());

        CalendarEventResponseDto result = calendarService.createEvent(dto, "userName2");

        Assertions.assertEquals("Holiday", result.getTitle());
        Assertions.assertEquals("Holiday description", result.getDescription());
        Assertions.assertEquals(2L, result.getEmployeeId());
        Assertions.assertEquals("HR Poster", result.getEmployeeName());
    }

    @Test
    void updateEvent_ShouldUpdateFieldsWithoutOverwritingCreatorMetadata() {
        Long id = 10L;
        CalendarDto dto = new CalendarDto();
        dto.setCalendarId(id);
        dto.setTitle("New Title");
        dto.setDescription("Updated description");
        dto.setIsActive(false);
        dto.setStartDate(Instant.now());
        dto.setEndDate(Instant.now().plus(5, ChronoUnit.DAYS));

        CompanyCalendar existing = new CompanyCalendar();
        Employee employee = new Employee();
        ReflectionTestUtils.setField(employee, "employeeId", 1L);
        employee.setFirstName("Management");
        employee.setLastName("Poster");
        Instant createdAt = Instant.now().minus(2, ChronoUnit.DAYS);
        CalendarEventResponseDto responseDto = new CalendarEventResponseDto();
        responseDto.setTitle("New Title");
        responseDto.setDescription("Updated description");
        responseDto.setEmployeeId(1L);
        responseDto.setEmployeeName("Management Poster");

        ReflectionTestUtils.setField(existing, "calendarId", id);
        existing.setTitle("Old Title");
        existing.setDescription("Old description");
        existing.setIsActive(true);
        existing.setStartDate(dto.getStartDate().minus(3, ChronoUnit.DAYS));
        existing.setEndDate(dto.getEndDate().minus(3, ChronoUnit.DAYS));
        existing.setEmployee(employee);
        existing.setCreatedAt(createdAt);

        when(calendarRepository.findByIdWithEmployee(id)).thenReturn(Optional.of(existing));
        when(calendarRepository.save(any(CompanyCalendar.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(calendarMapper.toCalendarEventResponseDto(any(CompanyCalendar.class))).thenReturn(responseDto);

        CalendarEventResponseDto result = calendarService.updateEvent(dto);

        Assertions.assertEquals("New Title", result.getTitle());
        Assertions.assertEquals("Updated description", result.getDescription());
        Assertions.assertEquals(1L, result.getEmployeeId());
        Assertions.assertEquals("Management Poster", result.getEmployeeName());
        Assertions.assertEquals("New Title", existing.getTitle());
        Assertions.assertEquals("Updated description", existing.getDescription());
        Assertions.assertFalse(existing.getIsActive());
        Assertions.assertEquals(dto.getStartDate(), existing.getStartDate());
        Assertions.assertEquals(dto.getEndDate(), existing.getEndDate());
        Assertions.assertSame(employee, existing.getEmployee());
        Assertions.assertEquals(createdAt, existing.getCreatedAt());
    }

    @Test
    void deleteEvent_ShouldSoftDelete() {
        Long id = 15L;
        CompanyCalendar existing = new CompanyCalendar();
        ReflectionTestUtils.setField(existing, "calendarId", id);
        existing.setIsActive(true);

        when(calendarRepository.findById(id)).thenReturn(Optional.of(existing));
        when(calendarRepository.save(any(CompanyCalendar.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CompanyCalendar result = calendarService.deleteEvent(id);

        Assertions.assertFalse(result.getIsActive());
    }

    @Test
    void createEvent_ShouldRejectEndDateBeforeStartDate() {
        CalendarDto dto = new CalendarDto();
        dto.setTitle("Town Hall Reminder");
        dto.setStartDate(Instant.parse("2026-04-10T09:00:00Z"));
        dto.setEndDate(Instant.parse("2026-04-08T01:30:00Z"));
        dto.setIsActive(true);

        InvalidRangeException error = Assertions.assertThrows(
                InvalidRangeException.class,
                () -> calendarService.createEvent(dto, "manager.user")
        );

        Assertions.assertEquals("End date and time must be after the start date and time.", error.getMessage());
        verifyNoInteractions(userAccountRepository, calendarMapper, calendarRepository, notificationService);
    }

    @Test
    void createEvent_ShouldNotifyAllEligibleRolesExceptAccountingAboutAnnouncement() {
        Instant startDate = Instant.parse("2026-04-08T01:30:00Z");
        Instant endDate = Instant.parse("2026-04-10T09:00:00Z");

        CalendarDto dto = new CalendarDto();
        dto.setTitle("Town Hall Reminder");
        dto.setDescription("Updated leadership schedule");
        dto.setStartDate(startDate);
        dto.setEndDate(endDate);

        CompanyCalendar entity = new CompanyCalendar();
        entity.setTitle(dto.getTitle());
        entity.setDescription(dto.getDescription());
        entity.setStartDate(startDate);
        entity.setEndDate(endDate);

        Employee author = buildEmployee(2L, "Harper", "Stone", EmploymentStatus.REGULAR, Position.HR, true);
        UserAccount actingUser = new UserAccount();
        actingUser.setUsername("manager.user");
        actingUser.setEmployee(author);
        entity.setEmployee(author);

        Employee employeeRecipient = buildEmployee(7L, "Avery", "Cruz", EmploymentStatus.REGULAR, Position.EMPLOYEE, true);
        Employee managerRecipient = buildEmployee(8L, "Jules", "Tan", EmploymentStatus.REGULAR, Position.MANAGER, true);
        Employee managementRecipient = buildEmployee(9L, "Riley", "Go", EmploymentStatus.REGULAR, Position.MANAGEMENT, true);
        Employee hrRecipient = buildEmployee(10L, "Morgan", "Lee", EmploymentStatus.REGULAR, Position.HR, true);
        Employee accountingRecipient = buildEmployee(11L, "Casey", "Lim", EmploymentStatus.REGULAR, Position.ACCOUNTING, true);
        Employee suspendedRecipient = buildEmployee(12L, "Drew", "Uy", EmploymentStatus.SUSPENDED, Position.EMPLOYEE, true);
        Employee noAccountRecipient = buildEmployee(13L, "Sam", "Ong", EmploymentStatus.REGULAR, Position.EMPLOYEE, false);

        CalendarEventResponseDto responseDto = new CalendarEventResponseDto();
        responseDto.setTitle(dto.getTitle());

        when(calendarMapper.toCompanyCalendar(dto)).thenReturn(entity);
        when(userAccountRepository.findByUsername("manager.user")).thenReturn(Optional.of(actingUser));
        when(calendarRepository.save(any(CompanyCalendar.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(calendarMapper.toCalendarEventResponseDto(any(CompanyCalendar.class))).thenReturn(responseDto);
        when(defaultConfig.getTimeZone()).thenReturn(ZoneId.of("Asia/Manila"));
        when(employeeRepository.findByEmploymentStatusNotOrderByLastNameAscFirstNameAsc(EmploymentStatus.TERMINATED))
                .thenReturn(List.of(
                        employeeRecipient,
                        managerRecipient,
                        managementRecipient,
                        hrRecipient,
                        accountingRecipient,
                        author,
                        suspendedRecipient,
                        noAccountRecipient
                ));

        calendarService.createEvent(dto, "manager.user");

        verify(notificationService).createNotification(
                same(employeeRecipient),
                eq("Town Hall Reminder (April 8, 2026 9:30 AM - April 10, 2026 5:00 PM): Harper Stone created an announcement."),
                eq("HIGH")
        );
        verify(notificationService).createNotification(
                same(managerRecipient),
                eq("Town Hall Reminder (April 8, 2026 9:30 AM - April 10, 2026 5:00 PM): Harper Stone created an announcement."),
                eq("HIGH")
        );
        verify(notificationService).createNotification(
                same(managementRecipient),
                eq("Town Hall Reminder (April 8, 2026 9:30 AM - April 10, 2026 5:00 PM): Harper Stone created an announcement."),
                eq("HIGH")
        );
        verify(notificationService).createNotification(
                same(hrRecipient),
                eq("Town Hall Reminder (April 8, 2026 9:30 AM - April 10, 2026 5:00 PM): Harper Stone created an announcement."),
                eq("HIGH")
        );
        verify(notificationService, never()).createNotification(same(accountingRecipient), any(), any());
        verify(notificationService, never()).createNotification(same(author), any(), any());
        verify(notificationService, never()).createNotification(same(suspendedRecipient), any(), any());
        verify(notificationService, never()).createNotification(same(noAccountRecipient), any(), any());
    }

    private Employee buildEmployee(
            Long employeeId,
            String firstName,
            String lastName,
            EmploymentStatus employmentStatus,
            Position position,
            boolean withUserAccount
    ) {
        Employee employee = new Employee();
        ReflectionTestUtils.setField(employee, "employeeId", employeeId);
        employee.setFirstName(firstName);
        employee.setLastName(lastName);
        employee.setEmploymentStatus(employmentStatus);
        employee.setPosition(position);

        if (withUserAccount) {
            UserAccount userAccount = new UserAccount();
            userAccount.setUsername(firstName.toLowerCase());
            userAccount.setEmployee(employee);
            employee.setUserAccount(userAccount);
        }

        return employee;
    }
}
