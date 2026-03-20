package org.example.maridone.testing;

import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.example.maridone.core.bank.BankAccount;
import org.example.maridone.core.bank.BankAccountRepository;
import org.example.maridone.core.employee.Employee;
import org.example.maridone.core.employee.EmployeeRepository;
import org.example.maridone.core.user.UserAccount;
import org.example.maridone.embeddable.Address;
import org.example.maridone.enums.AccountStatus;
import org.example.maridone.enums.DeductionType;
import org.example.maridone.enums.EarningsType;
import org.example.maridone.enums.EmploymentStatus;
import org.example.maridone.enums.ExemptionStatus;
import org.example.maridone.enums.LeaveType;
import org.example.maridone.enums.PayrollStatus;
import org.example.maridone.enums.Position;
import org.example.maridone.enums.RunType;
import org.example.maridone.enums.Status;
import org.example.maridone.leave.balance.LeaveBalance;
import org.example.maridone.leave.balance.LeaveBalanceRepository;
import org.example.maridone.leave.request.LeaveRequest;
import org.example.maridone.leave.request.LeaveRequestRepository;
import org.example.maridone.log.AttendanceLogRepository;
import org.example.maridone.log.attendance.AttendanceLog;
import org.example.maridone.notification.Notification;
import org.example.maridone.notification.NotificationRepository;
import org.example.maridone.overtime.OvertimeRequest;
import org.example.maridone.overtime.OvertimeRequestRepository;
import org.example.maridone.payroll.dispute.DisputeRequest;
import org.example.maridone.payroll.item.PayrollItem;
import org.example.maridone.payroll.item.component.DeductionsLine;
import org.example.maridone.payroll.item.component.EarningsLine;
import org.example.maridone.payroll.run.PayrollItemRepository;
import org.example.maridone.payroll.run.PayrollRun;
import org.example.maridone.payroll.run.PayrollRunRepository;
import org.example.maridone.schedule.calendar.CalendarRepository;
import org.example.maridone.schedule.calendar.CompanyCalendar;
import org.example.maridone.schedule.shift.TemplateShiftRepository;
import org.example.maridone.schedule.shift.TemplateShiftSchedule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.time.ZoneId;
import java.util.Map;
import java.util.Set;

@Component
@Profile("dev")
public class SampleRows {

    private static final ZoneId SEED_TIME_ZONE = ZoneId.of("Asia/Manila");

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private PayrollRunRepository payrollRunRepository;

    @Autowired
    private CalendarRepository calendarRepository;

    @Autowired
    private TemplateShiftRepository templateShiftRepository;

    @Autowired
    private BankAccountRepository bankAccountRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private LeaveRequestRepository leaveRequestRepository;

    @Autowired
    private LeaveBalanceRepository leaveBalanceRepository;

    @Autowired
    private AttendanceLogRepository attendanceLogRepository;

    @Autowired
    private OvertimeRequestRepository overtimeRequestRepository;

    @Autowired
    private PayrollItemRepository payrollItemRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;


    @PostConstruct
    public void init() {
        if (employeeRepository.count() == 0)
            sampleData();
    }

    public void sampleData() {
        // Create calendar events
        CompanyCalendar companyEvent = calendarEvents(new CompanyCalendar(), 1);
        CompanyCalendar companyEvent2 = calendarEvents(new CompanyCalendar(), 2);
        calendarRepository.save(companyEvent);
        calendarRepository.save(companyEvent2);


        PayrollRun payrollRun = new PayrollRun();
        payrollRun.setPayrollStatus(PayrollStatus.IN_PROGRESS);
        payrollRun.setRunType(RunType.REGULAR);
        payrollRun.setPeriodStart(LocalDate.now().minusDays(14));
        payrollRun.setPeriodEnd(LocalDate.now());
        payrollRun.setPeriodDescription("Test1");
        payrollRun = payrollRunRepository.save(payrollRun);

        List<PayrollItem> items = new ArrayList<>();

        // Create 10 sample employees
        for (int i = 1; i <= 10; i++) {
            // 1. Create and save employee first
            Employee emp = createEmployee(i);
            emp = employeeRepository.save(emp);

            // 2. Create and save child entities, linking back to employee
            createAndSaveBankAccounts(emp, i);
            createAndSaveNotifications(emp, i);
            createAndSaveLeaveRequests(emp, i);
            createAndSaveLeaveBalances(emp, i);
            createAndSaveShiftSchedules(emp, i);
            createAndSaveAttendanceLogs(emp, i);
            createAndSaveOvertimeRequests(emp, i);

            // 3. Create payroll item (will be saved via payrollRun cascade)
            PayrollItem item = createPayrollItem(emp, i, payrollRun);
            items.add(item);
        }

        // Save payroll run with all items
        payrollRun.setItems(items);
        payrollRunRepository.save(payrollRun);
    }

    private Employee createEmployee(int i) {
        Employee emp = new Employee();
        LocalDate birthDate = LocalDate.of(2000, 10, i + 1);
        Address address = new Address();

        // Set basic info
        emp.setFirstName("firstName" + i);
        if (i == 5 || i == 8) {
            emp.setMiddleName("middleName" + i);
            emp.setExemptionStatus(ExemptionStatus.EXEMPT);
        } else {
            emp.setExemptionStatus(ExemptionStatus.NON_EXEMPT);
        }
        emp.setLastName("lastName" + (i * 2));
        emp.setBirthDate(birthDate);
        emp.setEmploymentDateStart(LocalDate.now().minusDays(i * 10));
        emp.setYearlySalary(BigDecimal.valueOf(500000 * (i / 2.5)));

        if (i == 4) {
            emp.setEmploymentDateEnd(LocalDate.now().minusDays(i));
        }

        // Set employment status and position
        switch (i) {
            case 1:
                emp.setEmploymentStatus(EmploymentStatus.REGULAR);
                emp.setPosition(Position.MANAGEMENT);
                break;
            case 2:
                emp.setEmploymentStatus(EmploymentStatus.PART_TIME);
                emp.setPosition(Position.HR);
                break;
            case 3:
                emp.setEmploymentStatus(EmploymentStatus.SUSPENDED);
                emp.setPosition(Position.ACCOUNTING);
                break;
            case 4:
                emp.setEmploymentStatus(EmploymentStatus.TERMINATED);
                emp.setPosition(Position.EMPLOYEE);
                break;
            default:
                emp.setEmploymentStatus(EmploymentStatus.REGULAR);
                emp.setPosition(Position.EMPLOYEE);
        }

        emp.setEmail("email" + i + "@gmail.com");
        emp.setPhoneNumber("0901500009" + i);

        // Set address
        address.setPermanentAddress("permanentAddress" + i);
        address.setCity("city" + i);
        address.setState("state" + i);
        address.setZipCode("174" + i);
        address.setCountry("Philippines");

        if (i == 4) {
            address.setTemporaryAddress("temporaryAddress" + i);
        }

        emp.setAddress(address);

        // Create user account (saved via cascade)
        UserAccount userAccount = new UserAccount();
        userAccount.setAccountStatus(AccountStatus.ACTIVE);
        userAccount.setEmployee(emp);
        userAccount.setPasswordHash(passwordEncoder.encode("test"));
        userAccount.setUsername("userName" + i);
        emp.setUserAccount(userAccount);

        return emp;
    }

    private void createAndSaveBankAccounts(Employee emp, int i) {
        BankAccount bankAccount1 = new BankAccount();
        bankAccount1.setBankName("BPI");
        bankAccount1.setAccountNumber(String.valueOf(i));
        bankAccount1.setEmployee(emp);
        bankAccount1.setActive(i % 3 != 0);

        BankAccount bankAccount2 = new BankAccount();
        bankAccount2.setBankName("BDO");
        bankAccount2.setAccountNumber(String.valueOf(i * 10));
        bankAccount2.setEmployee(emp);
        bankAccount2.setActive(i % 6 != 0);

        bankAccountRepository.save(bankAccount1);
        bankAccountRepository.save(bankAccount2);
    }

    private void createAndSaveNotifications(Employee emp, int i) {
        List<Notification> notifications = new ArrayList<>();

        for (int j = 0; j < 5; j++) {
            Notification notification = new Notification();
            notification.setMessage("Sample notification message " + j);
            notification.setCreatedAt(Instant.now().minus(i, ChronoUnit.DAYS));
            notification.setReadStatus(j % 2 == 0);
            notification.setImportance(j == 2 ? "HIGH" : "LOW");
            notification.setEmployee(emp);
            notifications.add(notification);
        }

        notificationRepository.saveAll(notifications);
    }

    private void createAndSaveLeaveRequests(Employee emp, int i) {
        LeaveRequest leaveRequest = new LeaveRequest();
        leaveRequest.setEmployee(emp);
        leaveRequest.setStartDateTime(LocalDateTime.of(LocalDate.now(), LocalTime.of(23,30)));
        leaveRequest.setEndDateTime(LocalDateTime.of(LocalDate.now().plusDays(1), LocalTime.of(0,30)));
        leaveRequest.setRequestStatus(i % 2 == 0 ? Status.APPROVED : Status.PENDING);
        leaveRequest.setReason("Vacation request #" + i);

        leaveRequestRepository.save(leaveRequest);
    }

    private void createAndSaveLeaveBalances(Employee emp, int i) {
        LeaveType[] leaves = LeaveType.values();
        LeaveBalance leaveBalance = new LeaveBalance();
        leaveBalance.setEmployee(emp);
        leaveBalance.setBalanceHours(new BigDecimal(i * 24));
        leaveBalance.setLeaveType(leaves[i % 7]);

        leaveBalanceRepository.save(leaveBalance);
    }

    private void createAndSaveShiftSchedules(Employee emp, int i) {
        List<TemplateShiftSchedule> templateShiftSchedules = new ArrayList<>();

        for (int k = 1; k <= 7; k++) {
            TemplateShiftSchedule templateShiftSchedule = new TemplateShiftSchedule();
            templateShiftSchedule.setEmployee(emp);
            templateShiftSchedule.setDayOfWeek(DayOfWeek.of(k));
            // Changed to a 9-hour attendance window (8:00-17:00) since 1 unpaid lunch hour is included in attendance.
            templateShiftSchedule.setStartTime(LocalTime.of(8, 0));
            templateShiftSchedule.setEndTime(LocalTime.of(17, 0));
            templateShiftSchedules.add(templateShiftSchedule);
        }

        templateShiftRepository.saveAll(templateShiftSchedules);
    }

    private void createAndSaveAttendanceLogs(Employee emp, int i) {
        List<AttendanceLog> attendanceLogs = new ArrayList<>();

        if (i == 10) {
            seedNonExemptRuntimeAttendance(attendanceLogs, emp);
        } else if (i == 8) {
            seedExemptRuntimeAttendance(attendanceLogs, emp);
        }

        if (!attendanceLogs.isEmpty()) {
            attendanceLogRepository.saveAll(attendanceLogs);
        }
    }

    private void createAndSaveOvertimeRequests(Employee emp, int i) {
        List<OvertimeRequest> overtimeRequests = new ArrayList<>();

        if (i == 10) {
            // Runtime-visible approved overtime for the October non-exempt case.
            overtimeRequests.add(createApprovedOvertimeRequest(
                    emp,
                    LocalDate.of(2026, 10, 22),
                    LocalTime.of(18, 0),
                    LocalTime.of(20, 0),
                    "Seeded October OT for payroll runtime verification"
            ));
        }

        if (!overtimeRequests.isEmpty()) {
            overtimeRequestRepository.saveAll(overtimeRequests);
        }
    }

    private void seedNonExemptRuntimeAttendance(List<AttendanceLog> attendanceLogs, Employee emp) {
        // 15th payout: Jan 26-Feb 10, 2026. No holidays in the current PH cache, useful for >13-day coverage.
        addAttendanceRange(
                attendanceLogs,
                emp,
                LocalDate.of(2026, 1, 26),
                LocalDate.of(2026, 2, 10),
                Set.of(LocalDate.of(2026, 1, 30)),
                Map.of(
                        LocalDate.of(2026, 2, 2), new SeedAttendancePair(LocalTime.of(8, 30), LocalTime.of(17, 0))
                )
        );

        // 15th payout: Mar 26-Apr 10, 2026. Holiday-reduced window used for <13 deductible days.
        addAttendanceRange(
                attendanceLogs,
                emp,
                LocalDate.of(2026, 3, 26),
                LocalDate.of(2026, 4, 10),
                Set.of(
                        LocalDate.of(2026, 3, 28),
                        LocalDate.of(2026, 4, 3),
                        LocalDate.of(2026, 4, 9)
                ),
                Map.of(
                        LocalDate.of(2026, 3, 29), new SeedAttendancePair(LocalTime.of(8, 10), LocalTime.of(17, 0)),
                        LocalDate.of(2026, 4, 2), new SeedAttendancePair(LocalTime.of(8, 0), LocalTime.of(17, 0)),
                        LocalDate.of(2026, 4, 4), new SeedAttendancePair(LocalTime.of(8, 0), LocalTime.of(17, 0))
                )
        );

        // 30th payout: Oct 11-25, 2026. Clean no-holiday window for ordinary attendance deductions.
        addAttendanceRange(
                attendanceLogs,
                emp,
                LocalDate.of(2026, 10, 11),
                LocalDate.of(2026, 10, 25),
                Set.of(),
                Map.of(
                        LocalDate.of(2026, 10, 14), new SeedAttendancePair(LocalTime.of(8, 0), LocalTime.of(16, 0)),
                        LocalDate.of(2026, 10, 18), new SeedAttendancePair(LocalTime.of(8, 20), LocalTime.of(17, 0)),
                        // Overnight attendance to surface a visible night differential line in the October test run.
                        LocalDate.of(2026, 10, 20), new SeedAttendancePair(LocalTime.of(21, 0), LocalTime.of(6, 0), 1)
                )
        );
    }

    private void seedExemptRuntimeAttendance(List<AttendanceLog> attendanceLogs, Employee emp) {
        // Exempt runtime logs are seeded to show handbook behavior: whole-day absence may deduct, but lates do not.
        addAttendanceRange(
                attendanceLogs,
                emp,
                LocalDate.of(2026, 1, 26),
                LocalDate.of(2026, 2, 10),
                Set.of(LocalDate.of(2026, 2, 1)),
                Map.of(
                        LocalDate.of(2026, 2, 3), new SeedAttendancePair(LocalTime.of(9, 0), LocalTime.of(17, 0))
                )
        );

        addAttendanceRange(
                attendanceLogs,
                emp,
                LocalDate.of(2026, 3, 26),
                LocalDate.of(2026, 4, 10),
                Set.of(LocalDate.of(2026, 4, 8)),
                Map.of(
                        LocalDate.of(2026, 3, 27), new SeedAttendancePair(LocalTime.of(9, 30), LocalTime.of(17, 0))
                )
        );

        addAttendanceRange(
                attendanceLogs,
                emp,
                LocalDate.of(2026, 10, 11),
                LocalDate.of(2026, 10, 25),
                Set.of(),
                Map.of(
                        LocalDate.of(2026, 10, 16), new SeedAttendancePair(LocalTime.of(8, 45), LocalTime.of(17, 0))
                )
        );
    }

    private void addAttendanceRange(
            List<AttendanceLog> attendanceLogs,
            Employee emp,
            LocalDate startDate,
            LocalDate endDate,
            Set<LocalDate> absentDates,
            Map<LocalDate, SeedAttendancePair> overrides
    ) {
        LocalDate currentDate = startDate;
        while (!currentDate.isAfter(endDate)) {
            if (!absentDates.contains(currentDate)) {
                SeedAttendancePair pair = overrides.getOrDefault(
                        currentDate,
                        new SeedAttendancePair(LocalTime.of(8, 0), LocalTime.of(17, 0))
                );
                attendanceLogs.add(createAttendanceLog(emp, currentDate, pair.logIn(), "IN"));
                attendanceLogs.add(createAttendanceLog(emp, currentDate.plusDays(pair.logOutDayOffset()), pair.logOut(), "OUT"));
            }
            currentDate = currentDate.plusDays(1);
        }
    }

    private AttendanceLog createAttendanceLog(Employee emp, LocalDate workDate, LocalTime time, String direction) {
        AttendanceLog log = new AttendanceLog();
        log.setEmployeeId(emp.getEmployeeId());
        log.setTimestamp(workDate.atTime(time).atZone(SEED_TIME_ZONE).toInstant());
        log.setDirection(direction);
        return log;
    }

    private OvertimeRequest createApprovedOvertimeRequest(
            Employee emp,
            LocalDate workDate,
            LocalTime startTime,
            LocalTime endTime,
            String reason
    ) {
        OvertimeRequest request = new OvertimeRequest();
        request.setEmployee(emp);
        request.setRequestStatus(Status.APPROVED);
        request.setRequestAt(workDate.minusDays(1).atTime(9, 0).atZone(SEED_TIME_ZONE).toInstant());
        request.setWorkDate(workDate);
        request.setStartTime(workDate.atTime(startTime));
        request.setEndTime(workDate.atTime(endTime));
        request.setOvertimeType(EarningsType.OVERTIME);
        request.setReason(reason);
        request.setApprover("seed-admin");
        request.setApprovedAt(workDate.minusDays(1).atTime(10, 0).atZone(SEED_TIME_ZONE).toInstant());
        request.setApproveReason("Seeded approved overtime");
        return request;
    }

    private PayrollItem createPayrollItem(Employee emp, int i, PayrollRun payrollRun) {
        PayrollItem payrollItem = new PayrollItem();
        payrollItem.setEmployee(emp);
        payrollItem.setPayrollRun(payrollRun);
        payrollItem.setGrossPay(BigDecimal.valueOf(5000));
        payrollItem.setNetPay(BigDecimal.valueOf(3000));

        // Create deductions
        List<DeductionsLine> deductions = new ArrayList<>();
        for (int j = 0; j < 2; j++) {
            DeductionsLine deduction = new DeductionsLine();
            deduction.setPayrollItem(payrollItem);
            deduction.setAmount(BigDecimal.valueOf(1000));
            deduction.setDeductionType(j == 0 ? DeductionType.SSS : DeductionType.PHILHEALTH);
            deductions.add(deduction);
        }
        payrollItem.setDeductions(deductions);

        // Create earnings
        List<EarningsLine> earnings = new ArrayList<>();
        for (int j = 0; j < 5; j++) {
            EarningsLine earning = new EarningsLine();
            earning.setPayrollItem(payrollItem);
            earning.setAmount(BigDecimal.valueOf(1000));
            earning.setEarningsDate(LocalDate.now().minusDays(j));
            earning.setHours(BigDecimal.valueOf(5 + j));
            earning.setOvertime(false);
            earning.setRate(BigDecimal.valueOf(100));
            earnings.add(earning);
        }
        payrollItem.setEarnings(earnings);

        // Create dispute
        DisputeRequest dispute = new DisputeRequest();
        dispute.setSubject("Subject of Dispute " + i);
        if (i % 2 == 0) {
            dispute.setStatus(Status.PENDING);
        } else if (i == 5) {
            dispute.setStatus(Status.REJECTED);
        } else {
            dispute.setStatus(Status.APPROVED);
        }
        dispute.setCreatedAt(Instant.now());
        dispute.setReason("Reason of Dispute " + i);
        dispute.setUpdatedAt(null);
        dispute.setStatusReason(null);
        dispute.setPayrollItem(payrollItem);

        List<DisputeRequest> disputes = new ArrayList<>();
        disputes.add(dispute);
        payrollItem.setDisputes(disputes);

        return payrollItemRepository.save(payrollItem);
    }

    private CompanyCalendar calendarEvents(CompanyCalendar calendar, int i) {
        calendar.setIsActive(true);
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);

        if (i == 1) {
            calendar.setStartDate(now.minusMonths(1).toInstant());
            calendar.setEndDate(now.minusMonths(1).plusDays(2).toInstant());
            calendar.setTitle("Teambuilding");
        } else {
            calendar.setStartDate(now.minusMonths(i).toInstant());
            calendar.setEndDate(now.minusMonths(i).plusDays(3).toInstant());
            calendar.setTitle("Another event: " + i);
        }

        return calendar;
    }

    private record SeedAttendancePair(LocalTime logIn, LocalTime logOut, long logOutDayOffset) {
        private SeedAttendancePair(LocalTime logIn, LocalTime logOut) {
            this(logIn, logOut, 0);
        }
    }
}
