package org.example.maridone.payroll;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.example.maridone.config.PayrollProperties;
import org.example.maridone.core.employee.Employee;
import org.example.maridone.enums.DeductionType;
import org.example.maridone.enums.ExemptionStatus;
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
    private final PayrollProperties payrollProperties;

    public PayrollCalculator(
        EarningsRepository earningsRepository,
        DeductionsRepository deductionsRepository,
        PayrollProperties payrollProperties
    )
    {
        this.earningsRepository = earningsRepository;
        this.deductionsRepository = deductionsRepository;
        this.payrollProperties = payrollProperties;
    }


    public List<EarningsLine> setEarnings(Employee emp, PayrollItem item) {
        List<EarningsLine> earnings =  new ArrayList<>();
        BigDecimal grossPay = normalizeMoney(item.getGrossPay());
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
            earnings.add(createBaseEarningsLine(item, normalizeMoney(item.getGrossPay()), fallbackDate));
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
        BigDecimal yearlySalary = normalizeMoney(emp.getYearlySalary());
        BigDecimal grossPay = item != null ? normalizeMoney(item.getGrossPay()) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        if (yearlySalary.compareTo(BigDecimal.ZERO) <= 0 || grossPay.compareTo(BigDecimal.ZERO) <= 0) {
            return deductions;
        }

        BigDecimal absentAmount = normalizeMoney(absentDeductAmount);
        BigDecimal lateAmount = normalizeMoney(lateDeductAmount);

        BigDecimal collectedAbsent = collectAmount(grossPay, absentAmount);
        BigDecimal remainingAfterAbsent = grossPay.subtract(collectedAbsent);
        BigDecimal collectedLate = collectAmount(remainingAfterAbsent, lateAmount);

        addDeductionLine(deductions, item, DeductionType.ABSENT_DEDUCTION, collectedAbsent);
        addDeductionLine(deductions, item, DeductionType.LATE_PENALTY, collectedLate);

        BigDecimal payAfterAttendance = grossPay.subtract(collectedAbsent).subtract(collectedLate);
        BigDecimal monthlyBasicPay = payrollProperties.computeMonthlyBasicPay(yearlySalary);
        BigDecimal sssDue = payrollProperties.computeSssEmployeeSharePerCutoff(monthlyBasicPay);
        BigDecimal philHealthDue = payrollProperties.computePhilHealthEmployeeSharePerCutoff(monthlyBasicPay);
        BigDecimal pagibigDue = payrollProperties.computePagibigEmployeeSharePerCutoff(monthlyBasicPay);

        BigDecimal nti = payAfterAttendance.subtract(sssDue).subtract(philHealthDue).subtract(pagibigDue);
        if (nti.compareTo(BigDecimal.ZERO) < 0) {
            nti = BigDecimal.ZERO;
        }
        nti = normalizeMoney(nti);

        BigDecimal withholdingDue = payrollProperties.computeWithholdingTaxPerCutoff(nti);
        DeductionType withholdingType = payrollProperties.resolveWithholdingDeductionType(nti);

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
        BigDecimal normalized = normalizeMoney(amount);
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
        line.setAmount(normalizeMoney(amount));
        line.setEarningsDate(earningsDate);
        line.setPayrollItem(item);
        line.setOvertime(false);
        return line;
    }

    private BigDecimal collectAmount(BigDecimal pool, BigDecimal due) {
        BigDecimal normalizedPool = normalizeMoney(pool).max(BigDecimal.ZERO);
        BigDecimal normalizedDue = normalizeMoney(due).max(BigDecimal.ZERO);
        return normalizedDue.min(normalizedPool).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal normalizeMoney(BigDecimal value) {
        if (value == null) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private LocalDate resolveEarningsDate(PayrollItem item) {
        if (item.getPayrollRun() != null && item.getPayrollRun().getPeriodEnd() != null) {
            return item.getPayrollRun().getPeriodEnd();
        }
        return LocalDate.now();
    }

}
