package org.example.maridone.leave;

import org.example.maridone.common.CommonSpecs;
import org.example.maridone.core.employee.Employee;
import org.example.maridone.core.employee.EmployeeRepository;
import org.example.maridone.core.user.UserAccount;
import org.example.maridone.core.user.UserAccountRepository;
import org.example.maridone.enums.Status;
import org.example.maridone.exception.*;
import org.example.maridone.leave.balance.*;
import org.example.maridone.leave.dto.BalanceRequestDto;
import org.example.maridone.leave.dto.BalanceResponseDto;
import org.example.maridone.leave.dto.LeaveRequestDto;
import org.example.maridone.leave.dto.UpdateBalanceDto;
import org.example.maridone.leave.request.LeaveRequest;
import org.example.maridone.leave.request.LeaveRequestRepository;
import org.example.maridone.leave.spec.LeaveSpecs;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class LeaveService {

    private final LeaveBalanceRepository leaveBalanceRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final EmployeeRepository employeeRepository;
    private final UserAccountRepository userAccountRepository;
    private final LeaveMapper leaveMapper;

    public LeaveService(
            LeaveBalanceRepository leaveBalanceRepository,
            LeaveRequestRepository leaveRequestRepository,
            EmployeeRepository employeeRepository,
            UserAccountRepository userAccountRepository,
            LeaveMapper leaveMapper) {
        this.leaveBalanceRepository = leaveBalanceRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.employeeRepository = employeeRepository;
        this.userAccountRepository = userAccountRepository;
        this.leaveMapper = leaveMapper;
    }

    @Transactional
    public LeaveBalance createLeaveBalance(Long empId, BalanceRequestDto payload) {
        Specification<LeaveBalance> spec = Specification.allOf(
                LeaveSpecs.hasEmployeeId(empId),
                CommonSpecs.fieldEquals("leaveType", payload.getLeaveType())
        );
        Optional<LeaveBalance> check = leaveBalanceRepository.findOne(spec);
        Employee emp = employeeRepository.findById(empId).orElseThrow(() -> new EmployeeNotFoundException(empId));
        if (check.isPresent()) {
            throw new DuplicateLeaveException(empId, payload.getLeaveType());
        }

        LeaveBalance create = leaveBalanceRepository.save(leaveMapper.toEntity(payload, emp));
        return create;
    }

    public List<BalanceResponseDto> getBalance(Long empId) {
        return leaveMapper.toBalanceResponsesDto(leaveBalanceRepository.findByEmployee_EmployeeId(empId));
    }


    @Transactional
    public void updateBalance(Long empId, UpdateBalanceDto payload) {
        Specification<LeaveBalance> spec = Specification.allOf(
                LeaveSpecs.hasEmployeeId(empId),
                CommonSpecs.fieldEquals("leaveType",  payload.getLeaveType())
        );
        LeaveBalance lb = leaveBalanceRepository.findOne(spec).orElseThrow(() -> new LeaveNotFoundException
                (
                "Employee Id: " + empId + " has no Leave Balance of type: " + payload.getLeaveType()
                        + " Please create a leave type " + payload.getLeaveType() + " for Employee " + empId
                )
        );

        if (payload.getType().equalsIgnoreCase("add")) {
            lb.setBalanceHours(lb.getBalanceHours().add(payload.getBalanceHours()));
        } else {
            if (lb.getBalanceHours().compareTo(payload.getBalanceHours()) < 0) {
                String message = "Balance hours for Employee Id: " + empId + " is insufficient."
                        + " Remaining Balance = " + lb.getBalanceHours() + ", "
                        + "Requested = " +  payload.getBalanceHours();
                throw new InsufficientBalanceException(message);
            }
            lb.setBalanceHours(lb.getBalanceHours().subtract(payload.getBalanceHours()));
        }
        leaveBalanceRepository.save(lb);
    }

    @Transactional
    public LeaveRequest createLeaveRequest(LeaveRequestDto payload, Long empId) {
        Employee emp =  employeeRepository.findById(empId).orElseThrow(() -> new EmployeeNotFoundException(empId));
        LeaveRequest request = new LeaveRequest();
        request.setRequestStatus(Status.PENDING);
        request.setEmployee(emp);
        request.setStartDate(payload.getStartDate());
        request.setEndDate(payload.getEndDate());
        request.setReason(payload.getReason());
        leaveRequestRepository.save(request);
        return request;
    }

    @Transactional
    public LeaveRequest updateLeaveRequest(LeaveRequest payload, Long requestId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserAccount user = userAccountRepository.findByUsername(authentication.getName()).orElseThrow(
                () -> new AccountNotFoundException(authentication.getName()));

        LeaveRequest request = leaveRequestRepository.findById(requestId).orElseThrow(
                () -> new LeaveNotFoundException("Leave Request of Id: " + requestId + " is not found."));
        request.setApproverReason(payload.getApproverReason());
        request.setApprover(user.getEmployee().getLastName() + ", " + user.getEmployee().getFirstName());
        leaveRequestRepository.save(request);
        return request;
    }
}
