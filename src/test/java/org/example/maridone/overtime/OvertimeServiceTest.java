package org.example.maridone.overtime;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;

import org.example.maridone.config.DefaultConfig;
import org.example.maridone.core.employee.Employee;
import org.example.maridone.core.employee.EmployeeRepository;
import org.example.maridone.core.user.UserAccountRepository;
import org.example.maridone.enums.Status;
import org.example.maridone.exception.unauthorized.OvertimeException;
import org.example.maridone.overtime.dto.OvertimeRequestDto;
import org.example.maridone.overtime.dto.OvertimeResponseDto;
import org.example.maridone.overtime.dto.OvertimeUpdateDto;
import org.example.maridone.overtime.mapper.OvertimeMapper;
import org.example.maridone.schedule.shift.TemplateShiftRepository;
import org.example.maridone.schedule.shift.TemplateShiftSchedule;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OvertimeServiceTest {

    @Mock private OvertimeRequestRepository overtimeRequestRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private OvertimeMapper overtimeMapper;
    @Mock private UserAccountRepository userAccountRepository;
    @Mock private TemplateShiftRepository templateShiftRepository;
    @Mock private DefaultConfig defaultConfig;

    @InjectMocks
    private OvertimeService overtimeService;

    @Test
    void createOvertimeRequest_ShouldSave() {
        String username = "niko123";
        ZoneId zoneId = ZoneId.of("Asia/Manila");
        LocalDate workDate = LocalDate.now(zoneId).plusDays(1);
        OvertimeRequestDto dto = new OvertimeRequestDto();
        dto.setWorkDate(workDate);
        dto.setStartTime(LocalDateTime.of(workDate, LocalTime.of(17, 0)));
        dto.setEndTime(LocalDateTime.of(workDate, LocalTime.of(19, 0)));
        OvertimeRequest request = new OvertimeRequest();
        Employee emp = new Employee();
        ReflectionTestUtils.setField(emp, "employeeId", 9L);
        TemplateShiftSchedule shift = new TemplateShiftSchedule();
        shift.setDayOfWeek(workDate.getDayOfWeek());
        shift.setStartTime(LocalTime.of(8, 0));
        shift.setEndTime(LocalTime.of(17, 0));

        when(overtimeMapper.toOvertimeRequest(dto)).thenReturn(request);
        when(employeeRepository.findOne(any(Specification.class))).thenReturn(Optional.of(emp));
        when(defaultConfig.getTimeZone()).thenReturn(zoneId);
        when(templateShiftRepository.findAllByEmployeeId(9L)).thenReturn(java.util.List.of(shift));
        when(overtimeRequestRepository.save(any(OvertimeRequest.class))).thenReturn(request);

        OvertimeRequest result = overtimeService.createOvertimeRequest(dto, username);
        
        Assertions.assertNotNull(result);
    }

    @Test
    void createOvertimeRequest_ShouldRejectWindowInsideShift() {
        String username = "niko123";
        ZoneId zoneId = ZoneId.of("Asia/Manila");
        LocalDate workDate = LocalDate.now(zoneId).plusDays(1);
        OvertimeRequestDto dto = new OvertimeRequestDto();
        dto.setWorkDate(workDate);
        dto.setStartTime(LocalDateTime.of(workDate, LocalTime.of(9, 0)));
        dto.setEndTime(LocalDateTime.of(workDate, LocalTime.of(11, 0)));

        Employee emp = new Employee();
        ReflectionTestUtils.setField(emp, "employeeId", 9L);
        TemplateShiftSchedule shift = new TemplateShiftSchedule();
        shift.setDayOfWeek(workDate.getDayOfWeek());
        shift.setStartTime(LocalTime.of(8, 0));
        shift.setEndTime(LocalTime.of(17, 0));

        when(employeeRepository.findOne(any(Specification.class))).thenReturn(Optional.of(emp));
        when(defaultConfig.getTimeZone()).thenReturn(zoneId);
        when(templateShiftRepository.findAllByEmployeeId(9L)).thenReturn(java.util.List.of(shift));

        Assertions.assertThrows(OvertimeException.class, () -> overtimeService.createOvertimeRequest(dto, username));
    }

    @Test
    void updateOvertimeRequest_ShouldSucceed_WhenPending() {
        Long otId = 1L;
        String approver = "admin";
        OvertimeUpdateDto updateDto = new OvertimeUpdateDto();
        updateDto.setOvertimeId(otId);
        updateDto.setUpdateStatus(Status.APPROVED);

        OvertimeRequest request = new OvertimeRequest();
        ReflectionTestUtils.setField(request, "overtimeId", otId); 
        request.setRequestStatus(Status.PENDING); 

        Employee admin = new Employee();
        admin.setFirstName("Admin");
        admin.setLastName("User");

        when(overtimeRequestRepository.findById(otId)).thenReturn(Optional.of(request));
        when(employeeRepository.findOne(any(Specification.class))).thenReturn(Optional.of(admin));
        when(overtimeRequestRepository.save(any(OvertimeRequest.class))).thenReturn(request);
        
        OvertimeResponseDto responseDto = new OvertimeResponseDto();
        when(overtimeMapper.toOvertimeResponseDto(request)).thenReturn(responseDto);

        OvertimeResponseDto result = overtimeService.updateOvertimeRequest(updateDto, approver);
        
        Assertions.assertNotNull(result);
    }

    @Test
    void getTeamOvertimeRequests_ShouldReturnHandledEmployeesOnly() {
        String username = "manager1";
        OvertimeRequest request = new OvertimeRequest();
        OvertimeResponseDto responseDto = new OvertimeResponseDto();
        Page<OvertimeRequest> page = new PageImpl<>(java.util.List.of(request));

        when(userAccountRepository.findEmployeeIdByUsername(username)).thenReturn(2L);
        when(employeeRepository.findHandledEmployeeIds(2L)).thenReturn(java.util.List.of(3L, 4L));
        when(overtimeRequestRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(overtimeMapper.toOvertimeResponseDto(request)).thenReturn(responseDto);

        Page<OvertimeResponseDto> result =
                overtimeService.getTeamOvertimeRequests(new OvertimeFilter(null, null, null, null, null, null), username, Pageable.ofSize(20));

        Assertions.assertEquals(1, result.getTotalElements());
    }
}
