package org.example.maridone.payroll;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

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

    public PayrollCalculator(
        EarningsRepository earningsRepository,
        DeductionsRepository deductionsRepository
    )
    {
        this.earningsRepository = earningsRepository;
        this.deductionsRepository = deductionsRepository;
    }


    public List<EarningsLine> setEarnings(Employee emp, PayrollItem item) {
        List<EarningsLine> earnings =  new ArrayList<>();
        BigDecimal grossPay = normalizeMoney(item.getGrossPay());
        LocalDate earningsDate = resolveEarningsDate(item);
        if (emp.getExemptionStatus().equals(ExemptionStatus.EXEMPT)) {
            EarningsLine earningsLine = new EarningsLine();
            earningsLine.setRate(BigDecimal.ZERO);
            earningsLine.setHours(BigDecimal.ZERO);
            earningsLine.setAmount(grossPay);
            earningsLine.setEarningsDate(earningsDate);
            earningsLine.setPayrollItem(item);
            earningsLine.setOvertime(false);
            earnings.add(earningsLine);
        } else if (emp.getExemptionStatus().equals(ExemptionStatus.NON_EXEMPT)) {
            EarningsLine regularLine = new EarningsLine();
            regularLine.setRate(BigDecimal.ZERO);
            regularLine.setHours(BigDecimal.ZERO);
            regularLine.setAmount(grossPay);
            regularLine.setEarningsDate(earningsDate);
            regularLine.setPayrollItem(item);
            regularLine.setOvertime(false);
            earnings.add(regularLine);
        }
        return earnings;
    }

    public List<EarningsLine> setEarnings(Employee emp, PayrollItem item, List<EarningsLine> precomputedLines) {
        if (precomputedLines == null || precomputedLines.isEmpty()) {
            return List.of();
        }
        LocalDate fallbackDate = resolveEarningsDate(item);
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
        }
        return precomputedLines;
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
        BigDecimal monthlyBasicPay = yearlySalary.divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
        BigDecimal sssDue = computeSssEmployeeSharePerCutoff(monthlyBasicPay);
        BigDecimal philHealthDue = computePhilHealthEmployeeSharePerCutoff(monthlyBasicPay);
        BigDecimal pagibigDue = computePagibigEmployeeSharePerCutoff(monthlyBasicPay);

        BigDecimal nti = payAfterAttendance.subtract(sssDue).subtract(philHealthDue).subtract(pagibigDue);
        if (nti.compareTo(BigDecimal.ZERO) < 0) {
            nti = BigDecimal.ZERO;
        }
        nti = normalizeMoney(nti);

        BigDecimal withholdingDue = computeWithholdingTaxPerCutoff(nti);
        DeductionType withholdingType = resolveTaxBracketType(nti);

        BigDecimal collectiblePool = payAfterAttendance.max(BigDecimal.ZERO);
        BigDecimal sssCollected = collectAmount(collectiblePool, sssDue);
        collectiblePool = collectiblePool.subtract(sssCollected);
        BigDecimal philHealthCollected = collectAmount(collectiblePool, philHealthDue);
        collectiblePool = collectiblePool.subtract(philHealthCollected);
        BigDecimal pagibigCollected = collectAmount(collectiblePool, pagibigDue);
        collectiblePool = collectiblePool.subtract(pagibigCollected);
        BigDecimal withholdingCollected = collectAmount(collectiblePool, withholdingDue);

        addDeductionLine(deductions, item, DeductionType.SSS, sssCollected);
        addDeductionLine(deductions, item, DeductionType.PHILHEALTH, philHealthCollected);
        addDeductionLine(deductions, item, DeductionType.PAGIBIG, pagibigCollected);
        addDeductionLine(deductions, item, withholdingType, withholdingCollected);

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

    private BigDecimal computeWithholdingTaxPerCutoff(BigDecimal taxableCompensationPerCutoff) {
        BigDecimal nti = normalizeMoney(taxableCompensationPerCutoff);
        BigDecimal withholding;
        if (nti.compareTo(BigDecimal.valueOf(10_417)) <= 0) {
            withholding = BigDecimal.ZERO;
        } else if (nti.compareTo(new BigDecimal("16666.99")) <= 0) {
            withholding = nti.subtract(BigDecimal.valueOf(10_417)).multiply(BigDecimal.valueOf(0.15));
        } else if (nti.compareTo(new BigDecimal("33332.99")) <= 0) {
            withholding = BigDecimal.valueOf(937.50).add(
                    nti.subtract(BigDecimal.valueOf(16_667)).multiply(BigDecimal.valueOf(0.20))
            );
        } else if (nti.compareTo(new BigDecimal("83332.99")) <= 0) {
            withholding = BigDecimal.valueOf(4_270.83).add(
                    nti.subtract(BigDecimal.valueOf(33_333)).multiply(BigDecimal.valueOf(0.25))
            );
        } else if (nti.compareTo(new BigDecimal("333332.99")) <= 0) {
            withholding = BigDecimal.valueOf(16_770.83).add(
                    nti.subtract(BigDecimal.valueOf(83_333)).multiply(BigDecimal.valueOf(0.30))
            );
        } else {
            withholding = BigDecimal.valueOf(91_770.83).add(
                    nti.subtract(BigDecimal.valueOf(333_333)).multiply(BigDecimal.valueOf(0.35))
            );
        }
        return normalizeMoney(withholding);
    }

    private DeductionType resolveTaxBracketType(BigDecimal taxableCompensationPerCutoff) {
        BigDecimal nti = normalizeMoney(taxableCompensationPerCutoff);
        if (nti.compareTo(BigDecimal.valueOf(10_417)) <= 0) {
            return DeductionType.BRACKET_LEVEL_ONE;
        } else if (nti.compareTo(new BigDecimal("16666.99")) <= 0) {
            return DeductionType.BRACKET_LEVEL_TWO;
        } else if (nti.compareTo(new BigDecimal("33332.99")) <= 0) {
            return DeductionType.BRACKET_LEVEL_THREE;
        } else if (nti.compareTo(new BigDecimal("83332.99")) <= 0) {
            return DeductionType.BRACKET_LEVEL_FOUR;
        } else if (nti.compareTo(new BigDecimal("333332.99")) <= 0) {
            return DeductionType.BRACKET_LEVEL_FIVE;
        }
        return DeductionType.BRACKET_LEVEL_SIX;
    }

    private BigDecimal computeSssEmployeeSharePerCutoff(BigDecimal monthlyCompensation) {
        BigDecimal msc = resolveSssMsc(monthlyCompensation);
        if (msc.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal monthlyEmployeeShare = msc.multiply(BigDecimal.valueOf(0.05));
        return monthlyEmployeeShare.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal resolveSssMsc(BigDecimal monthlyCompensation) {
        BigDecimal monthly = normalizeMoney(monthlyCompensation);
        if (monthly.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal minMsc = BigDecimal.valueOf(5_000);
        BigDecimal maxMsc = BigDecimal.valueOf(35_000);
        BigDecimal clamped = monthly.max(minMsc).min(maxMsc);
        BigDecimal roundedUpMsc = clamped
                .divide(BigDecimal.valueOf(500), 0, RoundingMode.CEILING)
                .multiply(BigDecimal.valueOf(500));

        return roundedUpMsc.min(maxMsc).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal computePhilHealthEmployeeSharePerCutoff(BigDecimal monthlyCompensation) {
        BigDecimal monthly = normalizeMoney(monthlyCompensation);
        if (monthly.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal adjustedMonthly = monthly
                .max(BigDecimal.valueOf(10_000))
                .min(BigDecimal.valueOf(100_000));
        BigDecimal employeeMonthlyShare = adjustedMonthly.multiply(BigDecimal.valueOf(0.025));
        return employeeMonthlyShare.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal computePagibigEmployeeSharePerCutoff(BigDecimal monthlyCompensation) {
        BigDecimal monthly = normalizeMoney(monthlyCompensation);
        if (monthly.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal mfs = monthly.min(BigDecimal.valueOf(10_000));
        BigDecimal rate = monthly.compareTo(BigDecimal.valueOf(1_500)) <= 0
                ? BigDecimal.valueOf(0.01)
                : BigDecimal.valueOf(0.02);

        BigDecimal monthlyShare = mfs.multiply(rate);
        return monthlyShare.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
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
