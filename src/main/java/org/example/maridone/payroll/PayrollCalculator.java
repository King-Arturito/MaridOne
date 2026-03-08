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
        if (yearlySalary.compareTo(BigDecimal.ZERO) <= 0) {
            return deductions;
        }

        BigDecimal semiMonthlySalary = yearlySalary.divide(BigDecimal.valueOf(24), 2, RoundingMode.HALF_UP);
        addDeductionLine(deductions, item, DeductionType.SSS, semiMonthlySalary.multiply(BigDecimal.valueOf(0.05)));
        addDeductionLine(
                deductions,
                item,
                DeductionType.PHILHEALTH,
                semiMonthlySalary.multiply(BigDecimal.valueOf(0.025))
        );
        addDeductionLine(deductions, item, DeductionType.PAGIBIG, BigDecimal.valueOf(100));

        BigDecimal withholdingPerCutoff = computeWithholdingTaxPerCutoff(yearlySalary);
        if (withholdingPerCutoff.compareTo(BigDecimal.ZERO) > 0) {
            addDeductionLine(
                    deductions,
                    item,
                    resolveTaxBracketType(yearlySalary),
                    withholdingPerCutoff
            );
        }

        addDeductionLine(deductions, item, DeductionType.ABSENT_DEDUCTION, absentDeductAmount);
        addDeductionLine(deductions, item, DeductionType.LATE_PENALTY, lateDeductAmount);

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

    private BigDecimal computeWithholdingTaxPerCutoff(BigDecimal yearlySalary) {
        BigDecimal annualTax;
        if (yearlySalary.compareTo(BigDecimal.valueOf(250_000)) <= 0) {
            annualTax = BigDecimal.ZERO;
        } else if (yearlySalary.compareTo(BigDecimal.valueOf(400_000)) <= 0) {
            annualTax = yearlySalary.subtract(BigDecimal.valueOf(250_000)).multiply(BigDecimal.valueOf(0.15));
        } else if (yearlySalary.compareTo(BigDecimal.valueOf(800_000)) <= 0) {
            annualTax = BigDecimal.valueOf(22_500).add(
                    yearlySalary.subtract(BigDecimal.valueOf(400_000)).multiply(BigDecimal.valueOf(0.20))
            );
        } else if (yearlySalary.compareTo(BigDecimal.valueOf(2_000_000)) <= 0) {
            annualTax = BigDecimal.valueOf(102_500).add(
                    yearlySalary.subtract(BigDecimal.valueOf(800_000)).multiply(BigDecimal.valueOf(0.25))
            );
        } else if (yearlySalary.compareTo(BigDecimal.valueOf(8_000_000)) <= 0) {
            annualTax = BigDecimal.valueOf(402_500).add(
                    yearlySalary.subtract(BigDecimal.valueOf(2_000_000)).multiply(BigDecimal.valueOf(0.30))
            );
        } else {
            annualTax = BigDecimal.valueOf(2_202_500).add(
                    yearlySalary.subtract(BigDecimal.valueOf(8_000_000)).multiply(BigDecimal.valueOf(0.35))
            );
        }
        return annualTax.divide(BigDecimal.valueOf(24), 2, RoundingMode.HALF_UP);
    }

    private DeductionType resolveTaxBracketType(BigDecimal yearlySalary) {
        if (yearlySalary.compareTo(BigDecimal.valueOf(250_000)) <= 0) {
            return DeductionType.BRACKET_LEVEL_ONE;
        } else if (yearlySalary.compareTo(BigDecimal.valueOf(400_000)) <= 0) {
            return DeductionType.BRACKET_LEVEL_TWO;
        } else if (yearlySalary.compareTo(BigDecimal.valueOf(800_000)) <= 0) {
            return DeductionType.BRACKET_LEVEL_THREE;
        } else if (yearlySalary.compareTo(BigDecimal.valueOf(2_000_000)) <= 0) {
            return DeductionType.BRACKET_LEVEL_FOUR;
        } else if (yearlySalary.compareTo(BigDecimal.valueOf(8_000_000)) <= 0) {
            return DeductionType.BRACKET_LEVEL_FIVE;
        }
        return DeductionType.BRACKET_LEVEL_SIX;
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
