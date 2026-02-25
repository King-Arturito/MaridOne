package org.example.maridone.payroll;

import org.example.maridone.core.employee.Employee;
import org.example.maridone.enums.ExemptionStatus;
import org.example.maridone.payroll.item.PayrollItem;
import org.example.maridone.payroll.item.component.*;
import org.example.maridone.schedule.shift.TemplateShiftSchedule;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

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

    //earnings for non exempt
    public List<EarningsLine> setEarnings(Employee emp, PayrollItem item, List<TemplateShiftSchedule> shifts) {
        List<EarningsLine> earnings =  new ArrayList<>();

        return earnings;
    }

    //todo: Deductions Logic for Exempt
    public List<DeductionsLine> setDeductions(Employee emp) {
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
        if (schedules.isEmpty() || schedules == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal hours = BigDecimal.ZERO;
        for (TemplateShiftSchedule schedule : schedules) {
            hours = hours.add(calculateHours(schedule.getStartTime(), schedule.getEndTime()));
        }
        return hours.setScale(2, RoundingMode.HALF_UP);
    }



}
