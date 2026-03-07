package org.example.maridone.payroll;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.example.maridone.core.employee.Employee;
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
        if (emp.getExemptionStatus().equals(ExemptionStatus.EXEMPT)) {
            EarningsLine earningsLine = new EarningsLine();
            earningsLine.setRate(BigDecimal.ZERO);
            earningsLine.setHours(BigDecimal.ZERO);
            earningsLine.setAmount(item.getGrossPay());
            earningsLine.setEarningsDate(LocalDate.now());
            earnings.add(earningsLine);
        } else if (emp.getExemptionStatus().equals(ExemptionStatus.NON_EXEMPT)) {

        }
        return earnings;
    }

    //todo: Non-exempt earnings logic
    public List<EarningsLine> setEarnings(Employee emp, PayrollItem item, List<EarningsLine> precomputedLines) {
        return precomputedLines == null ? List.of() : precomputedLines;
    }

    //todo: Deductions Logic (Exempt + Non-Exempt) using passed deduction components
    public List<DeductionsLine> setDeductions(
            Employee emp,
            PayrollItem item,
            BigDecimal absentDeductAmount,
            BigDecimal lateDeductAmount
    ) {
        return List.of();
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

    //one hour unpaid lunch is excluded only for longer shifts
    public BigDecimal deductUnpaidLunchHour(BigDecimal rawHours) {
        if (rawHours == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        //for short shifts (e.g., half-day), do not deduct lunch
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

}
