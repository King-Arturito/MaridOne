package org.example.maridone.payroll;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.example.maridone.common.CommonCalculator;
import org.example.maridone.config.DefaultConfig;
import org.example.maridone.config.PayrollConfig;
import org.example.maridone.core.employee.Employee;
import org.example.maridone.enums.DeductionType;
import org.example.maridone.enums.ExemptionStatus;
import org.example.maridone.leave.request.LeaveRequest;
import org.example.maridone.log.attendance.AttendanceLog;
import org.example.maridone.payroll.item.PayrollItem;
import org.example.maridone.payroll.item.component.DeductionsLine;
import org.example.maridone.payroll.item.component.DeductionsRepository;
import org.example.maridone.payroll.item.component.EarningsLine;
import org.example.maridone.payroll.item.component.EarningsRepository;
import org.example.maridone.schedule.shift.TemplateShiftSchedule;
import org.springframework.stereotype.Component;

@Component
public class PayrollCalculator {

    private final EarningsRepository earningsRepository;
    private final DeductionsRepository deductionsRepository;
    private final BracketService bracketService;
    private final DefaultConfig defaultConfig;
    private final PayrollConfig payrollConfig;

    public PayrollCalculator(
        EarningsRepository earningsRepository,
        DeductionsRepository deductionsRepository,
        BracketService bracketService,
        DefaultConfig defaultConfig,
        PayrollConfig payrollConfig
    )
    {
        this.earningsRepository = earningsRepository;
        this.deductionsRepository = deductionsRepository;
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
        BigDecimal sssDue = computeSssEmployeeSharePerCutoff(monthlyBasicPay);
        BigDecimal philHealthDue = computePhilHealthEmployeeSharePerCutoff(monthlyBasicPay);
        BigDecimal pagibigDue = computePagibigEmployeeSharePerCutoff(monthlyBasicPay);

        BigDecimal nti = payAfterAttendance.subtract(sssDue).subtract(philHealthDue).subtract(pagibigDue);
        if (nti.compareTo(BigDecimal.ZERO) < 0) {
            nti = BigDecimal.ZERO;
        }
        nti = CommonCalculator.normalize(nti);

        BigDecimal withholdingDue = bracketService.computeWithholdingTaxPerCutoff(nti);
        DeductionType withholdingType = bracketService.resolveWithholdingDeductionType(nti);

        addDeductionLine(deductions, item, DeductionType.SSS, sssDue);
        addDeductionLine(deductions, item, DeductionType.PHILHEALTH, philHealthDue);
        addDeductionLine(deductions, item, DeductionType.PAGIBIG, pagibigDue);
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

    public BigDecimal calculateHours(List<TemplateShiftSchedule> schedules) {
        if (schedules == null || schedules.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal hours = BigDecimal.ZERO;
        for (TemplateShiftSchedule schedule : schedules) {
            hours = hours.add(calculateHours(schedule.getStartTime(), schedule.getEndTime()));
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

    public BigDecimal deductUnpaidLunchHour(BigDecimal rawHours) {
        if (rawHours == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        if (rawHours.compareTo(BigDecimal.valueOf(5)) <= 0) {
            return rawHours.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal adjusted = rawHours.subtract(BigDecimal.ONE);
        if (adjusted.compareTo(BigDecimal.ZERO) < 0) {
            adjusted = BigDecimal.ZERO;
        }
        return adjusted.setScale(2, RoundingMode.HALF_UP);
    }


    public long calculateLateMinutes(TemplateShiftSchedule schedule, AttendanceLog inLog, LeaveRequest leave) {
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

    public LocalTime resolveExpectedStartTime(TemplateShiftSchedule schedule, LeaveRequest leave) {
        LocalTime scheduleStart = schedule.getStartTime();
        if (leave == null || leave.getStartTime() == null || leave.getEndTime() == null) {
            return scheduleStart;
        }

        boolean leaveCoversShiftStart = !leave.getStartTime().isAfter(scheduleStart)
                && leave.getEndTime().isAfter(scheduleStart);
        return leaveCoversShiftStart ? leave.getEndTime() : scheduleStart;
    }

    public boolean hasAnyAttendanceLogForDate(List<AttendanceLog> attendanceLogs, LocalDate workDate) {
        return attendanceLogs.stream()
                .anyMatch(log -> log.getTimestamp()
                        .atZone(defaultConfig.getTimeZone())
                        .toLocalDate()
                        .equals(workDate));
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
        BigDecimal msc = resolveSssMsc(monthlyCompensation);
        if (msc.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal monthlyEmployeeShare = msc.multiply(payrollConfig.getSssEmployeeRate());
        return monthlyEmployeeShare.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
    }

    public BigDecimal computePhilHealthEmployeeSharePerCutoff(BigDecimal monthlyCompensation) {
        BigDecimal monthly = CommonCalculator.normalize(monthlyCompensation);
        if (monthly.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal adjustedMonthly = monthly.max(payrollConfig.getPhilHealthFloor()).min(payrollConfig.getPhilHealthCeiling());
        BigDecimal employeeMonthlyShare = adjustedMonthly.multiply(payrollConfig.getPhilHealthEmployeeRate());
        return employeeMonthlyShare.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
    }

    public BigDecimal computePagibigEmployeeSharePerCutoff(BigDecimal monthlyCompensation) {
        BigDecimal monthly = CommonCalculator.normalize(monthlyCompensation);
        if (monthly.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal mfs = monthly.min(payrollConfig.getPagibigMfsCap());
        BigDecimal rate = monthly.compareTo(payrollConfig.getPagibigLowerSalaryThreshold()) <= 0
                ? payrollConfig.getPagibigLowerRate()
                : payrollConfig.getPagibigHigherRate();

        BigDecimal monthlyShare = mfs.multiply(rate);
        return monthlyShare.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
    }

    public BigDecimal computeExemptDailyAbsenceRate(BigDecimal yearlyCompensation) {
        return computeDailyRate(yearlyCompensation);
    }

    public BigDecimal resolveHolidayOrRestDayMultiplier(boolean isRestDay, boolean isHoliday, boolean isRegularHoliday) {
        if (isRegularHoliday) {
            return payrollConfig.getRegularHolidayWorkMultiplier();
        }
        if (isHoliday) {
            return payrollConfig.getSpecialHolidayWorkMultiplier();
        }
        if (isRestDay) {
            return payrollConfig.getRestDayWorkMultiplier();
        }
        return BigDecimal.ONE;
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

}
