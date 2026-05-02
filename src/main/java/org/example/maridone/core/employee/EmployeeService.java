package org.example.maridone.core.employee;

import org.example.maridone.annotation.ExecutionTime;
import org.example.maridone.config.PayrollConfig;
import org.example.maridone.core.bank.BankAccount;
import org.example.maridone.core.bank.BankService;
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
import org.example.maridone.enums.SeparationReason;
import org.example.maridone.enums.AccountStatus;
import org.example.maridone.leave.balance.LeaveBalance;
import org.example.maridone.leave.balance.LeaveBalanceRepository;
import org.example.maridone.leave.LeaveBalancePolicy;
import org.example.maridone.leave.request.LeaveRequest;
import org.example.maridone.leave.request.LeaveRequestRepository;
import org.example.maridone.enums.Status;
import org.example.maridone.overtime.OvertimeRequest;
import org.example.maridone.overtime.OvertimeRequestRepository;
import org.example.maridone.schedule.dto.ShiftResponseDto;
import org.example.maridone.schedule.mapper.ScheduleMapper;
import org.example.maridone.schedule.shift.ShiftService;
import org.example.maridone.schedule.shift.TemplateShiftRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private final EmployeeSeparationRepository employeeSeparationRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final OvertimeRequestRepository overtimeRequestRepository;
    private final BankService bankService;

    EmployeeService(EmployeeRepository employeeRepository,
                    CoreMapper coreMapper,
                    UserAccountRepository userAccountRepository,
                    TemplateShiftRepository templateShiftRepository,
                    ScheduleMapper scheduleMapper,
                    ShiftService shiftService,
                    PayrollConfig payrollConfig,
                    LeaveBalanceRepository leaveBalanceRepository,
                    EmployeeSeparationRepository employeeSeparationRepository,
                    LeaveRequestRepository leaveRequestRepository,
                    OvertimeRequestRepository overtimeRequestRepository,
                    BankService bankService) {
        this.employeeRepository = employeeRepository;
        this.coreMapper = coreMapper;
        this.userAccountRepository = userAccountRepository;
        this.templateShiftRepository = templateShiftRepository;
        this.scheduleMapper = scheduleMapper;
        this.shiftService = shiftService;
        this.payrollConfig = payrollConfig;
        this.leaveBalanceRepository = leaveBalanceRepository;
        this.employeeSeparationRepository = employeeSeparationRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.overtimeRequestRepository = overtimeRequestRepository;
        this.bankService = bankService;
    }


    @Transactional
    @ExecutionTime
    public EmployeeResponseDto createEmployee(EmployeeRequestDto employeeRequestDto) {
        Employee employee = coreMapper.toEmployee(employeeRequestDto);
        employee.setCompanyEmail(normalizeRequiredEmail(employee.getCompanyEmail(), "Company email is required."));
        employee.setPersonalEmail(normalizeOptionalEmail(employee.getPersonalEmail()));
        employee.setPhoneNumber(normalizePhoneNumber(employee.getPhoneNumber()));
        employee.setPosition(Optional.ofNullable(employee.getPosition()).orElse(Position.EMPLOYEE));
        employee.setEmploymentStatus(Optional.ofNullable(employee.getEmploymentStatus()).orElse(EmploymentStatus.REGULAR));
        employee.setEmploymentDateStart(Optional.ofNullable(employee.getEmploymentDateStart()).orElse(LocalDate.now()));
        employee.setEmploymentDateEnd(employeeRequestDto.getEmploymentDateEnd());
        employee.setExemptionStatus(Optional.ofNullable(employeeRequestDto.getExemptionStatus()).orElse(resolveExemptionStatus(employee.getPosition())));
        employee.setYearlySalary(Optional.ofNullable(employee.getYearlySalary()).orElse(BigDecimal.ZERO));
        applyDepartmentAssignment(employee, employeeRequestDto);
        employee.setManager(resolveAssignedManager(employee, employeeRequestDto, null));
        employeeRepository.save(employee);
        applyBankAccountUpdate(employee, employeeRequestDto);
        shiftService.createShifts(employee);
        seedDefaultLeaveBalances(employee);
        return toEmployeeResponse(employee);
    }

    @ExecutionTime
    public Page<EmployeeResponseDto> getAllEmployees(EmployeeFilter employeeFilter, Pageable pageable, String username) {
        Specification<Employee> specs = Specification.allOf(
                EmployeeSpecs.excludeSelf(username),
                EmployeeSpecs.hasFilters(employeeFilter));
        Page<Employee> employeePage = employeeRepository.findAll(specs, pageable);
        Map<Long, List<ShiftResponseDto>> schedulesByEmployeeId =
                findSchedulesByEmployeeId(employeePage.stream().map(Employee::getEmployeeId).toList());
        return employeePage.map(employee -> sanitizeSummaryResponse(toEmployeeResponse(employee, schedulesByEmployeeId)));
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
                .map(this::sanitizeSummaryResponse)
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

        Optional.ofNullable(updated.getGender())
                .filter(v -> !v.equals(emp.getGender()))
                .ifPresent(emp::setGender);

        Optional.ofNullable(updated.getEmploymentStatus())
                .filter(v -> !v.equals(emp.getEmploymentStatus()))
                .ifPresent(emp::setEmploymentStatus);

        Optional.ofNullable(updated.getCompanyEmail())
                .filter(v -> !v.equals(emp.getCompanyEmail()))
                .ifPresent(newEmail -> updateCompanyEmail(emp, newEmail));

        Optional.ofNullable(updated.getPersonalEmail())
                .filter(v -> !Objects.equals(v, emp.getPersonalEmail()))
                .ifPresent(newEmail -> updatePersonalEmail(emp, newEmail));

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

        Long previousManagerId = Optional.ofNullable(emp.getManager()).map(Employee::getEmployeeId).orElse(null);
        Employee resolvedManager = resolveAssignedManager(emp, updated, id);
        Long nextManagerId = Optional.ofNullable(resolvedManager).map(Employee::getEmployeeId).orElse(null);
        boolean managerChanged = !Objects.equals(previousManagerId, nextManagerId);
        if (managerChanged) {
            emp.setManager(resolvedManager);
        }

        Optional.ofNullable(updated.getAddress()).ifPresent(newAddr -> updateAddress(emp, newAddr));

        Optional.ofNullable(updated.getSssNumber()).ifPresent(emp::setSssNumber);
        Optional.ofNullable(updated.getPhilhealthNumber()).ifPresent(emp::setPhilhealthNumber);
        Optional.ofNullable(updated.getPagibigNumber()).ifPresent(emp::setPagibigNumber);
        Optional.ofNullable(updated.getTinNumber()).ifPresent(emp::setTinNumber);

        Optional.ofNullable(updated.getEmergencyContactName()).ifPresent(emp::setEmergencyContactName);
        Optional.ofNullable(updated.getEmergencyContactRelationship()).ifPresent(emp::setEmergencyContactRelationship);
        Optional.ofNullable(updated.getEmergencyContactNumber()).ifPresent(emp::setEmergencyContactNumber);

        if (managerChanged) {
            reroutePendingApprovals(emp);
        }

        employeeRepository.save(emp);
        applyBankAccountUpdate(emp, updated);

        return toEmployeeResponse(emp);
    }

    @Transactional
    @ExecutionTime
    public EmployeeResponseDto updateSelfEmployeeProfile(Long id, EmployeeRequestDto updated) {
        Employee emp = employeeRepository.findById(id).orElseThrow(() -> new EmployeeNotFoundException(id));
        assertSelfEditableFieldsOnly(updated);

        Optional.ofNullable(updated.getPersonalEmail())
                .filter(v -> !Objects.equals(v, emp.getPersonalEmail()))
                .ifPresent(newEmail -> updatePersonalEmail(emp, newEmail));

        Optional.ofNullable(updated.getPhoneNumber())
                .filter(v -> !v.equals(emp.getPhoneNumber()))
                .ifPresent(phone -> updatePhoneNumber(emp, phone));

        Optional.ofNullable(updated.getAddress()).ifPresent(newAddr -> updateAddress(emp, newAddr));

        Optional.ofNullable(updated.getSssNumber()).ifPresent(emp::setSssNumber);
        Optional.ofNullable(updated.getPhilhealthNumber()).ifPresent(emp::setPhilhealthNumber);
        Optional.ofNullable(updated.getPagibigNumber()).ifPresent(emp::setPagibigNumber);
        Optional.ofNullable(updated.getTinNumber()).ifPresent(emp::setTinNumber);

        Optional.ofNullable(updated.getEmergencyContactName()).ifPresent(emp::setEmergencyContactName);
        Optional.ofNullable(updated.getEmergencyContactRelationship()).ifPresent(emp::setEmergencyContactRelationship);
        Optional.ofNullable(updated.getEmergencyContactNumber()).ifPresent(emp::setEmergencyContactNumber);

        employeeRepository.save(emp);
        applyBankAccountUpdate(emp, updated);
        return toEmployeeResponse(emp);
    }

    @Transactional
    @ExecutionTime
    public EmployeeResponseDto updateStatus(Long id, EmployeeRequestDto updated, String actorUsername) {
        Employee emp = employeeRepository.findById(id).orElseThrow(() -> new EmployeeNotFoundException(id));
        if (updated.getEmploymentStatus() == null) {
            throw new InvalidActionException("Employment status is required.");
        }
        if (emp.getEmploymentStatus() == EmploymentStatus.TERMINATED
                && updated.getEmploymentStatus() == EmploymentStatus.TERMINATED) {
            throw new InvalidActionException("This employee is already terminated.");
        }

        emp.setEmploymentStatus(updated.getEmploymentStatus());
        if (updated.getEmploymentStatus() == EmploymentStatus.TERMINATED) {
            recordSeparation(emp, updated, actorUsername);
        }
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
        response.setManagerName(resolveManagerName(employee));
        response.setManagerHeadLabel(resolveManagerHeadLabel(employee));
        response.setUserAccountName(findUsername(employee.getEmployeeId()));
        response.setAccountStatus(findAccountStatus(employee.getEmployeeId()));
        response.setShiftSchedules(resolveShiftSchedules(employee.getEmployeeId(), schedulesByEmployeeId));
        applyLatestSeparation(response, employee);
        return response;
    }

    private EmployeeResponseDto sanitizeSummaryResponse(EmployeeResponseDto response) {
        response.setSssNumber(null);
        response.setPhilhealthNumber(null);
        response.setPagibigNumber(null);
        response.setTinNumber(null);
        response.setEmergencyContactName(null);
        response.setEmergencyContactRelationship(null);
        response.setEmergencyContactNumber(null);
        response.setEligibleForRehire(null);
        response.setSeparationReason(null);
        response.setSeparationNotes(null);
        response.setSeparationEffectiveDate(null);
        response.setSeparationProcessedBy(null);
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
        response.setManagerName(resolveManagerName(employee));
        response.setManagerHeadLabel(resolveManagerHeadLabel(employee));
        response.setUserAccountName(findUsername(employee.getEmployeeId()));
        response.setAccountStatus(findAccountStatus(employee.getEmployeeId()));
        applyBankAccounts(response, employee);
        applyLatestSeparation(response, employee);
        return response;
    }

    private void applyBankAccounts(EmployeeDetailsDto response, Employee employee) {
        if (response == null || employee == null || employee.getEmployeeId() == null) {
            return;
        }

        List<BankAccount> bankAccounts = bankService.getBankAccounts(employee.getEmployeeId());
        response.setBankAccounts(coreMapper.toBankAccountDtos(bankAccounts));
        if (bankAccounts.isEmpty()) {
            return;
        }

        BankAccount account = bankAccounts.get(0);
        response.setBankName(account.getBankName());
        response.setBankAccountNumber(account.getAccountNumber());
        response.setBankAccountHolder(formatEmployeeName(employee));
    }

    private void applyBankAccountUpdate(Employee employee, EmployeeRequestDto updated) {
        if (!hasBankAccountPayload(updated)) {
            return;
        }

        String bankName = normalizeOptionalText(updated.getBankName());
        String bankAccountNumber = normalizeOptionalText(updated.getBankAccountNumber());

        if (bankName == null && bankAccountNumber == null) {
            return;
        }

        if (bankName == null || bankAccountNumber == null) {
            throw new InvalidActionException("Bank name and bank account number are required when updating bank details.");
        }

        bankService.upsertPrimaryBankAccount(employee, bankName, bankAccountNumber);
    }

    private boolean hasBankAccountPayload(EmployeeRequestDto updated) {
        return updated != null
                && (updated.getBankName() != null
                || updated.getBankAccountNumber() != null);
    }

    private AccountStatus findAccountStatus(Long employeeId) {
        return userAccountRepository.findByEmployee_EmployeeId(employeeId)
                .map(userAccount -> userAccount.getAccountStatus())
                .orElse(null);
    }

    private void recordSeparation(Employee employee, EmployeeRequestDto request, String actorUsername) {
        if (request.getSeparationReason() == null) {
            throw new InvalidActionException("Separation reason is required when terminating an employee.");
        }
        if (request.getEligibleForRehire() == null) {
            throw new InvalidActionException("Eligible for rehire must be specified when terminating an employee.");
        }

        LocalDate effectiveDate = Optional.ofNullable(request.getEmploymentDateEnd()).orElse(LocalDate.now());
        employee.setEmploymentDateEnd(effectiveDate);

        EmployeeSeparation separation = new EmployeeSeparation();
        separation.setEmployee(employee);
        separation.setReason(request.getSeparationReason());
        separation.setEligibleForRehire(request.getEligibleForRehire());
        separation.setNotes(normalizeSeparationNotes(request.getSeparationNotes()));
        separation.setEffectiveDate(effectiveDate);
        separation.setProcessedBy(normalizeActorUsername(actorUsername));
        separation.setProcessedAt(LocalDateTime.now());
        employeeSeparationRepository.save(separation);
    }

    private void reroutePendingApprovals(Employee employee) {
        if (employee == null || employee.getEmployeeId() == null) {
            return;
        }

        Status targetStatus = employee.getManager() == null ? Status.PENDING_HR : Status.PENDING_MANAGER;

        reroutePendingLeaveRequests(employee.getEmployeeId(), targetStatus);
        reroutePendingOvertimeRequests(employee.getEmployeeId(), targetStatus);
    }

    private void reroutePendingLeaveRequests(Long employeeId, Status targetStatus) {
        List<LeaveRequest> pendingRequests = leaveRequestRepository.findByEmployee_EmployeeIdAndRequestStatusIn(
                employeeId,
                List.of(Status.PENDING, Status.PENDING_MANAGER, Status.PENDING_HR)
        );

        for (LeaveRequest request : pendingRequests) {
            if (request.getManagerApprovedAt() != null) {
                continue;
            }
            if (request.getRequestStatus() == targetStatus) {
                continue;
            }

            request.setRequestStatus(targetStatus);
            request.setManagerApprover(null);
            request.setManagerApproverReason(null);
            request.setManagerApprovedAt(null);
        }

        leaveRequestRepository.saveAll(pendingRequests);
    }

    private void reroutePendingOvertimeRequests(Long employeeId, Status targetStatus) {
        List<OvertimeRequest> pendingRequests = overtimeRequestRepository.findByEmployee_EmployeeIdAndRequestStatusIn(
                employeeId,
                List.of(Status.PENDING, Status.PENDING_MANAGER, Status.PENDING_HR)
        );

        for (OvertimeRequest request : pendingRequests) {
            if (request.getManagerApprovedAt() != null) {
                continue;
            }
            if (request.getRequestStatus() == targetStatus) {
                continue;
            }

            request.setRequestStatus(targetStatus);
            request.setManagerApprover(null);
            request.setManagerApproveReason(null);
            request.setManagerApprovedAt(null);
        }

        overtimeRequestRepository.saveAll(pendingRequests);
    }

    private String normalizeSeparationNotes(String notes) {
        if (notes == null) {
            return null;
        }
        String trimmed = notes.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeActorUsername(String actorUsername) {
        if (actorUsername == null) {
            return null;
        }
        String trimmed = actorUsername.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void applyLatestSeparation(EmployeeResponseDto response, Employee employee) {
        if (employee.getEmploymentStatus() != EmploymentStatus.TERMINATED) {
            return;
        }
        employeeSeparationRepository.findTopByEmployee_EmployeeIdOrderByProcessedAtDescSeparationIdDesc(employee.getEmployeeId())
                .ifPresent(separation -> {
                    response.setSeparationReason(separation.getReason());
                    response.setEligibleForRehire(separation.getEligibleForRehire());
                    response.setSeparationNotes(separation.getNotes());
                    response.setSeparationEffectiveDate(separation.getEffectiveDate());
                    response.setSeparationProcessedBy(separation.getProcessedBy());
                });
    }

    private void applyLatestSeparation(EmployeeDetailsDto response, Employee employee) {
        if (employee.getEmploymentStatus() != EmploymentStatus.TERMINATED) {
            return;
        }
        employeeSeparationRepository.findTopByEmployee_EmployeeIdOrderByProcessedAtDescSeparationIdDesc(employee.getEmployeeId())
                .ifPresent(separation -> {
                    response.setSeparationReason(separation.getReason());
                    response.setEligibleForRehire(separation.getEligibleForRehire());
                    response.setSeparationNotes(separation.getNotes());
                    response.setSeparationEffectiveDate(separation.getEffectiveDate());
                    response.setSeparationProcessedBy(separation.getProcessedBy());
                });
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

    private String normalizeRequiredEmail(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String normalizeOptionalEmail(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void updateCompanyEmail(Employee employee, String email) {
        employee.setCompanyEmail(normalizeRequiredEmail(email, "Company email is required."));
    }

    private void updatePersonalEmail(Employee employee, String email) {
        employee.setPersonalEmail(normalizeRequiredEmail(email, "Personal email is required."));
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
                        || updated.getGender() != null
                        || updated.getEmploymentStatus() != null
                        || updated.getExemptionStatus() != null
                        || updated.getEmploymentDateStart() != null
                        || updated.getEmploymentDateEnd() != null
                        || updated.getCompanyEmail() != null
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

    private String resolveManagerName(Employee employee) {
        Employee manager = employee == null ? null : employee.getManager();
        if (manager == null) {
            return null;
        }
        return formatEmployeeName(manager);
    }

    private String resolveManagerHeadLabel(Employee employee) {
        Employee manager = employee == null ? null : employee.getManager();
        if (manager == null) {
            return null;
        }

        if (manager.getEmpTitle() != null) {
            return formatManagerHeadLabel(manager.getEmpTitle());
        }

        Position position = manager.getPosition();
        if (position == null) {
            return null;
        }

        return formatEnumLabel(position.name());
    }

    private String formatManagerHeadLabel(ManagerTitle managerTitle) {
        return managerTitle == null ? null : formatEnumLabel(managerTitle.name());
    }

    private String formatEnumLabel(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        return Stream.of(raw.trim().split("_+"))
                .filter(part -> !part.isBlank())
                .map(part -> part.substring(0, 1) + part.substring(1).toLowerCase())
                .collect(Collectors.joining(" "));
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

    private Employee resolveAssignedManager(Employee employee, EmployeeRequestDto request, Long employeeId) {
        Position effectivePosition = Optional.ofNullable(request.getPosition()).orElse(employee.getPosition());

        if (effectivePosition == Position.MANAGER) {
            return resolveManager(request.getManagerId(), employeeId);
        }

        if (request.getDepartment() != null && !request.getDepartment().isBlank()) {
            return resolveDepartmentManager(request.getDepartment(), employeeId);
        }

        if (request.getManagerId() != null) {
            return resolveManager(request.getManagerId(), employeeId);
        }

        return employee == null ? null : employee.getManager();
    }

    private Employee resolveDepartmentManager(String department, Long employeeId) {
        ManagerTitle departmentTitle = ManagerTitle.fromDepartmentLabel(department);
        Employee manager = employeeRepository
                .findFirstByEmpTitleAndPositionAndEmploymentStatusNot(
                        departmentTitle,
                        Position.MANAGER,
                        EmploymentStatus.TERMINATED
                )
                .orElseThrow(() -> new IllegalArgumentException("No active manager is assigned to the selected department."));

        if (employeeId != null && employeeId.equals(manager.getEmployeeId())) {
            throw new IllegalArgumentException("Employee cannot be their own manager.");
        }

        return manager;
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
        sickLeave.setBalanceHours(
                LeaveBalancePolicy.isEligibleForDefaultCredits(employee, LocalDate.now())
                        ? payrollConfig.getSickLeaveHours()
                        : BigDecimal.ZERO
        );

        LeaveBalance vacationLeave = new LeaveBalance();
        vacationLeave.setEmployee(employee);
        vacationLeave.setLeaveType(LeaveType.VACATION_LEAVE);
        vacationLeave.setBalanceHours(
                LeaveBalancePolicy.isEligibleForDefaultCredits(employee, LocalDate.now())
                        ? payrollConfig.getVacationLeaveHours()
                        : BigDecimal.ZERO
        );

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
