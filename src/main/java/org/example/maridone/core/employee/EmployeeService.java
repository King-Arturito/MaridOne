package org.example.maridone.core.employee;

import org.example.maridone.annotation.ExecutionTime;
import org.example.maridone.config.PayrollConfig;
import org.example.maridone.core.dto.EmployeeDetailsDto;
import org.example.maridone.core.dto.EmployeeOptionDto;
import org.example.maridone.core.dto.EmployeeRequestDto;
import org.example.maridone.core.dto.EmployeeResponseDto;
import org.example.maridone.core.mapper.CoreMapper;
import org.example.maridone.core.filter.EmployeeFilter;
import org.example.maridone.core.spec.EmployeeSpecs;
import org.example.maridone.core.user.UserAccountRepository;
import org.example.maridone.embeddable.Address;
import org.example.maridone.exception.notfound.AccountNotFoundException;
import org.example.maridone.exception.notfound.EmployeeNotFoundException;
import org.example.maridone.exception.unauthorized.InvalidActionException;
import org.example.maridone.enums.EmploymentStatus;
import org.example.maridone.enums.ExemptionStatus;
import org.example.maridone.enums.LeaveType;
import org.example.maridone.enums.ManagerTitle;
import org.example.maridone.enums.Position;
import org.example.maridone.leave.balance.LeaveBalance;
import org.example.maridone.leave.balance.LeaveBalanceRepository;
import org.example.maridone.schedule.dto.ShiftResponseDto;
import org.example.maridone.schedule.mapper.ScheduleMapper;
import org.example.maridone.schedule.shift.ShiftService;
import org.example.maridone.schedule.shift.TemplateShiftRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final CoreMapper coreMapper;
    private final UserAccountRepository userAccountRepository;
    private final TemplateShiftRepository templateShiftRepository;
    private final ScheduleMapper scheduleMapper;
    private final ShiftService shiftService;
    private final PayrollConfig payrollConfig;
    private final LeaveBalanceRepository leaveBalanceRepository;

    EmployeeService(EmployeeRepository employeeRepository,
                    CoreMapper coreMapper,
                    UserAccountRepository userAccountRepository,
                    TemplateShiftRepository templateShiftRepository,
                    ScheduleMapper scheduleMapper,
                    ShiftService shiftService,
                    PayrollConfig payrollConfig,
                    LeaveBalanceRepository leaveBalanceRepository) {
        this.employeeRepository = employeeRepository;
        this.coreMapper = coreMapper;
        this.userAccountRepository = userAccountRepository;
        this.templateShiftRepository = templateShiftRepository;
        this.scheduleMapper = scheduleMapper;
        this.shiftService = shiftService;
        this.payrollConfig = payrollConfig;
        this.leaveBalanceRepository = leaveBalanceRepository;
    }


    @Transactional
    @ExecutionTime
    public EmployeeResponseDto createEmployee(EmployeeRequestDto employeeRequestDto) {
        Employee employee = coreMapper.toEmployee(employeeRequestDto);
        employee.setPhoneNumber(normalizePhoneNumber(employee.getPhoneNumber()));
        employee.setPosition(Optional.ofNullable(employee.getPosition()).orElse(Position.EMPLOYEE));
        employee.setEmploymentStatus(Optional.ofNullable(employee.getEmploymentStatus()).orElse(EmploymentStatus.REGULAR));
        employee.setEmploymentDateStart(Optional.ofNullable(employee.getEmploymentDateStart()).orElse(LocalDate.now()));
        employee.setEmploymentDateEnd(employeeRequestDto.getEmploymentDateEnd());
        employee.setExemptionStatus(Optional.ofNullable(employeeRequestDto.getExemptionStatus()).orElse(resolveExemptionStatus(employee.getPosition())));
        employee.setYearlySalary(Optional.ofNullable(employee.getYearlySalary()).orElse(BigDecimal.ZERO));
        applyDepartmentAssignment(employee, employeeRequestDto);
        employee.setManager(resolveManager(employeeRequestDto.getManagerId(), null));
        employeeRepository.save(employee);
        shiftService.createShifts(employee);
        seedDefaultLeaveBalances(employee);
        return toEmployeeResponse(employee);
    }

    @ExecutionTime
    public Page<EmployeeResponseDto> getAllEmployees(EmployeeFilter employeeFilter, Pageable pageable) {
        Specification<Employee> specs = EmployeeSpecs.hasFilters(employeeFilter);
        Page<Employee> employeePage = employeeRepository.findAll(specs, pageable);
        Map<Long, List<ShiftResponseDto>> schedulesByEmployeeId =
                findSchedulesByEmployeeId(employeePage.stream().map(Employee::getEmployeeId).toList());
        return employeePage.map(employee -> toEmployeeResponse(employee, schedulesByEmployeeId));
    }

    @ExecutionTime
    public List<EmployeeOptionDto> getEmployeeOptions() {
        return employeeRepository.findByEmploymentStatusNotOrderByLastNameAscFirstNameAsc(EmploymentStatus.TERMINATED)
                .stream()
                .map(this::toEmployeeOption)
                .toList();
    }

    @ExecutionTime
    public List<EmployeeResponseDto> getTeamEmployees(String username) {
        Long managerId = resolveCurrentEmployeeId(username);
        return employeeRepository
                .findByManager_EmployeeIdAndEmploymentStatusNotOrderByLastNameAscFirstNameAsc(
                        managerId,
                        EmploymentStatus.TERMINATED
                )
                .stream()
                .map(this::toEmployeeResponse)
                .toList();
    }

    @ExecutionTime
    public EmployeeResponseDto getEmployee(Long id) {
        Employee emp = employeeRepository.findById(id).orElseThrow(() -> new EmployeeNotFoundException(id));
        return toEmployeeResponse(emp);
    }

    @ExecutionTime
    public EmployeeDetailsDto getSelfEmployee(Long id) {
        Employee emp = employeeRepository.findById(id).orElseThrow(() -> new EmployeeNotFoundException(id));
        return toEmployeeDetails(emp);
    }

    @Transactional
    @ExecutionTime
    public EmployeeResponseDto updateEmployee(Long id, EmployeeRequestDto updated) {
        Employee emp = employeeRepository.findById(id).orElseThrow(() -> new EmployeeNotFoundException(id));

        Optional.ofNullable(updated.getFirstName())
                .filter(v -> !v.equals(emp.getFirstName()))
                .ifPresent(emp::setFirstName);

        Optional.ofNullable(updated.getMiddleName())
                .filter(v -> !v.equals(emp.getMiddleName()))
                .ifPresent(emp::setMiddleName);

        Optional.ofNullable(updated.getLastName())
                .filter(v -> !v.equals(emp.getLastName()))
                .ifPresent(emp::setLastName);

        Optional.ofNullable(updated.getBirthDate())
                .filter(v -> !v.equals(emp.getBirthDate()))
                .ifPresent(emp::setBirthDate);

        Optional.ofNullable(updated.getEmploymentStatus())
                .filter(v -> !v.equals(emp.getEmploymentStatus()))
                .ifPresent(emp::setEmploymentStatus);

        Optional.ofNullable(updated.getEmail())
                .filter(v -> !v.equals(emp.getEmail()))
                .ifPresent(newEmail -> updateEmail(emp, newEmail));

        Optional.ofNullable(updated.getPhoneNumber())
                .filter(v -> !v.equals(emp.getPhoneNumber()))
                .ifPresent(phone -> updatePhoneNumber(emp, phone));

        Optional.ofNullable(updated.getPosition())
                .filter(v -> !v.equals(emp.getPosition()))
                .ifPresent(emp::setPosition);

        applyDepartmentAssignment(emp, updated);

        Optional.ofNullable(updated.getExemptionStatus())
                .filter(v -> !v.equals(emp.getExemptionStatus()))
                .ifPresent(emp::setExemptionStatus);

        Optional.ofNullable(updated.getEmploymentDateStart())
                .filter(v -> !v.equals(emp.getEmploymentDateStart()))
                .ifPresent(emp::setEmploymentDateStart);

        Optional.ofNullable(updated.getEmploymentDateEnd())
                .filter(v -> !v.equals(emp.getEmploymentDateEnd()))
                .ifPresent(emp::setEmploymentDateEnd);

        Optional.ofNullable(updated.getYearlySalary())
                .filter(v -> !v.equals(emp.getYearlySalary()))
                .ifPresent(emp::setYearlySalary);

        Optional.ofNullable(updated.getManagerId())
                .filter(v -> emp.getManager() == null || !v.equals(emp.getManager().getEmployeeId()))
                .ifPresent(managerId -> emp.setManager(resolveManager(managerId, id)));

        Optional.ofNullable(updated.getAddress()).ifPresent(newAddr -> updateAddress(emp, newAddr));

        employeeRepository.save(emp);

        return toEmployeeResponse(emp);
    }

    @Transactional
    @ExecutionTime
    public EmployeeResponseDto updateSelfEmployeeProfile(Long id, EmployeeRequestDto updated) {
        Employee emp = employeeRepository.findById(id).orElseThrow(() -> new EmployeeNotFoundException(id));
        assertSelfEditableFieldsOnly(updated);

        Optional.ofNullable(updated.getEmail())
                .filter(v -> !v.equals(emp.getEmail()))
                .ifPresent(newEmail -> updateEmail(emp, newEmail));

        Optional.ofNullable(updated.getPhoneNumber())
                .filter(v -> !v.equals(emp.getPhoneNumber()))
                .ifPresent(phone -> updatePhoneNumber(emp, phone));

        Optional.ofNullable(updated.getAddress()).ifPresent(newAddr -> updateAddress(emp, newAddr));

        employeeRepository.save(emp);
        return toEmployeeResponse(emp);
    }

    @Transactional
    @ExecutionTime
    public EmployeeResponseDto updateStatus(Long id, EmployeeRequestDto updated) {
        Employee emp = employeeRepository.findById(id).orElseThrow(() -> new EmployeeNotFoundException(id));
        if (updated.getEmploymentStatus() == null) {
            throw new InvalidActionException("Employment status is required.");
        }
        if (emp.getEmploymentStatus() == EmploymentStatus.TERMINATED
                && updated.getEmploymentStatus() == EmploymentStatus.TERMINATED) {
            throw new InvalidActionException("This employee is already terminated.");
        }

        emp.setEmploymentStatus(updated.getEmploymentStatus());
        employeeRepository.save(emp);
        return toEmployeeResponse(emp);
    }

    private EmployeeResponseDto toEmployeeResponse(Employee employee) {
        return toEmployeeResponse(employee, findSchedulesByEmployeeId(List.of(employee.getEmployeeId())));
    }

    private EmployeeOptionDto toEmployeeOption(Employee employee) {
        return new EmployeeOptionDto(
                employee.getEmployeeId(),
                formatEmployeeName(employee),
                employee.getEmploymentStatus(),
                employee.getPosition(),
                resolveDepartment(employee)
        );
    }

    private EmployeeResponseDto toEmployeeResponse(Employee employee, Map<Long, List<ShiftResponseDto>> schedulesByEmployeeId) {
        EmployeeResponseDto response = coreMapper.toEmployeeResponse(employee);
        response.setDepartment(resolveDepartment(employee));
        response.setUserAccountName(findUsername(employee.getEmployeeId()));
        response.setShiftSchedules(resolveShiftSchedules(employee.getEmployeeId(), schedulesByEmployeeId));
        return response;
    }

    private Map<Long, List<ShiftResponseDto>> findSchedulesByEmployeeId(Collection<Long> employeeIds) {
        if (employeeIds == null || employeeIds.isEmpty()) {
            return Map.of();
        }

        return templateShiftRepository.findAllByEmployeeIdIn(employeeIds).stream()
                .map(scheduleMapper::toResponseDto)
                .collect(Collectors.groupingBy(ShiftResponseDto::getEmployeeId));
    }

    private List<ShiftResponseDto> resolveShiftSchedules(Long employeeId, Map<Long, List<ShiftResponseDto>> schedulesByEmployeeId) {
        return schedulesByEmployeeId.getOrDefault(employeeId, List.of());
    }

    private String formatEmployeeName(Employee employee) {
        return Stream.of(employee.getFirstName(), employee.getMiddleName(), employee.getLastName())
                .filter(part -> part != null && !part.isBlank())
                .collect(Collectors.joining(" "));
    }

    private EmployeeDetailsDto toEmployeeDetails(Employee employee) {
        EmployeeDetailsDto response = coreMapper.toEmployeeDetailsDto(employee);
        response.setDepartment(resolveDepartment(employee));
        response.setUserAccountName(findUsername(employee.getEmployeeId()));
        return response;
    }

    private String findUsername(Long employeeId) {
        return userAccountRepository.findUsernameByEmployeeEmployeeId(employeeId).orElse(null);
    }

    private String normalizePhoneNumber(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Phone number is required.");
        }
        String digitsOnly = value.replaceAll("\\D", "");
        if (digitsOnly.length() != 11) {
            throw new IllegalArgumentException("Phone number must be exactly 11 digits.");
        }
        return digitsOnly;
    }

    private void updateEmail(Employee employee, String email) {
        employee.setEmail(email);
        employee.setIsVerified(false);
    }

    private void updatePhoneNumber(Employee employee, String phoneNumber) {
        employee.setPhoneNumber(normalizePhoneNumber(phoneNumber));
    }

    private void updateAddress(Employee employee, Address incoming) {
        Address existing = Optional.ofNullable(employee.getAddress()).orElseGet(() -> {
            Address address = new Address();
            employee.setAddress(address);
            return address;
        });

        Optional.ofNullable(incoming.getPermanentAddress())
                .filter(v -> !v.equals(existing.getPermanentAddress()))
                .ifPresent(existing::setPermanentAddress);

        Optional.ofNullable(incoming.getTemporaryAddress())
                .filter(v -> !v.equals(existing.getTemporaryAddress()))
                .ifPresent(existing::setTemporaryAddress);

        Optional.ofNullable(incoming.getCity())
                .filter(v -> !v.equals(existing.getCity()))
                .ifPresent(existing::setCity);

        Optional.ofNullable(incoming.getState())
                .filter(v -> !v.equals(existing.getState()))
                .ifPresent(existing::setState);

        Optional.ofNullable(incoming.getCountry())
                .filter(v -> !v.equals(existing.getCountry()))
                .ifPresent(existing::setCountry);

        Optional.ofNullable(incoming.getZipCode())
                .filter(v -> !v.equals(existing.getZipCode()))
                .ifPresent(existing::setZipCode);
    }

    private void assertSelfEditableFieldsOnly(EmployeeRequestDto updated) {
        boolean hasRestrictedField =
                updated.getFirstName() != null
                        || updated.getMiddleName() != null
                        || updated.getLastName() != null
                        || updated.getBirthDate() != null
                        || updated.getEmploymentStatus() != null
                        || updated.getExemptionStatus() != null
                        || updated.getEmploymentDateStart() != null
                        || updated.getEmploymentDateEnd() != null
                        || updated.getPosition() != null
                        || updated.getDepartment() != null
                        || updated.getYearlySalary() != null
                        || updated.getManagerId() != null;

        if (hasRestrictedField) {
            throw new InvalidActionException("You can only update your contact information and address.");
        }
    }

    private ExemptionStatus resolveExemptionStatus(Position position) {
        return switch (position) {
            case EMPLOYEE, ACCOUNTING, UNKNOWN -> ExemptionStatus.NON_EXEMPT;
            default -> ExemptionStatus.EXEMPT;
        };
    }

    private String resolveDepartment(Employee employee) {
        Employee current = employee;
        while (current != null) {
            ManagerTitle title = current.getEmpTitle();
            if (title != null) {
                return title.getDisplayName();
            }
            current = current.getManager();
        }
        return null;
    }

    private void applyDepartmentAssignment(Employee employee, EmployeeRequestDto request) {
        Position effectivePosition = Optional.ofNullable(request.getPosition()).orElse(employee.getPosition());

        if (effectivePosition == Position.MANAGER) {
            if (request.getDepartment() != null && !request.getDepartment().isBlank()) {
                employee.setEmpTitle(ManagerTitle.fromDepartmentLabel(request.getDepartment()));
                return;
            }

            if (employee.getEmpTitle() == null) {
                throw new IllegalArgumentException("Department is required when position is MANAGER.");
            }
            return;
        }

        if (request.getPosition() != null) {
            employee.setEmpTitle(null);
        }
    }

    private Employee resolveManager(Long managerId, Long employeeId) {
        if (managerId == null) {
            return null;
        }
        if (employeeId != null && employeeId.equals(managerId)) {
            throw new IllegalArgumentException("Employee cannot be their own manager.");
        }
        return employeeRepository.findById(managerId)
                .orElseThrow(() -> new EmployeeNotFoundException(managerId));
    }

    private void seedDefaultLeaveBalances(Employee employee) {
        if (employee == null || employee.getEmployeeId() == null) {
            return;
        }

        if (employee.getEmploymentStatus() == EmploymentStatus.TERMINATED
                || employee.getEmploymentStatus() == EmploymentStatus.SUSPENDED) {
            return;
        }

        LeaveBalance sickLeave = new LeaveBalance();
        sickLeave.setEmployee(employee);
        sickLeave.setLeaveType(LeaveType.SICK_LEAVE);
        sickLeave.setBalanceHours(payrollConfig.getSickLeaveHours());

        LeaveBalance vacationLeave = new LeaveBalance();
        vacationLeave.setEmployee(employee);
        vacationLeave.setLeaveType(LeaveType.VACATION_LEAVE);
        vacationLeave.setBalanceHours(payrollConfig.getVacationLeaveHours());

        leaveBalanceRepository.saveAll(List.of(sickLeave, vacationLeave));
    }

    private Long resolveCurrentEmployeeId(String username) {
        Long employeeId = userAccountRepository.findEmployeeIdByUsername(username);
        if (employeeId == null) {
            throw new AccountNotFoundException(username);
        }
        return employeeId;
    }

}
