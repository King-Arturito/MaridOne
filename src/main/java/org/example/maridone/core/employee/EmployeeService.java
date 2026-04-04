package org.example.maridone.core.employee;

import org.example.maridone.annotation.ExecutionTime;
import org.example.maridone.config.PayrollConfig;
import org.example.maridone.core.dto.EmployeeDetailsDto;
import org.example.maridone.core.dto.EmployeeRequestDto;
import org.example.maridone.core.dto.EmployeeResponseDto;
import org.example.maridone.core.mapper.CoreMapper;
import org.example.maridone.core.filter.EmployeeFilter;
import org.example.maridone.core.spec.EmployeeSpecs;
import org.example.maridone.core.user.UserAccountRepository;
import org.example.maridone.embeddable.Address;
import org.example.maridone.exception.notfound.AccountNotFoundException;
import org.example.maridone.exception.notfound.EmployeeNotFoundException;
import org.example.maridone.enums.EmploymentStatus;
import org.example.maridone.enums.ExemptionStatus;
import org.example.maridone.enums.ManagerTitle;
import org.example.maridone.enums.Position;
import org.example.maridone.schedule.dto.ShiftResponseDto;
import org.example.maridone.schedule.mapper.ScheduleMapper;
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

@Service
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final CoreMapper coreMapper;
    private final UserAccountRepository userAccountRepository;
    private final TemplateShiftRepository templateShiftRepository;
    private final ScheduleMapper scheduleMapper;
    private final PayrollConfig payrollConfig;

    EmployeeService(EmployeeRepository employeeRepository,
                    CoreMapper coreMapper,
                    UserAccountRepository userAccountRepository,
                    TemplateShiftRepository templateShiftRepository,
                    ScheduleMapper scheduleMapper,
                    PayrollConfig payrollConfig) {
        this.employeeRepository = employeeRepository;
        this.coreMapper = coreMapper;
        this.userAccountRepository = userAccountRepository;
        this.templateShiftRepository = templateShiftRepository;
        this.scheduleMapper = scheduleMapper;
        this.payrollConfig = payrollConfig;
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
        employee.setManager(resolveManager(employeeRequestDto.getManagerId(), null));
        employeeRepository.save(employee);
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
                .ifPresent(newEmail -> {
                    emp.setEmail(newEmail);
                    emp.setIsVerified(false);
                });

        Optional.ofNullable(updated.getPhoneNumber())
                .filter(v -> !v.equals(emp.getPhoneNumber()))
                .ifPresent(phone -> emp.setPhoneNumber(normalizePhoneNumber(phone)));

        Optional.ofNullable(updated.getPosition())
                .filter(v -> !v.equals(emp.getPosition()))
                .ifPresent(emp::setPosition);

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

        Optional.ofNullable(updated.getAddress()).ifPresent(newAddr -> {
            Address existing = Optional.ofNullable(emp.getAddress()).orElseGet(() -> {
                Address a = new Address();
                emp.setAddress(a);
                return a;
            });

            Optional.ofNullable(newAddr.getPermanentAddress())
                    .filter(v -> !v.equals(existing.getPermanentAddress()))
                    .ifPresent(existing::setPermanentAddress);

            Optional.ofNullable(newAddr.getTemporaryAddress())
                    .filter(v -> !v.equals(existing.getTemporaryAddress()))
                    .ifPresent(existing::setTemporaryAddress);

            Optional.ofNullable(newAddr.getCity())
                    .filter(v -> !v.equals(existing.getCity()))
                    .ifPresent(existing::setCity);

            Optional.ofNullable(newAddr.getState())
                    .filter(v -> !v.equals(existing.getState()))
                    .ifPresent(existing::setState);

            Optional.ofNullable(newAddr.getCountry())
                    .filter(v -> !v.equals(existing.getCountry()))
                    .ifPresent(existing::setCountry);

            Optional.ofNullable(newAddr.getZipCode())
                    .filter(v -> !v.equals(existing.getZipCode()))
                    .ifPresent(existing::setZipCode);
        });

        employeeRepository.save(emp);

        return toEmployeeResponse(emp);
    }

    @Transactional
    @ExecutionTime
    public EmployeeResponseDto updateStatus(Long id, EmployeeRequestDto updated) {
        Employee emp = employeeRepository.findById(id).orElseThrow(() -> new EmployeeNotFoundException(id));

        emp.setEmploymentStatus(updated.getEmploymentStatus());
        employeeRepository.save(emp);
        return toEmployeeResponse(emp);
    }

    private EmployeeResponseDto toEmployeeResponse(Employee employee) {
        return toEmployeeResponse(employee, findSchedulesByEmployeeId(List.of(employee.getEmployeeId())));
    }

    private EmployeeResponseDto toEmployeeResponse(Employee employee, Map<Long, List<ShiftResponseDto>> schedulesByEmployeeId) {
        EmployeeResponseDto response = coreMapper.toEmployeeResponse(employee);
        response.setDepartment(resolveDepartment(employee));
        response.setUserAccountName(findUsername(employee.getEmployeeId()));
        response.setShiftSchedules(resolveShiftSchedules(employee.getEmployeeId(), schedulesByEmployeeId));
        response.setGracePeriodMinutes(resolveGracePeriodMinutes());
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

    private Long resolveGracePeriodMinutes() {
        return Math.max(payrollConfig.getGracePeriod().toMinutes(), 0L);
    }

    private EmployeeDetailsDto toEmployeeDetails(Employee employee) {
        EmployeeDetailsDto response = coreMapper.toEmployeeDetailsDto(employee);
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

    private Long resolveCurrentEmployeeId(String username) {
        Long employeeId = userAccountRepository.findEmployeeIdByUsername(username);
        if (employeeId == null) {
            throw new AccountNotFoundException(username);
        }
        return employeeId;
    }

}
