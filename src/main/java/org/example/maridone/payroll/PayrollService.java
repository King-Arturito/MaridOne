package org.example.maridone.payroll;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
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
import org.example.maridone.config.DefaultProperties;
import org.example.maridone.config.PayrollProperties;
import org.example.maridone.core.employee.Employee;
import org.example.maridone.core.employee.EmployeeRepository;
import org.example.maridone.enums.EmploymentStatus;
import org.example.maridone.enums.ExemptionStatus;
import org.example.maridone.enums.Status;
import org.example.maridone.exception.notfound.EmployeeNotFoundException;
import org.example.maridone.exception.notfound.ItemNotFoundException;
import org.example.maridone.exception.notfound.RunNotFoundException;
import org.example.maridone.holiday.HolidayLookup;
import org.example.maridone.holiday.HolidayService;
import org.example.maridone.leave.request.LeaveRequest;
import org.example.maridone.leave.request.LeaveRequestRepository;
import org.example.maridone.log.AttendanceLogRepository;
import org.example.maridone.log.attendance.AttendanceLog;
import org.example.maridone.overtime.OvertimeRequest;
import org.example.maridone.overtime.OvertimeRequestRepository;
import org.example.maridone.overtime.spec.OvertimeSpecs;
import org.example.maridone.payroll.dto.ItemDetailsDto;
import org.example.maridone.payroll.dto.ItemSummaryDto;
import org.example.maridone.payroll.dto.PayrollItemDto;
import org.example.maridone.payroll.dto.RunCreateDto;
import org.example.maridone.payroll.item.PayrollItem;
import org.example.maridone.payroll.item.component.DeductionsLine;
import org.example.maridone.payroll.item.component.EarningsLine;
import org.example.maridone.payroll.mapper.PayrollMapper;
import org.example.maridone.payroll.run.PayrollItemRepository;
import org.example.maridone.payroll.run.PayrollRun;
import org.example.maridone.payroll.run.PayrollRunRepository;
import org.example.maridone.payroll.spec.ItemSpecs;
import org.example.maridone.schedule.shift.TemplateShiftRepository;
import org.example.maridone.schedule.shift.TemplateShiftSchedule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.validation.Valid;


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
    private final DefaultProperties defaultProperties;
    private final PayrollProperties payrollProperties;

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
            DefaultProperties defaultProperties,
            PayrollProperties payrollProperties
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
        this.defaultProperties = defaultProperties;
        this.payrollProperties = payrollProperties;
    }

    @Transactional
    @ExecutionTime
    public List<ItemDetailsDto> getItems(Long empId) {

        Specification<PayrollItem> spec = Specification.allOf(
                ItemSpecs.hasEmployeeId(empId)
        );
        List<PayrollItem> items = payrollItemRepository.findAll(spec);

        return payrollMapper.toItemDetailsDtos(items);
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
    public ItemDetailsDto createItem(@Valid PayrollItemDto payload) {
        PayrollItem item = new PayrollItem();
        PayrollRun run = payrollRunRepository.findById(payload.getPayId()).orElseThrow(
                () -> new RunNotFoundException("Payroll Run ID:" + payload.getPayId() + " not found.")
        );

        Employee emp = employeeRepository.findById(payload.getEmpId()).orElseThrow(
                () -> new EmployeeNotFoundException(payload.getEmpId())
        );

        item.setPayrollRun(run);
        item.setEmployee(emp);
        item.setGrossPay(BigDecimal.valueOf(-1));
        item.setNetPay(BigDecimal.valueOf(-1));
        payrollItemRepository.save(item);
        return payrollMapper.toItemDetailsDto(item);
    }

    @Transactional
    @ExecutionTime
    public PayrollItemDto updateItem(PayrollItemDto payload, Long itemId) {
        PayrollItem item = payrollItemRepository.findById(itemId).orElseThrow(
                () -> new ItemNotFoundException(itemId)
        );

        item.setGrossPay(payload.getGrossPay());
        item.setNetPay(payload.getNetPay());
        payrollItemRepository.save(item);
        return payrollMapper.toPayrollItemDto(item);
    }

    public Page<ItemSummaryDto> getRunItems(Long payId, Pageable pageable) {
        if (!payrollRunRepository.existsById(payId)) {
            throw new RunNotFoundException("Payroll Run ID:" + payId + " not found.");
        }

        Page<PayrollItem> items = payrollItemRepository.findByPayrollRun_PayId(payId, pageable);
        return items.map(payrollMapper::toItemSummaryDto);
    }

    @Transactional
    @ExecutionTime
    public void processPayroll(RunCreateDto payload) {
        PayrollRun run = createRun(payload);

        Specification<Employee> spec = CommonSpecs.fieldNotEquals("employmentStatus", EmploymentStatus.TERMINATED);
        List<Employee> employees = employeeRepository.findAll(spec);
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

    @Transactional
    public List<PayrollItem> processPayrollExempt(PayrollRun run, List<Employee> employees) {
        List<Long> employeeIds = employees.stream()
                .map(Employee::getEmployeeId)
                .toList();
        if (employeeIds.isEmpty()) {
            return List.of();
        }

        Instant periodStart = run.getPeriodStart()
                .atStartOfDay(defaultProperties.getTimeZone())
                .toInstant();

        Sort sorting = Sort.by(Sort.Direction.ASC, "timestamp");
        List<AttendanceLog> allLogs = attendanceLogRepository.findByEmployeeIdInAndTimestampBetween(
                employeeIds,
                periodStart,
                run.getPeriodEnd().atTime(LocalTime.MAX).plusHours(6).atZone(defaultProperties.getTimeZone()).toInstant(),
                sorting
        );
        List<TemplateShiftSchedule> allSchedules = templateShiftRepository.findByEmployee_EmployeeIdIn(employeeIds);
        List<LeaveRequest> allLeaves = leaveRequestRepository.findApprovedLeavesForPeriod(
                employeeIds, run.getPeriodStart(), run.getPeriodEnd()
        );
        HolidayLookup holidayLookup = holidayService.getHolidayLookup(run.getPeriodStart(), run.getPeriodEnd());
        Set<LocalDate> holidayDates = holidayLookup.holidayDates();

        Map<Long, List<AttendanceLog>> attendanceMap = safeList(allLogs).stream()
                .collect(Collectors.groupingBy(AttendanceLog::getEmployeeId));
        Map<Long, List<TemplateShiftSchedule>> scheduleMap = safeList(allSchedules).stream()
                .collect(Collectors.groupingBy(schedule -> schedule.getEmployee().getEmployeeId()));
        Map<Long, List<LeaveRequest>> leaveMap = safeList(allLeaves).stream()
                .collect(Collectors.groupingBy(leave -> leave.getEmployee().getEmployeeId()));

        return employees.stream()
                .map(emp -> {
                    PayrollItem item = new PayrollItem();
                    BigDecimal semiMonthlySalary = payrollProperties.computeSemiMonthlyBasicPay(emp.getYearlySalary());
                    BigDecimal dailyAbsenceRate = payrollProperties.computeExemptDailyAbsenceRate(emp.getYearlySalary());

                    List<AttendanceLog> attendanceLogs = attendanceMap.getOrDefault(emp.getEmployeeId(), List.of());
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
                .atStartOfDay(defaultProperties.getTimeZone())
                .toInstant();

        Sort sorting = Sort.by(Sort.Direction.ASC, "timestamp");
        List<AttendanceLog> allLogs = attendanceLogRepository
                .findByEmployeeIdInAndTimestampBetween(
                        employeeIds,
                        periodStart,
                        run.getPeriodEnd().atTime(LocalTime.MAX).plusHours(6).atZone(defaultProperties.getTimeZone()).toInstant(),
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

            BigDecimal basicPayPerPeriod = payrollProperties.computeSemiMonthlyBasicPay(emp.getYearlySalary());
            BigDecimal hourlyRate = payrollProperties.computeHourlyRate(emp.getYearlySalary());
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

                AttendanceLog dayInLog = findInLogForDate(attendanceLogs, loopDate);
                AttendanceLog dayOutLog = findOutLogAfterIn(attendanceLogs, dayInLog, loopDate);
                boolean hasCompleteAttendancePair = dayInLog != null && dayOutLog != null;

              //todo: verify
                LeaveRequest todaysLeave = findLeaveForDate(leaves, loopDate);

                List<OvertimeRequest> todaysOT = requests.stream()
                        .filter(ot -> ot.getWorkDate().equals(loopDate))
                        .toList();

                boolean isRestDay = (todaysSchedule == null);
                boolean isHoliday = holidayDates.contains(loopDate);
                boolean isRegularHoliday = regularHolidayDates.contains(loopDate);
                BigDecimal dayWorkMultiplier = payrollProperties.resolveHolidayOrRestDayMultiplier(
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

                        LocalTime logIn = inInstant.atZone(defaultProperties.getTimeZone()).toLocalTime();
                        LocalTime logOut = outInstant.atZone(defaultProperties.getTimeZone()).toLocalTime();

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
                        LocalTime logIn = dayInLog.getTimestamp().atZone(defaultProperties.getTimeZone()).toLocalTime();
                        LocalTime logOut = dayOutLog.getTimestamp().atZone(defaultProperties.getTimeZone()).toLocalTime();
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
                    LocalTime logIn = dayInLog.getTimestamp().atZone(defaultProperties.getTimeZone()).toLocalTime();
                    LocalTime logOut = dayOutLog.getTimestamp().atZone(defaultProperties.getTimeZone()).toLocalTime();

                    BigDecimal nsdHours = payrollCalculator.calculateNightDiffHours(logIn, logOut);
                    if (nsdHours.compareTo(BigDecimal.ZERO) > 0) {
                        EarningsLine nsdLine = new EarningsLine();
                        nsdLine.setEarningsDate(loopDate);
                        nsdLine.setHours(nsdHours);
                        BigDecimal nsdRateMultiplier = dayWorkMultiplier.multiply(
                                payrollProperties.getNightDifferentialMultiplier()
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
                    BigDecimal overtimeRateMultiplier = dayWorkMultiplier.multiply(payrollProperties.getOvertimeMultiplier());
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
            boolean hasAttendance = hasAnyAttendanceLogForDate(attendanceLogs, currentDate);

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
                AttendanceLog dayInLog = findInLogForDate(attendanceLogs, loopDate);
                AttendanceLog dayOutLog = findOutLogAfterIn(attendanceLogs, dayInLog, loopDate);
                boolean hasCompleteAttendancePair = dayInLog != null && dayOutLog != null;

                LeaveRequest todaysLeave = findLeaveForDate(leaves, loopDate);
                BigDecimal expectedHours = calculateExpectedShiftHours(todaysSchedule);
                BigDecimal leaveHours = resolvePaidLeaveHours(expectedHours, todaysLeave);
                BigDecimal expectedRemainingHours = expectedHours.subtract(leaveHours);

                if (expectedRemainingHours.compareTo(BigDecimal.ZERO) > 0) {
                    if (!hasCompleteAttendancePair) {
                        absentDeductAmount = absentDeductAmount.add(expectedRemainingHours.multiply(hourlyRate));
                    } else {
                        LocalTime logIn = dayInLog.getTimestamp().atZone(defaultProperties.getTimeZone()).toLocalTime();
                        LocalTime logOut = dayOutLog.getTimestamp().atZone(defaultProperties.getTimeZone()).toLocalTime();
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

        // Approved leaves are currently treated as paid leave in payroll because unpaid leave is not modeled yet.
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
        long lateMinutes = calculateLateMinutes(schedule, inLog, leave);
        long graceMinutes = Math.max(payrollProperties.getGracePeriod().toMinutes(), 0L);
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

    private long calculateLateMinutes(TemplateShiftSchedule schedule, AttendanceLog inLog, LeaveRequest leave) {
        if (schedule == null || inLog == null) {
            return 0L;
        }

        LocalTime expectedStart = resolveExpectedStartTime(schedule, leave);
        LocalTime actualIn = inLog.getTimestamp().atZone(defaultProperties.getTimeZone()).toLocalTime();
        if (!actualIn.isAfter(expectedStart)) {
            return 0L;
        }

        return Duration.between(expectedStart, actualIn).toMinutes();
    }

    private LocalTime resolveExpectedStartTime(TemplateShiftSchedule schedule, LeaveRequest leave) {
        LocalTime scheduleStart = schedule.getStartTime();
        if (leave == null || leave.getStartTime() == null || leave.getEndTime() == null) {
            return scheduleStart;
        }

        boolean leaveCoversShiftStart = !leave.getStartTime().isAfter(scheduleStart)
                && leave.getEndTime().isAfter(scheduleStart);
        return leaveCoversShiftStart ? leave.getEndTime() : scheduleStart;
    }

    private LeaveRequest findLeaveForDate(List<LeaveRequest> leaves, LocalDate workDate) {
        return leaves.stream()
                .filter(l -> l.getLeaveDate().equals(workDate))
                .findFirst()
                .orElse(null);
    }

    private boolean hasAnyAttendanceLogForDate(List<AttendanceLog> attendanceLogs, LocalDate workDate) {
        return attendanceLogs.stream()
                .anyMatch(log -> log.getTimestamp()
                        .atZone(defaultProperties.getTimeZone())
                        .toLocalDate()
                        .equals(workDate));
    }

    private AttendanceLog findInLogForDate(List<AttendanceLog> attendanceLogs, LocalDate workDate) {
        return attendanceLogs.stream()
                .filter(this::isInLog)
                .filter(log -> log.getTimestamp()
                        .atZone(defaultProperties.getTimeZone())
                        .toLocalDate()
                        .equals(workDate))
                .findFirst()
                .orElse(null);
    }

    private AttendanceLog findOutLogAfterIn(List<AttendanceLog> attendanceLogs, AttendanceLog inLog, LocalDate workDate) {
        if (inLog == null) {
            return null;
        }

        Instant cutoff = workDate.plusDays(1)
                .atTime(LocalTime.of(6, 0))
                .atZone(defaultProperties.getTimeZone())
                .toInstant();

        return attendanceLogs.stream()
                .filter(this::isOutLog)
                .filter(log -> log.getTimestamp().isAfter(inLog.getTimestamp()))
                .filter(log -> !log.getTimestamp().isAfter(cutoff))
                .findFirst()
                .orElse(null);
    }

    private boolean isInLog(AttendanceLog log) {
        return log.getDirection() != null && log.getDirection().trim().equalsIgnoreCase("IN");
    }

    private boolean isOutLog(AttendanceLog log) {
        return log.getDirection() != null && log.getDirection().trim().equalsIgnoreCase("OUT");
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private record AttendanceDeductionTotals(BigDecimal absentDeduction, BigDecimal lateDeduction) {}


}
