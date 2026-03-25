package org.example.maridone.leave;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.example.maridone.core.employee.Employee;
import org.example.maridone.core.employee.EmployeeRepository;
import org.example.maridone.core.user.UserAccountRepository;
import org.example.maridone.config.PayrollConfig;
import org.example.maridone.enums.LeaveType;
import org.example.maridone.enums.Status;
import org.example.maridone.leave.balance.LeaveBalance;
import org.example.maridone.leave.balance.LeaveBalanceRepository;
import org.example.maridone.leave.dto.BalanceRequestDto;
import org.example.maridone.leave.dto.LeaveRequestDto;
import org.example.maridone.leave.request.LeaveRequest;
import org.example.maridone.leave.request.LeaveRequestRepository;
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
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class LeaveServiceTest {

    @Mock private LeaveBalanceRepository leaveBalanceRepository;
    @Mock private LeaveRequestRepository leaveRequestRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private UserAccountRepository userAccountRepository;
    @Mock private TemplateShiftRepository templateShiftRepository;
    @Mock private LeaveMapper leaveMapper;
    @Mock private PayrollConfig payrollConfig;

    @InjectMocks
    private LeaveService leaveService;

    @Test
    void createLeaveBalance_ShouldSave_WhenNoDuplicate() {
        Long empId = 1L;
        BalanceRequestDto dto = new BalanceRequestDto();
        dto.setLeaveType(LeaveType.VACATION_LEAVE);
        dto.setBalanceHours(BigDecimal.valueOf(10));

        Employee emp = new Employee();
        ReflectionTestUtils.setField(emp, "employeeId", empId);
        
        LeaveBalance mappedBalance = new LeaveBalance();
        mappedBalance.setEmployee(emp);
        mappedBalance.setLeaveType(LeaveType.VACATION_LEAVE);

        when(employeeRepository.findById(empId)).thenReturn(Optional.of(emp));
        when(leaveBalanceRepository.findOne(any(Specification.class))).thenReturn(Optional.empty());
        when(leaveMapper.toEntity(dto, emp)).thenReturn(mappedBalance);
        when(leaveBalanceRepository.save(any(LeaveBalance.class))).thenReturn(mappedBalance);

        LeaveBalance result = leaveService.createLeaveBalance(empId, dto);

        Assertions.assertNotNull(result);
        Assertions.assertEquals(LeaveType.VACATION_LEAVE, result.getLeaveType());
    }

    @Test
    void createLeaveRequest_ShouldSetStatusPending() {
        Long empId = 1L;
        LeaveRequestDto dto = new LeaveRequestDto();
        dto.setStartDateTime(LocalDate.of(2026, 3, 23).atTime(8, 0));
        dto.setEndDateTime(LocalDate.of(2026, 3, 23).atTime(17, 0));
        dto.setReason("Vacation");
        dto.setLeaveType(LeaveType.VACATION_LEAVE);

        Employee emp = new Employee();
        ReflectionTestUtils.setField(emp, "employeeId", empId);

        LeaveBalance leaveBalance = new LeaveBalance();
        leaveBalance.setBalanceHours(BigDecimal.valueOf(9));

        when(employeeRepository.findById(empId)).thenReturn(Optional.of(emp));
        when(leaveBalanceRepository.findByLeaveTypeAndEmployee_EmployeeId(LeaveType.VACATION_LEAVE, empId))
                .thenReturn(Optional.of(leaveBalance));
        when(templateShiftRepository.findAllByEmployeeId(empId)).thenReturn(List.of(
                createSchedule(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(17, 0), emp)
        ));

        List<LeaveRequest> results = leaveService.createLeaveRequest(dto, empId);
        Assertions.assertNotNull(results);
        results.forEach(result -> {
            Assertions.assertEquals(Status.PENDING, result.getRequestStatus());
            Assertions.assertEquals(emp, result.getEmployee());
            Assertions.assertEquals("Vacation", result.getReason());
        });
    }

    @Test
    void createLeaveRequest_ShouldSplitPaidAndUnpaidUsingShiftCoverage() {
        Long empId = 1L;
        LeaveRequestDto dto = new LeaveRequestDto();
        dto.setStartDateTime(LocalDateTime.of(2026, 3, 23, 9, 0));
        dto.setEndDateTime(LocalDateTime.of(2026, 3, 27, 17, 0));
        dto.setReason("Vacation");
        dto.setLeaveType(LeaveType.VACATION_LEAVE);

        Employee emp = new Employee();
        ReflectionTestUtils.setField(emp, "employeeId", empId);

        LeaveBalance leaveBalance = new LeaveBalance();
        leaveBalance.setBalanceHours(BigDecimal.valueOf(20));

        when(employeeRepository.findById(empId)).thenReturn(Optional.of(emp));
        when(leaveBalanceRepository.findByLeaveTypeAndEmployee_EmployeeId(LeaveType.VACATION_LEAVE, empId))
                .thenReturn(Optional.of(leaveBalance));
        when(templateShiftRepository.findAllByEmployeeId(empId)).thenReturn(List.of(
                createSchedule(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(17, 0), emp),
                createSchedule(DayOfWeek.TUESDAY, LocalTime.of(9, 0), LocalTime.of(17, 0), emp),
                createSchedule(DayOfWeek.WEDNESDAY, LocalTime.of(9, 0), LocalTime.of(17, 0), emp),
                createSchedule(DayOfWeek.THURSDAY, LocalTime.of(9, 0), LocalTime.of(17, 0), emp),
                createSchedule(DayOfWeek.FRIDAY, LocalTime.of(9, 0), LocalTime.of(17, 0), emp)
        ));

        List<LeaveRequest> results = leaveService.createLeaveRequest(dto, empId);

        Assertions.assertEquals(2, results.size());

        LeaveRequest paid = results.get(0);
        Assertions.assertTrue(paid.getPaid());
        Assertions.assertEquals(LocalDateTime.of(2026, 3, 23, 9, 0), paid.getStartDateTime());
        Assertions.assertEquals(LocalDateTime.of(2026, 3, 25, 13, 0), paid.getEndDateTime());

        LeaveRequest unpaid = results.get(1);
        Assertions.assertFalse(unpaid.getPaid());
        Assertions.assertEquals(LocalDateTime.of(2026, 3, 25, 13, 0), unpaid.getStartDateTime());
        Assertions.assertEquals(LocalDateTime.of(2026, 3, 27, 17, 0), unpaid.getEndDateTime());
    }

    private TemplateShiftSchedule createSchedule(DayOfWeek day, LocalTime start, LocalTime end, Employee employee) {
        TemplateShiftSchedule schedule = new TemplateShiftSchedule();
        schedule.setDayOfWeek(day);
        schedule.setStartTime(start);
        schedule.setEndTime(end);
        schedule.setEmployee(employee);
        return schedule;
    }
}
