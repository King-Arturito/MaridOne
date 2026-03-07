package org.example.maridone.payroll.run;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
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
import org.example.maridone.exception.EmployeeNotFoundException;
import org.example.maridone.exception.ItemNotFoundException;
import org.example.maridone.exception.RunNotFoundException;
import org.example.maridone.leave.request.LeaveRequest;
import org.example.maridone.leave.request.LeaveRequestRepository;
import org.example.maridone.log.AttendanceLogRepository;
import org.example.maridone.log.attendance.AttendanceLog;
import org.example.maridone.overtime.OvertimeRequest;
import org.example.maridone.overtime.OvertimeRequestRepository;
import org.example.maridone.overtime.spec.OvertimeSpecs;
import org.example.maridone.payroll.PayrollCalculator;
import org.example.maridone.payroll.dto.ItemDetailsDto;
import org.example.maridone.payroll.dto.ItemSummaryDto;
import org.example.maridone.payroll.dto.PayrollItemDto;
import org.example.maridone.payroll.dto.RunCreateDto;
import org.example.maridone.payroll.item.PayrollItem;
import org.example.maridone.payroll.item.component.DeductionsLine;
import org.example.maridone.payroll.item.component.EarningsLine;
import org.example.maridone.payroll.mapper.PayrollMapper;
import org.example.maridone.payroll.spec.ItemSpecs;
import org.example.maridone.schedule.calendar.CalendarRepository;
import org.example.maridone.schedule.calendar.CompanyCalendar;
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
    private final CalendarRepository calendarRepository;
    private final PayrollCalculator payrollCalculator;
    private final PayrollMapper payrollMapper;
    private final DefaultProperties defaultProperties;

    PayrollService(
            PayrollRunRepository payrollRunRepository,
            PayrollItemRepository payrollItemRepository,
            EmployeeRepository employeeRepository,
            AttendanceLogRepository attendanceLogRepository,
            TemplateShiftRepository templateShiftRepository,
            OvertimeRequestRepository overtimeRequestRepository,
            LeaveRequestRepository leaveRequestRepository,
            CalendarRepository calendarRepository,
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
        this.calendarRepository = calendarRepository;
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





    /*
        PAYROLL PROCESS
    */
    @Transactional
    @ExecutionTime
    public void processPayroll(RunCreateDto payload) {
        //create run for this period
        PayrollRun run = createRun(payload);

        Specification<Employee> spec = CommonSpecs.fieldNotEquals("employmentStatus", EmploymentStatus.TERMINATED);
        List<Employee> employees = employeeRepository.findAll(spec);
        Map<Boolean, List<Employee>> partitioned = employees.stream()
                .collect(Collectors.partitioningBy(
                        emp -> emp.getExemptionStatus().equals(ExemptionStatus.EXEMPT)
                ));
        List<Employee> exemptEmployees = partitioned.getOrDefault(true, List.of());
        List<Employee> nonExemptEmployees = partitioned.getOrDefault(false, List.of());

        //do process payroll exempt for managerial roles
        List<PayrollItem> items = new ArrayList<>(processPayrollExempt(run, exemptEmployees));

        //do process payroll nonexempt for regular employees (non exempt)
        processPayrollNonExempt(run, items, nonExemptEmployees);
        //save all
        payrollItemRepository.saveAll(items);
    }

    @Transactional
    public List<PayrollItem> processPayrollExempt(PayrollRun run, List<Employee> employees) {

        //map each exempt employee with a PayrollItem Object
        //collect the result to a list
        //return the list
        return employees.stream()
                .map(emp -> {
                    PayrollItem item = new PayrollItem();
                    BigDecimal biweeklySalary = emp.getYearlySalary().divide(BigDecimal.valueOf(26), 2, RoundingMode.HALF_UP);
                    item.setEmployee(emp);
                    item.setPayrollRun(run);
                    //todo
                    item.setGrossPay(biweeklySalary);
                    item.setNetPay(biweeklySalary);
                    item.setDisputes(new ArrayList<>());
                    //todo
                    item.setEarnings(payrollCalculator.setEarnings(emp, item));
                    //todo
                    item.setDeductions(
                            payrollCalculator.setDeductions(
                                    emp,
                                    item,
                                    BigDecimal.ZERO,
                                    BigDecimal.ZERO
                            )
                    );
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

        //convert payroll period boundaries to instants using application time zone
        Instant periodStart = run.getPeriodStart()
                .atStartOfDay(defaultProperties.getTimeZone())
                .toInstant();
        Instant periodEnd = run.getPeriodEnd()
                .atTime(LocalTime.MAX)
                .atZone(defaultProperties.getTimeZone())
                .toInstant();

        //get all attendance logs in all those employeeIds within the specified period + 6 hours
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

        //get all overtime requests for the period
        List<OvertimeRequest> overtimeRequests = overtimeRequestRepository.findAll(overtimeSpecs);

        //get all shift schedules of employees
        List<TemplateShiftSchedule> allSchedules = templateShiftRepository.findByEmployee_EmployeeIdIn(employeeIds);

        //get all leave requests of employees for the period
        List<LeaveRequest> allLeaves = leaveRequestRepository.findApprovedLeavesForPeriod(
            employeeIds, run.getPeriodStart(), run.getPeriodEnd()
        );

        //load all active calendar events overlapping the payroll period
        List<CompanyCalendar> calendarEvents = calendarRepository.findActiveEventsOverlappingPeriod(periodStart, periodEnd);
        //expand holiday event ranges into date sets for quick holiday checks
        Set<LocalDate> holidayDates = new HashSet<>();
        Set<LocalDate> regularHolidayDates = new HashSet<>();
        for (CompanyCalendar event : calendarEvents) {
            String eventTitle = event.getTitle() == null ? "" : event.getTitle().trim().toLowerCase();
            boolean isRegularHolidayEvent = eventTitle.contains("regular holiday");
            boolean isSpecialNonWorkingHolidayEvent =
                    eventTitle.contains("special non-working holiday")
                            || eventTitle.contains("special nonworking holiday")
                            || eventTitle.contains("special non-working")
                            || eventTitle.contains("special nonworking");
            if (!isRegularHolidayEvent && !isSpecialNonWorkingHolidayEvent) {
                continue;
            }
            LocalDate start = event.getStartDate().atZone(defaultProperties.getTimeZone()).toLocalDate();
            LocalDate end = event.getEndDate().atZone(defaultProperties.getTimeZone()).toLocalDate();
            LocalDate cursor = start;
            while (!cursor.isAfter(end)) {
                holidayDates.add(cursor);
                if (isRegularHolidayEvent) {
                    regularHolidayDates.add(cursor);
                }
                cursor = cursor.plusDays(1);
            }
        }
        
        //create a key-value pair by emp id -> List<AttendanceLogs>
        Map<Long, List<AttendanceLog>> attendanceMap = allLogs.stream()
                .collect(Collectors.groupingBy(AttendanceLog::getEmployeeId));

        //create a key-value pair by emp id -> List<ShiftSchedule>
        Map<Long, List<TemplateShiftSchedule>> scheduleMap = allSchedules.stream()
                .collect(Collectors.groupingBy(schedule -> schedule.getEmployee().getEmployeeId()));

        //create a key-value pair by emp id -> List<OvertimeRequests>
        Map<Long, List<OvertimeRequest>> overtimeMap = overtimeRequests.stream()
                .collect(Collectors.groupingBy(OvertimeRequest::getEmployeeId));

        //create a key-value pair by emp id -> List<LeaveRequests>
        Map<Long, List<LeaveRequest>> leaveMap = allLeaves.stream()
                .collect(Collectors.groupingBy(leave -> leave.getEmployee().getEmployeeId()));

        for (Employee emp :  employees) {
            List<OvertimeRequest> requests = overtimeMap.getOrDefault(emp.getEmployeeId(), List.of());
            List<TemplateShiftSchedule> schedules = scheduleMap.getOrDefault(emp.getEmployeeId(), List.of());
            List<AttendanceLog> attendanceLogs = attendanceMap.getOrDefault(emp.getEmployeeId(), List.of());
            List<LeaveRequest> leaves = leaveMap.getOrDefault(emp.getEmployeeId(), List.of());

            //compute pay rates
            BigDecimal monthlyBasicPay = emp.getYearlySalary().divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
            BigDecimal basicPayPerPeriod = monthlyBasicPay.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
            BigDecimal dailyRate = monthlyBasicPay.divide(BigDecimal.valueOf(26), 2, RoundingMode.HALF_UP);
            BigDecimal hourlyRate = dailyRate.divide(BigDecimal.valueOf(8), 2, RoundingMode.HALF_UP);

            //initialize deduction totals and earnings lines
            BigDecimal absentDeductAmount = BigDecimal.ZERO;
            BigDecimal lateDeductAmount = BigDecimal.ZERO;
            List<EarningsLine> earningsLines = new ArrayList<>();

            //iterate each day in the payroll period
            LocalDate currentDate = run.getPeriodStart();
            while (!currentDate.isAfter(run.getPeriodEnd())) {
                LocalDate loopDate = currentDate;

                //get schedule, logs, leaves, and overtime for the current date
                TemplateShiftSchedule todaysSchedule = schedules.stream()
                        .filter(s -> s.getDayOfWeek() == loopDate.getDayOfWeek())
                        .findFirst().orElse(null);

                //pair logs as one IN + one OUT per shift; OUT can be until 6:00 AM next day
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
                        //get matched IN and OUT log timestamps
                        Instant inInstant = dayInLog.getTimestamp();
                        Instant outInstant = dayOutLog.getTimestamp();

                        //convert log timestamps to local time
                        LocalTime logIn = inInstant.atZone(defaultProperties.getTimeZone()).toLocalTime();
                        LocalTime logOut = outInstant.atZone(defaultProperties.getTimeZone()).toLocalTime();

                        //calculate worked hours
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
                    //regular workday flow
                    BigDecimal expectedHours = payrollCalculator.deductUnpaidLunchHour(
                            payrollCalculator.calculateHours(todaysSchedule.getStartTime(), todaysSchedule.getEndTime())
                    );
                    BigDecimal leaveHours = BigDecimal.ZERO;

                    if (todaysLeave != null) {
                        leaveHours = payrollCalculator.calculateHours(todaysLeave.getStartTime(), todaysLeave.getEndTime());
                        if (leaveHours.compareTo(expectedHours) > 0) {
                            leaveHours = expectedHours;
                        }
                    }

                    //expected hours to be worked after approved leave hours are deducted
                    BigDecimal expectedRemainingHours = expectedHours.subtract(leaveHours);
                    if (expectedRemainingHours.compareTo(BigDecimal.ZERO) <= 0) {
                        //no deduction for whole-day approved leave
                    } else if (!hasCompleteAttendancePair) {
                        //missing IN/OUT pair means absent for full day, or missing remaining hours after partial leave
                        if (todaysLeave == null) {
                            absentDeductAmount = absentDeductAmount.add(dailyRate);
                        } else {
                            lateDeductAmount = lateDeductAmount.add(expectedRemainingHours.multiply(hourlyRate));
                        }
                    } else {
                        LocalTime logIn = dayInLog.getTimestamp().atZone(defaultProperties.getTimeZone()).toLocalTime();
                        LocalTime logOut = dayOutLog.getTimestamp().atZone(defaultProperties.getTimeZone()).toLocalTime();
                        BigDecimal actualHours = payrollCalculator.deductUnpaidLunchHour(
                                payrollCalculator.calculateHours(logIn, logOut)
                        );
                        //baseline is full shift hours or remaining hours when leave exists
                        BigDecimal baseline = todaysLeave == null ? expectedHours : expectedRemainingHours;

                        if (actualHours.compareTo(baseline) < 0) {
                            BigDecimal missingHours = baseline.subtract(actualHours);
                            lateDeductAmount = lateDeductAmount.add(missingHours.multiply(hourlyRate));
                        }
                    }
                }

                //check hours between 10 PM and 6 AM (night differential)
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

                //create earnings lines for approved overtime requests
                for (OvertimeRequest ot : todaysOT) {
                    EarningsLine otLine = new EarningsLine();
                    otLine.setEarningsDate(loopDate);
                    otLine.setHours(payrollCalculator.calculateHours(ot.getStartTime().toLocalTime(), ot.getEndTime().toLocalTime()));
                    otLine.setRate(hourlyRate.multiply(BigDecimal.valueOf(1.25))); // Standard OT 125%
                    otLine.setAmount(otLine.getHours().multiply(otLine.getRate()));
                    otLine.setOvertime(true);
                    otLine.setOvertimeRequest(ot);
                    earningsLines.add(otLine);
                }

                currentDate = currentDate.plusDays(1);
            }

            //build payroll item for employee
            PayrollItem item = new PayrollItem();
            item.setEmployee(emp);
            item.setPayrollRun(run);
            item.setDisputes(new ArrayList<>());
            
            //delegate earnings and deductions to payroll calculator
            List<EarningsLine> calculatedEarnings = payrollCalculator.setEarnings(emp, item, earningsLines);
            if (calculatedEarnings == null) {
                calculatedEarnings = List.of();
            }
            item.setEarnings(calculatedEarnings);
            //link back payroll item for ORM mapping
            item.getEarnings().forEach(line -> line.setPayrollItem(item)); 
            
            //keep deductions population delegated to PayrollCalculator task owner
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

            //derive totals from computed line items
            BigDecimal earningsTotal = item.getEarnings() == null
                    ? BigDecimal.ZERO
                    : item.getEarnings().stream()
                            .map(EarningsLine::getAmount)
                            .filter(amount -> amount != null)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal deductionsTotal = item.getDeductions() == null
                    ? BigDecimal.ZERO
                    : item.getDeductions().stream()
                            .map(DeductionsLine::getAmount)
                            .filter(amount -> amount != null)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

            //compute final pay values
            BigDecimal grossPay = basicPayPerPeriod.add(earningsTotal).setScale(2, RoundingMode.HALF_UP);
            BigDecimal netPay = grossPay.subtract(deductionsTotal).setScale(2, RoundingMode.HALF_UP);

            item.setGrossPay(grossPay);
            item.setNetPay(netPay);

            //add payroll item to batch list
            items.add(item);
        }
    }


    /*
        HELPER METHODS FOR NON-EXEMPT
    */
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
