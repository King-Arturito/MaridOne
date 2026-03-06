package org.example.maridone.overtime;

import org.example.maridone.annotation.ExecutionTime;
import org.example.maridone.common.CommonSpecs;
import org.example.maridone.core.employee.Employee;
import org.example.maridone.core.employee.EmployeeRepository;
import org.example.maridone.core.spec.EmployeeSpecs;
import org.example.maridone.enums.Status;
import org.example.maridone.exception.AccountNotFoundException;
import org.example.maridone.exception.InvalidRangeException;
import org.example.maridone.exception.OvertimeException;
import org.example.maridone.overtime.dto.OvertimeRequestDto;
import org.example.maridone.overtime.dto.OvertimeResponseDto;
import org.example.maridone.overtime.dto.OvertimeUpdateDto;
import org.example.maridone.overtime.mapper.OvertimeMapper;
import org.example.maridone.overtime.spec.OvertimeSpecs;
import org.hibernate.sql.ast.tree.expression.Over;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Service
public class OvertimeService {

    private final OvertimeRequestRepository overtimeRequestRepository;
    private final EmployeeRepository employeeRepository;
    private final OvertimeMapper overtimeMapper;

    public OvertimeService(OvertimeRequestRepository overtimeRequestRepository,
                           OvertimeMapper overtimeMapper,
                           EmployeeRepository employeeRepository) {
        this.overtimeRequestRepository = overtimeRequestRepository;
        this.overtimeMapper = overtimeMapper;
        this.employeeRepository = employeeRepository;
    }

    @Transactional
    @ExecutionTime
    public OvertimeRequest createOvertimeRequest(OvertimeRequestDto requestDto, String username) {
        Specification<Employee> spec = Specification.allOf(
                EmployeeSpecs.hasUserAccount(username)
        );
        Employee emp = employeeRepository.findOne(spec).orElseThrow(() -> new AccountNotFoundException(username));

        Specification<OvertimeRequest> checkConflicts = Specification.allOf(
                CommonSpecs.fieldEquals("employee", emp),
                OvertimeSpecs.checkOverlaps(requestDto.getStartTime(), requestDto.getEndTime())
        );
        if (overtimeRequestRepository.count(checkConflicts) > 0) {
            throw new InvalidRangeException("Conflicting Schedules found.");
        }
        OvertimeRequest request = overtimeMapper.toOvertimeRequest(requestDto);
        request.setEmployee(emp);

        return overtimeRequestRepository.save(request);
    }

    @Transactional
    @ExecutionTime
    public OvertimeResponseDto updateOvertimeRequest(OvertimeUpdateDto updateDto, String approverUsername) {
        OvertimeRequest req = overtimeRequestRepository.findById(updateDto.getOvertimeId()).orElseThrow(() -> new OvertimeException("Overtime Not Found"));
        if (req.getRequestStatus() != Status.PENDING) {
            throw new OvertimeException("Overtime Request Not Pending/Already Processed");
        }
        Specification<Employee> spec = Specification.allOf(
                EmployeeSpecs.hasUserAccount(approverUsername)
        );
        Employee emp = employeeRepository.findOne(spec).orElseThrow(() -> new AccountNotFoundException(approverUsername));
        req.setApprover(emp.getLastName() + ", " +  emp.getFirstName());
        req.setRequestStatus(updateDto.getUpdateStatus());
        req.setApprovedAt(Instant.now());
        req.setApproveReason(updateDto.getApproveReason());
        overtimeRequestRepository.save(req);
        OvertimeResponseDto response = overtimeMapper.toOvertimeResponseDto(req);
        response.setEmployeeId(req.getEmployeeId());
        response.setApproveReason(req.getApproveReason());
        return response;
    }

    @ExecutionTime
    public Page<OvertimeResponseDto> getAllOvertimeRequest(Pageable pageable) {
        Specification<OvertimeRequest> specs =  Specification.allOf(
                OvertimeSpecs.hasStatus(Status.PENDING)
        );
        Page<OvertimeRequest> requests = overtimeRequestRepository.findAll(specs, pageable);
        return requests.map(overtimeMapper::toOvertimeResponseDto);
    }


    @ExecutionTime
    public Page<OvertimeResponseDto> getMyOvertimeRequests(Long empId, Pageable pageable) {
        Specification<OvertimeRequest> specs =  Specification.allOf(
                OvertimeSpecs.hasStatus(Status.PENDING),
                OvertimeSpecs.hasEmployeeId(empId)
        );
        Page<OvertimeRequest> myRequests = overtimeRequestRepository.findAll(specs, pageable);
        return myRequests.map(overtimeMapper::toOvertimeResponseDto);
    }
}
