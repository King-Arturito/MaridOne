package org.example.maridone.payroll;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.example.maridone.annotation.ExecutionTime;
import org.example.maridone.common.CommonSpecs;
import org.example.maridone.config.DefaultConfig;
import org.example.maridone.config.PayrollConfig;
import org.example.maridone.core.employee.Employee;
import org.example.maridone.core.employee.EmployeeRepository;
import org.example.maridone.enums.EmploymentStatus;
import org.example.maridone.enums.ExemptionStatus;
import org.example.maridone.enums.Status;
import org.example.maridone.holiday.HolidayLookup;
import org.example.maridone.holiday.HolidayService;
import org.example.maridone.leave.request.LeaveRequest;
import org.example.maridone.leave.request.LeaveRequestRepository;
import org.example.maridone.log.AttendanceLogRepository;
import org.example.maridone.log.attendance.AttendanceLog;
import org.example.maridone.overtime.OvertimeRequest;
import org.example.maridone.overtime.OvertimeRequestRepository;
import org.example.maridone.overtime.spec.OvertimeSpecs;
import org.example.maridone.payroll.dto.RunCreateDto;
import org.example.maridone.payroll.item.PayrollItem;
import org.example.maridone.payroll.item.component.DeductionsLine;
import org.example.maridone.payroll.item.component.EarningsLine;
import org.example.maridone.payroll.mapper.PayrollMapper;
import org.example.maridone.payroll.run.PayrollItemRepository;
import org.example.maridone.payroll.run.PayrollRun;
import org.example.maridone.payroll.run.PayrollRunRepository;
import org.example.maridone.schedule.shift.DailyShiftRepository;
import org.example.maridone.schedule.shift.DailyShiftSchedule;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
public class PayrollService {

    private final PayrollRunRepository payrollRunRepository;
    private final PayrollItemRepository payrollItemRepository;
    private final EmployeeRepository employeeRepository;
    private final AttendanceLogRepository attendanceLogRepository;
    private final DailyShiftRepository dailyShiftRepository;
    private final OvertimeRequestRepository overtimeRequestRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final HolidayService holidayService;
    private final PayrollCalculator payrollCalculator;
    private final PayrollMapper payrollMapper;
    private final DefaultConfig defaultConfig;
    private final PayrollConfig payrollConfig;

    public PayrollService(
            PayrollRunRepository payrollRunRepository,
            PayrollItemRepository payrollItemRepository,
            EmployeeRepository employeeRepository,
            AttendanceLogRepository attendanceLogRepository,
            DailyShiftRepository dailyShiftRepository,
            OvertimeRequestRepository overtimeRequestRepository,
            LeaveRequestRepository leaveRequestRepository,
            HolidayService holidayService,
            PayrollCalculator payrollCalculator,
            PayrollMapper payrollMapper,
            DefaultConfig defaultConfig,
            PayrollConfig payrollConfig
    )
    {
        this.payrollRunRepository = payrollRunRepository;
        this.payrollItemRepository = payrollItemRepository;
        this.employeeRepository = employeeRepository;
        this.attendanceLogRepository = attendanceLogRepository;
        this.dailyShiftRepository = dailyShiftRepository;
        this.overtimeRequestRepository = overtimeRequestRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.holidayService = holidayService;
        this.payrollCalculator = payrollCalculator;
        this.payrollMapper = payrollMapper;
        this.defaultConfig = defaultConfig;
        this.payrollConfig = payrollConfig;
    }



    @Transactional
    @ExecutionTime
    public PayrollRun createRun(RunCreateDto payload) {
        PayrollRun run = new PayrollRun();
        run.setPeriodDescription(payload.getPeriodDescription());
        run.setRunType(payload.getRunType());
        run.setPayrollStatus(payload.getPayrollStatus());
        run.setPeriodEnd(payload.getPeriodEnd());
        run.setPeriodStart(payload.getPeriodStart());
        payrollRunRepository.save(run);
        return run;
    }


    @Transactional
    @ExecutionTime
    public void processPayroll(RunCreateDto payload) {
        //create a payroll run for periodStart - periodEnd
        PayrollRun run = createRun(payload);

        Specification<Employee> spec = CommonSpecs.fieldNotEquals("employmentStatus", EmploymentStatus.TERMINATED);
        //get non-terminated employees
        List<Employee> employees = employeeRepository.findAll(spec);
        //separate employees by exempt and nonexempt
        Map<Boolean, List<Employee>> partitioned = employees.stream()
                .collect(Collectors.partitioningBy(
                        emp -> emp.getExemptionStatus().equals(ExemptionStatus.EXEMPT)
                ));
        List<Employee> exemptEmployees = partitioned.getOrDefault(true, List.of());
        List<Employee> nonExemptEmployees = partitioned.getOrDefault(false, List.of());

        List<PayrollItem> items = new ArrayList<>(processPayrollExempt(run, exemptEmployees));

        processPayrollNonExempt(run, items, nonExemptEmployees);
        payrollItemRepository.saveAll(items);
    }


    //for exempt employees
    @Transactional
    public List<PayrollItem> processPayrollExempt(PayrollRun run, List<Employee> employees) {
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
        List<AttendanceLog> allLogs = attendanceLogRepository.findByEmployeeIdInAndTimestampBetween(
                employeeIds,
                attendanceWindowStart,
                run.getPeriodEnd().atTime(LocalTime.MAX).plusHours(6).atZone(defaultConfig.getTimeZone()).toInstant(),
                sorting
        );

        //get all schedules of employees in employeeIds
        List<DailyShiftSchedule> allSchedules = dailyShiftRepository.findAllByEmployeeIdsAndPeriod(
                employeeIds,
                schedulePeriodStart,
                schedulePeriodEndExclusive
        );
        //get all APPROVED leaves of employees in employeeIds for payroll period
        List<LeaveRequest> allLeaves = leaveRequestRepository.findApprovedLeavesForPeriod(
                employeeIds,
                schedulePeriodStart,
                schedulePeriodEndExclusive
        );

        //find any holidays for a day in payroll period
        HolidayLookup holidayLookup = holidayService.getHolidayLookup(run.getPeriodStart(), run.getPeriodEnd());
        Set<LocalDate> holidayDates = holidayLookup.holidayDates();

        //group List of Objects(Attendance, DailyShifts, LeaveRequest) to an employeeId inside their object
        Map<Long, List<AttendanceLog>> attendanceMap = safeList(allLogs).stream()
                .collect(Collectors.groupingBy(AttendanceLog::getEmployeeId));
        Map<Long, List<DailyShiftSchedule>> scheduleMap = safeList(allSchedules).stream()
                .collect(Collectors.groupingBy(DailyShiftSchedule::getEmployeeId));
        Map<Long, List<LeaveRequest>> leaveMap = safeList(allLeaves).stream()
                .collect(Collectors.groupingBy(leave -> leave.getEmployee().getEmployeeId()));

        //iterate through employees
        return employees.stream()
                .map(emp -> {
                    //create payroll item for the employee
                    PayrollItem item = new PayrollItem();

                    //compute salary for payroll period
                    BigDecimal semiMonthlySalary = payrollCalculator.computeSemiMonthlyBasicPay(emp.getYearlySalary());
                    //compute how much should be subtracted per day.
                    BigDecimal dailyAbsenceRate = payrollCalculator.computeExemptDailyAbsenceRate(emp.getYearlySalary());
                    //get attendance of employee, else empty list
                    List<AttendanceLog> attendanceLogs = attendanceMap.getOrDefault(emp.getEmployeeId(), List.of());
                    List<DailyShiftSchedule> schedules = scheduleMap.getOrDefault(emp.getEmployeeId(), List.of());
                    List<LeaveRequest> leaves = leaveMap.getOrDefault(emp.getEmployeeId(), List.of());
                    Map<LocalDate, List<DailyShiftSchedule>> schedulesByDate = schedules.stream()
                            .collect(Collectors.groupingBy(DailyShiftSchedule::getWorkDate));
                    AttendanceDeductionTotals attendanceDeductions = calculateExemptAttendanceDeductions(
                            run,
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
                    List<DeductionsLine> deductions = payrollCalculator.setDeductions(
                            emp,
                            item,
                            attendanceDeductions.absentDeduction(),
                            attendanceDeductions.lateDeduction()
                    );
                    item.setDeductions(deductions);
                    BigDecimal totalDeductions = deductions.stream()
                            .map(DeductionsLine::getAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    item.setNetPay(semiMonthlySalary.subtract(totalDeductions).setScale(2, RoundingMode.HALF_UP));
                    return item;
                })
                .toList();
    }

    //for nonexempt
    @Transactional
    public void processPayrollNonExempt(PayrollRun run, List<PayrollItem> items, List<Employee> employees) {

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
        List<AttendanceLog> allLogs = attendanceLogRepository
                .findByEmployeeIdInAndTimestampBetween(
                        employeeIds,
                        attendanceWindowStart,
                        run.getPeriodEnd().atTime(LocalTime.MAX).plusHours(6).atZone(defaultConfig.getTimeZone()).toInstant(),
                        sorting);

        // only approved overtime inside the payroll window contributes extra pay.
        Specification<OvertimeRequest> overtimeSpecs = Specification.allOf(
                OvertimeSpecs.hasStatus(Status.APPROVED),
                OvertimeSpecs.hasEmployeeIds(employeeIds),
                CommonSpecs.fieldIsBetween(
                        "workDate",
                        run.getPeriodStart(),
                        run.getPeriodEnd())
        );

        List<OvertimeRequest> overtimeRequests = overtimeRequestRepository.findAll(overtimeSpecs);

        List<DailyShiftSchedule> allSchedules = dailyShiftRepository.findAllByEmployeeIdsAndPeriod(
                employeeIds,
                schedulePeriodStart,
                schedulePeriodEndExclusive
        );

        List<LeaveRequest> allLeaves = leaveRequestRepository.findApprovedLeavesForPeriod(
                employeeIds,
                schedulePeriodStart,
                schedulePeriodEndExclusive
        );

        HolidayLookup holidayLookup = holidayService.getHolidayLookup(run.getPeriodStart(), run.getPeriodEnd());
        Set<LocalDate> holidayDates = holidayLookup.holidayDates();
        Set<LocalDate> regularHolidayDates = holidayLookup.regularHolidayDates();

        // group shared data up front so the inner employee/day loop only does simple lookups.
        Map<Long, List<AttendanceLog>> attendanceMap = allLogs.stream()
                .collect(Collectors.groupingBy(AttendanceLog::getEmployeeId));

        Map<Long, List<DailyShiftSchedule>> scheduleMap = allSchedules.stream()
                .collect(Collectors.groupingBy(DailyShiftSchedule::getEmployeeId));

        Map<Long, List<OvertimeRequest>> overtimeMap = overtimeRequests.stream()
                .collect(Collectors.groupingBy(OvertimeRequest::getEmployeeId));

        Map<Long, List<LeaveRequest>> leaveMap = allLeaves.stream()
                .collect(Collectors.groupingBy(leave -> leave.getEmployee().getEmployeeId()));

        for (Employee emp :  employees) {
            // non-exempt payroll starts from cutoff basic pay, then layers in variable earnings and attendance deductions.
            List<OvertimeRequest> requests = overtimeMap.getOrDefault(emp.getEmployeeId(), List.of());
            List<DailyShiftSchedule> schedules = scheduleMap.getOrDefault(emp.getEmployeeId(), List.of());
            List<AttendanceLog> attendanceLogs = attendanceMap.getOrDefault(emp.getEmployeeId(), List.of());
            List<LeaveRequest> leaves = leaveMap.getOrDefault(emp.getEmployeeId(), List.of());

            BigDecimal basicPayPerPeriod = payrollCalculator.computeSemiMonthlyBasicPay(emp.getYearlySalary());
            BigDecimal hourlyRate = payrollCalculator.computeHourlyRate(emp.getYearlySalary());
            Map<LocalDate, List<DailyShiftSchedule>> schedulesByDate = schedules.stream()
                    .collect(Collectors.groupingBy(DailyShiftSchedule::getWorkDate));

            BigDecimal absentDeductAmount = BigDecimal.ZERO;
            BigDecimal lateDeductAmount = BigDecimal.ZERO;
            List<EarningsLine> earningsLines = new ArrayList<>();

            LocalDate currentDate = run.getPeriodStart();
            while (!currentDate.isAfter(run.getPeriodEnd())) {
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
                            earningsLines.add(restDayEarnings);
                        }
                    }
                } else {
                    // ordinary workdays compare all attendance sessions against the split daily-shift segments.
                    BigDecimal expectedHours = payrollCalculator.calculateExpectedShiftHours(todaysSchedules);
                    if (expectedHours.compareTo(BigDecimal.ZERO) <= 0) {
                        // leave this day alone if the generated schedules carry no payable hours.
                    } else if (!hasCompleteAttendancePair) {
                        // missing the whole scheduled day counts as ordinary absence.
                        absentDeductAmount = absentDeductAmount.add(expectedHours.multiply(hourlyRate));
                    } else {
                        // only count overlap inside the scheduled segments so split-leave days stay accurate.
                        BigDecimal actualHours = payrollCalculator.deductUnpaidLunchHour(
                                payrollCalculator.calculateWorkedHoursWithinSchedules(
                                        todaysSchedules,
                                        attendanceSessions
                                )
                        );
                        if (actualHours.compareTo(expectedHours) < 0) {
                            // grace only softens the late-start portion, not every kind of missing hour.
                            BigDecimal missingHours = expectedHours.subtract(actualHours);
                            BigDecimal chargeableMissingHours = payrollCalculator.applyGracePeriodToMissingHours(
                                    primarySchedule,
                                    dayInLog,
                                    todaysLeave,
                                    missingHours
                            );
                            lateDeductAmount = lateDeductAmount.add(chargeableMissingHours.multiply(hourlyRate));
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
                        earningsLines.add(nsdLine);
                    }

                    if (payableNightOvertimeHours.compareTo(BigDecimal.ZERO) > 0) {
                        EarningsLine overtimeNsdLine = new EarningsLine();
                        overtimeNsdLine.setEarningsDate(loopDate);
                        overtimeNsdLine.setHours(payableNightOvertimeHours);
                        BigDecimal overtimeNsdRateMultiplier = dayWorkMultiplier
                                .multiply(payrollConfig.getOvertimeMultiplier())
                                .multiply(payrollConfig.getNightDifferentialMultiplier());
                        overtimeNsdLine.setRate(hourlyRate.multiply(overtimeNsdRateMultiplier));
                        overtimeNsdLine.setAmount(payableNightOvertimeHours.multiply(overtimeNsdLine.getRate()));
                        overtimeNsdLine.setOvertime(false);
                        earningsLines.add(overtimeNsdLine);
                    }
                }

                // OT earnings are limited to approved hours that also overlap actual attendance.
                for (PayableOvertimeBreakdown payableOt : payableTodaysOT) {
                    EarningsLine otLine = new EarningsLine();
                    otLine.setEarningsDate(loopDate);
                    otLine.setHours(payableOt.payableHours());
                    BigDecimal overtimeRateMultiplier = dayWorkMultiplier.multiply(payrollConfig.getOvertimeMultiplier());
                    otLine.setRate(hourlyRate.multiply(overtimeRateMultiplier));
                    otLine.setAmount(otLine.getHours().multiply(otLine.getRate()));
                    otLine.setOvertime(true);
                    otLine.setOvertimeRequest(payableOt.request());
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

            BigDecimal earningsTotal = item.getEarnings() == null
                    ? BigDecimal.ZERO
                    : item.getEarnings().stream()
                            .map(EarningsLine::getAmount)
                            .filter(amount -> amount != null)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal grossPay = earningsTotal.setScale(2, RoundingMode.HALF_UP);
            item.setGrossPay(grossPay);

            List<DeductionsLine> calculatedDeductions = payrollCalculator.setDeductions(
                    emp,
                    item,
                    absentDeductAmount,
                    lateDeductAmount
            );
            if (calculatedDeductions == null) {
                calculatedDeductions = List.of();
            }
            item.setDeductions(calculatedDeductions);
            item.getDeductions().forEach(line -> line.setPayrollItem(item));

            BigDecimal deductionsTotal = item.getDeductions() == null
                    ? BigDecimal.ZERO
                    : item.getDeductions().stream()
                            .map(DeductionsLine::getAmount)
                            .filter(amount -> amount != null)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal netPay = grossPay.subtract(deductionsTotal).setScale(2, RoundingMode.HALF_UP);
            item.setNetPay(netPay);

            items.add(item);
        }
    }

    private AttendanceDeductionTotals calculateExemptAttendanceDeductions(
            PayrollRun run,
            List<AttendanceLog> attendanceLogs,
            Map<LocalDate, List<DailyShiftSchedule>> schedulesByDate,
            List<LeaveRequest> leaves,
            BigDecimal dailyAbsenceRate,
            Set<LocalDate> holidayDates,
            BigDecimal deductionCap
    ) {
        BigDecimal absentDeductAmount = BigDecimal.ZERO;

        LocalDate currentDate = run.getPeriodStart();
        while (!currentDate.isAfter(run.getPeriodEnd())) {
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

    private record AttendanceDeductionTotals(BigDecimal absentDeduction, BigDecimal lateDeduction) {}

    private record PayableOvertimeBreakdown(
            OvertimeRequest request,
            BigDecimal payableHours,
            BigDecimal nightHours
    ) {}


}
