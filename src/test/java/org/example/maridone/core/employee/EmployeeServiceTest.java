package org.example.maridone.core.employee;

import java.util.Optional;
import java.util.List;
import java.util.ArrayList;

import org.example.maridone.config.PayrollConfig;
import org.example.maridone.core.bank.BankService;
import org.example.maridone.core.dto.EmployeeRequestDto;
import org.example.maridone.core.dto.EmployeeResponseDto;
import org.example.maridone.core.mapper.CoreMapper;
import org.example.maridone.core.user.UserAccountRepository;
import org.example.maridone.core.user.UserAccountService;
import org.example.maridone.embeddable.Address;
import org.example.maridone.enums.EmploymentStatus;
import org.example.maridone.enums.ExemptionStatus;
import org.example.maridone.enums.Gender;
import org.example.maridone.enums.LeaveType;
import org.example.maridone.enums.ManagerTitle;
import org.example.maridone.enums.Position;
import org.example.maridone.enums.SeparationReason;
import org.example.maridone.exception.notfound.EmployeeNotFoundException;
import org.example.maridone.exception.badrequest.InvalidActionException;
import org.example.maridone.leave.balance.LeaveBalance;
import org.example.maridone.leave.balance.LeaveBalanceRepository;
import org.example.maridone.leave.request.LeaveRequest;
import org.example.maridone.leave.request.LeaveRequestRepository;
import org.example.maridone.overtime.OvertimeRequest;
import org.example.maridone.overtime.OvertimeRequestRepository;
import org.example.maridone.schedule.mapper.ScheduleMapper;
import org.example.maridone.schedule.shift.ShiftService;
import org.example.maridone.schedule.shift.TemplateShiftRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private CoreMapper coreMapper;

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private TemplateShiftRepository templateShiftRepository;

    @Mock
    private ScheduleMapper scheduleMapper;

    @Mock
    private ShiftService shiftService;

    @Mock
    private PayrollConfig payrollConfig;

    @Mock
    private LeaveBalanceRepository leaveBalanceRepository;

    @Mock
    private EmployeeSeparationRepository employeeSeparationRepository;

    @Mock
    private LeaveRequestRepository leaveRequestRepository;

    @Mock
    private OvertimeRequestRepository overtimeRequestRepository;

    @Mock
    private BankService bankService;

    @Mock
    private UserAccountService userAccountService;

    @InjectMocks
    private EmployeeService employeeService;

    private void stubScheduleDependencies() {
        when(templateShiftRepository.findAllByEmployeeIdIn(anyCollection())).thenReturn(List.of());
    }

    @Test
    void createEmployee_ShouldReturnResponseDto() {
        stubScheduleDependencies();
        EmployeeRequestDto requestDto = new EmployeeRequestDto();
        requestDto.setFirstName("Frieren");
        
        Employee employee = new Employee();
        ReflectionTestUtils.setField(employee, "employeeId", 1L);
        employee.setFirstName("Frieren");
        employee.setLastName("Test");
        employee.setCompanyEmail("frieren@example.com");
        employee.setPhoneNumber("09171234567");
        employee.setBirthDate(java.time.LocalDate.of(2000, 1, 1));
        employee.setPosition(Position.EMPLOYEE);

        EmployeeResponseDto responseDto = new EmployeeResponseDto();
        responseDto.setId(1L);
        responseDto.setFirstName("Frieren");

        when(coreMapper.toEmployee(any(EmployeeRequestDto.class))).thenReturn(employee);
        when(employeeRepository.save(any(Employee.class))).thenReturn(employee);
        when(coreMapper.toEmployeeResponse(any(Employee.class))).thenReturn(responseDto);
        when(userAccountRepository.findUsernameByEmployeeEmployeeId(anyLong())).thenReturn(Optional.empty());
        when(payrollConfig.getSickLeaveHours()).thenReturn(java.math.BigDecimal.valueOf(40));
        when(payrollConfig.getVacationLeaveHours()).thenReturn(java.math.BigDecimal.valueOf(40));

        EmployeeResponseDto result = employeeService.createEmployee(requestDto);

        Assertions.assertNotNull(result);
        Assertions.assertEquals(1L, result.getId()); 
        Assertions.assertEquals("Frieren", result.getFirstName());
        Assertions.assertNotNull(employee.getEmploymentDateStart());
        Assertions.assertEquals(ExemptionStatus.NON_EXEMPT, employee.getExemptionStatus());
        verify(employeeRepository).save(any(Employee.class));
        verify(userAccountService).createUserAccount(argThat(account ->
                account.getEmployeeId().equals(1L)
        ));
        verify(shiftService).createShifts(employee);
        verify(leaveBalanceRepository).saveAll(argThat(balances -> {
                    List<LeaveBalance> collected = new ArrayList<>();
                    balances.forEach(collected::add);
                    return collected.size() == 2 &&
                            collected.stream().map(LeaveBalance::getLeaveType).toList().containsAll(List.of(
                                LeaveType.SICK_LEAVE,
                                LeaveType.VACATION_LEAVE
                        )) &&
                            collected.stream().allMatch(balance -> balance.getBalanceHours().compareTo(java.math.BigDecimal.valueOf(40)) == 0);
                }
        ));
    }

    @Test
    void createEmployee_ShouldSeedZeroDefaultLeaveBalances_ForNewHire() {
        stubScheduleDependencies();
        EmployeeRequestDto requestDto = new EmployeeRequestDto();
        requestDto.setFirstName("Fern");
        requestDto.setEmploymentStatus(EmploymentStatus.NEW_HIRE);

        Employee employee = new Employee();
        ReflectionTestUtils.setField(employee, "employeeId", 2L);
        employee.setFirstName("Fern");
        employee.setLastName("Test");
        employee.setCompanyEmail("fern@example.com");
        employee.setPhoneNumber("09171234568");
        employee.setBirthDate(java.time.LocalDate.of(2001, 1, 1));
        employee.setPosition(Position.EMPLOYEE);
        employee.setEmploymentStatus(EmploymentStatus.NEW_HIRE);
        employee.setEmploymentDateStart(java.time.LocalDate.now());

        EmployeeResponseDto responseDto = new EmployeeResponseDto();
        responseDto.setId(2L);
        responseDto.setFirstName("Fern");

        when(coreMapper.toEmployee(any(EmployeeRequestDto.class))).thenReturn(employee);
        when(employeeRepository.save(any(Employee.class))).thenReturn(employee);
        when(coreMapper.toEmployeeResponse(any(Employee.class))).thenReturn(responseDto);
        when(userAccountRepository.findUsernameByEmployeeEmployeeId(anyLong())).thenReturn(Optional.empty());

        employeeService.createEmployee(requestDto);

        verify(leaveBalanceRepository).saveAll(argThat(balances -> {
                    List<LeaveBalance> collected = new ArrayList<>();
                    balances.forEach(collected::add);
                    return collected.size() == 2 &&
                            collected.stream().allMatch(balance -> balance.getBalanceHours().compareTo(java.math.BigDecimal.ZERO) == 0);
                }
        ));
    }

    @Test
    void getEmployee_ShouldReturnResponse_WhenFound() {
        stubScheduleDependencies();
        Long empId = 1L;
        Employee employee = new Employee();
        ReflectionTestUtils.setField(employee, "employeeId", empId);
        Employee manager = new Employee();
        manager.setEmpTitle(ManagerTitle.HEAD_OF_PEOPLE_OPERATIONS);
        employee.setManager(manager);

        EmployeeResponseDto responseDto = new EmployeeResponseDto();
        responseDto.setId(empId); 

        when(employeeRepository.findById(empId)).thenReturn(Optional.of(employee));
        when(coreMapper.toEmployeeResponse(employee)).thenReturn(responseDto);
        when(userAccountRepository.findUsernameByEmployeeEmployeeId(anyLong())).thenReturn(Optional.empty());

        EmployeeResponseDto result = employeeService.getEmployee(empId);

        Assertions.assertNotNull(result);
        Assertions.assertEquals(empId, result.getId()); 
        Assertions.assertEquals("People Operations Department", result.getDepartment());
    }

    @Test
    void getEmployee_ShouldThrowException_WhenNotFound() {
        Long empId = 99L;
        when(employeeRepository.findById(empId)).thenReturn(Optional.empty());

        Assertions.assertThrows(EmployeeNotFoundException.class, () -> {
            employeeService.getEmployee(empId);
        });
    }

    @Test
    void createEmployee_ShouldThrow_WhenPhoneNumberIsNotExactly11Digits() {
        EmployeeRequestDto requestDto = new EmployeeRequestDto();
        Employee employee = new Employee();
        employee.setFirstName("Frieren");
        employee.setLastName("Test");
        employee.setCompanyEmail("frieren@example.com");
        employee.setPhoneNumber("0917123456");
        employee.setBirthDate(java.time.LocalDate.of(2000, 1, 1));
        employee.setPosition(Position.EMPLOYEE);

        when(coreMapper.toEmployee(any(EmployeeRequestDto.class))).thenReturn(employee);

        IllegalArgumentException error = Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> employeeService.createEmployee(requestDto)
        );

        Assertions.assertEquals("Phone number must be exactly 11 digits.", error.getMessage());
        verify(employeeRepository, never()).save(any(Employee.class));
    }

    @Test
    void createEmployee_ShouldSkipDefaultLeaveBalances_ForSuspendedEmployee() {
        stubScheduleDependencies();
        EmployeeRequestDto requestDto = new EmployeeRequestDto();

        Employee employee = new Employee();
        ReflectionTestUtils.setField(employee, "employeeId", 8L);
        employee.setFirstName("Lucas");
        employee.setLastName("Suspended");
        employee.setCompanyEmail("lucas@example.com");
        employee.setPhoneNumber("09171234567");
        employee.setBirthDate(java.time.LocalDate.of(2000, 1, 1));
        employee.setPosition(Position.EMPLOYEE);
        employee.setEmploymentStatus(EmploymentStatus.SUSPENDED);

        EmployeeResponseDto responseDto = new EmployeeResponseDto();
        responseDto.setId(8L);

        when(coreMapper.toEmployee(any(EmployeeRequestDto.class))).thenReturn(employee);
        when(employeeRepository.save(any(Employee.class))).thenReturn(employee);
        when(coreMapper.toEmployeeResponse(any(Employee.class))).thenReturn(responseDto);
        when(userAccountRepository.findUsernameByEmployeeEmployeeId(anyLong())).thenReturn(Optional.empty());

        employeeService.createEmployee(requestDto);

        verify(leaveBalanceRepository, never()).saveAll(any());
    }

    @Test
    void createEmployee_ShouldAssignManagerDepartment_WhenPositionIsManager() {
        stubScheduleDependencies();
        EmployeeRequestDto requestDto = new EmployeeRequestDto();
        requestDto.setDepartment("People Operations Department");

        Employee employee = new Employee();
        ReflectionTestUtils.setField(employee, "employeeId", 18L);
        employee.setFirstName("Cecilia");
        employee.setLastName("Reyes");
        employee.setCompanyEmail("cecilia@example.com");
        employee.setPhoneNumber("09171234567");
        employee.setBirthDate(java.time.LocalDate.of(1990, 6, 18));
        employee.setPosition(Position.MANAGER);

        EmployeeResponseDto responseDto = new EmployeeResponseDto();
        responseDto.setId(18L);

        when(coreMapper.toEmployee(any(EmployeeRequestDto.class))).thenReturn(employee);
        when(employeeRepository.save(any(Employee.class))).thenReturn(employee);
        when(coreMapper.toEmployeeResponse(any(Employee.class))).thenReturn(responseDto);
        when(userAccountRepository.findUsernameByEmployeeEmployeeId(anyLong())).thenReturn(Optional.empty());
        when(payrollConfig.getSickLeaveHours()).thenReturn(java.math.BigDecimal.valueOf(40));
        when(payrollConfig.getVacationLeaveHours()).thenReturn(java.math.BigDecimal.valueOf(40));

        employeeService.createEmployee(requestDto);

        Assertions.assertEquals(ManagerTitle.HEAD_OF_PEOPLE_OPERATIONS, employee.getEmpTitle());
    }

    @Test
    void getTeamEmployees_ShouldReturnHandledEmployeesExcludingTerminated() {
        stubScheduleDependencies();
        Employee teamMember = new Employee();
        ReflectionTestUtils.setField(teamMember, "employeeId", 11L);
        teamMember.setFirstName("Team");
        teamMember.setLastName("Member");

        EmployeeResponseDto responseDto = new EmployeeResponseDto();
        responseDto.setId(11L);
        responseDto.setFirstName("Team");

        when(userAccountRepository.findEmployeeIdByUsername("manager1")).thenReturn(2L);
        when(employeeRepository.findByManager_EmployeeIdAndEmploymentStatusNotOrderByLastNameAscFirstNameAsc(
                2L,
                EmploymentStatus.TERMINATED
        )).thenReturn(List.of(teamMember));
        when(coreMapper.toEmployeeResponse(teamMember)).thenReturn(responseDto);
        when(userAccountRepository.findUsernameByEmployeeEmployeeId(11L)).thenReturn(Optional.of("team11"));

        List<EmployeeResponseDto> result = employeeService.getTeamEmployees("manager1");

        Assertions.assertEquals(1, result.size());
        Assertions.assertEquals(11L, result.get(0).getId());
        Assertions.assertEquals("team11", result.get(0).getUserAccountName());
    }

    @Test
    void updateStatus_ShouldThrow_WhenEmployeeAlreadyTerminated() {
        Employee employee = new Employee();
        ReflectionTestUtils.setField(employee, "employeeId", 14L);
        employee.setEmploymentStatus(EmploymentStatus.TERMINATED);

        EmployeeRequestDto requestDto = new EmployeeRequestDto();
        requestDto.setEmploymentStatus(EmploymentStatus.TERMINATED);

        when(employeeRepository.findById(14L)).thenReturn(Optional.of(employee));

        InvalidActionException error = Assertions.assertThrows(
                InvalidActionException.class,
                () -> employeeService.updateStatus(14L, requestDto, "hr.user")
        );

        Assertions.assertEquals("This employee is already terminated.", error.getMessage());
        verify(employeeRepository, never()).save(any(Employee.class));
    }

    @Test
    void updateStatus_ShouldRecordSeparation_WhenEmployeeIsTerminated() {
        stubScheduleDependencies();
        Employee employee = new Employee();
        ReflectionTestUtils.setField(employee, "employeeId", 35L);
        employee.setEmploymentStatus(EmploymentStatus.REGULAR);

        EmployeeRequestDto requestDto = new EmployeeRequestDto();
        requestDto.setEmploymentStatus(EmploymentStatus.TERMINATED);
        requestDto.setEmploymentDateEnd(java.time.LocalDate.of(2026, 4, 16));
        requestDto.setSeparationReason(SeparationReason.VOLUNTARY_RESIGNATION);
        requestDto.setEligibleForRehire(Boolean.TRUE);
        requestDto.setSeparationNotes("Cleared turnover requirements.");

        EmployeeResponseDto responseDto = new EmployeeResponseDto();
        responseDto.setId(35L);

        when(employeeRepository.findById(35L)).thenReturn(Optional.of(employee));
        when(employeeRepository.save(any(Employee.class))).thenReturn(employee);
        when(coreMapper.toEmployeeResponse(employee)).thenReturn(responseDto);
        when(userAccountRepository.findUsernameByEmployeeEmployeeId(35L)).thenReturn(Optional.empty());

        final EmployeeSeparation[] savedSeparation = new EmployeeSeparation[1];
        doAnswer(invocation -> {
            savedSeparation[0] = invocation.getArgument(0);
            return savedSeparation[0];
        }).when(employeeSeparationRepository).save(any(EmployeeSeparation.class));
        when(employeeSeparationRepository.findTopByEmployee_EmployeeIdOrderByProcessedAtDescSeparationIdDesc(35L))
                .thenAnswer(invocation -> Optional.ofNullable(savedSeparation[0]));

        EmployeeResponseDto result = employeeService.updateStatus(35L, requestDto, "hr.lead");

        Assertions.assertEquals(EmploymentStatus.TERMINATED, employee.getEmploymentStatus());
        Assertions.assertEquals(java.time.LocalDate.of(2026, 4, 16), employee.getEmploymentDateEnd());
        Assertions.assertEquals(SeparationReason.VOLUNTARY_RESIGNATION, result.getSeparationReason());
        Assertions.assertEquals(Boolean.TRUE, result.getEligibleForRehire());
        Assertions.assertEquals("Cleared turnover requirements.", result.getSeparationNotes());
        Assertions.assertEquals(java.time.LocalDate.of(2026, 4, 16), result.getSeparationEffectiveDate());
        Assertions.assertEquals("hr.lead", result.getSeparationProcessedBy());
        verify(employeeSeparationRepository).save(argThat(separation ->
                separation.getEmployee() == employee
                        && separation.getReason() == SeparationReason.VOLUNTARY_RESIGNATION
                        && Boolean.TRUE.equals(separation.getEligibleForRehire())
                        && "Cleared turnover requirements.".equals(separation.getNotes())
                        && java.time.LocalDate.of(2026, 4, 16).equals(separation.getEffectiveDate())
                        && "hr.lead".equals(separation.getProcessedBy())
        ));
    }

    @Test
    void updateStatus_ShouldTerminateWithoutSeparation_WhenNoSeparationDetailsProvided() {
        stubScheduleDependencies();
        Employee employee = new Employee();
        ReflectionTestUtils.setField(employee, "employeeId", 36L);
        employee.setEmploymentStatus(EmploymentStatus.REGULAR);

        EmployeeRequestDto requestDto = new EmployeeRequestDto();
        requestDto.setEmploymentStatus(EmploymentStatus.TERMINATED);

        EmployeeResponseDto responseDto = new EmployeeResponseDto();
        responseDto.setId(36L);
        responseDto.setEmploymentStatus(EmploymentStatus.TERMINATED);

        when(employeeRepository.findById(36L)).thenReturn(Optional.of(employee));
        when(employeeRepository.save(any(Employee.class))).thenReturn(employee);
        when(coreMapper.toEmployeeResponse(employee)).thenReturn(responseDto);
        when(userAccountRepository.findUsernameByEmployeeEmployeeId(36L)).thenReturn(Optional.empty());

        EmployeeResponseDto result = employeeService.updateStatus(36L, requestDto, "hr.user");

        Assertions.assertEquals(EmploymentStatus.TERMINATED, employee.getEmploymentStatus());
        Assertions.assertNotNull(employee.getEmploymentDateEnd());
        Assertions.assertEquals(EmploymentStatus.TERMINATED, result.getEmploymentStatus());
        verify(employeeSeparationRepository, never()).save(any(EmployeeSeparation.class));
        verify(employeeRepository).save(employee);
    }

    @Test
    void updateStatus_ShouldRequireCompleteSeparationDetails_WhenSeparationPayloadIsProvided() {
        Employee employee = new Employee();
        ReflectionTestUtils.setField(employee, "employeeId", 36L);
        employee.setEmploymentStatus(EmploymentStatus.REGULAR);

        EmployeeRequestDto requestDto = new EmployeeRequestDto();
        requestDto.setEmploymentStatus(EmploymentStatus.TERMINATED);
        requestDto.setEligibleForRehire(Boolean.FALSE);

        when(employeeRepository.findById(36L)).thenReturn(Optional.of(employee));

        InvalidActionException error = Assertions.assertThrows(
                InvalidActionException.class,
                () -> employeeService.updateStatus(36L, requestDto, "hr.user")
        );

        Assertions.assertEquals("Separation reason is required when terminating an employee.", error.getMessage());
        verify(employeeSeparationRepository, never()).save(any(EmployeeSeparation.class));
        verify(employeeRepository, never()).save(any(Employee.class));
    }

    @Test
    void updateSelfEmployeeProfile_ShouldChangeSelfEditableFields() {
        stubScheduleDependencies();
        Employee employee = new Employee();
        ReflectionTestUtils.setField(employee, "employeeId", 21L);
        employee.setBirthDate(java.time.LocalDate.of(1995, 2, 14));
        employee.setGender(Gender.FEMALE);
        employee.setCompanyEmail("old@company.example.com");
        employee.setPersonalEmail("old@example.com");
        employee.setPhoneNumber("09170000000");
        employee.setIsVerified(true);

        Address existingAddress = new Address();
        existingAddress.setCity("Makati");
        employee.setAddress(existingAddress);

        EmployeeRequestDto requestDto = new EmployeeRequestDto();
        requestDto.setBirthDate(java.time.LocalDate.of(1996, 3, 20));
        requestDto.setGender(Gender.NON_BINARY);
        requestDto.setPersonalEmail("new@example.com");
        requestDto.setPhoneNumber("09171234567");
        Address newAddress = new Address();
        newAddress.setCity("Pasig");
        requestDto.setAddress(newAddress);

        when(employeeRepository.findById(21L)).thenReturn(Optional.of(employee));
        when(employeeRepository.save(any(Employee.class))).thenReturn(employee);
        when(coreMapper.toEmployeeResponse(any(Employee.class))).thenReturn(new EmployeeResponseDto());
        when(userAccountRepository.findUsernameByEmployeeEmployeeId(anyLong())).thenReturn(Optional.empty());

        employeeService.updateSelfEmployeeProfile(21L, requestDto);

        Assertions.assertEquals(java.time.LocalDate.of(1996, 3, 20), employee.getBirthDate());
        Assertions.assertEquals(Gender.NON_BINARY, employee.getGender());
        Assertions.assertEquals("new@example.com", employee.getPersonalEmail());
        Assertions.assertEquals("09171234567", employee.getPhoneNumber());
        Assertions.assertEquals("Pasig", employee.getAddress().getCity());
        Assertions.assertFalse(employee.getIsVerified());
    }

    @Test
    void updateSelfEmployeeProfile_ShouldRejectCompanyEmail() {
        Employee employee = new Employee();
        ReflectionTestUtils.setField(employee, "employeeId", 23L);

        EmployeeRequestDto requestDto = new EmployeeRequestDto();
        requestDto.setCompanyEmail("new@company.example.com");

        when(employeeRepository.findById(23L)).thenReturn(Optional.of(employee));

        InvalidActionException error = Assertions.assertThrows(
                InvalidActionException.class,
                () -> employeeService.updateSelfEmployeeProfile(23L, requestDto)
        );

        Assertions.assertEquals("Company email is managed by HR and cannot be updated from self-service.", error.getMessage());
        verify(employeeRepository, never()).save(any(Employee.class));
    }

    @Test
    void updateSelfEmployeeProfile_ShouldRejectDuplicatePersonalEmail() {
        Employee employee = new Employee();
        ReflectionTestUtils.setField(employee, "employeeId", 24L);
        employee.setPersonalEmail("old@example.com");
        employee.setIsVerified(true);

        EmployeeRequestDto requestDto = new EmployeeRequestDto();
        requestDto.setPersonalEmail(" npdalisay@gmail.com ");

        when(employeeRepository.findById(24L)).thenReturn(Optional.of(employee));
        when(employeeRepository.existsByPersonalEmailIgnoreCaseAndEmployeeIdNot("npdalisay@gmail.com", 24L))
                .thenReturn(true);

        InvalidActionException error = Assertions.assertThrows(
                InvalidActionException.class,
                () -> employeeService.updateSelfEmployeeProfile(24L, requestDto)
        );

        Assertions.assertEquals("That personal email is already used by another employee profile.", error.getMessage());
        Assertions.assertEquals("old@example.com", employee.getPersonalEmail());
        Assertions.assertTrue(employee.getIsVerified());
        verify(employeeRepository, never()).save(any(Employee.class));
    }

    @Test
    void updateEmployee_ShouldClearManagerDepartment_WhenPositionChangesAwayFromManager() {
        stubScheduleDependencies();
        Employee employee = new Employee();
        ReflectionTestUtils.setField(employee, "employeeId", 30L);
        employee.setPosition(Position.MANAGER);
        employee.setEmpTitle(ManagerTitle.PRODUCTION_SUPERVISOR);

        EmployeeRequestDto requestDto = new EmployeeRequestDto();
        requestDto.setPosition(Position.EMPLOYEE);

        when(employeeRepository.findById(30L)).thenReturn(Optional.of(employee));
        when(employeeRepository.save(any(Employee.class))).thenReturn(employee);
        when(coreMapper.toEmployeeResponse(any(Employee.class))).thenReturn(new EmployeeResponseDto());
        when(userAccountRepository.findUsernameByEmployeeEmployeeId(anyLong())).thenReturn(Optional.empty());

        employeeService.updateEmployee(30L, requestDto);

        Assertions.assertNull(employee.getEmpTitle());
    }

    @Test
    void updateEmployee_ShouldReroutePendingRequests_WhenManagerChanges() {
        stubScheduleDependencies();
        Employee employee = new Employee();
        ReflectionTestUtils.setField(employee, "employeeId", 44L);
        employee.setPosition(Position.EMPLOYEE);

        Employee oldManager = new Employee();
        ReflectionTestUtils.setField(oldManager, "employeeId", 4L);
        oldManager.setPosition(Position.MANAGER);
        employee.setManager(oldManager);

        Employee newManager = new Employee();
        ReflectionTestUtils.setField(newManager, "employeeId", 8L);
        newManager.setPosition(Position.MANAGER);

        LeaveRequest pendingLeave = new LeaveRequest();
        pendingLeave.setRequestStatus(org.example.maridone.enums.Status.PENDING_HR);

        OvertimeRequest pendingOvertime = new OvertimeRequest();
        pendingOvertime.setRequestStatus(org.example.maridone.enums.Status.PENDING_HR);

        EmployeeRequestDto requestDto = new EmployeeRequestDto();
        requestDto.setManagerId(8L);

        when(employeeRepository.findById(44L)).thenReturn(Optional.of(employee));
        when(employeeRepository.findById(8L)).thenReturn(Optional.of(newManager));
        when(employeeRepository.save(any(Employee.class))).thenReturn(employee);
        when(coreMapper.toEmployeeResponse(any(Employee.class))).thenReturn(new EmployeeResponseDto());
        when(userAccountRepository.findUsernameByEmployeeEmployeeId(anyLong())).thenReturn(Optional.empty());
        when(leaveRequestRepository.findByEmployee_EmployeeIdAndRequestStatusIn(eq(44L), any()))
                .thenReturn(List.of(pendingLeave));
        when(overtimeRequestRepository.findByEmployee_EmployeeIdAndRequestStatusIn(eq(44L), any()))
                .thenReturn(List.of(pendingOvertime));

        employeeService.updateEmployee(44L, requestDto);

        Assertions.assertSame(newManager, employee.getManager());
        Assertions.assertEquals(org.example.maridone.enums.Status.PENDING_MANAGER, pendingLeave.getRequestStatus());
        Assertions.assertEquals(org.example.maridone.enums.Status.PENDING_MANAGER, pendingOvertime.getRequestStatus());
        verify(leaveRequestRepository).saveAll(List.of(pendingLeave));
        verify(overtimeRequestRepository).saveAll(List.of(pendingOvertime));
    }

    @Test
    void updateEmployee_ShouldResolveManagerFromDepartmentAndReroutePendingRequests() {
        stubScheduleDependencies();
        Employee employee = new Employee();
        ReflectionTestUtils.setField(employee, "employeeId", 52L);
        employee.setPosition(Position.EMPLOYEE);

        Employee oldManager = new Employee();
        ReflectionTestUtils.setField(oldManager, "employeeId", 5L);
        oldManager.setPosition(Position.MANAGER);
        employee.setManager(oldManager);

        Employee departmentManager = new Employee();
        ReflectionTestUtils.setField(departmentManager, "employeeId", 10L);
        departmentManager.setPosition(Position.MANAGER);
        departmentManager.setEmpTitle(ManagerTitle.HEAD_OF_PROCUREMENT);

        LeaveRequest pendingLeave = new LeaveRequest();
        pendingLeave.setRequestStatus(org.example.maridone.enums.Status.PENDING_MANAGER);

        OvertimeRequest pendingOvertime = new OvertimeRequest();
        pendingOvertime.setRequestStatus(org.example.maridone.enums.Status.PENDING_MANAGER);

        EmployeeRequestDto requestDto = new EmployeeRequestDto();
        requestDto.setDepartment("Procurements Department");

        when(employeeRepository.findById(52L)).thenReturn(Optional.of(employee));
        when(employeeRepository.findFirstByEmpTitleAndPositionAndEmploymentStatusNot(
                ManagerTitle.HEAD_OF_PROCUREMENT,
                Position.MANAGER,
                EmploymentStatus.TERMINATED
        )).thenReturn(Optional.of(departmentManager));
        when(employeeRepository.save(any(Employee.class))).thenReturn(employee);
        when(coreMapper.toEmployeeResponse(any(Employee.class))).thenReturn(new EmployeeResponseDto());
        when(userAccountRepository.findUsernameByEmployeeEmployeeId(anyLong())).thenReturn(Optional.empty());
        when(leaveRequestRepository.findByEmployee_EmployeeIdAndRequestStatusIn(eq(52L), any()))
                .thenReturn(List.of(pendingLeave));
        when(overtimeRequestRepository.findByEmployee_EmployeeIdAndRequestStatusIn(eq(52L), any()))
                .thenReturn(List.of(pendingOvertime));

        employeeService.updateEmployee(52L, requestDto);

        Assertions.assertSame(departmentManager, employee.getManager());
        Assertions.assertEquals(org.example.maridone.enums.Status.PENDING_MANAGER, pendingLeave.getRequestStatus());
        Assertions.assertEquals(org.example.maridone.enums.Status.PENDING_MANAGER, pendingOvertime.getRequestStatus());
        verify(leaveRequestRepository).saveAll(List.of(pendingLeave));
        verify(overtimeRequestRepository).saveAll(List.of(pendingOvertime));
    }

    @Test
    void updateEmployee_ShouldNotRerouteRequestsAlreadyApprovedByManager() {
        stubScheduleDependencies();
        Employee employee = new Employee();
        ReflectionTestUtils.setField(employee, "employeeId", 46L);
        employee.setPosition(Position.EMPLOYEE);

        Employee oldManager = new Employee();
        ReflectionTestUtils.setField(oldManager, "employeeId", 6L);
        oldManager.setPosition(Position.MANAGER);
        employee.setManager(oldManager);

        Employee newManager = new Employee();
        ReflectionTestUtils.setField(newManager, "employeeId", 9L);
        newManager.setPosition(Position.MANAGER);

        LeaveRequest forwardedLeave = new LeaveRequest();
        forwardedLeave.setRequestStatus(org.example.maridone.enums.Status.PENDING_HR);
        forwardedLeave.setManagerApprovedAt(java.time.Instant.now());

        OvertimeRequest forwardedOvertime = new OvertimeRequest();
        forwardedOvertime.setRequestStatus(org.example.maridone.enums.Status.PENDING_HR);
        forwardedOvertime.setManagerApprovedAt(java.time.Instant.now());

        EmployeeRequestDto requestDto = new EmployeeRequestDto();
        requestDto.setManagerId(9L);

        when(employeeRepository.findById(46L)).thenReturn(Optional.of(employee));
        when(employeeRepository.findById(9L)).thenReturn(Optional.of(newManager));
        when(employeeRepository.save(any(Employee.class))).thenReturn(employee);
        when(coreMapper.toEmployeeResponse(any(Employee.class))).thenReturn(new EmployeeResponseDto());
        when(userAccountRepository.findUsernameByEmployeeEmployeeId(anyLong())).thenReturn(Optional.empty());
        when(leaveRequestRepository.findByEmployee_EmployeeIdAndRequestStatusIn(eq(46L), any()))
                .thenReturn(List.of(forwardedLeave));
        when(overtimeRequestRepository.findByEmployee_EmployeeIdAndRequestStatusIn(eq(46L), any()))
                .thenReturn(List.of(forwardedOvertime));

        employeeService.updateEmployee(46L, requestDto);

        Assertions.assertEquals(org.example.maridone.enums.Status.PENDING_HR, forwardedLeave.getRequestStatus());
        Assertions.assertEquals(org.example.maridone.enums.Status.PENDING_HR, forwardedOvertime.getRequestStatus());
    }

    @Test
    void updateSelfEmployeeProfile_ShouldRejectRestrictedFields() {
        Employee employee = new Employee();
        ReflectionTestUtils.setField(employee, "employeeId", 22L);

        EmployeeRequestDto requestDto = new EmployeeRequestDto();
        requestDto.setPosition(Position.HR);

        when(employeeRepository.findById(22L)).thenReturn(Optional.of(employee));

        InvalidActionException error = Assertions.assertThrows(
                InvalidActionException.class,
                () -> employeeService.updateSelfEmployeeProfile(22L, requestDto)
        );

        Assertions.assertEquals("HR-managed employment details cannot be updated from self-service.", error.getMessage());
        verify(employeeRepository, never()).save(any(Employee.class));
    }
}
