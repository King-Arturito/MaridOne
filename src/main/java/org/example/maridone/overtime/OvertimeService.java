package org.example.maridone.overtime;

import org.example.maridone.annotation.NotifyRole;
import org.example.maridone.annotation.ExecutionTime;
import org.example.maridone.annotation.Notify;
import org.example.maridone.common.CommonSpecs;
import org.example.maridone.config.DefaultConfig;
import org.example.maridone.core.employee.Employee;
import org.example.maridone.core.employee.EmployeeRepository;
import org.example.maridone.core.spec.EmployeeSpecs;
import org.example.maridone.core.user.UserAccount;
import org.example.maridone.core.user.UserAccountRepository;
import org.example.maridone.enums.Position;
import org.example.maridone.enums.Status;
import org.example.maridone.exception.conflict.OverlappingDatesException;
import org.example.maridone.exception.notfound.AccountNotFoundException;
import org.example.maridone.exception.unauthorized.InvalidActionException;
import org.example.maridone.exception.unauthorized.OvertimeException;
import org.example.maridone.overtime.dto.OvertimeRequestDto;
import org.example.maridone.overtime.dto.OvertimeResponseDto;
import org.example.maridone.overtime.dto.OvertimeUpdateDto;
import org.example.maridone.overtime.mapper.OvertimeMapper;
import org.example.maridone.overtime.spec.OvertimeSpecs;
import org.example.maridone.schedule.shift.TemplateShiftRepository;
import org.example.maridone.schedule.shift.TemplateShiftSchedule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
public class OvertimeService {

    private final OvertimeRequestRepository overtimeRequestRepository;
    private final EmployeeRepository employeeRepository;
    private final OvertimeMapper overtimeMapper;
    private final UserAccountRepository userAccountRepository;
    private final TemplateShiftRepository templateShiftRepository;
    private final DefaultConfig defaultConfig;

    public OvertimeService(OvertimeRequestRepository overtimeRequestRepository,
                           OvertimeMapper overtimeMapper,
                           EmployeeRepository employeeRepository,
                           UserAccountRepository userAccountRepository,
                           TemplateShiftRepository templateShiftRepository,
                           DefaultConfig defaultConfig) {
        this.overtimeRequestRepository = overtimeRequestRepository;
        this.overtimeMapper = overtimeMapper;
        this.employeeRepository = employeeRepository;
        this.userAccountRepository = userAccountRepository;
        this.templateShiftRepository = templateShiftRepository;
        this.defaultConfig = defaultConfig;
    }

    @Transactional
    @ExecutionTime
    @NotifyRole(message = "Overtime Requested", importance = "HIGH", targetRole = Position.HR)
    public OvertimeRequest createOvertimeRequest(OvertimeRequestDto requestDto, String username) {
        Specification<Employee> spec = Specification.allOf(
                EmployeeSpecs.hasUserAccount(username)
        );
        Employee emp = employeeRepository.findOne(spec).orElseThrow(() -> new AccountNotFoundException(username));
        validateRequestWindow(requestDto);
        validateOutsideShiftSchedule(emp, requestDto.getStartTime(), requestDto.getEndTime());

        Specification<OvertimeRequest> checkConflicts = Specification.allOf(
                CommonSpecs.fieldEquals("employee", emp),
                CommonSpecs.fieldNotEquals("requestStatus", Status.CANCELLED),
                CommonSpecs.fieldNotEquals("requestStatus", Status.REJECTED),
                OvertimeSpecs.checkOverlaps(requestDto.getStartTime(), requestDto.getEndTime())
        );
        if (overtimeRequestRepository.count(checkConflicts) > 0) {
            throw new OverlappingDatesException("Conflicting Schedules found.");
        }
        OvertimeRequest request = overtimeMapper.toOvertimeRequest(requestDto);
        request.setEmployee(emp);

        return overtimeRequestRepository.save(request);
    }

    @Transactional
    @ExecutionTime
    @Notify(message = "Your Overtime Request has been #{#result.requestStatus}", importance = "HIGH", targetEmployee = "#result.employeeId")
    public OvertimeResponseDto updateOvertimeRequest(OvertimeUpdateDto updateDto, String approverUsername) {
        OvertimeRequest req = overtimeRequestRepository.findById(updateDto.getOvertimeId()).orElseThrow(() -> new OvertimeException("Overtime Not Found"));
        if (req.getRequestStatus() != Status.PENDING) {
            throw new OvertimeException("Overtime Request Not Pending/Already Processed");
        }
        Employee emp = userAccountRepository.findByUsername(approverUsername)
                .map(UserAccount::getEmployee)
                .orElseThrow(() -> new AccountNotFoundException(approverUsername));

        Long requestEmployeeId = req.getEmployeeId() != null
                ? req.getEmployeeId()
                : req.getEmployee() != null
                    ? req.getEmployee().getEmployeeId()
                    : null;

        validateApproverAccess(emp, approverUsername, requestEmployeeId);

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
    public Page<OvertimeResponseDto> getAllOvertimeRequest(OvertimeFilter filter, Pageable pageable) {
        Specification<OvertimeRequest> specs =  Specification.allOf(
                OvertimeSpecs.filtered(filter)
        );
        Page<OvertimeRequest> requests = overtimeRequestRepository.findAll(specs, pageable);
        return requests.map(overtimeMapper::toOvertimeResponseDto);
    }

    @ExecutionTime
    public Page<OvertimeResponseDto> getTeamOvertimeRequests(OvertimeFilter filter, String username, Pageable pageable) {
        List<Long> handledEmployeeIds = resolveHandledEmployeeIds(username);
        if (handledEmployeeIds.isEmpty()) {
            return Page.empty(pageable);
        }

        Specification<OvertimeRequest> specs = Specification.allOf(
                OvertimeSpecs.filtered(filter),
                OvertimeSpecs.hasEmployeeIds(handledEmployeeIds)
        );
        return overtimeRequestRepository.findAll(specs, pageable).map(overtimeMapper::toOvertimeResponseDto);
    }


    @ExecutionTime
    public Page<OvertimeResponseDto> getMyOvertimeRequests(Long empId, Pageable pageable) {
        Specification<OvertimeRequest> specs =  Specification.allOf(
                OvertimeSpecs.hasEmployeeId(empId)
        );
        Page<OvertimeRequest> myRequests = overtimeRequestRepository.findAll(specs, pageable);
        return myRequests.map(overtimeMapper::toOvertimeResponseDto);
    }

    @Transactional
    @ExecutionTime
    @NotifyRole(message = "Overtime Request Cancelled.", targetRole = Position.HR)
    public void cancelRequest(Long overtimeId) {
        OvertimeRequest overtimeRequest =  overtimeRequestRepository.findById(overtimeId).orElseThrow(() -> new OvertimeException("Overtime Not Found"));
        if (overtimeRequest.getRequestStatus().equals(Status.APPROVED)) {
            throw new IllegalStateException("Cannot cancel. Overtime Request has been approved already.");
        }
        overtimeRequest.setRequestStatus(Status.CANCELLED);
        overtimeRequestRepository.save(overtimeRequest);

    }

    private List<Long> resolveHandledEmployeeIds(String username) {
        Long employeeId = userAccountRepository.findEmployeeIdByUsername(username);
        if (employeeId == null) {
            throw new AccountNotFoundException(username);
        }
        return employeeRepository.findHandledEmployeeIds(employeeId);
    }

    private void validateApproverAccess(Employee approver, String username, Long requestEmployeeId) {
        if (approver.getPosition() != Position.MANAGER) {
            return;
        }

        if (requestEmployeeId == null) {
            throw new InvalidActionException("The overtime request is missing its employee reference.");
        }

        List<Long> handledEmployeeIds = resolveHandledEmployeeIds(username);
        if (!handledEmployeeIds.contains(requestEmployeeId)) {
            throw new InvalidActionException("Managers can only review overtime requests from their own team.");
        }
    }

    private void validateRequestWindow(OvertimeRequestDto requestDto) {
        LocalDate workDate = requestDto.getWorkDate();
        LocalDateTime startTime = requestDto.getStartTime();
        LocalDateTime endTime = requestDto.getEndTime();

        if (workDate == null || startTime == null || endTime == null) {
            throw new OvertimeException("Overtime date and time window are required.");
        }
        if (!startTime.toLocalDate().equals(workDate)) {
            throw new OvertimeException("The overtime start date must match the selected work date.");
        }
        if (!endTime.isAfter(startTime)) {
            throw new OvertimeException("End date and time must be later than the start date and time.");
        }
        if (workDate.isBefore(LocalDate.now(defaultConfig.getTimeZone()))) {
            throw new OvertimeException("Overtime cannot be requested for past dates.");
        }

        LocalDate maxEndDate = startTime.toLocalDate().plusDays(1);
        if (endTime.toLocalDate().isAfter(maxEndDate)) {
            throw new OvertimeException("End date can only be up to the day after the selected start date.");
        }
        if (endTime.toLocalDate().isEqual(maxEndDate) && endTime.toLocalTime().isAfter(LocalTime.of(6, 0))) {
            throw new OvertimeException("If overtime ends the next day, the latest allowed end time is 6:00 AM.");
        }
    }

    private void validateOutsideShiftSchedule(Employee employee, LocalDateTime requestStart, LocalDateTime requestEnd) {
        TemplateShiftSchedule shiftSchedule = templateShiftRepository.findAllByEmployeeId(employee.getEmployeeId())
                .stream()
                .filter(schedule -> schedule.getDayOfWeek() == requestStart.getDayOfWeek())
                .findFirst()
                .orElseThrow(() -> new OvertimeException("Your work schedule is not set up yet, so overtime cannot be processed."));

        LocalDate shiftDate = requestStart.toLocalDate();
        LocalDateTime shiftStart = LocalDateTime.of(shiftDate, shiftSchedule.getStartTime());
        LocalDateTime shiftEnd = shiftSchedule.getEndTime().isAfter(shiftSchedule.getStartTime())
                || shiftSchedule.getEndTime().equals(shiftSchedule.getStartTime())
                ? LocalDateTime.of(shiftDate, shiftSchedule.getEndTime())
                : LocalDateTime.of(shiftDate.plusDays(1), shiftSchedule.getEndTime());

        boolean isOutsideShift = !requestEnd.isAfter(shiftStart) || !requestStart.isBefore(shiftEnd);
        if (!isOutsideShift) {
            throw new OvertimeException("Overtime can only be requested outside your scheduled shift.");
        }
    }
}
