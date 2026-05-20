package org.example.maridone.payroll;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.example.maridone.annotation.ExecutionTime;
import org.example.maridone.annotation.GeneratePayslip;
import org.example.maridone.common.CommonCalculator;
import org.example.maridone.common.CommonSpecs;
import org.example.maridone.config.DefaultConfig;
import org.example.maridone.config.PayrollConfig;
import org.example.maridone.core.employee.Employee;
import org.example.maridone.core.employee.EmployeeRepository;
import org.example.maridone.enums.DeductionType;
import org.example.maridone.enums.EarningsType;
import org.example.maridone.enums.EmploymentStatus;
import org.example.maridone.enums.ExemptionStatus;
import org.example.maridone.enums.PayrollStatus;
import org.example.maridone.enums.Position;
import org.example.maridone.enums.RunType;
import org.example.maridone.enums.Status;
import org.example.maridone.exception.notfound.RunNotFoundException;
import org.example.maridone.exception.badrequest.InvalidActionException;
import org.example.maridone.holiday.HolidayLookup;
import org.example.maridone.holiday.HolidayService;
import org.example.maridone.leave.request.LeaveRequest;
import org.example.maridone.leave.request.LeaveRequestRepository;
import org.example.maridone.log.AttendanceLogRepository;
import org.example.maridone.log.attendance.AttendanceLog;
import org.example.maridone.notification.NotificationMessageFormatter;
import org.example.maridone.overtime.OvertimeRequest;
import org.example.maridone.overtime.OvertimeRequestRepository;
import org.example.maridone.overtime.spec.OvertimeSpecs;
import org.example.maridone.payroll.dto.PayrollRunViewDto;
import org.example.maridone.payroll.dto.RunCreateDto;
import org.example.maridone.payroll.event.PayRunNotifyEvent;
import org.example.maridone.payroll.item.PayrollItem;
import org.example.maridone.payroll.item.PayrollItemService;
import org.example.maridone.payroll.item.component.DeductionsLine;
import org.example.maridone.payroll.item.component.EarningsLine;
import org.example.maridone.payroll.item.component.EmployerContributionLine;
import org.example.maridone.payroll.run.PayrollItemRepository;
import org.example.maridone.payroll.run.PayrollRun;
import org.example.maridone.payroll.run.PayrollRunRepository;
import org.example.maridone.schedule.shift.DailyShiftRepository;
import org.example.maridone.schedule.shift.DailyShiftSchedule;
import org.example.maridone.schedule.shift.TemplateShiftRepository;
import org.example.maridone.schedule.shift.TemplateShiftSchedule;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
public class PayrollService {
    private static final Set<DeductionType> ATTENDANCE_DEDUCTION_TYPES = EnumSet.of(
            DeductionType.ABSENT_DEDUCTION,
            DeductionType.LATE_PENALTY
    );
    private static final Set<DeductionType> WITHHOLDING_TYPES = EnumSet.of(
            DeductionType.BRACKET_LEVEL_ONE,
            DeductionType.BRACKET_LEVEL_TWO,
            DeductionType.BRACKET_LEVEL_THREE,
            DeductionType.BRACKET_LEVEL_FOUR,
            DeductionType.BRACKET_LEVEL_FIVE,
            DeductionType.BRACKET_LEVEL_SIX
    );

    private final PayrollRunRepository payrollRunRepository;
    private final PayrollItemRepository payrollItemRepository;
    private final EmployeeRepository employeeRepository;
    private final AttendanceLogRepository attendanceLogRepository;
    private final DailyShiftRepository dailyShiftRepository;
    private final TemplateShiftRepository templateShiftRepository;
    private final OvertimeRequestRepository overtimeRequestRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final HolidayService holidayService;
    private final PayrollCalculator payrollCalculator;
    private final DefaultConfig defaultConfig;
    private final PayrollConfig payrollConfig;
    private final PayrollItemService payrollItemService;
    private final ApplicationEventPublisher eventPublisher;

    public PayrollService(
            PayrollRunRepository payrollRunRepository,
            PayrollItemRepository payrollItemRepository,
            EmployeeRepository employeeRepository,
            AttendanceLogRepository attendanceLogRepository,
            DailyShiftRepository dailyShiftRepository,
            TemplateShiftRepository templateShiftRepository,
            OvertimeRequestRepository overtimeRequestRepository,
            LeaveRequestRepository leaveRequestRepository,
            HolidayService holidayService,
            PayrollCalculator payrollCalculator,
            DefaultConfig defaultConfig,
            PayrollConfig payrollConfig,
            PayrollItemService payrollItemService,
            ApplicationEventPublisher eventPublisher
    )
    {
        this.payrollRunRepository = payrollRunRepository;
        this.payrollItemRepository = payrollItemRepository;
        this.employeeRepository = employeeRepository;
        this.attendanceLogRepository = attendanceLogRepository;
        this.dailyShiftRepository = dailyShiftRepository;
        this.templateShiftRepository = templateShiftRepository;
        this.overtimeRequestRepository = overtimeRequestRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.holidayService = holidayService;
        this.payrollCalculator = payrollCalculator;
        this.defaultConfig = defaultConfig;
        this.payrollConfig = payrollConfig;
        this.payrollItemService = payrollItemService;
        this.eventPublisher = eventPublisher;
    }



    @Transactional
    @ExecutionTime
    public PayrollRun createRun(RunCreateDto payload) {
        assertValidPeriod(payload);
        PayrollRun run = new PayrollRun();
        run.setPeriodDescription(payload.getPeriodDescription());
        run.setRunType(payload.getRunType());
        run.setPayrollStatus(PayrollStatus.IN_PROGRESS);
        run.setPeriodEnd(payload.getPeriodEnd());
        run.setPeriodStart(payload.getPeriodStart());
        run.setPaymentDate(payload.getPaymentDate() != null ? payload.getPaymentDate() : payload.getPeriodEnd());
        payrollRunRepository.save(run);
        return run;
    }

    @Transactional
    @ExecutionTime
    @GeneratePayslip
    public PayrollRunViewDto postRun(Long payId) {
        PayrollRun run = payrollRunRepository.findById(payId)
                .orElseThrow(() -> new RunNotFoundException("Payroll Run ID:" + payId + " not found."));
        if (run.getPayrollStatus() == PayrollStatus.POSTED) {
            throw new InvalidActionException("This payroll run has already been posted.");
        }
        List<Long> empIds = payrollItemRepository.findEmployeeIdsByPayrollRun(payId);
        if (empIds.isEmpty()) {
            throw new InvalidActionException("This payroll run has no generated payroll items to post yet.");
        }
        run.setPayrollStatus(PayrollStatus.POSTED);
        payrollRunRepository.save(run);

        eventPublisher.publishEvent(new PayRunNotifyEvent(empIds, formatPayRunNotification(run), payId));
        return getRun(payId);
    }

    private String formatPayRunNotification(PayrollRun payrollRun) {
        LocalDate periodStart = payrollRun.getPeriodStart();
        LocalDate periodEnd = payrollRun.getPeriodEnd();

        if (periodStart == null || periodEnd == null) {
            return "Your payslip is ready.";
        }

        return "Your payslip for "
                + periodStart.format(NotificationMessageFormatter.DATE_FORMATTER)
                + " to "
                + periodEnd.format(NotificationMessageFormatter.DATE_FORMATTER)
                + " is ready.";
    }

    @Transactional(readOnly = true)
    @ExecutionTime
    public List<PayrollRunViewDto> getRuns() {
        return payrollRunRepository.findAllRunViews();
    }

    @Transactional(readOnly = true)
    @ExecutionTime
    public PayrollRunViewDto getRun(Long payId) {
        return payrollRunRepository.findRunViewByPayId(payId)
                .orElseThrow(() -> new RunNotFoundException("Payroll Run ID:" + payId + " not found."));
    }

    @Transactional
    @ExecutionTime
    public PayrollRunViewDto processPayroll(RunCreateDto payload) {
        assertProcessablePayroll(payload);

        //create a payroll run for periodStart - periodEnd
        PayrollRun run = createRun(payload);

        // Only include employees whose employment window overlaps the payroll period.
        List<Employee> employees = findPayrollEligibleEmployees(run);
        // Automatic payroll should only include employees with generated work schedules
        // for the period, or paid leave that legitimately replaces those schedules.
        employees = filterEmployeesWithPayrollInputs(run, employees);
        //separate employees by exempt and nonexempt
        Map<Boolean, List<Employee>> partitioned = partitionEmployeesByExemption(employees);
        List<Employee> exemptEmployees = partitioned.getOrDefault(true, List.of());
        List<Employee> nonExemptEmployees = partitioned.getOrDefault(false, List.of());
        List<Long> employeeIds = employees.stream()
                .map(Employee::getEmployeeId)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, Map<DayOfWeek, List<TemplateShiftSchedule>>> templateSchedulesByEmployeeAndDay =
                groupTemplateSchedulesByEmployeeAndDay(findTemplateSchedules(employeeIds));

        List<PayrollItem> items = new ArrayList<>(processPayrollExempt(
                run,
                exemptEmployees,
                templateSchedulesByEmployeeAndDay
        ));

        processPayrollNonExempt(run, items, nonExemptEmployees, templateSchedulesByEmployeeAndDay);
        payrollItemRepository.saveAll(items);
        return getRun(run.getPayId());
    }

    //for exempt employees
    @Transactional
    public List<PayrollItem> processPayrollExempt(
            PayrollRun run,
            List<Employee> employees,
            Map<Long, Map<DayOfWeek, List<TemplateShiftSchedule>>> templateSchedulesByEmployeeAndDay
    ) {
        //get all employee ids of exempt employees
        //already filtered by processPayroll()
        List<Long> employeeIds = employees.stream()
                .map(Employee::getEmployeeId)
                .toList();
        if (employeeIds.isEmpty()) {
            return List.of();
        }

        //get start period of the payroll run
        Instant attendanceWindowStart = run.getPeriodStart()
                .atStartOfDay(defaultConfig.getTimeZone())
                .toInstant();
        LocalDateTime schedulePeriodStart = run.getPeriodStart().atStartOfDay();
        LocalDateTime schedulePeriodEndExclusive = run.getPeriodEnd().plusDays(1).atStartOfDay();

        //sort the attendance log by timestamp [OLDEST FIRST]
        Sort sorting = Sort.by(Sort.Direction.ASC, "timestamp");

        /*
         * get a sorted list of attendance logs for a specific group of employees
         * that occurred within a defined time window.
         *
         * employeeIds: whose logs will we get?
         * periodStart:  The start of the time period (inclusive)
         * periodEnd: The end of the time period (inclusive)
         *      +6 hours to obtain night diff logs too
         * sorting: sorted by oldest logs first
         */
        List<AttendanceLog> allLogs = findAttendanceLogs(employeeIds, run, attendanceWindowStart, sorting);

        //get all schedules of employees in employeeIds
        List<DailyShiftSchedule> allDailyScheduleEntries = findAllDailySchedules(
                employeeIds,
                schedulePeriodStart,
                schedulePeriodEndExclusive
        );
        List<DailyShiftSchedule> allSchedules = filterPayableDailySchedules(allDailyScheduleEntries);
        //get all APPROVED leaves of employees in employeeIds for payroll period
        List<LeaveRequest> allLeaves = findApprovedLeaves(employeeIds, schedulePeriodStart, schedulePeriodEndExclusive);

        //find any holidays for a day in payroll period
        HolidayLookup holidayLookup = holidayService.getHolidayLookup(run.getPeriodStart(), run.getPeriodEnd());
        Set<LocalDate> holidayDates = holidayLookup.holidayDates();

        //group List of Objects(Attendance, DailyShifts, LeaveRequest) to an employeeId inside their object
        Map<Long, List<AttendanceLog>> attendanceMap = groupAttendanceLogs(allLogs);
        Map<Long, List<DailyShiftSchedule>> scheduleMap = groupSchedulesByEmployee(allSchedules);
        Map<Long, Map<LocalDate, List<DailyShiftSchedule>>> allDailySchedulesByEmployeeAndDate =
                groupAllDailySchedulesByEmployeeAndDate(allDailyScheduleEntries);
        Map<Long, List<LeaveRequest>> leaveMap = groupLeavesByEmployee(allLeaves);

        //iterate through employees
        return employees.stream()
                .map(emp -> {
                    EmploymentWindow employmentWindow = resolveEmploymentWindow(emp, run)
                            .orElseThrow(() -> new InvalidActionException(
                                    "Employee " + emp.getEmployeeId() + " does not overlap this payroll run."
                            ));
                    //create payroll item for the employee
                    PayrollItem item = new PayrollItem();

                    //compute salary for payroll period
                    BigDecimal semiMonthlySalary = resolveProratedBasicPay(
                            emp,
                            run,
                            employmentWindow,
                            allDailySchedulesByEmployeeAndDate,
                            templateSchedulesByEmployeeAndDay
                    );
                    //compute how much should be subtracted per day.
                    BigDecimal dailyAbsenceRate = payrollCalculator.computeExemptDailyAbsenceRate(emp.getYearlySalary());
                    //get attendance of employee, else empty list
                    List<AttendanceLog> attendanceLogs = attendanceMap.getOrDefault(emp.getEmployeeId(), List.of());
                    List<DailyShiftSchedule> schedules = scheduleMap.getOrDefault(emp.getEmployeeId(), List.of());
                    List<LeaveRequest> leaves = leaveMap.getOrDefault(emp.getEmployeeId(), List.of());
                    Map<LocalDate, List<DailyShiftSchedule>> schedulesByDate = schedules.stream()
                            .collect(Collectors.groupingBy(DailyShiftSchedule::getWorkDate));
                    AttendanceDeductionTotals attendanceDeductions = calculateExemptAttendanceDeductions(
                            employmentWindow.start(),
                            employmentWindow.end(),
                            attendanceLogs,
                            schedulesByDate,
                            leaves,
                            dailyAbsenceRate,
                            holidayDates,
                            semiMonthlySalary
                    );

                    item.setEmployee(emp);
                    item.setPayrollRun(run);
                    item.setGrossPay(semiMonthlySalary);
                    item.setDisputes(new ArrayList<>());
                    item.setEarnings(payrollCalculator.setEarnings(emp, item));
                    item.getEarnings().forEach(line -> line.setPayrollItem(item));
                    PayrollCalculator.RegularTaxContext taxContext = resolveRegularTaxContext(emp, run, item);
                    item.setEmployerContributions(buildEmployerContributionLines(item, taxContext));
                    List<DeductionsLine> deductions = payrollCalculator.setDeductions(
                            emp,
                            item,
                            attendanceDeductions.absentDeduction(),
                            attendanceDeductions.lateDeduction(),
                            taxContext
                    );
                    item.setDeductions(deductions);
                    BigDecimal totalDeductions = sumDeductionAmounts(deductions);
                    item.setNetPay(CommonCalculator.normalizeNonNegative(semiMonthlySalary.subtract(totalDeductions)));
                    return item;
                })
                .toList();
    }

    //for nonexempt
    @Transactional
    public void processPayrollNonExempt(
            PayrollRun run,
            List<PayrollItem> items,
            List<Employee> employees,
            Map<Long, Map<DayOfWeek, List<TemplateShiftSchedule>>> templateSchedulesByEmployeeAndDay
    ) {

        List<Long> employeeIds = employees.stream()
                .map(Employee::getEmployeeId)
                .toList();
        // skip the whole non-exempt path if this run has no non-exempt employees.
        if (employeeIds.isEmpty()) {
            return;
        }

        // keep overnight outs up to 6am attached to the work date so nd and overnight attendance still pair correctly.
        Instant attendanceWindowStart = run.getPeriodStart()
                .atStartOfDay(defaultConfig.getTimeZone())
                .toInstant();
        LocalDateTime schedulePeriodStart = run.getPeriodStart().atStartOfDay();
        LocalDateTime schedulePeriodEndExclusive = run.getPeriodEnd().plusDays(1).atStartOfDay();

        Sort sorting = Sort.by(Sort.Direction.ASC, "timestamp");
        List<AttendanceLog> allLogs = findAttendanceLogs(employeeIds, run, attendanceWindowStart, sorting);

        // only approved overtime inside the payroll window contributes extra pay.
        Specification<OvertimeRequest> overtimeSpecs = Specification.allOf(
                OvertimeSpecs.hasStatus(Status.APPROVED),
                CommonSpecs.fieldEquals("isPaid", true),
                OvertimeSpecs.hasEmployeeIds(employeeIds),
                CommonSpecs.fieldIsBetween(
                        "workDate",
                        run.getPeriodStart(),
                        run.getPeriodEnd())
        );

        List<OvertimeRequest> overtimeRequests = overtimeRequestRepository.findAll(overtimeSpecs);

        List<DailyShiftSchedule> allDailyScheduleEntries = findAllDailySchedules(
                employeeIds,
                schedulePeriodStart,
                schedulePeriodEndExclusive
        );
        List<DailyShiftSchedule> allSchedules = filterPayableDailySchedules(allDailyScheduleEntries);

        List<LeaveRequest> allLeaves = findApprovedLeaves(employeeIds, schedulePeriodStart, schedulePeriodEndExclusive);

        HolidayLookup holidayLookup = holidayService.getHolidayLookup(run.getPeriodStart(), run.getPeriodEnd());
        Set<LocalDate> holidayDates = holidayLookup.holidayDates();
        Set<LocalDate> regularHolidayDates = holidayLookup.regularHolidayDates();

        // group shared data up front so the inner employee/day loop only does simple lookups.
        Map<Long, List<AttendanceLog>> attendanceMap = groupAttendanceLogs(allLogs);

        Map<Long, List<DailyShiftSchedule>> scheduleMap = groupSchedulesByEmployee(allSchedules);
        Map<Long, Map<LocalDate, List<DailyShiftSchedule>>> allDailySchedulesByEmployeeAndDate =
                groupAllDailySchedulesByEmployeeAndDate(allDailyScheduleEntries);

        Map<Long, List<OvertimeRequest>> overtimeMap = groupOvertimeRequestsByEmployee(overtimeRequests);

        Map<Long, List<LeaveRequest>> leaveMap = groupLeavesByEmployee(allLeaves);

        for (Employee emp :  employees) {
            EmploymentWindow employmentWindow = resolveEmploymentWindow(emp, run)
                    .orElse(null);
            if (employmentWindow == null) {
                continue;
            }
            // non-exempt payroll starts from cutoff basic pay, then layers in variable earnings and attendance deductions.
            List<OvertimeRequest> requests = overtimeMap.getOrDefault(emp.getEmployeeId(), List.of());
            List<DailyShiftSchedule> schedules = scheduleMap.getOrDefault(emp.getEmployeeId(), List.of());
            List<AttendanceLog> attendanceLogs = attendanceMap.getOrDefault(emp.getEmployeeId(), List.of());
            List<LeaveRequest> leaves = leaveMap.getOrDefault(emp.getEmployeeId(), List.of());

            BigDecimal basicPayPerPeriod = resolveProratedBasicPay(
                    emp,
                    run,
                    employmentWindow,
                    allDailySchedulesByEmployeeAndDate,
                    templateSchedulesByEmployeeAndDay
            );
            BigDecimal hourlyRate = payrollCalculator.computeHourlyRate(emp.getYearlySalary());
            Map<LocalDate, List<DailyShiftSchedule>> schedulesByDate = schedules.stream()
                    .collect(Collectors.groupingBy(DailyShiftSchedule::getWorkDate));

            BigDecimal absentDeductAmount = BigDecimal.ZERO;
            BigDecimal lateDeductAmount = BigDecimal.ZERO;
            List<EarningsLine> earningsLines = new ArrayList<>();

            LocalDate currentDate = employmentWindow.start();
            while (!currentDate.isAfter(employmentWindow.end())) {
                LocalDate loopDate = currentDate;

                // build all in-out sessions for the work date instead of relying on only the first pair.
                List<PayrollCalculator.AttendanceSession> attendanceSessions =
                        payrollCalculator.findAttendanceSessionsForDate(attendanceLogs, loopDate);
                boolean hasCompleteAttendancePair = !attendanceSessions.isEmpty();
                AttendanceLog dayInLog = hasCompleteAttendancePair ? attendanceSessions.get(0).inLog() : null;

                // resolve everything for this date together so shifts, leave, attendance, and ot stay in sync.
                LeaveRequest todaysLeave = findLeaveForDate(leaves, loopDate);
                List<DailyShiftSchedule> todaysSchedules = schedulesByDate.getOrDefault(loopDate, List.of());
                DailyShiftSchedule primarySchedule = findEarliestSchedule(todaysSchedules);

                List<OvertimeRequest> todaysOT = requests.stream()
                        .filter(ot -> ot.getWorkDate().equals(loopDate))
                        .filter(ot -> Boolean.TRUE.equals(ot.getPaid()))
                        .toList();
                List<PayableOvertimeBreakdown> payableTodaysOT = hasCompleteAttendancePair
                        ? todaysOT.stream()
                                .map(ot -> new PayableOvertimeBreakdown(
                                        ot,
                                        payrollCalculator.calculateWorkedHoursWithinWindow(
                                                attendanceSessions,
                                                ot.getStartTime(),
                                                ot.getEndTime()
                                        ),
                                        payrollCalculator.calculateNightDiffHoursWithinWindow(
                                                attendanceSessions,
                                                ot.getStartTime(),
                                                ot.getEndTime()
                                        )
                                ))
                                .filter(ot -> ot.payableHours().compareTo(BigDecimal.ZERO) > 0)
                                .toList()
                        : List.of();

                // non-exempt attendance is driven by the generated daily shifts for the date.
                boolean isRestDay = todaysSchedules.isEmpty() && todaysLeave == null;
                boolean isHoliday = holidayDates.contains(loopDate);
                boolean isRegularHoliday = regularHolidayDates.contains(loopDate);
                BigDecimal dayWorkPremiumRate = payrollCalculator.resolveHolidayOrRestDayPremiumRate(
                        isRestDay,
                        isHoliday,
                        isRegularHoliday
                );
                BigDecimal dayWorkMultiplier = payrollCalculator.resolveHolidayOrRestDayMultiplier(
                        isRestDay,
                        isHoliday,
                        isRegularHoliday
                );
                BigDecimal payableOvertimeHours = payableTodaysOT.stream()
                        .map(PayableOvertimeBreakdown::payableHours)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal payableNightOvertimeHours = payableTodaysOT.stream()
                        .map(PayableOvertimeBreakdown::nightHours)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                // worked rest-day and holiday hours earn premium lines instead of ordinary attendance deductions.
                if (isRestDay || isHoliday) {
                    if (hasCompleteAttendancePair) {
                        BigDecimal hoursWorked = payrollCalculator.deductUnpaidLunchHour(
                                payrollCalculator.calculateHoursFromAttendanceSessions(attendanceSessions)
                        );

                        // base pay already covers ordinary hours, so this line only adds the premium increment.
                        BigDecimal premiumHours = hoursWorked.subtract(payableOvertimeHours).max(BigDecimal.ZERO);
                        BigDecimal premiumMultiplier = dayWorkPremiumRate;

                        if (premiumHours.compareTo(BigDecimal.ZERO) > 0
                                && premiumMultiplier.compareTo(BigDecimal.ZERO) > 0) {
                            EarningsLine restDayEarnings = new EarningsLine();
                            restDayEarnings.setEarningsDate(loopDate);
                            restDayEarnings.setHours(premiumHours);
                            restDayEarnings.setRate(hourlyRate.multiply(premiumMultiplier));
                            restDayEarnings.setAmount(restDayEarnings.getHours().multiply(restDayEarnings.getRate()));
                            restDayEarnings.setOvertime(false);
                            restDayEarnings.setEarningsType(EarningsType.HOLIDAY);
                            restDayEarnings.setTaxableAmount(restDayEarnings.getAmount());
                            restDayEarnings.setNonTaxableAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
                            earningsLines.add(restDayEarnings);
                        }
                    }
                } else {
                    // ordinary workdays compare all attendance sessions against the split daily-shift segments.
                    BigDecimal expectedHours = payrollCalculator.calculatePayableScheduledHours(todaysSchedules);
                    if (expectedHours.compareTo(BigDecimal.ZERO) <= 0) {
                        // leave this day alone if the generated schedules carry no payable hours.
                    } else {
                        // scheduled coverage comes from actual worked hours plus any paid leave overlap on the same segments.
                        BigDecimal actualHours = hasCompleteAttendancePair
                                ? payrollCalculator.calculatePayableWorkedHoursWithinSchedules(
                                        todaysSchedules,
                                        attendanceSessions
                                )
                                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                        BigDecimal leaveHours = payrollCalculator.calculatePayableLeaveHoursWithinSchedules(
                                todaysSchedules,
                                todaysLeave
                        );
                        BigDecimal coveredHours = actualHours.add(leaveHours).min(expectedHours);

                        if (coveredHours.compareTo(BigDecimal.ZERO) <= 0) {
                            absentDeductAmount = absentDeductAmount.add(expectedHours.multiply(hourlyRate));
                        } else if (coveredHours.compareTo(expectedHours) < 0) {
                            BigDecimal missingHours = expectedHours.subtract(coveredHours);
                            lateDeductAmount = lateDeductAmount.add(missingHours.multiply(hourlyRate));
                        }
                    }
                }

                if (hasCompleteAttendancePair) {
                    // Split regular night work from OT-backed night work so OT night diff compounds on the OT rate.
                    BigDecimal totalNightHours = payrollCalculator.calculateNightDiffHours(attendanceSessions);
                    BigDecimal regularNightHours = totalNightHours.subtract(payableNightOvertimeHours).max(BigDecimal.ZERO);
                    if (regularNightHours.compareTo(BigDecimal.ZERO) > 0) {
                        EarningsLine nsdLine = new EarningsLine();
                        nsdLine.setEarningsDate(loopDate);
                        nsdLine.setHours(regularNightHours);
                        BigDecimal nsdRateMultiplier = dayWorkMultiplier.multiply(
                                payrollConfig.getNightDifferentialMultiplier()
                        );
                        nsdLine.setRate(hourlyRate.multiply(nsdRateMultiplier));
                        nsdLine.setAmount(regularNightHours.multiply(nsdLine.getRate()));
                        nsdLine.setOvertime(false);
                        nsdLine.setEarningsType(EarningsType.NIGHTDIFF);
                        nsdLine.setTaxableAmount(nsdLine.getAmount());
                        nsdLine.setNonTaxableAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
                        earningsLines.add(nsdLine);
                    }

                    for (PayableOvertimeBreakdown payableOt : payableTodaysOT) {
                        if (payableOt.nightHours().compareTo(BigDecimal.ZERO) <= 0) {
                            continue;
                        }
                        EarningsLine overtimeNsdLine = new EarningsLine();
                        overtimeNsdLine.setEarningsDate(loopDate);
                        overtimeNsdLine.setHours(payableOt.nightHours());
                        BigDecimal overtimeNsdRateMultiplier = dayWorkMultiplier
                                .multiply(payrollCalculator.resolveOvertimeMultiplier(
                                        payableOt.request().getOvertimeType()
                                ))
                                .multiply(payrollConfig.getNightDifferentialMultiplier());
                        overtimeNsdLine.setRate(hourlyRate.multiply(overtimeNsdRateMultiplier));
                        overtimeNsdLine.setAmount(payableOt.nightHours().multiply(overtimeNsdLine.getRate()));
                        overtimeNsdLine.setOvertime(false);
                        overtimeNsdLine.setEarningsType(EarningsType.NIGHTDIFF);
                        overtimeNsdLine.setTaxableAmount(overtimeNsdLine.getAmount());
                        overtimeNsdLine.setNonTaxableAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
                        earningsLines.add(overtimeNsdLine);
                    }
                }

                // OT earnings are limited to approved hours that also overlap actual attendance.
                for (PayableOvertimeBreakdown payableOt : payableTodaysOT) {
                    EarningsLine otLine = new EarningsLine();
                    otLine.setEarningsDate(loopDate);
                    otLine.setHours(payableOt.payableHours());
                    BigDecimal overtimeRateMultiplier = dayWorkMultiplier.multiply(
                            payrollCalculator.resolveOvertimeMultiplier(payableOt.request().getOvertimeType())
                    );
                    otLine.setRate(hourlyRate.multiply(overtimeRateMultiplier));
                    otLine.setAmount(otLine.getHours().multiply(otLine.getRate()));
                    otLine.setOvertime(true);
                    otLine.setOvertimeRequest(payableOt.request());
                    otLine.setEarningsType(payableOt.request().getOvertimeType());
                    otLine.setTaxableAmount(otLine.getAmount());
                    otLine.setNonTaxableAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
                    earningsLines.add(otLine);
                }

                currentDate = currentDate.plusDays(1);
            }

            // keep attendance deductions from eating more than the cutoff basic pay.
            AttendanceDeductionTotals cappedDeductions = applyAttendanceDeductionCap(
                    absentDeductAmount,
                    lateDeductAmount,
                    basicPayPerPeriod
            );
            absentDeductAmount = cappedDeductions.absentDeduction();
            lateDeductAmount = cappedDeductions.lateDeduction();

            PayrollItem item = new PayrollItem();
            item.setEmployee(emp);
            item.setPayrollRun(run);
            item.setDisputes(new ArrayList<>());
            // gross starts at basic cutoff pay and the calculated earnings lines add the variable pieces.
            item.setGrossPay(basicPayPerPeriod);

            List<EarningsLine> calculatedEarnings = payrollCalculator.setEarnings(emp, item, earningsLines);
            if (calculatedEarnings == null) {
                calculatedEarnings = List.of();
            }
            item.setEarnings(calculatedEarnings);
            item.getEarnings().forEach(line -> line.setPayrollItem(item));

            BigDecimal earningsTotal = sumEarningAmounts(item.getEarnings());

            BigDecimal grossPay = earningsTotal.setScale(2, RoundingMode.HALF_UP);
            item.setGrossPay(grossPay);

            PayrollCalculator.RegularTaxContext taxContext = resolveRegularTaxContext(emp, run, item);
            item.setEmployerContributions(buildEmployerContributionLines(item, taxContext));
            List<DeductionsLine> calculatedDeductions = payrollCalculator.setDeductions(
                    emp,
                    item,
                    absentDeductAmount,
                    lateDeductAmount,
                    taxContext
            );
            if (calculatedDeductions == null) {
                calculatedDeductions = List.of();
            }
            item.setDeductions(calculatedDeductions);
            item.getDeductions().forEach(line -> line.setPayrollItem(item));

            BigDecimal deductionsTotal = sumDeductionAmounts(item.getDeductions());

            BigDecimal netPay = CommonCalculator.normalizeNonNegative(grossPay.subtract(deductionsTotal));
            item.setNetPay(netPay);

            items.add(item);
        }
    }

    private AttendanceDeductionTotals calculateExemptAttendanceDeductions(
            LocalDate periodStart,
            LocalDate periodEnd,
            List<AttendanceLog> attendanceLogs,
            Map<LocalDate, List<DailyShiftSchedule>> schedulesByDate,
            List<LeaveRequest> leaves,
            BigDecimal dailyAbsenceRate,
            Set<LocalDate> holidayDates,
            BigDecimal deductionCap
    ) {
        if (periodStart == null || periodEnd == null || periodEnd.isBefore(periodStart)) {
            return applyAttendanceDeductionCap(BigDecimal.ZERO, BigDecimal.ZERO, deductionCap);
        }

        BigDecimal absentDeductAmount = BigDecimal.ZERO;

        LocalDate currentDate = periodStart;
        while (!currentDate.isAfter(periodEnd)) {
            boolean isHoliday = holidayDates.contains(currentDate);
            boolean hasOverlappingLeave = hasOverlappingLeave(
                    schedulesByDate.getOrDefault(currentDate, List.of()),
                    leaves
            );
            boolean hasAttendance = payrollCalculator.hasAnyAttendanceLogForDate(attendanceLogs, currentDate);
            boolean hasScheduledWork = !schedulesByDate.getOrDefault(currentDate, List.of()).isEmpty();

            // Exempt staff are treated as salary-preserved unless they miss the whole scheduled day without approved leave.
            if (hasScheduledWork && !isHoliday && !hasOverlappingLeave && !hasAttendance) {
                absentDeductAmount = absentDeductAmount.add(dailyAbsenceRate);
            }

            currentDate = currentDate.plusDays(1);
        }

        return applyAttendanceDeductionCap(absentDeductAmount, BigDecimal.ZERO, deductionCap);
    }

    private AttendanceDeductionTotals applyAttendanceDeductionCap(
            BigDecimal absentDeductAmount,
            BigDecimal lateDeductAmount,
            BigDecimal deductionCap
    ) {
        BigDecimal absent = absentDeductAmount == null ? BigDecimal.ZERO : absentDeductAmount;
        BigDecimal late = lateDeductAmount == null ? BigDecimal.ZERO : lateDeductAmount;
        BigDecimal cap = deductionCap == null ? BigDecimal.ZERO : deductionCap;

        BigDecimal attendanceDeductionTotal = absent.add(late);
        if (attendanceDeductionTotal.compareTo(cap) > 0) {
            BigDecimal overflow = attendanceDeductionTotal.subtract(cap);
            if (late.compareTo(overflow) >= 0) {
                late = late.subtract(overflow);
            } else {
                overflow = overflow.subtract(late);
                late = BigDecimal.ZERO;
                absent = absent.subtract(overflow).max(BigDecimal.ZERO);
            }
        }

        return new AttendanceDeductionTotals(
                absent.setScale(2, RoundingMode.HALF_UP),
                late.setScale(2, RoundingMode.HALF_UP)
        );
    }

    private DailyShiftSchedule findEarliestSchedule(List<DailyShiftSchedule> schedules) {
        if (schedules == null || schedules.isEmpty()) {
            return null;
        }

        return schedules.stream()
                .min(Comparator.comparing(DailyShiftSchedule::getStartDateTime))
                .orElse(null);
    }

    private LeaveRequest findLeaveForDate(List<LeaveRequest> leaves, LocalDate workDate) {
        return leaves.stream()
                .filter(l -> l.coversDate(workDate))
                .findFirst()
                .orElse(null);
    }

    private boolean hasOverlappingLeave(List<DailyShiftSchedule> schedules, List<LeaveRequest> leaves) {
        if (schedules == null || schedules.isEmpty() || leaves == null || leaves.isEmpty()) {
            return false;
        }

        return schedules.stream().anyMatch(schedule -> leaves.stream().anyMatch(leave -> leaveOverlapsSchedule(leave, schedule)));
    }

    private boolean leaveOverlapsSchedule(LeaveRequest leave, DailyShiftSchedule schedule) {
        if (leave == null
                || schedule == null
                || leave.getStartDateTime() == null
                || leave.getEndDateTime() == null
                || schedule.getStartDateTime() == null
                || schedule.getEndDateTime() == null) {
            return false;
        }

        return leave.getStartDateTime().isBefore(schedule.getEndDateTime())
                && leave.getEndDateTime().isAfter(schedule.getStartDateTime());
    }


    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private List<Employee> findPayrollEligibleEmployees(PayrollRun run) {
        return employeeRepository.findPayrollEligibleEmployees(
                run.getPeriodStart(),
                run.getPeriodEnd(),
                EmploymentStatus.TERMINATED
        ).stream()
                .filter(employee -> employee != null && employee.getPosition() != Position.ACCOUNTING)
                .toList();
    }

    private List<Employee> filterEmployeesWithPayrollInputs(PayrollRun run, List<Employee> employees) {
        if (employees == null || employees.isEmpty()) {
            return List.of();
        }

        List<Long> employeeIds = employees.stream()
                .map(Employee::getEmployeeId)
                .filter(Objects::nonNull)
                .toList();

        if (employeeIds.isEmpty()) {
            return List.of();
        }

        LocalDateTime schedulePeriodStart = run.getPeriodStart().atStartOfDay();
        LocalDateTime schedulePeriodEndExclusive = run.getPeriodEnd().plusDays(1).atStartOfDay();

        Set<Long> scheduledEmployeeIds = findDailySchedules(
                employeeIds,
                schedulePeriodStart,
                schedulePeriodEndExclusive
        ).stream()
                .map(DailyShiftSchedule::getEmployeeId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<Long> paidLeaveEmployeeIds = findApprovedLeaves(
                employeeIds,
                schedulePeriodStart,
                schedulePeriodEndExclusive
        ).stream()
                .map(LeaveRequest::getEmployee)
                .filter(Objects::nonNull)
                .map(Employee::getEmployeeId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        return employees.stream()
                .filter(employee -> {
                    Long employeeId = employee.getEmployeeId();
                    return employeeId != null
                            && (scheduledEmployeeIds.contains(employeeId) || paidLeaveEmployeeIds.contains(employeeId));
                })
                .toList();
    }

    private Map<Boolean, List<Employee>> partitionEmployeesByExemption(List<Employee> employees) {
        return employees.stream()
                .collect(Collectors.partitioningBy(
                        emp -> emp.getExemptionStatus().equals(ExemptionStatus.EXEMPT)
                ));
    }

    private List<AttendanceLog> findAttendanceLogs(
            List<Long> employeeIds,
            PayrollRun run,
            Instant attendanceWindowStart,
            Sort sorting
    ) {
        return attendanceLogRepository.findByEmployeeIdInAndTimestampBetween(
                employeeIds,
                attendanceWindowStart,
                run.getPeriodEnd().atTime(LocalTime.MAX).plusHours(6).atZone(defaultConfig.getTimeZone()).toInstant(),
                sorting
        );
    }

    private List<DailyShiftSchedule> findDailySchedules(
            List<Long> employeeIds,
            LocalDateTime schedulePeriodStart,
            LocalDateTime schedulePeriodEndExclusive
    ) {
        return findAllDailySchedules(
                employeeIds,
                schedulePeriodStart,
                schedulePeriodEndExclusive
        ).stream()
                .filter(schedule -> !schedule.isDayOff())
                .toList();
    }

    private List<DailyShiftSchedule> findAllDailySchedules(
            List<Long> employeeIds,
            LocalDateTime schedulePeriodStart,
            LocalDateTime schedulePeriodEndExclusive
    ) {
        return dailyShiftRepository.findAllByEmployeeIdsAndPeriod(
                employeeIds,
                schedulePeriodStart,
                schedulePeriodEndExclusive
        );
    }

    private List<DailyShiftSchedule> filterPayableDailySchedules(List<DailyShiftSchedule> allSchedules) {
        return safeList(allSchedules).stream()
                .filter(schedule -> !schedule.isDayOff())
                .toList();
    }

    private List<LeaveRequest> findApprovedLeaves(
            List<Long> employeeIds,
            LocalDateTime schedulePeriodStart,
            LocalDateTime schedulePeriodEndExclusive
    ) {
        return leaveRequestRepository.findApprovedLeavesForPeriod(
                employeeIds,
                schedulePeriodStart,
                schedulePeriodEndExclusive
        ).stream()
                .filter(leave -> Boolean.TRUE.equals(leave.getPaid()))
                .toList();
    }

    private Map<Long, List<AttendanceLog>> groupAttendanceLogs(List<AttendanceLog> allLogs) {
        return safeList(allLogs).stream()
                .collect(Collectors.groupingBy(AttendanceLog::getEmployeeId));
    }

    private Map<Long, List<DailyShiftSchedule>> groupSchedulesByEmployee(List<DailyShiftSchedule> allSchedules) {
        return safeList(allSchedules).stream()
                .collect(Collectors.groupingBy(DailyShiftSchedule::getEmployeeId));
    }

    private Map<Long, Map<LocalDate, List<DailyShiftSchedule>>> groupAllDailySchedulesByEmployeeAndDate(
            List<DailyShiftSchedule> allSchedules
    ) {
        return safeList(allSchedules).stream()
                .filter(schedule -> schedule.getEmployeeId() != null && schedule.getWorkDate() != null)
                .collect(Collectors.groupingBy(
                        DailyShiftSchedule::getEmployeeId,
                        Collectors.groupingBy(DailyShiftSchedule::getWorkDate)
                ));
    }

    private List<TemplateShiftSchedule> findTemplateSchedules(List<Long> employeeIds) {
        if (employeeIds == null || employeeIds.isEmpty()) {
            return List.of();
        }
        return templateShiftRepository.findAllByEmployeeIdIn(employeeIds);
    }

    private Map<Long, Map<DayOfWeek, List<TemplateShiftSchedule>>> groupTemplateSchedulesByEmployeeAndDay(
            List<TemplateShiftSchedule> templateSchedules
    ) {
        return safeList(templateSchedules).stream()
                .filter(schedule -> schedule.getEmployeeId() != null && schedule.getDayOfWeek() != null)
                .collect(Collectors.groupingBy(
                        TemplateShiftSchedule::getEmployeeId,
                        Collectors.groupingBy(TemplateShiftSchedule::getDayOfWeek)
                ));
    }

    private Optional<EmploymentWindow> resolveEmploymentWindow(Employee employee, PayrollRun run) {
        if (employee == null
                || run == null
                || run.getPeriodStart() == null
                || run.getPeriodEnd() == null
                || employee.getEmploymentDateStart() == null) {
            return Optional.empty();
        }

        LocalDate effectiveStart = employee.getEmploymentDateStart().isAfter(run.getPeriodStart())
                ? employee.getEmploymentDateStart()
                : run.getPeriodStart();
        LocalDate employmentEnd = employee.getEmploymentDateEnd();
        LocalDate effectiveEnd = employmentEnd == null || employmentEnd.isAfter(run.getPeriodEnd())
                ? run.getPeriodEnd()
                : employmentEnd;

        if (effectiveEnd.isBefore(effectiveStart)) {
            return Optional.empty();
        }

        return Optional.of(new EmploymentWindow(effectiveStart, effectiveEnd));
    }

    private BigDecimal resolveProratedBasicPay(
            Employee employee,
            PayrollRun run,
            EmploymentWindow employmentWindow,
            Map<Long, Map<LocalDate, List<DailyShiftSchedule>>> allDailySchedulesByEmployeeAndDate,
            Map<Long, Map<DayOfWeek, List<TemplateShiftSchedule>>> templateSchedulesByEmployeeAndDay
    ) {
        long fullPeriodWorkdays = countPlannedWorkdays(
                employee.getEmployeeId(),
                run.getPeriodStart(),
                run.getPeriodEnd(),
                allDailySchedulesByEmployeeAndDate,
                templateSchedulesByEmployeeAndDay
        );
        long employmentWindowWorkdays = countPlannedWorkdays(
                employee.getEmployeeId(),
                employmentWindow.start(),
                employmentWindow.end(),
                allDailySchedulesByEmployeeAndDate,
                templateSchedulesByEmployeeAndDay
        );

        if (fullPeriodWorkdays > 0) {
            return payrollCalculator.computeProratedSemiMonthlyBasicPay(
                    employee.getYearlySalary(),
                    employmentWindowWorkdays,
                    fullPeriodWorkdays
            );
        }

        long fullPeriodDays = countInclusiveDays(run.getPeriodStart(), run.getPeriodEnd());
        long employmentDays = countInclusiveDays(employmentWindow.start(), employmentWindow.end());
        return payrollCalculator.computeProratedSemiMonthlyBasicPay(
                employee.getYearlySalary(),
                employmentDays,
                fullPeriodDays
        );
    }

    private long countPlannedWorkdays(
            Long employeeId,
            LocalDate startDate,
            LocalDate endDate,
            Map<Long, Map<LocalDate, List<DailyShiftSchedule>>> allDailySchedulesByEmployeeAndDate,
            Map<Long, Map<DayOfWeek, List<TemplateShiftSchedule>>> templateSchedulesByEmployeeAndDay
    ) {
        if (employeeId == null || startDate == null || endDate == null || endDate.isBefore(startDate)) {
            return 0L;
        }

        long workdays = 0L;
        LocalDate currentDate = startDate;
        while (!currentDate.isAfter(endDate)) {
            if (hasPlannedWorkday(
                    employeeId,
                    currentDate,
                    allDailySchedulesByEmployeeAndDate,
                    templateSchedulesByEmployeeAndDay
            )) {
                workdays++;
            }
            currentDate = currentDate.plusDays(1);
        }

        return workdays;
    }

    private boolean hasPlannedWorkday(
            Long employeeId,
            LocalDate workDate,
            Map<Long, Map<LocalDate, List<DailyShiftSchedule>>> allDailySchedulesByEmployeeAndDate,
            Map<Long, Map<DayOfWeek, List<TemplateShiftSchedule>>> templateSchedulesByEmployeeAndDay
    ) {
        Map<LocalDate, List<DailyShiftSchedule>> dailySchedulesByDate =
                allDailySchedulesByEmployeeAndDate.getOrDefault(employeeId, Map.of());
        List<DailyShiftSchedule> dayEntries = dailySchedulesByDate.get(workDate);
        if (dayEntries != null && !dayEntries.isEmpty()) {
            return dayEntries.stream().anyMatch(schedule -> !schedule.isDayOff());
        }

        Map<DayOfWeek, List<TemplateShiftSchedule>> templatesByDay =
                templateSchedulesByEmployeeAndDay.getOrDefault(employeeId, Map.of());
        List<TemplateShiftSchedule> templateShifts = templatesByDay.getOrDefault(workDate.getDayOfWeek(), List.of());
        return !templateShifts.isEmpty();
    }

    private long countInclusiveDays(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
            return 0L;
        }
        return startDate.datesUntil(endDate.plusDays(1)).count();
    }

    private Map<Long, List<LeaveRequest>> groupLeavesByEmployee(List<LeaveRequest> allLeaves) {
        return safeList(allLeaves).stream()
                .collect(Collectors.groupingBy(leave -> leave.getEmployee().getEmployeeId()));
    }

    private Map<Long, List<OvertimeRequest>> groupOvertimeRequestsByEmployee(List<OvertimeRequest> overtimeRequests) {
        return safeList(overtimeRequests).stream()
                .collect(Collectors.groupingBy(OvertimeRequest::getEmployeeId));
    }

    private BigDecimal sumEarningAmounts(List<EarningsLine> earnings) {
        return safeList(earnings).stream()
                .map(EarningsLine::getAmount)
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumDeductionAmounts(List<DeductionsLine> deductions) {
        return safeList(deductions).stream()
                .map(DeductionsLine::getAmount)
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private PayrollCalculator.RegularTaxContext resolveRegularTaxContext(
            Employee employee,
            PayrollRun run,
            PayrollItem currentItem
    ) {
        if (employee == null
                || employee.getEmployeeId() == null
                || run == null
                || run.getRunType() == RunType.ADJUSTMENT
                || currentItem == null) {
            return PayrollCalculator.RegularTaxContext.empty();
        }

        LocalDate paymentDate = run.getPaymentDate() != null ? run.getPaymentDate() : run.getPeriodEnd();
        if (paymentDate == null || paymentDate.getDayOfMonth() != 30) {
            return PayrollCalculator.RegularTaxContext.empty();
        }

        Optional<PayrollItem> priorItem = resolvePriorRegularCutoffItem(employee.getEmployeeId(), paymentDate);
        BigDecimal currentBasicPay = resolveItemBasicPay(currentItem);
        BigDecimal currentGrossPay = resolveItemGrossPay(currentItem);

        BigDecimal priorBasicPay = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal priorGrossPay = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal priorMonthPayAfterAttendance = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal priorMonthWithholding = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        boolean hasPriorRegularCutoff = priorItem.isPresent();

        if (priorItem.isPresent()) {
            PayrollItem resolvedPriorItem = priorItem.get();
            priorBasicPay = resolveItemBasicPay(resolvedPriorItem);
            priorGrossPay = resolveItemGrossPay(resolvedPriorItem);
            BigDecimal priorAttendanceDeductions = sumMatchingDeductionAmounts(
                    resolvedPriorItem.getDeductions(),
                    ATTENDANCE_DEDUCTION_TYPES
            );
            priorMonthPayAfterAttendance = priorMonthPayAfterAttendance.add(
                    CommonCalculator.normalizeNonNegative(priorGrossPay.subtract(priorAttendanceDeductions))
            );
            priorMonthWithholding = priorMonthWithholding.add(
                    sumMatchingDeductionAmounts(resolvedPriorItem.getDeductions(), WITHHOLDING_TYPES)
            );
        }

        return new PayrollCalculator.RegularTaxContext(
                CommonCalculator.normalize(priorBasicPay.add(currentBasicPay)),
                CommonCalculator.normalize(priorGrossPay.add(currentGrossPay)),
                priorMonthPayAfterAttendance,
                priorMonthWithholding,
                hasPriorRegularCutoff
        );
    }

    private Optional<PayrollItem> resolvePriorRegularCutoffItem(Long employeeId, LocalDate currentPaymentDate) {
        if (employeeId == null || currentPaymentDate == null) {
            return Optional.empty();
        }

        List<PayrollItem> priorItems = safeList(
                payrollItemRepository.findByEmployee_EmployeeIdAndPayrollRun_RunTypeOrderByPayrollRun_PaymentDateDescPayrollRun_PeriodEndDescItemIdDesc(
                        employeeId,
                        RunType.REGULAR
                )
        ).stream()
                .filter(item -> isPriorRegularCutoffInSameMonth(item, currentPaymentDate))
                .toList();

        return priorItems.stream()
                .filter(item -> item.getPayrollRun() != null && item.getPayrollRun().getPayrollStatus() == PayrollStatus.POSTED)
                .findFirst()
                .or(() -> priorItems.stream().findFirst());
    }

    private List<EmployerContributionLine> buildEmployerContributionLines(
            PayrollItem item,
            PayrollCalculator.RegularTaxContext taxContext
    ) {
        if (item == null || item.getPayrollRun() == null || taxContext == null || !isMonthEndRegularRun(item.getPayrollRun())) {
            return new ArrayList<>();
        }

        List<EmployerContributionLine> lines = new ArrayList<>();
        lines.add(createEmployerContributionLine(
                item,
                org.example.maridone.enums.EmployerContributionType.SSS,
                payrollCalculator.computeSssEmployerShareMonthly(taxContext.monthlyGrossPay())
        ));
        lines.add(createEmployerContributionLine(
                item,
                org.example.maridone.enums.EmployerContributionType.PHILHEALTH,
                payrollCalculator.computePhilHealthEmployerShareMonthly(taxContext.monthlyBasicPay())
        ));
        lines.add(createEmployerContributionLine(
                item,
                org.example.maridone.enums.EmployerContributionType.PAGIBIG,
                payrollCalculator.computePagibigEmployerShareMonthly(taxContext.monthlyBasicPay())
        ));
        return lines;
    }

    private EmployerContributionLine createEmployerContributionLine(
            PayrollItem item,
            org.example.maridone.enums.EmployerContributionType type,
            BigDecimal amount
    ) {
        EmployerContributionLine line = new EmployerContributionLine();
        line.setPayrollItem(item);
        line.setContributionType(type);
        line.setAmount(CommonCalculator.normalize(amount));
        return line;
    }

    private boolean isMonthEndRegularRun(PayrollRun run) {
        if (run == null || run.getRunType() != RunType.REGULAR) {
            return false;
        }

        LocalDate paymentDate = run.getPaymentDate() != null ? run.getPaymentDate() : run.getPeriodEnd();
        return paymentDate != null && paymentDate.getDayOfMonth() == 30;
    }

    private BigDecimal resolveItemGrossPay(PayrollItem item) {
        if (item == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal earningsTotal = sumEarningAmounts(item.getEarnings());
        if (earningsTotal.compareTo(BigDecimal.ZERO) > 0) {
            return CommonCalculator.normalize(earningsTotal);
        }
        return CommonCalculator.normalize(item.getGrossPay());
    }

    private BigDecimal resolveItemBasicPay(PayrollItem item) {
        if (item == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal basicTotal = safeList(item.getEarnings()).stream()
                .filter(line -> line != null && line.getEarningsType() == EarningsType.BASIC)
                .map(EarningsLine::getAmount)
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), BigDecimal::add);

        if (basicTotal.compareTo(BigDecimal.ZERO) > 0) {
            return CommonCalculator.normalize(basicTotal);
        }
        return CommonCalculator.normalize(item.getGrossPay());
    }

    private boolean isPriorRegularCutoffInSameMonth(PayrollItem priorItem, LocalDate currentPaymentDate) {
        if (priorItem == null || priorItem.getPayrollRun() == null || currentPaymentDate == null) {
            return false;
        }

        PayrollRun priorRun = priorItem.getPayrollRun();
        LocalDate priorPaymentDate = priorRun.getPaymentDate() != null ? priorRun.getPaymentDate() : priorRun.getPeriodEnd();
        if (priorPaymentDate == null) {
            return false;
        }

        return priorRun.getRunType() == RunType.REGULAR
                && priorPaymentDate.getYear() == currentPaymentDate.getYear()
                && priorPaymentDate.getMonth() == currentPaymentDate.getMonth()
                && priorPaymentDate.isBefore(currentPaymentDate);
    }

    private BigDecimal sumMatchingDeductionAmounts(List<DeductionsLine> deductions, Set<DeductionType> types) {
        return safeList(deductions).stream()
                .filter(line -> line != null && line.getDeductionType() != null && types.contains(line.getDeductionType()))
                .map(DeductionsLine::getAmount)
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), BigDecimal::add);
    }

    private void assertValidPeriod(RunCreateDto payload) {
        if (payload == null || payload.getPeriodStart() == null || payload.getPeriodEnd() == null) {
            throw new InvalidActionException("Payroll period start and end dates are required.");
        }
        if (payload.getPeriodEnd().isBefore(payload.getPeriodStart())) {
            throw new InvalidActionException("Payroll period end cannot be earlier than the start date.");
        }
        LocalDate today = LocalDate.now(resolvePayrollTimeZone());
        if (payload.getPeriodStart().isAfter(today) || payload.getPeriodEnd().isAfter(today)) {
            throw new InvalidActionException("Payroll period dates cannot be later than today.");
        }
    }

    private ZoneId resolvePayrollTimeZone() {
        return defaultConfig != null && defaultConfig.getTimeZone() != null
                ? defaultConfig.getTimeZone()
                : ZoneId.systemDefault();
    }

    private void assertProcessablePayroll(RunCreateDto payload) {
        assertValidPeriod(payload);
        if (payload.getRunType() != RunType.REGULAR) {
            throw new InvalidActionException("Only regular payroll runs can be generated automatically.");
        }
        if (payrollRunRepository.existsByPeriodStartAndPeriodEndAndRunType(
                payload.getPeriodStart(),
                payload.getPeriodEnd(),
                RunType.REGULAR
        )) {
            throw new InvalidActionException("A regular payroll run already exists for this period.");
        }
    }

    private record AttendanceDeductionTotals(BigDecimal absentDeduction, BigDecimal lateDeduction) {}

    private record EmploymentWindow(LocalDate start, LocalDate end) {}

    private record PayableOvertimeBreakdown(
            OvertimeRequest request,
            BigDecimal payableHours,
            BigDecimal nightHours
    ) {}


}
