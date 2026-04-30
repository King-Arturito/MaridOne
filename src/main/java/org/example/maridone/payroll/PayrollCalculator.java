package org.example.maridone.payroll;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.example.maridone.common.CommonCalculator;
import org.example.maridone.config.DefaultConfig;
import org.example.maridone.config.PayrollConfig;
import org.example.maridone.core.employee.Employee;
import org.example.maridone.enums.DeductionType;
import org.example.maridone.enums.EarningsType;
import org.example.maridone.enums.ExemptionStatus;
import org.example.maridone.leave.request.LeaveRequest;
import org.example.maridone.log.attendance.AttendanceLog;
import org.example.maridone.payroll.item.PayrollItem;
import org.example.maridone.payroll.item.component.DeductionsLine;
import org.example.maridone.payroll.item.component.EarningsLine;
import org.example.maridone.schedule.shift.DailyShiftSchedule;
import org.springframework.stereotype.Component;

@Component
public class PayrollCalculator {

    public record AttendanceSession(AttendanceLog inLog, AttendanceLog outLog) {}
    public record RegularTaxContext(
            BigDecimal monthlyBasicPay,
            BigDecimal monthlyGrossPay,
            BigDecimal priorMonthPayAfterAttendance,
            BigDecimal priorMonthWithholding,
            boolean hasPriorRegularCutoff
    ) {
        public RegularTaxContext {
            monthlyBasicPay = CommonCalculator.normalize(monthlyBasicPay);
            monthlyGrossPay = CommonCalculator.normalize(monthlyGrossPay);
            priorMonthPayAfterAttendance = CommonCalculator.normalize(priorMonthPayAfterAttendance);
            priorMonthWithholding = CommonCalculator.normalize(priorMonthWithholding);
        }

        public static RegularTaxContext empty() {
            return new RegularTaxContext(
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    false
            );
        }
    }

    private enum RegularTaxSchedule {
        LEGACY_PER_CUTOFF,
        FIRST_HALF,
        SECOND_HALF,
        NONE
    }

    private final BracketService bracketService;
    private final DefaultConfig defaultConfig;
    private final PayrollConfig payrollConfig;

    public PayrollCalculator(
        BracketService bracketService,
        DefaultConfig defaultConfig,
        PayrollConfig payrollConfig
    )
    {
        this.bracketService = bracketService;
        this.defaultConfig = defaultConfig;
        this.payrollConfig = payrollConfig;
    }


    private int overlapMinutes(int startA, int endA, int startB, int endB) {
        int start = Math.max(startA, startB);
        int end = Math.min(endA, endB);
        return Math.max(0, end - start);
    }

    private void addDeductionLine(
            List<DeductionsLine> deductions,
            PayrollItem item,
            DeductionType type,
            BigDecimal amount
    ) {
        BigDecimal normalized = CommonCalculator.normalize(amount);
        if (normalized.compareTo(BigDecimal.ZERO) <= 0) return;

        DeductionsLine line = new DeductionsLine();
        line.setPayrollItem(item);
        line.setDeductionType(type);
        line.setAmount(normalized);
        deductions.add(line);
    }

    private EarningsLine createBaseEarningsLine(PayrollItem item, BigDecimal amount, LocalDate earningsDate) {
        EarningsLine line = new EarningsLine();
        line.setRate(BigDecimal.ZERO);
        line.setHours(BigDecimal.ZERO);
        line.setAmount(CommonCalculator.normalize(amount));
        line.setEarningsDate(earningsDate);
        line.setPayrollItem(item);
        line.setOvertime(false);
        line.setEarningsType(EarningsType.BASIC);
        line.setTaxableAmount(CommonCalculator.normalize(amount));
        line.setNonTaxableAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        return line;
    }

    private BigDecimal collectAmount(BigDecimal pool, BigDecimal due) {
        BigDecimal normalizedPool = CommonCalculator.normalize(pool).max(BigDecimal.ZERO);
        BigDecimal normalizedDue = CommonCalculator.normalize(due).max(BigDecimal.ZERO);
        return normalizedDue.min(normalizedPool).setScale(2, RoundingMode.HALF_UP);
    }

    private LocalDate resolveEarningsDate(PayrollItem item) {
        if (item.getPayrollRun() != null && item.getPayrollRun().getPeriodEnd() != null) {
            return item.getPayrollRun().getPeriodEnd();
        }
        return LocalDate.now();
    }

    private LocalDate resolvePaymentDate(PayrollItem item) {
        if (item == null || item.getPayrollRun() == null) {
            return null;
        }
        if (item.getPayrollRun().getPaymentDate() != null) {
            return item.getPayrollRun().getPaymentDate();
        }
        return item.getPayrollRun().getPeriodEnd();
    }

    private RegularTaxSchedule resolveRegularTaxSchedule(PayrollItem item) {
        LocalDate paymentDate = resolvePaymentDate(item);
        if (paymentDate == null) {
            return RegularTaxSchedule.LEGACY_PER_CUTOFF;
        }

        int dayOfMonth = paymentDate.getDayOfMonth();
        if (dayOfMonth == 15) {
            return RegularTaxSchedule.FIRST_HALF;
        }
        if (dayOfMonth == 30) {
            return RegularTaxSchedule.SECOND_HALF;
        }
        return RegularTaxSchedule.NONE;
    }

    public List<EarningsLine> setEarnings(Employee emp, PayrollItem item) {
        List<EarningsLine> earnings =  new ArrayList<>();
        BigDecimal grossPay = CommonCalculator.normalize(item.getGrossPay());
        LocalDate earningsDate = resolveEarningsDate(item);
        if (emp.getExemptionStatus().equals(ExemptionStatus.EXEMPT)
                || emp.getExemptionStatus().equals(ExemptionStatus.NON_EXEMPT)) {
            earnings.add(createBaseEarningsLine(item, grossPay, earningsDate));
        }
        return earnings;
    }

    public List<EarningsLine> setEarnings(Employee emp, PayrollItem item, List<EarningsLine> precomputedLines) {
        LocalDate fallbackDate = resolveEarningsDate(item);
        List<EarningsLine> earnings = new ArrayList<>();

        if (emp.getExemptionStatus().equals(ExemptionStatus.NON_EXEMPT)) {
            earnings.add(createBaseEarningsLine(item, CommonCalculator.normalize(item.getGrossPay()), fallbackDate));
        }

        if (precomputedLines == null || precomputedLines.isEmpty()) {
            return earnings;
        }

        for (EarningsLine line : precomputedLines) {
            line.setPayrollItem(item);
            if (line.getEarningsDate() == null) {
                line.setEarningsDate(fallbackDate);
            }
            if (line.getOvertime() == null) {
                line.setOvertime(false);
            }
            if (line.getRate() == null) {
                line.setRate(BigDecimal.ZERO);
            }
            if (line.getHours() == null) {
                line.setHours(BigDecimal.ZERO);
            }
            if (line.getAmount() == null) {
                line.setAmount(BigDecimal.ZERO);
            }
            if (line.getEarningsType() == null) {
                line.setEarningsType(EarningsType.FIXED);
            }
            if (line.getEarningsType() != EarningsType.BONUS) {
                BigDecimal normalizedAmount = CommonCalculator.normalize(line.getAmount());
                line.setTaxableAmount(normalizedAmount);
                line.setNonTaxableAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            }
            earnings.add(line);
        }
        return earnings;
    }

    public List<DeductionsLine> setDeductions(
            Employee emp,
            PayrollItem item,
            BigDecimal absentDeductAmount,
            BigDecimal lateDeductAmount
    ) {
        return setDeductions(emp, item, absentDeductAmount, lateDeductAmount, RegularTaxContext.empty());
    }

    public List<DeductionsLine> setDeductions(
            Employee emp,
            PayrollItem item,
            BigDecimal absentDeductAmount,
            BigDecimal lateDeductAmount,
            RegularTaxContext taxContext
    ) {
        List<DeductionsLine> deductions = new ArrayList<>();
        BigDecimal yearlySalary = CommonCalculator.normalize(emp.getYearlySalary());
        BigDecimal grossPay = item != null ? CommonCalculator.normalize(item.getGrossPay()) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        if (yearlySalary.compareTo(BigDecimal.ZERO) <= 0 || grossPay.compareTo(BigDecimal.ZERO) <= 0) {
            return deductions;
        }

        BigDecimal absentAmount = CommonCalculator.normalize(absentDeductAmount);
        BigDecimal lateAmount = CommonCalculator.normalize(lateDeductAmount);

        BigDecimal collectedAbsent = collectAmount(grossPay, absentAmount);
        BigDecimal remainingAfterAbsent = grossPay.subtract(collectedAbsent);
        BigDecimal collectedLate = collectAmount(remainingAfterAbsent, lateAmount);

        addDeductionLine(deductions, item, DeductionType.ABSENT_DEDUCTION, collectedAbsent);
        addDeductionLine(deductions, item, DeductionType.LATE_PENALTY, collectedLate);

        BigDecimal payAfterAttendance = grossPay.subtract(collectedAbsent).subtract(collectedLate);
        BigDecimal monthlyBasicPay = computeMonthlyBasicPay(yearlySalary);
        BigDecimal cutoffSssDue = computeSssEmployeeSharePerCutoff(monthlyBasicPay);
        BigDecimal cutoffPhilHealthDue = computePhilHealthEmployeeSharePerCutoff(monthlyBasicPay);
        BigDecimal cutoffPagibigDue = computePagibigEmployeeSharePerCutoff(monthlyBasicPay);
        BigDecimal cutoffStatutoryTotal = CommonCalculator.normalize(
                cutoffSssDue.add(cutoffPhilHealthDue).add(cutoffPagibigDue)
        );

        RegularTaxSchedule schedule = resolveRegularTaxSchedule(item);
        if (schedule == RegularTaxSchedule.NONE) {
            return deductions;
        }

        if (schedule == RegularTaxSchedule.FIRST_HALF) {
            BigDecimal nti = CommonCalculator.normalizeNonNegative(payAfterAttendance);
            BigDecimal withholdingDue = bracketService.computeWithholdingTaxPerCutoff(nti);
            DeductionType withholdingType = bracketService.resolveWithholdingDeductionType(nti);
            addDeductionLine(deductions, item, withholdingType, withholdingDue);
            return deductions;
        }

        if (schedule == RegularTaxSchedule.SECOND_HALF) {
            BigDecimal monthlyBasicBase = taxContext.monthlyBasicPay().compareTo(BigDecimal.ZERO) > 0
                    ? taxContext.monthlyBasicPay()
                    : monthlyBasicPay;
            BigDecimal monthlyGrossBase = taxContext.monthlyGrossPay().compareTo(BigDecimal.ZERO) > 0
                    ? taxContext.monthlyGrossPay()
                    : grossPay;

            BigDecimal monthlySssDue = computeSssEmployeeShareMonthly(monthlyGrossBase);
            BigDecimal monthlyPhilHealthDue = computePhilHealthEmployeeShareMonthly(monthlyBasicBase);
            BigDecimal monthlyPagibigDue = computePagibigEmployeeShareMonthly(monthlyBasicBase);
            BigDecimal monthlyStatutoryTotal = CommonCalculator.normalize(
                    monthlySssDue.add(monthlyPhilHealthDue).add(monthlyPagibigDue)
            );

            BigDecimal monthlyPayAfterAttendance = CommonCalculator.normalize(
                    taxContext.priorMonthPayAfterAttendance().add(payAfterAttendance)
            );
            BigDecimal monthlyNti = CommonCalculator.normalizeNonNegative(
                    monthlyPayAfterAttendance.subtract(monthlyStatutoryTotal)
            );
            BigDecimal monthlyWithholdingDue = computeWithholdingTaxPerMonth(monthlyNti);
            BigDecimal withholdingDue = CommonCalculator.normalizeNonNegative(
                    monthlyWithholdingDue.subtract(taxContext.priorMonthWithholding())
            );
            DeductionType withholdingType = resolveWithholdingDeductionTypePerMonth(monthlyNti);

            addDeductionLine(deductions, item, DeductionType.SSS, monthlySssDue);
            addDeductionLine(deductions, item, DeductionType.PHILHEALTH, monthlyPhilHealthDue);
            addDeductionLine(deductions, item, DeductionType.PAGIBIG, monthlyPagibigDue);
            addDeductionLine(deductions, item, withholdingType, withholdingDue);
            return deductions;
        }

        BigDecimal nti = CommonCalculator.normalizeNonNegative(payAfterAttendance.subtract(cutoffStatutoryTotal));
        BigDecimal withholdingDue = bracketService.computeWithholdingTaxPerCutoff(nti);
        DeductionType withholdingType = bracketService.resolveWithholdingDeductionType(nti);

        addDeductionLine(deductions, item, DeductionType.SSS, cutoffSssDue);
        addDeductionLine(deductions, item, DeductionType.PHILHEALTH, cutoffPhilHealthDue);
        addDeductionLine(deductions, item, DeductionType.PAGIBIG, cutoffPagibigDue);
        addDeductionLine(deductions, item, withholdingType, withholdingDue);

        return deductions;
    }


    public BigDecimal calculateHours(LocalTime start, LocalTime end) {
        Duration time = Duration.between(start,end);
        if (time.isNegative()) {
            time = time.plusDays(1);
        }

        return BigDecimal.valueOf(time.toMinutes()).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateHours(Instant start, Instant end) {
        if (start == null || end == null || !end.isAfter(start)) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        Duration time = Duration.between(start, end);
        return BigDecimal.valueOf(time.toMinutes()).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateHours(List<DailyShiftSchedule> schedules) {
        if (schedules == null || schedules.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal hours = BigDecimal.ZERO;
        for (DailyShiftSchedule schedule : schedules) {
            hours = hours.add(calculateHours(schedule.getStartTime(), schedule.getEndTime()));
        }
        return hours.setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateHoursFromAttendanceSessions(List<AttendanceSession> attendanceSessions) {
        if (attendanceSessions == null || attendanceSessions.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal hours = BigDecimal.ZERO;
        for (AttendanceSession session : attendanceSessions) {
            hours = hours.add(calculateHours(
                    session.inLog().getTimestamp(),
                    session.outLog().getTimestamp()
            ));
        }
        return hours.setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateWorkedHoursWithinSchedules(
            List<DailyShiftSchedule> schedules,
            List<AttendanceSession> attendanceSessions
    ) {
        if (schedules == null || schedules.isEmpty() || attendanceSessions == null || attendanceSessions.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal hours = BigDecimal.ZERO;
        for (DailyShiftSchedule schedule : schedules) {
            Instant scheduleStart = toInstant(schedule.getStartDateTime());
            Instant scheduleEnd = toInstant(schedule.getEndDateTime());
            if (scheduleStart == null || scheduleEnd == null || !scheduleEnd.isAfter(scheduleStart)) {
                continue;
            }

            for (AttendanceSession session : attendanceSessions) {
                Instant overlapStart = maxInstant(scheduleStart, session.inLog().getTimestamp());
                Instant overlapEnd = minInstant(scheduleEnd, session.outLog().getTimestamp());
                if (overlapEnd.isAfter(overlapStart)) {
                    hours = hours.add(calculateHours(overlapStart, overlapEnd));
                }
            }
        }

        return hours.setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateWorkedHoursWithinWindow(
            List<AttendanceSession> attendanceSessions,
            LocalDateTime windowStart,
            LocalDateTime windowEnd
    ) {
        Instant start = toInstant(windowStart);
        Instant end = toInstant(windowEnd);
        if (attendanceSessions == null
                || attendanceSessions.isEmpty()
                || start == null
                || end == null
                || !end.isAfter(start)) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal hours = BigDecimal.ZERO;
        for (AttendanceSession session : attendanceSessions) {
            Instant overlapStart = maxInstant(start, session.inLog().getTimestamp());
            Instant overlapEnd = minInstant(end, session.outLog().getTimestamp());
            if (overlapEnd.isAfter(overlapStart)) {
                hours = hours.add(calculateHours(overlapStart, overlapEnd));
            }
        }

        return hours.setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateNightDiffHours(LocalTime logIn, LocalTime logOut) {
        int inMinutes = logIn.getHour() * 60 + logIn.getMinute();
        int outMinutes = logOut.getHour() * 60 + logOut.getMinute();

        if (outMinutes <= inMinutes) {
            outMinutes += 24 * 60;
        }

        int nightMinutes = overlapMinutes(inMinutes, outMinutes, 0, 6 * 60)
                + overlapMinutes(inMinutes, outMinutes, 22 * 60, 30 * 60);

        return BigDecimal.valueOf(nightMinutes)
                .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateNightDiffHours(Instant start, Instant end) {
        if (start == null || end == null || !end.isAfter(start)) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal hours = BigDecimal.ZERO;
        LocalDate startDate = start.atZone(defaultConfig.getTimeZone()).toLocalDate().minusDays(1);
        LocalDate endDate = end.atZone(defaultConfig.getTimeZone()).toLocalDate();

        for (LocalDate currentDate = startDate; !currentDate.isAfter(endDate); currentDate = currentDate.plusDays(1)) {
            Instant nightWindowStart = currentDate.atTime(22, 0)
                    .atZone(defaultConfig.getTimeZone())
                    .toInstant();
            Instant nightWindowEnd = currentDate.plusDays(1).atTime(6, 0)
                    .atZone(defaultConfig.getTimeZone())
                    .toInstant();

            Instant overlapStart = maxInstant(start, nightWindowStart);
            Instant overlapEnd = minInstant(end, nightWindowEnd);
            if (overlapEnd.isAfter(overlapStart)) {
                hours = hours.add(calculateHours(overlapStart, overlapEnd));
            }
        }

        return hours.setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateNightDiffHours(List<AttendanceSession> attendanceSessions) {
        if (attendanceSessions == null || attendanceSessions.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal hours = BigDecimal.ZERO;
        for (AttendanceSession session : attendanceSessions) {
            hours = hours.add(calculateNightDiffHours(
                    session.inLog().getTimestamp(),
                    session.outLog().getTimestamp()
            ));
        }
        return hours.setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateNightDiffHoursWithinWindow(
            List<AttendanceSession> attendanceSessions,
            LocalDateTime windowStart,
            LocalDateTime windowEnd
    ) {
        Instant start = toInstant(windowStart);
        Instant end = toInstant(windowEnd);
        if (attendanceSessions == null
                || attendanceSessions.isEmpty()
                || start == null
                || end == null
                || !end.isAfter(start)) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal hours = BigDecimal.ZERO;
        for (AttendanceSession session : attendanceSessions) {
            Instant overlapStart = maxInstant(start, session.inLog().getTimestamp());
            Instant overlapEnd = minInstant(end, session.outLog().getTimestamp());
            if (overlapEnd.isAfter(overlapStart)) {
                hours = hours.add(calculateNightDiffHours(overlapStart, overlapEnd));
            }
        }

        return hours.setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal deductUnpaidLunchHour(BigDecimal rawHours) {
        return CommonCalculator.deductUnpaidLunchHour(rawHours);
    }

    public BigDecimal calculatePayableScheduledHours(List<DailyShiftSchedule> schedules) {
        BigDecimal rawHours = calculateHours(schedules);
        return applyLunchDeductionForSchedules(rawHours, schedules);
    }

    public BigDecimal calculatePayableWorkedHoursWithinSchedules(
            List<DailyShiftSchedule> schedules,
            List<AttendanceSession> attendanceSessions
    ) {
        BigDecimal rawHours = calculateWorkedHoursWithinSchedules(schedules, attendanceSessions);
        return applyLunchDeductionForSchedules(rawHours, schedules);
    }

    public BigDecimal calculateExpectedShiftHours(List<DailyShiftSchedule> schedules) {
        if (schedules == null || schedules.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        return calculatePayableScheduledHours(schedules);
    }

    private BigDecimal applyLunchDeductionForSchedules(BigDecimal rawHours, List<DailyShiftSchedule> schedules) {
        if (rawHours == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        if (hasExplicitScheduleBreak(schedules)) {
            return rawHours.setScale(2, RoundingMode.HALF_UP);
        }

        return deductUnpaidLunchHour(rawHours);
    }

    private boolean hasExplicitScheduleBreak(List<DailyShiftSchedule> schedules) {
        return schedules != null && schedules.size() > 1;
    }

    public long calculateLateMinutes(DailyShiftSchedule schedule, AttendanceLog inLog, LeaveRequest leave) {
        if (schedule == null || inLog == null) {
            return 0L;
        }

        LocalTime expectedStart = resolveExpectedStartTime(schedule, leave);
        LocalTime actualIn = inLog.getTimestamp().atZone(defaultConfig.getTimeZone()).toLocalTime();
        if (!actualIn.isAfter(expectedStart)) {
            return 0L;
        }

        return Duration.between(expectedStart, actualIn).toMinutes();
    }

    public LocalTime resolveExpectedStartTime(DailyShiftSchedule schedule, LeaveRequest leave) {
        LocalTime scheduleStart = schedule.getStartTime();
        if (leave == null || leave.getStartDateTime() == null || leave.getEndDateTime() == null) {
            return scheduleStart;
        }

        LocalTime leaveStart = leave.getStartDateTime().toLocalTime();
        LocalTime leaveEnd = leave.getEndDateTime().toLocalTime();
        boolean leaveCoversShiftStart = !leaveStart.isAfter(scheduleStart)
                && leaveEnd.isAfter(scheduleStart);
        return leaveCoversShiftStart ? leaveEnd : scheduleStart;
    }

    public boolean hasAnyAttendanceLogForDate(List<AttendanceLog> attendanceLogs, LocalDate workDate) {
        return attendanceLogs.stream()
                .anyMatch(log -> log.getTimestamp()
                        .atZone(defaultConfig.getTimeZone())
                        .toLocalDate()
                        .equals(workDate));
    }

    public List<AttendanceSession> findAttendanceSessionsForDate(List<AttendanceLog> attendanceLogs, LocalDate workDate) {
        if (attendanceLogs == null || attendanceLogs.isEmpty() || workDate == null) {
            return List.of();
        }

        Instant cutoff = workDate.plusDays(1)
                .atTime(LocalTime.of(6, 0))
                .atZone(defaultConfig.getTimeZone())
                .toInstant();

        List<AttendanceLog> relevantLogs = attendanceLogs.stream()
                .filter(log -> log.getTimestamp() != null)
                .filter(log -> !log.getTimestamp().isAfter(cutoff))
                .filter(log -> {
                    LocalDate logDate = log.getTimestamp().atZone(defaultConfig.getTimeZone()).toLocalDate();
                    return logDate.equals(workDate) || logDate.equals(workDate.plusDays(1));
                })
                .sorted(Comparator.comparing(AttendanceLog::getTimestamp))
                .toList();

        List<AttendanceSession> sessions = new ArrayList<>();
        AttendanceLog currentIn = null;

        for (AttendanceLog log : relevantLogs) {
            if (isInLog(log)) {
                LocalDate logDate = log.getTimestamp().atZone(defaultConfig.getTimeZone()).toLocalDate();
                if (currentIn == null && logDate.equals(workDate)) {
                    currentIn = log;
                }
                continue;
            }

            if (isOutLog(log) && currentIn != null && log.getTimestamp().isAfter(currentIn.getTimestamp())) {
                sessions.add(new AttendanceSession(currentIn, log));
                currentIn = null;
            }
        }

        return sessions;
    }

    public AttendanceLog findOutLogAfterIn(List<AttendanceLog> attendanceLogs, AttendanceLog inLog, LocalDate workDate) {
        if (inLog == null) {
            return null;
        }

        Instant cutoff = workDate.plusDays(1)
                .atTime(LocalTime.of(6, 0))
                .atZone(defaultConfig.getTimeZone())
                .toInstant();

        return attendanceLogs.stream()
                .filter(this::isOutLog)
                .filter(log -> log.getTimestamp().isAfter(inLog.getTimestamp()))
                .filter(log -> !log.getTimestamp().isAfter(cutoff))
                .findFirst()
                .orElse(null);
    }

    public boolean isInLog(AttendanceLog log) {
        return log.getDirection() != null && log.getDirection().trim().equalsIgnoreCase("IN");
    }

    public boolean isOutLog(AttendanceLog log) {
        return log.getDirection() != null && log.getDirection().trim().equalsIgnoreCase("OUT");
    }

    public AttendanceLog findInLogForDate(List<AttendanceLog> attendanceLogs, LocalDate workDate) {
        return attendanceLogs.stream()
                .filter(this::isInLog)
                .filter(log -> log.getTimestamp()
                        .atZone(defaultConfig.getTimeZone())
                        .toLocalDate()
                        .equals(workDate))
                .findFirst()
                .orElse(null);
    }

    public BigDecimal computeMonthlyBasicPay(BigDecimal yearlyCompensation) {
        BigDecimal yearly = CommonCalculator.normalize(yearlyCompensation);
        if (yearly.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        return yearly.divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
    }

    public BigDecimal computeSemiMonthlyBasicPay(BigDecimal yearlyCompensation) {
        BigDecimal yearly = CommonCalculator.normalize(yearlyCompensation);
        if (yearly.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        return yearly.divide(BigDecimal.valueOf(24), 2, RoundingMode.HALF_UP);
    }

    public BigDecimal computeProratedSemiMonthlyBasicPay(
            BigDecimal yearlyCompensation,
            long coveredUnits,
            long totalUnits
    ) {
        BigDecimal fullPeriodPay = computeSemiMonthlyBasicPay(yearlyCompensation);
        if (fullPeriodPay.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        if (totalUnits <= 0) {
            return fullPeriodPay;
        }

        long normalizedCoveredUnits = Math.max(0L, Math.min(coveredUnits, totalUnits));
        BigDecimal ratio = BigDecimal.valueOf(normalizedCoveredUnits)
                .divide(BigDecimal.valueOf(totalUnits), 6, RoundingMode.HALF_UP);

        return fullPeriodPay.multiply(ratio).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal computeDailyRate(BigDecimal yearlyCompensation) {
        BigDecimal monthly = computeMonthlyBasicPay(yearlyCompensation);
        if (monthly.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        return monthly.divide(BigDecimal.valueOf(26), 2, RoundingMode.HALF_UP);
    }

    public BigDecimal computeHourlyRate(BigDecimal yearlyCompensation) {
        BigDecimal daily = computeDailyRate(yearlyCompensation);
        if (daily.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);
        }

        return daily.divide(BigDecimal.valueOf(8), 6, RoundingMode.HALF_UP);
    }

    public BigDecimal computeSssEmployeeSharePerCutoff(BigDecimal monthlyCompensation) {
        BigDecimal monthlyEmployeeShare = computeSssEmployeeShareMonthly(monthlyCompensation);
        return monthlyEmployeeShare.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
    }

    public BigDecimal computeSssEmployeeShareMonthly(BigDecimal monthlyCompensation) {
        BigDecimal msc = resolveSssMsc(monthlyCompensation);
        if (msc.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return CommonCalculator.normalize(msc.multiply(payrollConfig.getSssEmployeeRate()));
    }

    public BigDecimal computePhilHealthEmployeeSharePerCutoff(BigDecimal monthlyCompensation) {
        BigDecimal employeeMonthlyShare = computePhilHealthEmployeeShareMonthly(monthlyCompensation);
        return employeeMonthlyShare.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
    }

    public BigDecimal computePhilHealthEmployeeShareMonthly(BigDecimal monthlyCompensation) {
        BigDecimal monthly = CommonCalculator.normalize(monthlyCompensation);
        if (monthly.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal adjustedMonthly = monthly.max(payrollConfig.getPhilHealthFloor()).min(payrollConfig.getPhilHealthCeiling());
        return CommonCalculator.normalize(adjustedMonthly.multiply(payrollConfig.getPhilHealthEmployeeRate()));
    }

    public BigDecimal computePagibigEmployeeSharePerCutoff(BigDecimal monthlyCompensation) {
        BigDecimal monthlyShare = computePagibigEmployeeShareMonthly(monthlyCompensation);
        return monthlyShare.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
    }

    public BigDecimal computePagibigEmployeeShareMonthly(BigDecimal monthlyCompensation) {
        BigDecimal monthly = CommonCalculator.normalize(monthlyCompensation);
        if (monthly.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal mfs = monthly.min(payrollConfig.getPagibigMfsCap());
        BigDecimal rate = monthly.compareTo(payrollConfig.getPagibigLowerSalaryThreshold()) <= 0
                ? payrollConfig.getPagibigLowerRate()
                : payrollConfig.getPagibigHigherRate();

        BigDecimal monthlyShare = mfs.multiply(rate);
        return CommonCalculator.normalize(monthlyShare);
    }

    public BigDecimal computeWithholdingTaxPerMonth(BigDecimal taxableCompensationPerMonth) {
        BigDecimal monthlyNti = CommonCalculator.normalize(taxableCompensationPerMonth).max(BigDecimal.ZERO.setScale(2));
        if (monthlyNti.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal ntiPerCutoff = monthlyNti.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
        BigDecimal withholdingPerCutoff = bracketService.computeWithholdingTaxPerCutoff(ntiPerCutoff);
        return CommonCalculator.normalize(withholdingPerCutoff.multiply(BigDecimal.valueOf(2)));
    }

    public DeductionType resolveWithholdingDeductionTypePerMonth(BigDecimal taxableCompensationPerMonth) {
        BigDecimal monthlyNti = CommonCalculator.normalize(taxableCompensationPerMonth).max(BigDecimal.ZERO.setScale(2));
        BigDecimal ntiPerCutoff = monthlyNti.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
        return bracketService.resolveWithholdingDeductionType(ntiPerCutoff);
    }

    public BigDecimal computeExemptDailyAbsenceRate(BigDecimal yearlyCompensation) {
        return computeDailyRate(yearlyCompensation);
    }

    public BigDecimal computeSssEmployerShareMonthly(BigDecimal monthlyGrossCompensation) {
        BigDecimal msc = resolveSssMsc(monthlyGrossCompensation);
        if (msc.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal regularBase = msc.min(payrollConfig.getSssWispThreshold());
        BigDecimal wispBase = msc.subtract(payrollConfig.getSssWispThreshold()).max(BigDecimal.ZERO.setScale(2));
        BigDecimal ecAmount = msc.compareTo(payrollConfig.getSssEcThreshold()) < 0
                ? payrollConfig.getSssEcLowerAmount()
                : payrollConfig.getSssEcUpperAmount();

        return CommonCalculator.normalize(
                regularBase.multiply(payrollConfig.getSssEmployerRegularRate())
                        .add(wispBase.multiply(payrollConfig.getSssEmployerWispRate()))
                        .add(ecAmount)
        );
    }

    public BigDecimal computePhilHealthEmployerShareMonthly(BigDecimal monthlyBasicCompensation) {
        BigDecimal monthly = CommonCalculator.normalize(monthlyBasicCompensation);
        if (monthly.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal adjustedMonthly = monthly.max(payrollConfig.getPhilHealthFloor()).min(payrollConfig.getPhilHealthCeiling());
        return CommonCalculator.normalize(adjustedMonthly.multiply(payrollConfig.getPhilHealthEmployerRate()));
    }

    public BigDecimal computePagibigEmployerShareMonthly(BigDecimal monthlyBasicCompensation) {
        BigDecimal monthly = CommonCalculator.normalize(monthlyBasicCompensation);
        if (monthly.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal mfs = monthly.min(payrollConfig.getPagibigMfsCap());
        return CommonCalculator.normalize(mfs.multiply(payrollConfig.getPagibigEmployerRate()));
    }

    public BigDecimal resolveHolidayOrRestDayPremiumRate(boolean isRestDay, boolean isHoliday, boolean isRegularHoliday) {
        if (isRegularHoliday) {
            return payrollConfig.getRegularHolidayWorkMultiplier();
        }
        if (isHoliday) {
            return payrollConfig.getSpecialHolidayWorkMultiplier();
        }
        if (isRestDay) {
            return payrollConfig.getRestDayWorkMultiplier();
        }
        return BigDecimal.ZERO;
    }

    public BigDecimal resolveHolidayOrRestDayMultiplier(boolean isRestDay, boolean isHoliday, boolean isRegularHoliday) {
        BigDecimal premiumRate = resolveHolidayOrRestDayPremiumRate(isRestDay, isHoliday, isRegularHoliday);
        return premiumRate.add(BigDecimal.ONE);
    }

    public BigDecimal resolveOvertimeMultiplier(EarningsType overtimeType) {
        if (overtimeType == EarningsType.DOUBLE_OVERTIME) {
            return payrollConfig.getDoubleOvertimeMultiplier();
        }
        if (overtimeType == EarningsType.TRIPLE_OVERTIME) {
            return payrollConfig.getTripleOvertimeMultiplier();
        }
        return payrollConfig.getOvertimeMultiplier();
    }

    private BigDecimal resolveSssMsc(BigDecimal monthlyCompensation) {
        BigDecimal monthly = CommonCalculator.normalize(monthlyCompensation);
        if (monthly.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal clamped = monthly.max(payrollConfig.getSssMinimumMsc()).min(payrollConfig.getSssMaximumMsc());
        BigDecimal roundedMsc = clamped
                .divide(payrollConfig.getSssMscStep(), 0, RoundingMode.HALF_UP)
                .multiply(payrollConfig.getSssMscStep());

        return roundedMsc.setScale(2, RoundingMode.HALF_UP);
    }

    private Instant toInstant(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return value.atZone(defaultConfig.getTimeZone()).toInstant();
    }

    private Instant maxInstant(Instant first, Instant second) {
        return first.compareTo(second) >= 0 ? first : second;
    }

    private Instant minInstant(Instant first, Instant second) {
        return first.compareTo(second) <= 0 ? first : second;
    }

}
