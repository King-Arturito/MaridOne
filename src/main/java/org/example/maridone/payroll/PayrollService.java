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
import org.example.maridone.config.DefaultProperties;
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
            DefaultProperties defaultProperties
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

        return employees.stream()
                .map(emp -> {
                    PayrollItem item = new PayrollItem();
                    BigDecimal semiMonthlySalary = emp.getYearlySalary().divide(BigDecimal.valueOf(24), 2, RoundingMode.HALF_UP);
                    item.setEmployee(emp);
                    item.setPayrollRun(run);
                    item.setGrossPay(semiMonthlySalary);
                    item.setDisputes(new ArrayList<>());
                    item.setEarnings(payrollCalculator.setEarnings(emp, item));
                    List<DeductionsLine> deductions = payrollCalculator.setDeductions(
                            emp,
                            item,
                            BigDecimal.ZERO,
                            BigDecimal.ZERO
                    );
                    item.setDeductions(deductions);
                    BigDecimal totalDeductions = deductions.stream()
                            .map(DeductionsLine::getAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    item.setNetPay(semiMonthlySalary.subtract(totalDeductions).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP));
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
            employeeIds, run.getPeriodStart(), run.getPeriodEnd()
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

            BigDecimal monthlyBasicPay = emp.getYearlySalary().divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
            BigDecimal basicPayPerPeriod = emp.getYearlySalary().divide(BigDecimal.valueOf(24), 2, RoundingMode.HALF_UP);
            BigDecimal dailyRate = monthlyBasicPay.divide(BigDecimal.valueOf(26), 2, RoundingMode.HALF_UP);
            BigDecimal hourlyRate = dailyRate.divide(BigDecimal.valueOf(8), 6, RoundingMode.HALF_UP);
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

                LeaveRequest todaysLeave = leaves.stream()
                        .filter(l -> l.getLeaveDate().equals(loopDate))
                        .findFirst().orElse(null);

                List<OvertimeRequest> todaysOT = requests.stream()
                        .filter(ot -> ot.getWorkDate().equals(loopDate))
                        .toList();

                boolean isRestDay = (todaysSchedule == null);
                boolean isHoliday = holidayDates.contains(loopDate);
                boolean isRegularHoliday = regularHolidayDates.contains(loopDate);

                if (isRestDay || isHoliday) {
                    if (hasCompleteAttendancePair) {
                        Instant inInstant = dayInLog.getTimestamp();
                        Instant outInstant = dayOutLog.getTimestamp();

                        LocalTime logIn = inInstant.atZone(defaultProperties.getTimeZone()).toLocalTime();
                        LocalTime logOut = outInstant.atZone(defaultProperties.getTimeZone()).toLocalTime();

                        BigDecimal hoursWorked = payrollCalculator.deductUnpaidLunchHour(
                                payrollCalculator.calculateHours(logIn, logOut)
                        );

                        if (hoursWorked.compareTo(BigDecimal.ZERO) > 0) {
                            EarningsLine restDayEarnings = new EarningsLine();
                            restDayEarnings.setEarningsDate(loopDate);
                            restDayEarnings.setHours(hoursWorked);
                            BigDecimal premiumMultiplier = isRegularHoliday ? BigDecimal.valueOf(2.0) : BigDecimal.valueOf(1.3);
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
                            lateDeductAmount = lateDeductAmount.add(missingHours.multiply(hourlyRate));
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
                        nsdLine.setRate(hourlyRate.multiply(BigDecimal.valueOf(0.10)));
                        nsdLine.setAmount(nsdHours.multiply(nsdLine.getRate()));
                        nsdLine.setOvertime(false);
                        earningsLines.add(nsdLine);
                    }
                }

                for (OvertimeRequest ot : todaysOT) {
                    EarningsLine otLine = new EarningsLine();
                    otLine.setEarningsDate(loopDate);
                    otLine.setHours(payrollCalculator.calculateHours(ot.getStartTime().toLocalTime(), ot.getEndTime().toLocalTime()));
                    otLine.setRate(hourlyRate.multiply(BigDecimal.valueOf(1.25)));
                    otLine.setAmount(otLine.getHours().multiply(otLine.getRate()));
                    otLine.setOvertime(true);
                    otLine.setOvertimeRequest(ot);
                    earningsLines.add(otLine);
                }

                currentDate = currentDate.plusDays(1);
            }

            BigDecimal attendanceDeductionTotal = absentDeductAmount.add(lateDeductAmount);
            if (attendanceDeductionTotal.compareTo(basicPayPerPeriod) > 0) {
                BigDecimal overflow = attendanceDeductionTotal.subtract(basicPayPerPeriod);
                if (lateDeductAmount.compareTo(overflow) >= 0) {
                    lateDeductAmount = lateDeductAmount.subtract(overflow);
                } else {
                    overflow = overflow.subtract(lateDeductAmount);
                    lateDeductAmount = BigDecimal.ZERO;
                    absentDeductAmount = absentDeductAmount.subtract(overflow).max(BigDecimal.ZERO);
                }
            }

            PayrollItem item = new PayrollItem();
            item.setEmployee(emp);
            item.setPayrollRun(run);
            item.setDisputes(new ArrayList<>());

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

            BigDecimal grossPay = basicPayPerPeriod.add(earningsTotal).setScale(2, RoundingMode.HALF_UP);
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

            BigDecimal netPay = grossPay.subtract(deductionsTotal).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            item.setNetPay(netPay);

            items.add(item);
        }
    }

    private BigDecimal calculateExpectedShiftHours(TemplateShiftSchedule schedule) {
        return payrollCalculator.deductUnpaidLunchHour(
                payrollCalculator.calculateHours(schedule.getStartTime(), schedule.getEndTime())
        );
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


}
