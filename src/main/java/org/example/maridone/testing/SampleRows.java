package org.example.maridone.testing;

import jakarta.annotation.PostConstruct;
import org.example.maridone.core.bank.BankAccount;
import org.example.maridone.core.bank.BankAccountRepository;
import org.example.maridone.core.employee.Employee;
import org.example.maridone.core.employee.EmployeeRepository;
import org.example.maridone.core.user.UserAccount;
import org.example.maridone.embeddable.Address;
import org.example.maridone.enums.AccountStatus;
import org.example.maridone.enums.EmploymentStatus;
import org.example.maridone.enums.ExemptionStatus;
import org.example.maridone.enums.LeaveType;
import org.example.maridone.enums.Position;
import org.example.maridone.leave.balance.LeaveBalance;
import org.example.maridone.leave.balance.LeaveBalanceRepository;
import org.example.maridone.log.AttendanceLogRepository;
import org.example.maridone.log.attendance.AttendanceLog;
import org.example.maridone.notification.Notification;
import org.example.maridone.notification.NotificationRepository;
import org.example.maridone.schedule.calendar.CalendarRepository;
import org.example.maridone.schedule.calendar.CompanyCalendar;
import org.example.maridone.schedule.shift.TemplateShiftRepository;
import org.example.maridone.schedule.shift.TemplateShiftSchedule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;

@Component
@Profile("dev")
public class SampleRows {

    private static final ZoneId SEED_TIME_ZONE = ZoneId.of("Asia/Manila");
    private static final LocalTime SHIFT_START = LocalTime.of(8, 0);
    private static final LocalTime SHIFT_END = LocalTime.of(17, 0);

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private CalendarRepository calendarRepository;

    @Autowired
    private TemplateShiftRepository templateShiftRepository;

    @Autowired
    private BankAccountRepository bankAccountRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private LeaveBalanceRepository leaveBalanceRepository;

    @Autowired
    private AttendanceLogRepository attendanceLogRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @PostConstruct
    public void init() {
        if (employeeRepository.count() == 0) {
            sampleData();
        }
    }

    public void sampleData() {
        List<Employee> employees = createEmployees();

        assignManagers(employees);
        createAndSaveCompanyCalendar(employees);
        createAndSaveUserAccounts(employees);
        createAndSaveBankAccounts(employees);
        createAndSaveShiftSchedules(employees);
        createAndSaveLeaveBalances(employees);
        createAndSaveNotifications(employees);
        createAndSaveAttendanceLogs(employees);
    }

    private List<Employee> createEmployees() {
        List<Employee> employees = new ArrayList<>();
        for (EmployeeSeed seed : employeeSeeds()) {
            employees.add(employeeRepository.save(toEmployee(seed)));
        }
        return employees;
    }

    private Employee toEmployee(EmployeeSeed seed) {
        Employee employee = new Employee();
        employee.setFirstName(seed.firstName());
        employee.setMiddleName(seed.middleName());
        employee.setLastName(seed.lastName());
        employee.setBirthDate(seed.birthDate());
        employee.setEmploymentDateStart(seed.employmentDateStart());
        employee.setEmploymentDateEnd(seed.employmentDateEnd());
        employee.setYearlySalary(seed.yearlySalary());
        employee.setEmail(seed.email());
        employee.setIsVerified(seed.isVerified());
        employee.setPhoneNumber(seed.phoneNumber());
        employee.setEmploymentStatus(seed.employmentStatus());
        employee.setExemptionStatus(seed.exemptionStatus());
        employee.setPosition(seed.position());
        employee.setAddress(toAddress(seed));
        return employee;
    }

    private Address toAddress(EmployeeSeed seed) {
        Address address = new Address();
        address.setPermanentAddress(seed.permanentAddress());
        address.setTemporaryAddress(seed.temporaryAddress());
        address.setCity(seed.city());
        address.setState(seed.state());
        address.setCountry(seed.country());
        address.setZipCode(seed.zipCode());
        return address;
    }

    private void assignManagers(List<Employee> employees) {
        Employee cecilia = employees.get(10);
        Employee daniel = employees.get(11);

        employees.get(4).setManager(cecilia);
        employees.get(7).setManager(cecilia);
        employees.get(8).setManager(daniel);
        employees.get(9).setManager(daniel);

        employeeRepository.saveAll(List.of(
                employees.get(4),
                employees.get(7),
                employees.get(8),
                employees.get(9)
        ));
    }

    private void createAndSaveCompanyCalendar(List<Employee> employees) {
        List<CompanyCalendar> events = new ArrayList<>();
        for (CalendarSeed seed : calendarSeeds()) {
            CompanyCalendar event = new CompanyCalendar();
            event.setTitle(seed.title());
            event.setDescription(seed.description());
            event.setStartDate(toInstant(seed.startDateTime()));
            event.setEndDate(toInstant(seed.endDateTime()));
            event.setIsActive(true);
            event.setEmployee(employees.get(seed.authorIndex()));
            event.setCreatedAt(toInstant(seed.createdAt()));
            events.add(event);
        }
        calendarRepository.saveAll(events);
    }

    private void createAndSaveUserAccounts(List<Employee> employees) {
        for (Employee employee : employees) {
            UserAccount userAccount = new UserAccount();
            userAccount.setEmployee(employee);
            userAccount.setUsername(employee.getFirstName().toLowerCase(Locale.ROOT) + employee.getEmployeeId());
            userAccount.setPasswordHash(passwordEncoder.encode("test"));
            userAccount.setAccountStatus(AccountStatus.ACTIVE);
            userAccount.setTwoFactorEnabled(false);
            userAccount.setTwoFactorSecret(null);
            employee.setUserAccount(userAccount);
            employeeRepository.save(employee);
        }
    }

    private void createAndSaveBankAccounts(List<Employee> employees) {
        List<BankAccount> accounts = new ArrayList<>();
        for (Employee employee : employees) {
            accounts.add(createBankAccount(employee, "100-800-", "Bank Alpha"));
            accounts.add(createBankAccount(employee, "200-900-", "Bank Beta"));
        }
        bankAccountRepository.saveAll(accounts);
    }

    private BankAccount createBankAccount(Employee employee, String prefix, String bankName) {
        BankAccount account = new BankAccount();
        account.setEmployee(employee);
        account.setAccountNumber(prefix + String.format("%04d", employee.getEmployeeId()));
        account.setBankName(bankName);
        account.setActive(true);
        return account;
    }

    private void createAndSaveShiftSchedules(List<Employee> employees) {
        List<TemplateShiftSchedule> schedules = new ArrayList<>();
        for (Employee employee : employees) {
            for (DayOfWeek dayOfWeek : DayOfWeek.values()) {
                TemplateShiftSchedule schedule = new TemplateShiftSchedule();
                schedule.setEmployee(employee);
                schedule.setDayOfWeek(dayOfWeek);
                schedule.setStartTime(SHIFT_START);
                schedule.setEndTime(SHIFT_END);
                schedules.add(schedule);
            }
        }
        templateShiftRepository.saveAll(schedules);
    }

    private void createAndSaveLeaveBalances(List<Employee> employees) {
        List<LeaveBalance> leaveBalances = new ArrayList<>();
        for (Employee employee : employees) {
            for (LeaveType leaveType : LeaveType.values()) {
                LeaveBalance leaveBalance = new LeaveBalance();
                leaveBalance.setEmployee(employee);
                leaveBalance.setLeaveType(leaveType);
                leaveBalance.setBalanceHours(BigDecimal.valueOf(6.00));
                leaveBalances.add(leaveBalance);
            }
        }
        leaveBalanceRepository.saveAll(leaveBalances);
    }

    private void createAndSaveNotifications(List<Employee> employees) {
        List<Notification> notifications = new ArrayList<>();
        ZonedDateTime base = ZonedDateTime.of(2026, 4, 1, 8, 0, 0, 0, SEED_TIME_ZONE);

        for (Employee employee : employees) {
            for (int i = 1; i <= 10; i++) {
                Notification notification = new Notification();
                notification.setMessage("System Alert #" + i + " for employee #" + employee.getEmployeeId());
                notification.setImportance(i <= 5 ? "HIGH" : "LOW");
                notification.setReadStatus(i <= 3);
                notification.setCreatedAt(
                        base.plusHours((employee.getEmployeeId() - 1) * 2L)
                                .plusHours(i * 6L)
                                .toInstant()
                );
                notification.setEmployee(employee);
                notifications.add(notification);
            }
        }

        notificationRepository.saveAll(notifications);
    }

    private void createAndSaveAttendanceLogs(List<Employee> employees) {
        List<AttendanceLog> attendanceLogs = new ArrayList<>();
        LocalDate startDate = LocalDate.of(2026, 4, 1);
        LocalDate endDate = LocalDate.of(2026, 5, 1);
        List<LocalDate> workDates = new ArrayList<>();

        for (LocalDate currentDate = startDate; !currentDate.isAfter(endDate); currentDate = currentDate.plusDays(1)) {
            workDates.add(currentDate);
        }

        for (Employee employee : employees) {
            if (employee.getEmploymentStatus() == EmploymentStatus.TERMINATED
                    || employee.getEmploymentStatus() == EmploymentStatus.SUSPENDED) {
                continue;
            }

            Map<LocalDate, AttendanceVariance> varianceByDate = buildAttendanceVarianceByDate(employee, workDates);
            for (LocalDate workDate : workDates) {
                AttendanceVariance variance = varianceByDate.getOrDefault(workDate, AttendanceVariance.ON_TIME);
                attendanceLogs.add(createAttendanceLog(employee, workDate, "IN", variance));
                attendanceLogs.add(createAttendanceLog(employee, workDate, "OUT", variance));
            }
        }

        attendanceLogRepository.saveAll(attendanceLogs);
    }

    private Map<LocalDate, AttendanceVariance> buildAttendanceVarianceByDate(Employee employee, List<LocalDate> workDates) {
        int anomalyCount = 1 + Math.floorMod(employee.getEmployeeId().intValue(), 4);
        Map<LocalDate, AttendanceVariance> varianceByDate = new HashMap<>();

        workDates.stream()
                .sorted(Comparator.comparingInt(date ->
                        Math.floorMod(Objects.hash(employee.getEmployeeId(), date, "attendance-variance"), Integer.MAX_VALUE)))
                .limit(anomalyCount)
                .forEach(date -> varianceByDate.put(
                        date,
                        Math.floorMod(Objects.hash(employee.getEmployeeId(), date, "attendance-variance-type"), 2) == 0
                                ? AttendanceVariance.LATE_IN
                                : AttendanceVariance.EARLY_OUT
                ));

        return varianceByDate;
    }

    private AttendanceLog createAttendanceLog(Employee employee, LocalDate workDate, String direction, AttendanceVariance variance) {
        LocalTime baseTime = "IN".equals(direction) ? SHIFT_START : SHIFT_END;
        int offsetMinutes = switch (variance) {
            case ON_TIME -> 0;
            case LATE_IN -> "IN".equals(direction) ? lateOffsetMinutes(employee, workDate) : 0;
            case EARLY_OUT -> "OUT".equals(direction) ? -earlyOutOffsetMinutes(employee, workDate) : 0;
        };

        AttendanceLog attendanceLog = new AttendanceLog();
        attendanceLog.setEmployeeId(employee.getEmployeeId());
        attendanceLog.setDirection(direction);
        attendanceLog.setTimestamp(
                workDate.atTime(baseTime)
                        .plusMinutes(offsetMinutes)
                        .atZone(SEED_TIME_ZONE)
                        .toInstant()
        );
        return attendanceLog;
    }

    private int lateOffsetMinutes(Employee employee, LocalDate workDate) {
        return 5 + Math.floorMod(
                Objects.hash(employee.getEmployeeId(), workDate, "late-minutes"),
                14
        );
    }

    private int earlyOutOffsetMinutes(Employee employee, LocalDate workDate) {
        return 5 + Math.floorMod(
                Objects.hash(employee.getEmployeeId(), workDate, "early-out-minutes"),
                14
        );
    }

    private Instant toInstant(LocalDateTime dateTime) {
        return dateTime.atZone(SEED_TIME_ZONE).toInstant();
    }

    private List<EmployeeSeed> employeeSeeds() {
        return List.of(
                new EmployeeSeed("John", null, "Doe", LocalDate.of(1985, 1, 15), LocalDate.of(2024, 1, 8), null,
                        BigDecimal.valueOf(1_500_000.00), "john.doe@maridone.test", true, "09170000001",
                        EmploymentStatus.REGULAR, ExemptionStatus.EXEMPT, Position.MANAGEMENT,
                        "101 Executive Ave", null, "Makati", "Metro Manila", "Philippines", "1200"),
                new EmployeeSeed("Jane", null, "Smith", LocalDate.of(1988, 3, 22), LocalDate.of(2024, 2, 12), null,
                        BigDecimal.valueOf(1_200_000.00), "jane.smith@maridone.test", true, "09170000002",
                        EmploymentStatus.REGULAR, ExemptionStatus.EXEMPT, Position.MANAGEMENT,
                        "102 Executive Ave", null, "Taguig", "Metro Manila", "Philippines", "1630"),
                new EmployeeSeed("Robert", null, "Brown", LocalDate.of(1990, 5, 9), LocalDate.of(2025, 1, 6), null,
                        BigDecimal.valueOf(600_000.00), "robert.brown@maridone.test", true, "09170000003",
                        EmploymentStatus.REGULAR, ExemptionStatus.EXEMPT, Position.HR,
                        "201 People Ops St", null, "Pasig", "Metro Manila", "Philippines", "1600"),
                new EmployeeSeed("Emily", "Anne", "Davis", LocalDate.of(1992, 7, 11), LocalDate.of(2025, 2, 3), null,
                        BigDecimal.valueOf(550_000.00), "emily.davis@maridone.test", true, "09170000004",
                        EmploymentStatus.REGULAR, ExemptionStatus.EXEMPT, Position.HR,
                        "202 People Ops St", null, "Quezon City", "Metro Manila", "Philippines", "1100"),
                new EmployeeSeed("Michael", null, "Wilson", LocalDate.of(1995, 9, 17), LocalDate.of(2026, 1, 15), null,
                        BigDecimal.valueOf(300_000.00), "michael.wilson@maridone.test", false, "09170000005",
                        EmploymentStatus.NEW_HIRE, ExemptionStatus.EXEMPT, Position.EMPLOYEE,
                        "301 Operations Rd", null, "Mandaluyong", "Metro Manila", "Philippines", "1550"),
                new EmployeeSeed("Linda", null, "Jones", LocalDate.of(1991, 4, 28), LocalDate.of(2024, 6, 10), null,
                        BigDecimal.valueOf(500_000.00), "linda.jones@maridone.test", true, "09170000006",
                        EmploymentStatus.REGULAR, ExemptionStatus.NON_EXEMPT, Position.ACCOUNTING,
                        "401 Finance Blvd", null, "Pasay", "Metro Manila", "Philippines", "1300"),
                new EmployeeSeed("William", null, "Garcia", LocalDate.of(1998, 12, 3), LocalDate.of(2026, 2, 2), null,
                        BigDecimal.valueOf(320_000.00), "william.garcia@maridone.test", false, "09170000007",
                        EmploymentStatus.NEW_HIRE, ExemptionStatus.NON_EXEMPT, Position.ACCOUNTING,
                        "402 Finance Blvd", null, "Paranaque", "Metro Manila", "Philippines", "1700"),
                new EmployeeSeed("Susan", null, "Miller", LocalDate.of(1999, 10, 20), LocalDate.of(2025, 11, 3), null,
                        BigDecimal.valueOf(150_000.00), "susan.miller@maridone.test", true, "09170000008",
                        EmploymentStatus.PART_TIME, ExemptionStatus.NON_EXEMPT, Position.EMPLOYEE,
                        "501 Support Lane", null, "Manila", "Metro Manila", "Philippines", "1000"),
                new EmployeeSeed("Joseph", null, "Taylor", LocalDate.of(1987, 8, 13), LocalDate.of(2024, 4, 1), LocalDate.of(2026, 3, 20),
                        BigDecimal.valueOf(100_000.00), "joseph.taylor@maridone.test", true, "09170000009",
                        EmploymentStatus.TERMINATED, ExemptionStatus.NON_EXEMPT, Position.EMPLOYEE,
                        "502 Support Lane", "Temp Housing Block A", "Caloocan", "Metro Manila", "Philippines", "1400"),
                new EmployeeSeed("Richard", null, "Moore", LocalDate.of(1993, 11, 30), LocalDate.of(2025, 8, 18), null,
                        BigDecimal.valueOf(200_000.00), "richard.moore@maridone.test", true, "09170000010",
                        EmploymentStatus.SUSPENDED, ExemptionStatus.NON_EXEMPT, Position.EMPLOYEE,
                        "503 Support Lane", null, "Las Pinas", "Metro Manila", "Philippines", "1740"),
                new EmployeeSeed("Cecilia", null, "Reyes", LocalDate.of(1989, 6, 18), LocalDate.of(2024, 3, 11), null,
                        BigDecimal.valueOf(720_000.00), "cecilia.reyes@maridone.test", true, "09170000011",
                        EmploymentStatus.REGULAR, ExemptionStatus.EXEMPT, Position.MANAGER,
                        "601 Leadership Row", null, "San Juan", "Metro Manila", "Philippines", "1502"),
                new EmployeeSeed("Daniel", null, "Cruz", LocalDate.of(1991, 2, 25), LocalDate.of(2024, 5, 20), null,
                        BigDecimal.valueOf(710_000.00), "daniel.cruz@maridone.test", true, "09170000012",
                        EmploymentStatus.REGULAR, ExemptionStatus.EXEMPT, Position.MANAGER,
                        "602 Leadership Row", null, "Marikina", "Metro Manila", "Philippines", "1800")
        );
    }

    private List<CalendarSeed> calendarSeeds() {
        return List.of(
                new CalendarSeed("Q2 Orientation",
                        "Quarter 2 onboarding, policy refreshers, and introductions for all employees.",
                        LocalDateTime.of(2026, 4, 1, 9, 0),
                        LocalDateTime.of(2026, 4, 1, 17, 0),
                        2,
                        LocalDateTime.of(2026, 3, 29, 8, 30)),
                new CalendarSeed("Strategy Workshop",
                        "Leadership and department heads align on Q2 execution priorities and KPIs.",
                        LocalDateTime.of(2026, 4, 5, 8, 0),
                        LocalDateTime.of(2026, 4, 6, 17, 0),
                        0,
                        LocalDateTime.of(2026, 4, 1, 10, 15)),
                new CalendarSeed("Security Audit",
                        "Internal systems and access review covering devices, credentials, and compliance checks.",
                        LocalDateTime.of(2026, 4, 10, 9, 0),
                        LocalDateTime.of(2026, 4, 11, 17, 0),
                        5,
                        LocalDateTime.of(2026, 4, 3, 9, 0)),
                new CalendarSeed("Leadership Summit",
                        "Management forum for escalation handling, staffing plans, and budget checkpoints.",
                        LocalDateTime.of(2026, 4, 16, 10, 0),
                        LocalDateTime.of(2026, 4, 16, 15, 0),
                        1,
                        LocalDateTime.of(2026, 4, 8, 11, 45)),
                new CalendarSeed("Team Building Day",
                        "Cross-functional games, workshops, and employee engagement activities.",
                        LocalDateTime.of(2026, 4, 21, 8, 0),
                        LocalDateTime.of(2026, 4, 21, 17, 0),
                        3,
                        LocalDateTime.of(2026, 4, 12, 14, 0)),
                new CalendarSeed("General Assembly",
                        "Company-wide updates on financial performance, operations, and people initiatives.",
                        LocalDateTime.of(2026, 4, 25, 14, 0),
                        LocalDateTime.of(2026, 4, 25, 16, 0),
                        0,
                        LocalDateTime.of(2026, 4, 15, 16, 20)),
                new CalendarSeed("Infrastructure Review",
                        "Review of office systems, attendance tooling, and deployment readiness.",
                        LocalDateTime.of(2026, 4, 29, 9, 0),
                        LocalDateTime.of(2026, 4, 29, 12, 0),
                        5,
                        LocalDateTime.of(2026, 4, 18, 9, 40)),
                new CalendarSeed("Labor Day Holiday",
                        "Observed company holiday. Offices are closed except for approved essential operations.",
                        LocalDateTime.of(2026, 5, 1, 0, 0),
                        LocalDateTime.of(2026, 5, 1, 23, 59, 59),
                        2,
                        LocalDateTime.of(2026, 4, 20, 8, 0)),
                new CalendarSeed("Project Kickoff",
                        "Kickoff for the next internal systems milestone with scope and owner alignment.",
                        LocalDateTime.of(2026, 5, 6, 9, 0),
                        LocalDateTime.of(2026, 5, 6, 10, 0),
                        1,
                        LocalDateTime.of(2026, 4, 25, 13, 0)),
                new CalendarSeed("Stakeholder Meeting",
                        "Monthly stakeholder sync for delivery risks, budget status, and staffing concerns.",
                        LocalDateTime.of(2026, 5, 12, 10, 0),
                        LocalDateTime.of(2026, 5, 12, 12, 0),
                        0,
                        LocalDateTime.of(2026, 4, 28, 15, 30))
        );
    }

    private record EmployeeSeed(
            String firstName,
            String middleName,
            String lastName,
            LocalDate birthDate,
            LocalDate employmentDateStart,
            LocalDate employmentDateEnd,
            BigDecimal yearlySalary,
            String email,
            boolean isVerified,
            String phoneNumber,
            EmploymentStatus employmentStatus,
            ExemptionStatus exemptionStatus,
            Position position,
            String permanentAddress,
            String temporaryAddress,
            String city,
            String state,
            String country,
            String zipCode
    ) {
    }

    private record CalendarSeed(
            String title,
            String description,
            LocalDateTime startDateTime,
            LocalDateTime endDateTime,
            int authorIndex,
            LocalDateTime createdAt
    ) {
    }

    private enum AttendanceVariance {
        ON_TIME,
        LATE_IN,
        EARLY_OUT
    }
}
