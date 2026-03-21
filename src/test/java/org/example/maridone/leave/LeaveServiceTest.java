package org.example.maridone.leave;

import java.math.BigDecimal;
import java.time.LocalDate;
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
        dto.setStartDateTime(LocalDate.now().atTime(8, 0));
        dto.setEndDateTime(LocalDate.now().atTime(17, 0));
        dto.setReason("Vacation");

        Employee emp = new Employee();
        ReflectionTestUtils.setField(emp, "employeeId", empId);

        when(employeeRepository.findById(empId)).thenReturn(Optional.of(emp));
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(i -> i.getArgument(0));

        LeaveRequest result = leaveService.createLeaveRequest(dto, empId);

        Assertions.assertEquals(Status.PENDING, result.getRequestStatus());
        Assertions.assertEquals(emp, result.getEmployee());
        Assertions.assertEquals("Vacation", result.getReason());
    }
}
