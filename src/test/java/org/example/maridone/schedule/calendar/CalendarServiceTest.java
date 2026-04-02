package org.example.maridone.schedule.calendar;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.example.maridone.core.employee.Employee;
import org.example.maridone.core.user.UserAccount;
import org.example.maridone.core.user.UserAccountRepository;
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
    @Mock private UserAccountRepository userAccountRepository;

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

        when(calendarMapper.toCompanyCalendar(dto)).thenReturn(entity);
        when(userAccountRepository.findByUsername("userName2")).thenReturn(Optional.of(userAccount));
        when(calendarRepository.save(any(CompanyCalendar.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CompanyCalendar result = calendarService.createEvent(dto, "userName2");

        Assertions.assertEquals("Holiday", result.getTitle());
        Assertions.assertEquals("Holiday description", result.getDescription());
        Assertions.assertTrue(result.getIsActive());
        Assertions.assertSame(employee, result.getEmployee());
        Assertions.assertNotNull(result.getCreatedAt());
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

        ReflectionTestUtils.setField(existing, "calendarId", id);
        existing.setTitle("Old Title");
        existing.setDescription("Old description");
        existing.setIsActive(true);
        existing.setStartDate(dto.getStartDate().minus(3, ChronoUnit.DAYS));
        existing.setEndDate(dto.getEndDate().minus(3, ChronoUnit.DAYS));
        existing.setEmployee(employee);
        existing.setCreatedAt(createdAt);

        when(calendarRepository.findById(id)).thenReturn(Optional.of(existing));
        when(calendarRepository.save(any(CompanyCalendar.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CompanyCalendar result = calendarService.updateEvent(dto);

        Assertions.assertEquals("New Title", result.getTitle());
        Assertions.assertEquals("Updated description", result.getDescription());
        Assertions.assertFalse(result.getIsActive());
        Assertions.assertEquals(dto.getStartDate(), result.getStartDate());
        Assertions.assertEquals(dto.getEndDate(), result.getEndDate());
        Assertions.assertSame(employee, result.getEmployee());
        Assertions.assertEquals(createdAt, result.getCreatedAt());
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
}
