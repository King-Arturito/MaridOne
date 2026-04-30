package org.example.maridone.testing;

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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import org.example.maridone.core.bank.BankAccount;
import org.example.maridone.core.bank.BankAccountRepository;
import org.example.maridone.core.employee.Employee;
import org.example.maridone.core.employee.EmployeeRepository;
import org.example.maridone.core.user.UserAccount;
import org.example.maridone.embeddable.Address;
import org.example.maridone.enums.AccountStatus;
import org.example.maridone.enums.EmploymentStatus;
import org.example.maridone.enums.ExemptionStatus;
import org.example.maridone.enums.Gender;
import org.example.maridone.enums.LeaveType;
import org.example.maridone.enums.ManagerTitle;
import org.example.maridone.enums.Position;
import org.example.maridone.leave.balance.LeaveBalance;
import org.example.maridone.leave.balance.LeaveBalanceRepository;
import org.example.maridone.log.AttendanceLogRepository;
import org.example.maridone.log.attendance.AttendanceLog;
import org.example.maridone.notification.Notification;
import org.example.maridone.notification.NotificationRepository;
import org.example.maridone.schedule.calendar.CalendarRepository;
import org.example.maridone.schedule.calendar.CompanyCalendar;
import org.example.maridone.schedule.shift.DailyShiftRepository;
import org.example.maridone.schedule.shift.DailyShiftSchedule;
import org.example.maridone.schedule.shift.TemplateShiftRepository;
import org.example.maridone.schedule.shift.TemplateShiftSchedule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
@Profile("dev")
public class SampleRows {

    private static final ZoneId SEED_TIME_ZONE = ZoneId.of("Asia/Manila");
    private static final LocalDate SEED_START_DATE = LocalDate.of(2025, 1, 1);
    private static final LocalDate SEED_END_DATE = LocalDate.of(2026, 4, 11);
    private static final LocalTime SHIFT_START = LocalTime.of(8, 0);
    private static final LocalTime SHIFT_END = LocalTime.of(17, 0);
    private static final LocalTime NIGHT_SHIFT_START = LocalTime.of(22, 0);
    private static final LocalTime NIGHT_SHIFT_END = LocalTime.of(6, 0);
    private static final List<DayOfWeek> STANDARD_WORK_DAYS = List.of(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY,
            DayOfWeek.SATURDAY
    );
    private static final List<Integer> SEEDED_LATE_MONTHS = List.of(2, 3);
    private static final int SEEDED_LATE_DAYS_PER_MONTH = 2;
    private static final LocalDate TARGETED_LATE_DATE_ONE = LocalDate.of(2026, 3, 30);
    private static final LocalDate TARGETED_LATE_DATE_TWO = LocalDate.of(2026, 3, 31);
    private static final LocalDate TARGETED_LATE_EARLY_OUT_DATE = LocalDate.of(2026, 3, 28);
    private static final LocalDate TARGETED_ABSENT_DATE_ONE = LocalDate.of(2025, 4, 24);
    private static final LocalDate TARGETED_ABSENT_DATE_TWO = LocalDate.of(2025, 10, 14);
    private static final LocalDate TARGETED_ABSENT_DATE_THREE = LocalDate.of(2026, 2, 18);
    private static final LocalDate TARGETED_INCOMPLETE_MISSING_OUT_DATE_ONE = LocalDate.of(2025, 6, 17);
    private static final LocalDate TARGETED_INCOMPLETE_MISSING_OUT_DATE_TWO = LocalDate.of(2026, 1, 13);
    private static final LocalDate TARGETED_INCOMPLETE_MISSING_IN_DATE_ONE = LocalDate.of(2025, 9, 9);
    private static final LocalDate TARGETED_INCOMPLETE_MISSING_IN_DATE_TWO = LocalDate.of(2026, 4, 7);
    private static final int MIN_LATE_OFFSET_MINUTES = 18;
    private static final int LATE_OFFSET_VARIATION_MINUTES = 15;
    private static final String NIGHT_SHIFT_TEST_EMAIL = "nina.soriano@maridone.test";
    private static final String ZERO_SALARY_TEST_EMAIL = "omar.delosreyes@maridone.test";
    private static final String LATE_EARLY_OUT_TEST_EMAIL = "richard.moore@maridone.test";

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private CalendarRepository calendarRepository;

    @Autowired
    private TemplateShiftRepository templateShiftRepository;

    @Autowired
    private DailyShiftRepository dailyShiftRepository;

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
        List<TemplateShiftSchedule> templateSchedules = createAndSaveShiftSchedules(employees);
        createAndSaveSeededDailyShifts(templateSchedules);
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
        employee.setEmploymentDateStart(resolveSeedEmploymentStart(seed));
        employee.setEmploymentDateEnd(seed.employmentDateEnd());
        employee.setYearlySalary(seed.yearlySalary());
        employee.setCompanyEmail(seed.email());
        employee.setPersonalEmail(buildPersonalEmail(seed.email()));
        employee.setIsVerified(seed.isVerified());
        employee.setPhoneNumber(seed.phoneNumber());
        employee.setGender(seed.gender());
        employee.setEmploymentStatus(seed.employmentStatus());
        employee.setExemptionStatus(seed.exemptionStatus());
        employee.setPosition(seed.position());
        employee.setEmpTitle(seed.empTitle());
        employee.setAddress(toAddress(seed));
        employee.setSssNumber(seed.sssNumber());
        employee.setPhilhealthNumber(seed.philhealthNumber());
        employee.setPagibigNumber(seed.pagibigNumber());
        employee.setTinNumber(seed.tinNumber());
        employee.setEmergencyContactName(seed.emergencyContactName());
        employee.setEmergencyContactRelationship(seed.emergencyContactRelationship());
        employee.setEmergencyContactNumber(seed.emergencyContactNumber());
        return employee;
    }

    private LocalDate resolveSeedEmploymentStart(EmployeeSeed seed) {
        LocalDate seededStart = seed.employmentDateStart();
        if (seededStart == null || seededStart.isAfter(SEED_START_DATE)) {
            return SEED_START_DATE;
        }
        return seededStart;
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
        for (Employee employee : employees) {
            employee.setManager(null);
        }

        List<Employee> managers = employees.stream()
                .filter(employee -> employee.getPosition() == Position.MANAGER)
                .toList();

        if (managers.isEmpty()) {
            employeeRepository.saveAll(employees);
            return;
        }

        List<Employee> individualContributors = employees.stream()
                .filter(employee -> employee.getPosition() == Position.EMPLOYEE)
                .toList();

        for (int index = 0; index < individualContributors.size(); index++) {
            Employee employee = individualContributors.get(index);
            Employee manager = managers.get(index % managers.size());
            employee.setManager(manager);
        }

        employeeRepository.saveAll(employees);
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
            userAccount.setAccountId(employee.getEmployeeId());
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

    private List<TemplateShiftSchedule> createAndSaveShiftSchedules(List<Employee> employees) {
        List<TemplateShiftSchedule> schedules = new ArrayList<>();
        for (Employee employee : employees) {
            for (DayOfWeek dayOfWeek : STANDARD_WORK_DAYS) {
                TemplateShiftSchedule schedule = new TemplateShiftSchedule();
                schedule.setEmployee(employee);
                schedule.setDayOfWeek(dayOfWeek);
                schedule.setStartTime(resolveShiftStart(employee));
                schedule.setEndTime(resolveShiftEnd(employee));
                schedules.add(schedule);
            }
        }
        templateShiftRepository.saveAll(schedules);
        return schedules;
    }

    private void createAndSaveSeededDailyShifts(List<TemplateShiftSchedule> templateSchedules) {
        List<DailyShiftSchedule> dailyShifts = new ArrayList<>();

        for (LocalDate workDate = SEED_START_DATE; !workDate.isAfter(SEED_END_DATE); workDate = workDate.plusDays(1)) {
            DayOfWeek workDay = workDate.getDayOfWeek();
            for (TemplateShiftSchedule template : templateSchedules) {
                Employee employee = template.getEmployee();
                if (employee == null || template.getDayOfWeek() != workDay) {
                    continue;
                }
                if (workDate.isBefore(employee.getEmploymentDateStart())) {
                    continue;
                }
                if (employee.getEmploymentDateEnd() != null && workDate.isAfter(employee.getEmploymentDateEnd())) {
                    continue;
                }

                DailyShiftSchedule dailyShift = new DailyShiftSchedule();
                dailyShift.setTemplateShiftSchedule(template);
                dailyShift.setStartDateTime(LocalDateTime.of(workDate, template.getStartTime()));
                dailyShift.setEndDateTime(
                        LocalDateTime.of(workDate, template.getEndTime()).plusDays(
                                template.getEndTime().isBefore(template.getStartTime()) ? 1 : 0
                        )
                );
                dailyShift.setDayOff(false);
                dailyShifts.add(dailyShift);
            }
        }

        dailyShiftRepository.saveAll(dailyShifts);
    }

    private void createAndSaveLeaveBalances(List<Employee> employees) {
        List<LeaveBalance> leaveBalances = new ArrayList<>();
        for (Employee employee : employees) {
            for (LeaveType leaveType : LeaveType.values()) {
                LeaveBalance leaveBalance = new LeaveBalance();
                leaveBalance.setEmployee(employee);
                leaveBalance.setLeaveType(leaveType);
                leaveBalance.setBalanceHours(BigDecimal.valueOf(40.00));
                leaveBalances.add(leaveBalance);
            }
        }
        leaveBalanceRepository.saveAll(leaveBalances);
    }

    private void createAndSaveNotifications(List<Employee> employees) {
        List<Notification> notifications = new ArrayList<>();
        ZonedDateTime base = ZonedDateTime.of(2026, 2, 1, 8, 0, 0, 0, SEED_TIME_ZONE);
        List<String> seededMessages = List.of(
                "Your payslip for January 1, 2026 to January 15, 2026 is ready.",
                "Leave update: your request is awaiting review.",
                "Overtime update: your request has been received.",
                "Attendance reminder: please review your latest time logs.",
                "Training assigned: the employee handbook is ready for review.",
                "Calendar update: a new company event was added.",
                "Profile reminder: review your contact details.",
                "Payroll reminder: check your latest deductions summary.",
                "Security reminder: update your password regularly.",
                "HR update: a new company announcement is available."
        );

        for (Employee employee : employees) {
            for (int i = 0; i < seededMessages.size(); i++) {
                Notification notification = new Notification();
                notification.setMessage(seededMessages.get(i));
                notification.setImportance(i < 5 ? "HIGH" : "LOW");
                notification.setReadStatus(i < 3);
                notification.setCreatedAt(
                        base.plusHours((employee.getEmployeeId() - 1) * 2L)
                                .plusHours((i + 1L) * 6L)
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
        List<LocalDate> allWorkDates = buildWorkDates(SEED_START_DATE, SEED_END_DATE);

        for (Employee employee : employees) {
            List<LocalDate> employeeWorkDates = allWorkDates.stream()
                    .filter(date -> !date.isBefore(employee.getEmploymentDateStart()))
                    .filter(date -> employee.getEmploymentDateEnd() == null || !date.isAfter(employee.getEmploymentDateEnd()))
                    .toList();

            if (employeeWorkDates.isEmpty()) {
                continue;
            }

            AttendanceAnomalyPlan anomalyPlan = buildAttendanceAnomalyPlan(employee, employeeWorkDates);
            for (LocalDate workDate : employeeWorkDates) {
                AttendanceVariance variance = resolveAttendanceVariance(employee, workDate, anomalyPlan);
                switch (variance) {
                    case ABSENT -> {
                        // Skip both logs so attendance reports can surface a true absence.
                    }
                    case INCOMPLETE_MISSING_OUT ->
                            attendanceLogs.add(createAttendanceLog(employee, workDate, "IN", AttendanceVariance.ON_TIME));
                    case INCOMPLETE_MISSING_IN ->
                            attendanceLogs.add(createAttendanceLog(employee, workDate, "OUT", AttendanceVariance.ON_TIME));
                    default -> {
                        attendanceLogs.add(createAttendanceLog(employee, workDate, "IN", variance));
                        attendanceLogs.add(createAttendanceLog(employee, workDate, "OUT", variance));
                    }
                }
            }
        }

        attendanceLogRepository.saveAll(attendanceLogs);
    }

    private List<LocalDate> buildWorkDates(LocalDate startDate, LocalDate endDate) {
        List<LocalDate> workDates = new ArrayList<>();
        for (LocalDate currentDate = startDate; !currentDate.isAfter(endDate); currentDate = currentDate.plusDays(1)) {
            if (STANDARD_WORK_DAYS.contains(currentDate.getDayOfWeek())) {
                workDates.add(currentDate);
            }
        }
        return workDates;
    }

    private AttendanceAnomalyPlan buildAttendanceAnomalyPlan(Employee employee, List<LocalDate> workDates) {
        Set<LocalDate> lateDates = buildLateDates(employee, workDates);
        Set<LocalDate> absentDates = buildAbsentDates(employee, workDates);
        Set<LocalDate> incompleteMissingOutDates = buildIncompleteMissingOutDates(employee, workDates);
        Set<LocalDate> incompleteMissingInDates = buildIncompleteMissingInDates(employee, workDates);

        incompleteMissingOutDates.removeAll(absentDates);
        incompleteMissingInDates.removeAll(absentDates);
        lateDates.removeAll(absentDates);
        lateDates.removeAll(incompleteMissingOutDates);
        lateDates.removeAll(incompleteMissingInDates);

        return new AttendanceAnomalyPlan(
                lateDates,
                absentDates,
                incompleteMissingOutDates,
                incompleteMissingInDates
        );
    }

    private Set<LocalDate> buildLateDates(Employee employee, List<LocalDate> workDates) {
        Set<LocalDate> lateDates = new LinkedHashSet<>();
        for (int month : SEEDED_LATE_MONTHS) {
            workDates.stream()
                    .filter(date -> date.getMonthValue() == month)
                    .sorted(Comparator.comparingInt(date ->
                            Math.floorMod(Objects.hash(employee.getEmployeeId(), date, "seeded-late-day"), Integer.MAX_VALUE)))
                    .limit(SEEDED_LATE_DAYS_PER_MONTH)
                    .forEach(lateDates::add);
        }
        addTargetedWorkDate(workDates, lateDates, TARGETED_LATE_DATE_ONE, Math.floorMod(employee.getEmployeeId().intValue(), 4) == 1);
        addTargetedWorkDate(workDates, lateDates, TARGETED_LATE_DATE_TWO, Math.floorMod(employee.getEmployeeId().intValue(), 4) == 2);
        return lateDates;
    }

    private Set<LocalDate> buildAbsentDates(Employee employee, List<LocalDate> workDates) {
        Set<LocalDate> absentDates = new LinkedHashSet<>();
        int anomalyBucket = Math.floorMod(employee.getEmployeeId().intValue(), 6);
        addTargetedWorkDate(workDates, absentDates, TARGETED_ABSENT_DATE_ONE, anomalyBucket == 0);
        addTargetedWorkDate(workDates, absentDates, TARGETED_ABSENT_DATE_TWO, anomalyBucket == 1);
        addTargetedWorkDate(workDates, absentDates, TARGETED_ABSENT_DATE_THREE, anomalyBucket == 2);
        return absentDates;
    }

    private Set<LocalDate> buildIncompleteMissingOutDates(Employee employee, List<LocalDate> workDates) {
        Set<LocalDate> incompleteDates = new LinkedHashSet<>();
        int anomalyBucket = Math.floorMod(employee.getEmployeeId().intValue(), 6);
        addTargetedWorkDate(workDates, incompleteDates, TARGETED_INCOMPLETE_MISSING_OUT_DATE_ONE, anomalyBucket == 3);
        addTargetedWorkDate(workDates, incompleteDates, TARGETED_INCOMPLETE_MISSING_OUT_DATE_TWO, anomalyBucket == 4);
        return incompleteDates;
    }

    private Set<LocalDate> buildIncompleteMissingInDates(Employee employee, List<LocalDate> workDates) {
        Set<LocalDate> incompleteDates = new LinkedHashSet<>();
        int anomalyBucket = Math.floorMod(employee.getEmployeeId().intValue(), 6);
        addTargetedWorkDate(workDates, incompleteDates, TARGETED_INCOMPLETE_MISSING_IN_DATE_ONE, anomalyBucket == 5);
        addTargetedWorkDate(workDates, incompleteDates, TARGETED_INCOMPLETE_MISSING_IN_DATE_TWO, anomalyBucket == 0);
        return incompleteDates;
    }

    private void addTargetedWorkDate(List<LocalDate> workDates,
                                     Set<LocalDate> targetDates,
                                     LocalDate targetDate,
                                     boolean shouldInclude) {
        if (shouldInclude && workDates.contains(targetDate)) {
            targetDates.add(targetDate);
        }
    }

    private AttendanceLog createAttendanceLog(Employee employee, LocalDate workDate, String direction, AttendanceVariance variance) {
        LocalTime shiftStart = resolveShiftStart(employee);
        LocalTime shiftEnd = resolveShiftEnd(employee);
        LocalTime baseTime = "IN".equals(direction) ? shiftStart : shiftEnd;
        int offsetMinutes = switch (variance) {
            case ON_TIME -> 0;
            case LATE_IN -> "IN".equals(direction) ? lateOffsetMinutes(employee, workDate) : 0;
            case EARLY_OUT -> "OUT".equals(direction) ? -earlyOutOffsetMinutes(employee, workDate) : 0;
            case LATE_IN_AND_EARLY_OUT -> "IN".equals(direction)
                    ? lateOffsetMinutes(employee, workDate)
                    : -earlyOutOffsetMinutes(employee, workDate);
            case ABSENT, INCOMPLETE_MISSING_OUT, INCOMPLETE_MISSING_IN -> 0;
        };
        LocalDate logDate = workDate;
        if ("OUT".equals(direction) && shiftEnd.isBefore(shiftStart)) {
            logDate = workDate.plusDays(1);
        }

        AttendanceLog attendanceLog = new AttendanceLog();
        attendanceLog.setEmployeeId(employee.getEmployeeId());
        attendanceLog.setDirection(direction);
        attendanceLog.setTimestamp(
                logDate.atTime(baseTime)
                        .plusMinutes(offsetMinutes)
                        .atZone(SEED_TIME_ZONE)
                        .toInstant()
        );
        return attendanceLog;
    }

    private AttendanceVariance resolveAttendanceVariance(Employee employee,
                                                         LocalDate workDate,
                                                         AttendanceAnomalyPlan anomalyPlan) {
        if (anomalyPlan.absentDates().contains(workDate)) {
            return AttendanceVariance.ABSENT;
        }
        if (anomalyPlan.incompleteMissingOutDates().contains(workDate)) {
            return AttendanceVariance.INCOMPLETE_MISSING_OUT;
        }
        if (anomalyPlan.incompleteMissingInDates().contains(workDate)) {
            return AttendanceVariance.INCOMPLETE_MISSING_IN;
        }
        if (isLateAndEarlyOutTestEmployee(employee) && TARGETED_LATE_EARLY_OUT_DATE.equals(workDate)) {
            return AttendanceVariance.LATE_IN_AND_EARLY_OUT;
        }
        if (anomalyPlan.lateDates().contains(workDate)) {
            return AttendanceVariance.LATE_IN;
        }
        return AttendanceVariance.ON_TIME;
    }

    private int lateOffsetMinutes(Employee employee, LocalDate workDate) {
        return MIN_LATE_OFFSET_MINUTES + Math.floorMod(
                Objects.hash(employee.getEmployeeId(), workDate, "late-minutes"),
                LATE_OFFSET_VARIATION_MINUTES
        );
    }

    private int earlyOutOffsetMinutes(Employee employee, LocalDate workDate) {
        return 5 + Math.floorMod(
                Objects.hash(employee.getEmployeeId(), workDate, "early-out-minutes"),
                14
        );
    }

    private LocalTime resolveShiftStart(Employee employee) {
        return isNightShiftTestEmployee(employee) ? NIGHT_SHIFT_START : SHIFT_START;
    }

    private LocalTime resolveShiftEnd(Employee employee) {
        return isNightShiftTestEmployee(employee) ? NIGHT_SHIFT_END : SHIFT_END;
    }

    private boolean isNightShiftTestEmployee(Employee employee) {
        return employee != null && NIGHT_SHIFT_TEST_EMAIL.equalsIgnoreCase(employee.getCompanyEmail());
    }

    private boolean isLateAndEarlyOutTestEmployee(Employee employee) {
        return employee != null && LATE_EARLY_OUT_TEST_EMAIL.equalsIgnoreCase(employee.getCompanyEmail());
    }

    private String buildPersonalEmail(String companyEmail) {
        if (companyEmail == null || companyEmail.isBlank() || !companyEmail.contains("@")) {
            return companyEmail;
        }

        int atIndex = companyEmail.indexOf('@');
        return companyEmail.substring(0, atIndex) + ".personal" + companyEmail.substring(atIndex);
    }

    private Instant toInstant(LocalDateTime dateTime) {
        return dateTime.atZone(SEED_TIME_ZONE).toInstant();
    }

    private List<EmployeeSeed> employeeSeeds() {
        return List.of(
                new EmployeeSeed("John", null, "Doe", LocalDate.of(1985, 1, 15), LocalDate.of(2024, 1, 8), null,
                        BigDecimal.valueOf(1_500_000.00), "john.doe@maridone.test", true, "09170000001", Gender.MALE,
                        EmploymentStatus.REGULAR, ExemptionStatus.EXEMPT, Position.MANAGEMENT, null,
                        "101 Executive Ave", null, "Makati", "Metro Manila", "Philippines", "1200",
                        "01-2222222-3", "04-555555555-6", "1234-5678-9012", "123-456-789-000",
                        "Jane Doe", "Spouse", "09171234567"),
                new EmployeeSeed("Jane", null, "Smith", LocalDate.of(1988, 3, 22), LocalDate.of(2024, 2, 12), null,
                        BigDecimal.valueOf(1_200_000.00), "jane.smith@maridone.test", true, "09170000002", Gender.FEMALE,
                        EmploymentStatus.REGULAR, ExemptionStatus.EXEMPT, Position.MANAGEMENT, null,
                        "102 Executive Ave", null, "Taguig", "Metro Manila", "Philippines", "1630",
                        "02-2222222-3", "05-555555555-6", "2234-5678-9012", "223-456-789-000",
                        "John Smith", "Spouse", "09172234567"),
                new EmployeeSeed("Robert", null, "Brown", LocalDate.of(1990, 5, 9), LocalDate.of(2025, 1, 6), null,
                        BigDecimal.valueOf(600_000.00), "robert.brown@maridone.test", true, "09170000003", Gender.MALE,
                        EmploymentStatus.REGULAR, ExemptionStatus.EXEMPT, Position.HR, null,
                        "201 People Ops St", null, "Pasig", "Metro Manila", "Philippines", "1600",
                        "03-2222222-3", "06-555555555-6", "3234-5678-9012", "323-456-789-000",
                        "Mary Brown", "Mother", "09173234567"),
                new EmployeeSeed("Emily", "Anne", "Davis", LocalDate.of(1992, 7, 11), LocalDate.of(2025, 2, 3), null,
                        BigDecimal.valueOf(550_000.00), "emily.davis@maridone.test", true, "09170000004", Gender.FEMALE,
                        EmploymentStatus.REGULAR, ExemptionStatus.EXEMPT, Position.HR, null,
                        "202 People Ops St", null, "Quezon City", "Metro Manila", "Philippines", "1100",
                        "04-2222222-3", "07-555555555-6", "4234-5678-9012", "423-456-789-000",
                        "James Davis", "Father", "09174234567"),
                new EmployeeSeed("Linda", null, "Jones", LocalDate.of(1991, 4, 28), LocalDate.of(2024, 6, 10), null,
                        BigDecimal.valueOf(500_000.00), "linda.jones@maridone.test", true, "09170000005", Gender.FEMALE,
                        EmploymentStatus.REGULAR, ExemptionStatus.NON_EXEMPT, Position.ACCOUNTING, null,
                        "401 Finance Blvd", null, "Pasay", "Metro Manila", "Philippines", "1300",
                        "05-2222222-3", "08-555555555-6", "5234-5678-9012", "523-456-789-000",
                        "Michael Jones", "Brother", "09175234567"),
                new EmployeeSeed("William", null, "Garcia", LocalDate.of(1998, 12, 3), LocalDate.of(2026, 2, 2), null,
                        BigDecimal.valueOf(320_000.00), "william.garcia@maridone.test", false, "09170000006", Gender.MALE,
                        EmploymentStatus.NEW_HIRE, ExemptionStatus.NON_EXEMPT, Position.ACCOUNTING, null,
                        "402 Finance Blvd", null, "Paranaque", "Metro Manila", "Philippines", "1700",
                        "06-2222222-3", "09-555555555-6", "6234-5678-9012", "623-456-789-000",
                        "Sophia Garcia", "Sister", "09176234567"),
                new EmployeeSeed("Cecilia", null, "Reyes", LocalDate.of(1989, 6, 18), LocalDate.of(2024, 3, 11), null,
                        BigDecimal.valueOf(720_000.00), "cecilia.reyes@maridone.test", true, "09170000007", Gender.FEMALE,
                        EmploymentStatus.REGULAR, ExemptionStatus.EXEMPT, Position.MANAGER, ManagerTitle.HEAD_OF_PEOPLE_OPERATIONS,
                        "601 Leadership Row", null, "San Juan", "Metro Manila", "Philippines", "1502",
                        "07-2222222-3", "10-555555555-6", "7234-5678-9012", "723-456-789-000",
                        "Antonio Reyes", "Husband", "09177234567"),
                new EmployeeSeed("Daniel", null, "Cruz", LocalDate.of(1991, 2, 25), LocalDate.of(2024, 5, 20), null,
                        BigDecimal.valueOf(710_000.00), "daniel.cruz@maridone.test", true, "09170000008", Gender.MALE,
                        EmploymentStatus.REGULAR, ExemptionStatus.EXEMPT, Position.MANAGER, ManagerTitle.HEAD_OF_PROCUREMENT,
                        "602 Leadership Row", null, "Marikina", "Metro Manila", "Philippines", "1800",
                        "08-2222222-3", "11-555555555-6", "8234-5678-9012", "823-456-789-000",
                        "Elena Cruz", "Wife", "09178234567"),
                new EmployeeSeed("Patricia", null, "Santos", LocalDate.of(1990, 8, 14), LocalDate.of(2024, 7, 1), null,
                        BigDecimal.valueOf(705_000.00), "patricia.santos@maridone.test", true, "09170000009", Gender.FEMALE,
                        EmploymentStatus.REGULAR, ExemptionStatus.EXEMPT, Position.MANAGER, ManagerTitle.PRODUCTION_SUPERVISOR,
                        "603 Leadership Row", null, "Pasig", "Metro Manila", "Philippines", "1605",
                        "09-2222222-3", "12-555555555-6", "9234-5678-9012", "923-456-789-000",
                        "Ricardo Santos", "Father", "09179234567"),
                new EmployeeSeed("Adrian", null, "Flores", LocalDate.of(1992, 10, 8), LocalDate.of(2024, 8, 12), null,
                        BigDecimal.valueOf(715_000.00), "adrian.flores@maridone.test", true, "09170000010", Gender.MALE,
                        EmploymentStatus.REGULAR, ExemptionStatus.EXEMPT, Position.MANAGER, ManagerTitle.PRODUCT_DEVELOPMENT_ENGINEER,
                        "604 Leadership Row", null, "Quezon City", "Metro Manila", "Philippines", "1110",
                        "10-2222222-3", "13-555555555-6", "0234-5678-9012", "023-456-789-000",
                        "Luz Flores", "Mother", "09170234567"),
                new EmployeeSeed("Michael", null, "Wilson", LocalDate.of(1995, 9, 17), LocalDate.of(2026, 1, 15), null,
                        BigDecimal.valueOf(300_000.00), "michael.wilson@maridone.test", false, "09170000011", Gender.MALE,
                        EmploymentStatus.NEW_HIRE, ExemptionStatus.NON_EXEMPT, Position.EMPLOYEE, null,
                        "301 Operations Rd", null, "Mandaluyong", "Metro Manila", "Philippines", "1550",
                        "11-2222222-3", "14-555555555-6", "1134-5678-9012", "113-456-789-000",
                        "Sarah Wilson", "Sister", "09171134567"),
                new EmployeeSeed("Susan", null, "Miller", LocalDate.of(1999, 10, 20), LocalDate.of(2025, 11, 3), null,
                        BigDecimal.valueOf(150_000.00), "susan.miller@maridone.test", true, "09170000012", Gender.FEMALE,
                        EmploymentStatus.PART_TIME, ExemptionStatus.NON_EXEMPT, Position.EMPLOYEE, null,
                        "302 Operations Rd", null, "Manila", "Metro Manila", "Philippines", "1000",
                        "12-2222222-3", "15-555555555-6", "1234-5678-9013", "123-456-789-001",
                        "George Miller", "Father", "09171234568"),
                new EmployeeSeed("Joseph", null, "Taylor", LocalDate.of(1987, 8, 13), LocalDate.of(2024, 4, 1), null,
                        BigDecimal.valueOf(260_000.00), "joseph.taylor@maridone.test", true, "09170000013", Gender.MALE,
                        EmploymentStatus.REGULAR, ExemptionStatus.NON_EXEMPT, Position.EMPLOYEE, null,
                        "303 Operations Rd", "Temp Housing Block A", "Caloocan", "Metro Manila", "Philippines", "1400",
                        "13-2222222-3", "16-555555555-6", "1334-5678-9012", "133-456-789-000",
                        "Anna Taylor", "Wife", "09171334567"),
                new EmployeeSeed("Richard", null, "Moore", LocalDate.of(1993, 11, 30), LocalDate.of(2025, 8, 18), null,
                        BigDecimal.valueOf(280_000.00), "richard.moore@maridone.test", true, "09170000014", Gender.MALE,
                        EmploymentStatus.REGULAR, ExemptionStatus.NON_EXEMPT, Position.EMPLOYEE, null,
                        "304 Support Lane", null, "Las Pinas", "Metro Manila", "Philippines", "1740",
                        "14-2222222-3", "17-555555555-6", "1434-5678-9012", "143-456-789-000",
                        "Thomas Moore", "Brother", "09171434567"),
                new EmployeeSeed("Olivia", null, "Ramos", LocalDate.of(1996, 6, 21), LocalDate.of(2025, 4, 7), null,
                        BigDecimal.valueOf(275_000.00), "olivia.ramos@maridone.test", true, "09170000015", Gender.FEMALE,
                        EmploymentStatus.REGULAR, ExemptionStatus.NON_EXEMPT, Position.EMPLOYEE, null,
                        "305 Support Lane", null, "Makati", "Metro Manila", "Philippines", "1227",
                        "15-2222222-3", "18-555555555-6", "1534-5678-9012", "153-456-789-000",
                        "Carmen Ramos", "Mother", "09171534567"),
                new EmployeeSeed("Ethan", null, "Navarro", LocalDate.of(1994, 12, 1), LocalDate.of(2025, 5, 19), null,
                        BigDecimal.valueOf(285_000.00), "ethan.navarro@maridone.test", true, "09170000016", Gender.MALE,
                        EmploymentStatus.REGULAR, ExemptionStatus.NON_EXEMPT, Position.EMPLOYEE, null,
                        "306 Support Lane", null, "Taguig", "Metro Manila", "Philippines", "1634",
                        "16-2222222-3", "19-555555555-6", "1634-5678-9012", "163-456-789-000",
                        "Felix Navarro", "Father", "09171634567"),
                new EmployeeSeed("Grace", null, "Bautista", LocalDate.of(1997, 1, 29), LocalDate.of(2025, 6, 2), null,
                        BigDecimal.valueOf(295_000.00), "grace.bautista@maridone.test", true, "09170000017", Gender.FEMALE,
                        EmploymentStatus.REGULAR, ExemptionStatus.NON_EXEMPT, Position.EMPLOYEE, null,
                        "307 Production Ave", null, "Pasig", "Metro Manila", "Philippines", "1603",
                        "17-2222222-3", "20-555555555-6", "1734-5678-9012", "173-456-789-000",
                        "Isabel Bautista", "Sister", "09171734567"),
                new EmployeeSeed("Noah", null, "Mendoza", LocalDate.of(1998, 4, 16), LocalDate.of(2025, 7, 14), null,
                        BigDecimal.valueOf(305_000.00), "noah.mendoza@maridone.test", true, "09170000018", Gender.MALE,
                        EmploymentStatus.REGULAR, ExemptionStatus.NON_EXEMPT, Position.EMPLOYEE, null,
                        "308 Production Ave", null, "Antipolo", "Rizal", "Philippines", "1870",
                        "18-2222222-3", "21-555555555-6", "1834-5678-9012", "183-456-789-000",
                        "Gabriel Mendoza", "Brother", "09171834567"),
                new EmployeeSeed("Chloe", null, "Lim", LocalDate.of(1995, 2, 11), LocalDate.of(2025, 8, 4), null,
                        BigDecimal.valueOf(298_000.00), "chloe.lim@maridone.test", true, "09170000019", Gender.FEMALE,
                        EmploymentStatus.REGULAR, ExemptionStatus.NON_EXEMPT, Position.EMPLOYEE, null,
                        "309 Production Ave", null, "Marikina", "Metro Manila", "Philippines", "1805",
                        "19-2222222-3", "22-555555555-6", "1934-5678-9012", "193-456-789-000",
                        "David Lim", "Father", "09171934567"),
                new EmployeeSeed("Lucas", null, "Torres", LocalDate.of(1993, 3, 5), LocalDate.of(2025, 9, 1), null,
                        BigDecimal.valueOf(315_000.00), "lucas.torres@maridone.test", true, "09170000020", Gender.MALE,
                        EmploymentStatus.SUSPENDED, ExemptionStatus.NON_EXEMPT, Position.EMPLOYEE, null,
                        "310 Product Dev Blvd", null, "Quezon City", "Metro Manila", "Philippines", "1105",
                        "20-2222222-3", "23-555555555-6", "2034-5678-9012", "203-456-789-000",
                        "Julia Torres", "Wife", "09172034567"),
                new EmployeeSeed("Mia", null, "Castillo", LocalDate.of(1999, 5, 27), LocalDate.of(2025, 10, 6), null,
                        BigDecimal.valueOf(290_000.00), "mia.castillo@maridone.test", true, "09170000021", Gender.FEMALE,
                        EmploymentStatus.REGULAR, ExemptionStatus.NON_EXEMPT, Position.EMPLOYEE, null,
                        "311 Product Dev Blvd", null, "Mandaluyong", "Metro Manila", "Philippines", "1554",
                        "21-2222222-3", "24-555555555-6", "2134-5678-9012", "213-456-789-000",
                        "Victor Castillo", "Father", "09172134567"),
                new EmployeeSeed("Sean", null, "Velasco", LocalDate.of(1994, 7, 19), LocalDate.of(2025, 11, 10), null,
                        BigDecimal.valueOf(310_000.00), "sean.velasco@maridone.test", true, "09170000022", Gender.MALE,
                        EmploymentStatus.TERMINATED, ExemptionStatus.NON_EXEMPT, Position.EMPLOYEE, null,
                        "312 Product Dev Blvd", null, "Pasay", "Metro Manila", "Philippines", "1309",
                        "22-2222222-3", "25-555555555-6", "2234-5678-9012", "223-456-789-000",
                        "Rosa Velasco", "Mother", "09172234567"),
                new EmployeeSeed("Nina", null, "Soriano", LocalDate.of(1996, 9, 9), LocalDate.of(2025, 12, 1), null,
                        BigDecimal.valueOf(320_000.00), NIGHT_SHIFT_TEST_EMAIL, true, "09170000023", Gender.FEMALE,
                        EmploymentStatus.REGULAR, ExemptionStatus.NON_EXEMPT, Position.EMPLOYEE, null,
                        "401 Night Shift Ave", null, "Taguig", "Metro Manila", "Philippines", "1631",
                        "23-2222222-3", "26-555555555-6", "2334-5678-9012", "233-456-789-000",
                        "Pedro Soriano", "Father", "09172334567"),
                new EmployeeSeed("Omar", null, "Delos Reyes", LocalDate.of(1997, 10, 12), LocalDate.of(2025, 12, 8), null,
                        BigDecimal.ZERO, ZERO_SALARY_TEST_EMAIL, true, "09170000024", Gender.MALE,
                        EmploymentStatus.REGULAR, ExemptionStatus.NON_EXEMPT, Position.EMPLOYEE, null,
                        "402 Test Case Ave", null, "Pasig", "Metro Manila", "Philippines", "1604",
                        "24-2222222-3", "27-555555555-6", "2434-5678-9012", "243-456-789-000",
                        "Liza Delos Reyes", "Wife", "09172434567")
        );
    }

    private List<CalendarSeed> calendarSeeds() {
        return List.of(
                new CalendarSeed("Quarterly Orientation",
                        "Quarterly onboarding, policy refreshers, and introductions for all employees.",
                        LocalDateTime.of(2026, 2, 1, 9, 0),
                        LocalDateTime.of(2026, 2, 1, 17, 0),
                        2,
                        LocalDateTime.of(2026, 2, 1, 8, 30)),
                new CalendarSeed("Strategy Workshop",
                        "Leadership and department heads align on Q2 execution priorities and KPIs.",
                        LocalDateTime.of(2026, 2, 5, 8, 0),
                        LocalDateTime.of(2026, 2, 6, 17, 0),
                        0,
                        LocalDateTime.of(2026, 2, 2, 10, 15)),
                new CalendarSeed("Security Audit",
                        "Internal systems and access review covering devices, credentials, and compliance checks.",
                        LocalDateTime.of(2026, 2, 10, 9, 0),
                        LocalDateTime.of(2026, 2, 11, 17, 0),
                        2,
                        LocalDateTime.of(2026, 2, 4, 9, 0)),
                new CalendarSeed("Leadership Summit",
                        "Management forum for escalation handling, staffing plans, and budget checkpoints.",
                        LocalDateTime.of(2026, 2, 16, 10, 0),
                        LocalDateTime.of(2026, 2, 16, 15, 0),
                        1,
                        LocalDateTime.of(2026, 2, 8, 11, 45)),
                new CalendarSeed("Team Building Day",
                        "Cross-functional games, workshops, and employee engagement activities.",
                        LocalDateTime.of(2026, 2, 21, 8, 0),
                        LocalDateTime.of(2026, 2, 21, 17, 0),
                        3,
                        LocalDateTime.of(2026, 2, 12, 14, 0)),
                new CalendarSeed("General Assembly",
                        "Company-wide updates on financial performance, operations, and people initiatives.",
                        LocalDateTime.of(2026, 2, 25, 14, 0),
                        LocalDateTime.of(2026, 2, 25, 16, 0),
                        0,
                        LocalDateTime.of(2026, 2, 15, 16, 20)),
                new CalendarSeed("Infrastructure Review",
                        "Review of office systems, attendance tooling, and deployment readiness.",
                        LocalDateTime.of(2026, 3, 1, 9, 0),
                        LocalDateTime.of(2026, 3, 1, 12, 0),
                        3,
                        LocalDateTime.of(2026, 2, 18, 9, 40)),
                new CalendarSeed("Company Holiday",
                        "Observed company holiday. Offices are closed except for approved essential operations.",
                        LocalDateTime.of(2026, 3, 7, 0, 0),
                        LocalDateTime.of(2026, 3, 7, 23, 59, 59),
                        2,
                        LocalDateTime.of(2026, 2, 20, 8, 0)),
                new CalendarSeed("Project Kickoff",
                        "Kickoff for the next internal systems milestone with scope and owner alignment.",
                        LocalDateTime.of(2026, 3, 18, 9, 0),
                        LocalDateTime.of(2026, 3, 18, 10, 0),
                        1,
                        LocalDateTime.of(2026, 2, 25, 13, 0)),
                new CalendarSeed("Stakeholder Meeting",
                        "Monthly stakeholder sync for delivery risks, budget status, and staffing concerns.",
                        LocalDateTime.of(2026, 3, 28, 10, 0),
                        LocalDateTime.of(2026, 3, 28, 12, 0),
                        0,
                        LocalDateTime.of(2026, 3, 10, 15, 30))
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
            Gender gender,
            EmploymentStatus employmentStatus,
            ExemptionStatus exemptionStatus,
            Position position,
            ManagerTitle empTitle,
            String permanentAddress,
            String temporaryAddress,
            String city,
            String state,
            String country,
            String zipCode,
            String sssNumber,
            String philhealthNumber,
            String pagibigNumber,
            String tinNumber,
            String emergencyContactName,
            String emergencyContactRelationship,
            String emergencyContactNumber
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

    private record AttendanceAnomalyPlan(
            Set<LocalDate> lateDates,
            Set<LocalDate> absentDates,
            Set<LocalDate> incompleteMissingOutDates,
            Set<LocalDate> incompleteMissingInDates
    ) {
    }

    private enum AttendanceVariance {
        ON_TIME,
        LATE_IN,
        EARLY_OUT,
        LATE_IN_AND_EARLY_OUT,
        ABSENT,
        INCOMPLETE_MISSING_OUT,
        INCOMPLETE_MISSING_IN
    }
}
