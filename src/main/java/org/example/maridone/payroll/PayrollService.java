package org.example.maridone.payroll;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
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
import org.example.maridone.schedule.shift.TemplateShiftRepository;
import org.example.maridone.schedule.shift.TemplateShiftSchedule;
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
    private final TemplateShiftRepository templateShiftRepository;
    private final OvertimeRequestRepository overtimeRequestRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final HolidayService holidayService;
    private final PayrollCalculator payrollCalculator;
    private final PayrollMapper payrollMapper;
    private final DefaultConfig defaultConfig;
    private final PayrollConfig payrollConfig;
    private final BracketService bracketService;

    public PayrollService(
            PayrollRunRepository payrollRunRepository,
            PayrollItemRepository payrollItemRepository,
            EmployeeRepository employeeRepository,
            AttendanceLogRepository attendanceLogRepository,
            TemplateShiftRepository templateShiftRepository,
            OvertimeRequestRepository overtimeRequestRepository,
            LeaveRequestRepository leaveRequestRepository,
            HolidayService holidayService,
            PayrollCalculator payrollCalculator,
            PayrollMapper payrollMapper,
            DefaultConfig defaultConfig,
            PayrollConfig payrollConfig,
            BracketService bracketService
    )
    {
        this.payrollRunRepository = payrollRunRepository;
        this.payrollItemRepository = payrollItemRepository;
        this.employeeRepository = employeeRepository;
        this.attendanceLogRepository = attendanceLogRepository;
        this.templateShiftRepository = templateShiftRepository;
        this.overtimeRequestRepository = overtimeRequestRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.holidayService = holidayService;
        this.payrollCalculator = payrollCalculator;
        this.payrollMapper = payrollMapper;
        this.defaultConfig = defaultConfig;
        this.payrollConfig = payrollConfig;
        this.bracketService = bracketService;
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
        Instant periodStart = run.getPeriodStart()
                .atStartOfDay(defaultConfig.getTimeZone())
                .toInstant();

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
                periodStart,
                run.getPeriodEnd().atTime(LocalTime.MAX).plusHours(6).atZone(defaultConfig.getTimeZone()).toInstant(),
                sorting
        );

        //get all schedules of employees in employeeIds
        List<TemplateShiftSchedule> allSchedules = templateShiftRepository.findByEmployee_EmployeeIdIn(employeeIds);
        //get all APPROVED leaves of employees in employeeIds for payroll period
        List<LeaveRequest> allLeaves = leaveRequestRepository.findApprovedLeavesForPeriod(
                employeeIds, run.getPeriodStart().atStartOfDay(), run.getPeriodEnd().plusDays(1).atStartOfDay()
        );

        //find any holidays for a day in payroll period
        HolidayLookup holidayLookup = holidayService.getHolidayLookup(run.getPeriodStart(), run.getPeriodEnd());
        Set<LocalDate> holidayDates = holidayLookup.holidayDates();

        //group List of Objects(Attendance, TemplateShifts, LeaveRequest) to an employeeId inside their object
        Map<Long, List<AttendanceLog>> attendanceMap = safeList(allLogs).stream()
                .collect(Collectors.groupingBy(AttendanceLog::getEmployeeId));
        Map<Long, List<TemplateShiftSchedule>> scheduleMap = safeList(allSchedules).stream()
                .collect(Collectors.groupingBy(schedule -> schedule.getEmployee().getEmployeeId()));
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
                    //get
                    List<TemplateShiftSchedule> schedules = scheduleMap.getOrDefault(emp.getEmployeeId(), List.of());
                    List<LeaveRequest> leaves = leaveMap.getOrDefault(emp.getEmployeeId(), List.of());
                    Map<DayOfWeek, TemplateShiftSchedule> schedulesByDay = schedules.stream()
                            .collect(Collectors.toMap(
                                    TemplateShiftSchedule::getDayOfWeek,
                                    schedule -> schedule,
                                    (first, ignored) -> first
                            ));
                    AttendanceDeductionTotals attendanceDeductions = calculateExemptAttendanceDeductions(
                            run,
                            attendanceLogs,
                            schedulesByDay,
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

    @Transactional
    public void processPayrollNonExempt(PayrollRun run, List<PayrollItem> items, List<Employee> employees) {

        List<Long> employeeIds = employees.stream()
                .map(Employee::getEmployeeId)
                .toList();
        if (employeeIds.isEmpty()) {
            return;
        }

        Instant periodStart = run.getPeriodStart()
                .atStartOfDay(defaultConfig.getTimeZone())
                .toInstant();

        Sort sorting = Sort.by(Sort.Direction.ASC, "timestamp");
        List<AttendanceLog> allLogs = attendanceLogRepository
                .findByEmployeeIdInAndTimestampBetween(
                        employeeIds,
                        periodStart,
                        run.getPeriodEnd().atTime(LocalTime.MAX).plusHours(6).atZone(defaultConfig.getTimeZone()).toInstant(),
                        sorting);

        Specification<OvertimeRequest> overtimeSpecs = Specification.allOf(
                OvertimeSpecs.hasStatus(Status.APPROVED),
                OvertimeSpecs.hasEmployeeIds(employeeIds),
                CommonSpecs.fieldIsBetween(
                        "workDate",
                        run.getPeriodStart(),
                        run.getPeriodEnd())
        );

        List<OvertimeRequest> overtimeRequests = overtimeRequestRepository.findAll(overtimeSpecs);

        List<TemplateShiftSchedule> allSchedules = templateShiftRepository.findByEmployee_EmployeeIdIn(employeeIds);

        List<LeaveRequest> allLeaves = leaveRequestRepository.findApprovedLeavesForPeriod(
            employeeIds, run.getPeriodStart().atStartOfDay(), run.getPeriodEnd().plusDays(1).atStartOfDay()
        );

        HolidayLookup holidayLookup = holidayService.getHolidayLookup(run.getPeriodStart(), run.getPeriodEnd());
        Set<LocalDate> holidayDates = holidayLookup.holidayDates();
        Set<LocalDate> regularHolidayDates = holidayLookup.regularHolidayDates();

        Map<Long, List<AttendanceLog>> attendanceMap = allLogs.stream()
                .collect(Collectors.groupingBy(AttendanceLog::getEmployeeId));

        Map<Long, List<TemplateShiftSchedule>> scheduleMap = allSchedules.stream()
                .collect(Collectors.groupingBy(schedule -> schedule.getEmployee().getEmployeeId()));

        Map<Long, List<OvertimeRequest>> overtimeMap = overtimeRequests.stream()
                .collect(Collectors.groupingBy(OvertimeRequest::getEmployeeId));

        Map<Long, List<LeaveRequest>> leaveMap = allLeaves.stream()
                .collect(Collectors.groupingBy(leave -> leave.getEmployee().getEmployeeId()));

        for (Employee emp :  employees) {
            List<OvertimeRequest> requests = overtimeMap.getOrDefault(emp.getEmployeeId(), List.of());
            List<TemplateShiftSchedule> schedules = scheduleMap.getOrDefault(emp.getEmployeeId(), List.of());
            List<AttendanceLog> attendanceLogs = attendanceMap.getOrDefault(emp.getEmployeeId(), List.of());
            List<LeaveRequest> leaves = leaveMap.getOrDefault(emp.getEmployeeId(), List.of());

            BigDecimal basicPayPerPeriod = payrollCalculator.computeSemiMonthlyBasicPay(emp.getYearlySalary());
            BigDecimal hourlyRate = payrollCalculator.computeHourlyRate(emp.getYearlySalary());
            Map<DayOfWeek, TemplateShiftSchedule> schedulesByDay = schedules.stream()
                    .collect(Collectors.toMap(
                            TemplateShiftSchedule::getDayOfWeek,
                            schedule -> schedule,
                            (first, ignored) -> first
                    ));

            BigDecimal absentDeductAmount = BigDecimal.ZERO;
            BigDecimal lateDeductAmount = BigDecimal.ZERO;
            List<EarningsLine> earningsLines = new ArrayList<>();

            LocalDate currentDate = run.getPeriodStart();
            while (!currentDate.isAfter(run.getPeriodEnd())) {
                LocalDate loopDate = currentDate;

                TemplateShiftSchedule todaysSchedule = schedulesByDay.get(loopDate.getDayOfWeek());

                AttendanceLog dayInLog = payrollCalculator.findInLogForDate(attendanceLogs, loopDate);
                AttendanceLog dayOutLog = payrollCalculator.findOutLogAfterIn(attendanceLogs, dayInLog, loopDate);
                boolean hasCompleteAttendancePair = dayInLog != null && dayOutLog != null;

              //todo: verify
                LeaveRequest todaysLeave = findLeaveForDate(leaves, loopDate);

                List<OvertimeRequest> todaysOT = requests.stream()
                        .filter(ot -> ot.getWorkDate().equals(loopDate))
                        .toList();

                boolean isRestDay = (todaysSchedule == null);
                boolean isHoliday = holidayDates.contains(loopDate);
                boolean isRegularHoliday = regularHolidayDates.contains(loopDate);
                BigDecimal dayWorkMultiplier = payrollCalculator.resolveHolidayOrRestDayMultiplier(
                        isRestDay,
                        isHoliday,
                        isRegularHoliday
                );
                BigDecimal overtimeHours = todaysOT.stream()
                        .map(ot -> payrollCalculator.calculateHours(ot.getStartTime().toLocalTime(), ot.getEndTime().toLocalTime()))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                if (isRestDay || isHoliday) {
                    if (hasCompleteAttendancePair) {
                        Instant inInstant = dayInLog.getTimestamp();
                        Instant outInstant = dayOutLog.getTimestamp();

                        LocalTime logIn = inInstant.atZone(defaultConfig.getTimeZone()).toLocalTime();
                        LocalTime logOut = outInstant.atZone(defaultConfig.getTimeZone()).toLocalTime();

                        BigDecimal hoursWorked = payrollCalculator.deductUnpaidLunchHour(
                                payrollCalculator.calculateHours(logIn, logOut)
                        );

                        BigDecimal premiumHours = hoursWorked.subtract(overtimeHours).max(BigDecimal.ZERO);
                        BigDecimal premiumMultiplier = dayWorkMultiplier.subtract(BigDecimal.ONE);

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
                    BigDecimal expectedHours = calculateExpectedShiftHours(todaysSchedule);
                    BigDecimal leaveHours = BigDecimal.ZERO;

                    if (todaysLeave != null) {
                        // Approved leaves are currently treated as paid leave in payroll because unpaid leave is not modeled yet.
              //todo: verify
                            leaveHours = payrollCalculator.calculateHours(todaysLeave.getStartTime(), todaysLeave.getEndTime());
                        if (leaveHours.compareTo(expectedHours) > 0) {
                            leaveHours = expectedHours;
                        }
                    }

                    BigDecimal expectedRemainingHours = expectedHours.subtract(leaveHours);
                    if (expectedRemainingHours.compareTo(BigDecimal.ZERO) <= 0) {
                    } else if (!hasCompleteAttendancePair) {
                        absentDeductAmount = absentDeductAmount.add(expectedRemainingHours.multiply(hourlyRate));
                    } else {
                        LocalTime logIn = dayInLog.getTimestamp().atZone(defaultConfig.getTimeZone()).toLocalTime();
                        LocalTime logOut = dayOutLog.getTimestamp().atZone(defaultConfig.getTimeZone()).toLocalTime();
                        BigDecimal actualHours = payrollCalculator.deductUnpaidLunchHour(
                                payrollCalculator.calculateHours(logIn, logOut)
                        );
                        BigDecimal baseline = todaysLeave == null ? expectedHours : expectedRemainingHours;

                        if (actualHours.compareTo(baseline) < 0) {
                            BigDecimal missingHours = baseline.subtract(actualHours);
                            BigDecimal chargeableMissingHours = applyGracePeriodToMissingHours(
                                    todaysSchedule,
                                    dayInLog,
                                    todaysLeave,
                                    missingHours
                            );
                            lateDeductAmount = lateDeductAmount.add(chargeableMissingHours.multiply(hourlyRate));
                        }
                    }
                }

                if (hasCompleteAttendancePair) {
                    LocalTime logIn = dayInLog.getTimestamp().atZone(defaultConfig.getTimeZone()).toLocalTime();
                    LocalTime logOut = dayOutLog.getTimestamp().atZone(defaultConfig.getTimeZone()).toLocalTime();

                    BigDecimal nsdHours = payrollCalculator.calculateNightDiffHours(logIn, logOut);
                    if (nsdHours.compareTo(BigDecimal.ZERO) > 0) {
                        EarningsLine nsdLine = new EarningsLine();
                        nsdLine.setEarningsDate(loopDate);
                        nsdLine.setHours(nsdHours);
                        BigDecimal nsdRateMultiplier = dayWorkMultiplier.multiply(
                                payrollConfig.getNightDifferentialMultiplier()
                        );
                        nsdLine.setRate(hourlyRate.multiply(nsdRateMultiplier));
                        nsdLine.setAmount(nsdHours.multiply(nsdLine.getRate()));
                        nsdLine.setOvertime(false);
                        earningsLines.add(nsdLine);
                    }
                }

                for (OvertimeRequest ot : todaysOT) {
                    EarningsLine otLine = new EarningsLine();
                    otLine.setEarningsDate(loopDate);
                    otLine.setHours(payrollCalculator.calculateHours(ot.getStartTime().toLocalTime(), ot.getEndTime().toLocalTime()));
                    BigDecimal overtimeRateMultiplier = dayWorkMultiplier.multiply(payrollConfig.getOvertimeMultiplier());
                    otLine.setRate(hourlyRate.multiply(overtimeRateMultiplier));
                    otLine.setAmount(otLine.getHours().multiply(otLine.getRate()));
                    otLine.setOvertime(true);
                    otLine.setOvertimeRequest(ot);
                    earningsLines.add(otLine);
                }

                currentDate = currentDate.plusDays(1);
            }

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

    private BigDecimal calculateExpectedShiftHours(TemplateShiftSchedule schedule) {
        return payrollCalculator.deductUnpaidLunchHour(
                payrollCalculator.calculateHours(schedule.getStartTime(), schedule.getEndTime())
        );
    }

    private AttendanceDeductionTotals calculateExemptAttendanceDeductions(
            PayrollRun run,
            List<AttendanceLog> attendanceLogs,
            Map<DayOfWeek, TemplateShiftSchedule> schedulesByDay,
            List<LeaveRequest> leaves,
            BigDecimal dailyAbsenceRate,
            Set<LocalDate> holidayDates,
            BigDecimal deductionCap
    ) {
        BigDecimal absentDeductAmount = BigDecimal.ZERO;

        LocalDate currentDate = run.getPeriodStart();
        while (!currentDate.isAfter(run.getPeriodEnd())) {
            TemplateShiftSchedule todaysSchedule = schedulesByDay.get(currentDate.getDayOfWeek());
            boolean isHoliday = holidayDates.contains(currentDate);
            LeaveRequest todaysLeave = findLeaveForDate(leaves, currentDate);
            boolean hasAttendance = payrollCalculator.hasAnyAttendanceLogForDate(attendanceLogs, currentDate);

            // Exempt staff are treated as salary-preserved unless they miss the whole scheduled day without approved leave.
            if (todaysSchedule != null && !isHoliday && todaysLeave == null && !hasAttendance) {
                absentDeductAmount = absentDeductAmount.add(dailyAbsenceRate);
            }

            currentDate = currentDate.plusDays(1);
        }

        return applyAttendanceDeductionCap(absentDeductAmount, BigDecimal.ZERO, deductionCap);
    }

    private AttendanceDeductionTotals calculateAttendanceDeductions(
            PayrollRun run,
            List<AttendanceLog> attendanceLogs,
            Map<DayOfWeek, TemplateShiftSchedule> schedulesByDay,
            List<LeaveRequest> leaves,
            BigDecimal hourlyRate,
            Set<LocalDate> holidayDates,
            BigDecimal deductionCap
    ) {
        BigDecimal absentDeductAmount = BigDecimal.ZERO;
        BigDecimal lateDeductAmount = BigDecimal.ZERO;

        LocalDate currentDate = run.getPeriodStart();
        while (!currentDate.isAfter(run.getPeriodEnd())) {
            LocalDate loopDate = currentDate;
            TemplateShiftSchedule todaysSchedule = schedulesByDay.get(loopDate.getDayOfWeek());
            boolean isHoliday = holidayDates.contains(loopDate);
            if (todaysSchedule != null && !isHoliday) {
                AttendanceLog dayInLog = payrollCalculator.findInLogForDate(attendanceLogs, loopDate);
                AttendanceLog dayOutLog = payrollCalculator.findOutLogAfterIn(attendanceLogs, dayInLog, loopDate);
                boolean hasCompleteAttendancePair = dayInLog != null && dayOutLog != null;

                LeaveRequest todaysLeave = findLeaveForDate(leaves, loopDate);
                BigDecimal expectedHours = calculateExpectedShiftHours(todaysSchedule);
                BigDecimal leaveHours = resolvePaidLeaveHours(expectedHours, todaysLeave);
                BigDecimal expectedRemainingHours = expectedHours.subtract(leaveHours);

                if (expectedRemainingHours.compareTo(BigDecimal.ZERO) > 0) {
                    if (!hasCompleteAttendancePair) {
                        absentDeductAmount = absentDeductAmount.add(expectedRemainingHours.multiply(hourlyRate));
                    } else {
                        LocalTime logIn = dayInLog.getTimestamp().atZone(defaultConfig.getTimeZone()).toLocalTime();
                        LocalTime logOut = dayOutLog.getTimestamp().atZone(defaultConfig.getTimeZone()).toLocalTime();
                        BigDecimal actualHours = payrollCalculator.deductUnpaidLunchHour(
                                payrollCalculator.calculateHours(logIn, logOut)
                        );
                        BigDecimal baseline = todaysLeave == null ? expectedHours : expectedRemainingHours;
                        if (actualHours.compareTo(baseline) < 0) {
                            BigDecimal missingHours = baseline.subtract(actualHours);
                            BigDecimal chargeableMissingHours = applyGracePeriodToMissingHours(
                                    todaysSchedule,
                                    dayInLog,
                                    todaysLeave,
                                    missingHours
                            );
                            lateDeductAmount = lateDeductAmount.add(chargeableMissingHours.multiply(hourlyRate));
                        }
                    }
                }
            }
            currentDate = currentDate.plusDays(1);
        }

        return applyAttendanceDeductionCap(absentDeductAmount, lateDeductAmount, deductionCap);
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

    private BigDecimal resolvePaidLeaveHours(BigDecimal expectedHours, LeaveRequest leave) {
        if (expectedHours == null || leave == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal leaveHours = payrollCalculator.calculateHours(leave.getStartTime(), leave.getEndTime());
        if (leaveHours.compareTo(expectedHours) > 0) {
            return expectedHours;
        }
        return leaveHours;
    }

    private BigDecimal applyGracePeriodToMissingHours(
            TemplateShiftSchedule schedule,
            AttendanceLog inLog,
            LeaveRequest leave,
            BigDecimal missingHours
    ) {
        if (missingHours == null || missingHours.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal missingMinutes = missingHours.multiply(BigDecimal.valueOf(60));
        BigDecimal roundedMissingMinutes = missingMinutes.setScale(0, RoundingMode.HALF_UP);
        long lateMinutes = payrollCalculator.calculateLateMinutes(schedule, inLog, leave);
        long graceMinutes = Math.max(payrollConfig.getGracePeriod().toMinutes(), 0L);
        long coveredMinutes = Math.min(
                roundedMissingMinutes.longValue(),
                Math.min(lateMinutes, graceMinutes)
        );

        BigDecimal chargeableMinutes = roundedMissingMinutes.subtract(BigDecimal.valueOf(coveredMinutes));
        if (chargeableMinutes.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        return chargeableMinutes.divide(BigDecimal.valueOf(60), 6, RoundingMode.HALF_UP);
    }

    private LeaveRequest findLeaveForDate(List<LeaveRequest> leaves, LocalDate workDate) {
        return leaves.stream()
                .filter(l -> l.coversDate(workDate))
                .findFirst()
                .orElse(null);
    }


    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private record AttendanceDeductionTotals(BigDecimal absentDeduction, BigDecimal lateDeduction) {}


}
