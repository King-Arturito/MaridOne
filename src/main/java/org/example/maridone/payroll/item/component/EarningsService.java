package org.example.maridone.payroll.item.component;

import org.example.maridone.common.CommonCalculator;
import org.example.maridone.config.PayrollConfig;
import org.example.maridone.core.employee.Employee;
import org.example.maridone.holiday.HolidayLookup;
import org.example.maridone.holiday.HolidayService;
import org.example.maridone.payroll.PayrollCalculator;
import org.example.maridone.payroll.dto.EarningsDto;
import org.example.maridone.payroll.item.PayrollItem;
import org.example.maridone.payroll.mapper.PayrollMapper;
import org.example.maridone.payroll.run.PayrollRun;
import org.example.maridone.enums.RunType;
import org.example.maridone.schedule.shift.DailyShiftRepository;
import org.example.maridone.schedule.shift.DailyShiftSchedule;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class EarningsService {
    private static final BigDecimal RATE_TOLERANCE = new BigDecimal("0.02");
    private static final String BASIC_PAY = "Basic Pay";
    private static final String APPROVED_OVERTIME = "Approved Overtime";
    private static final String REGULAR_HOLIDAY_PREMIUM = "Regular Holiday Premium";
    private static final String SPECIAL_HOLIDAY_PREMIUM = "Special Holiday Premium";
    private static final String REST_DAY_PREMIUM = "Rest Day Premium";
    private static final String NIGHT_DIFFERENTIAL = "Night Differential";
    private static final String OT_NIGHT_DIFFERENTIAL = "OT Night Differential";
    private static final String PREMIUM_PAY = "Premium Pay";

    private final EarningsRepository earningsRepository;
    private final PayrollMapper payrollMapper;
    private final HolidayService holidayService;
    private final DailyShiftRepository dailyShiftRepository;
    private final PayrollCalculator payrollCalculator;
    private final PayrollConfig payrollConfig;

    public EarningsService(
            EarningsRepository earningsRepository,
            PayrollMapper payrollMapper,
            HolidayService holidayService,
            DailyShiftRepository dailyShiftRepository,
            PayrollCalculator payrollCalculator,
            PayrollConfig payrollConfig
    ) {
        this.earningsRepository = earningsRepository;
        this.payrollMapper = payrollMapper;
        this.holidayService = holidayService;
        this.dailyShiftRepository = dailyShiftRepository;
        this.payrollCalculator = payrollCalculator;
        this.payrollConfig = payrollConfig;
    }

    @Transactional(readOnly = true)
    public List<EarningsDto> getEarnings(Long itemId) {
        List<EarningsLine> earnings = earningsRepository.findByPayrollItem_ItemId(itemId);
        return toDtos(earnings, buildContext(earnings));
    }

    @Transactional(readOnly = true)
    public List<EarningsDto> getOriginalEarnings(Long itemId) {
        List<EarningsLine> earnings = earningsRepository.findByPayrollItem_ItemId(itemId);
        return toDtos(earnings, buildContext(earnings));
    }

    private List<EarningsDto> toDtos(List<EarningsLine> earnings, EarningsContext context) {
        return earnings.stream()
                .map(line -> {
                    EarningsDto dto = payrollMapper.toEarningsDto(line);
                    dto.setDescription(describeLine(line, context));
                    return dto;
                })
                .toList();
    }

    private EarningsContext buildContext(List<EarningsLine> earnings) {
        if (earnings == null || earnings.isEmpty() || earnings.stream().noneMatch(this::needsClassificationContext)) {
            return EarningsContext.empty();
        }

        EarningsLine sample = earnings.get(0);
        PayrollItem item = sample.getPayrollItem();
        if (item == null) {
            return EarningsContext.empty();
        }

        Employee employee = item.getEmployee();
        PayrollRun run = item.getPayrollRun();
        if (employee == null || employee.getEmployeeId() == null || run == null
                || run.getPeriodStart() == null || run.getPeriodEnd() == null) {
            return EarningsContext.empty();
        }

        HolidayLookup holidayLookup = safeHolidayLookup(run);
        List<DailyShiftSchedule> schedules = dailyShiftRepository.findAllByEmployeeIdsAndPeriod(
                List.of(employee.getEmployeeId()),
                run.getPeriodStart().atStartOfDay(),
                run.getPeriodEnd().plusDays(1).atStartOfDay()
        );
        Map<LocalDate, List<DailyShiftSchedule>> schedulesByDate = schedules.stream()
                .collect(Collectors.groupingBy(DailyShiftSchedule::getWorkDate));

        return new EarningsContext(
                payrollCalculator.computeHourlyRate(employee.getYearlySalary()),
                holidayLookup,
                schedulesByDate
        );
    }

    private HolidayLookup safeHolidayLookup(PayrollRun run) {
        try {
            return holidayService.getHolidayLookup(run.getPeriodStart(), run.getPeriodEnd());
        } catch (RuntimeException ex) {
            return HolidayLookup.empty();
        }
    }

    private boolean needsClassificationContext(EarningsLine line) {
        return !isBasicPay(line) && !Boolean.TRUE.equals(line.getOvertime());
    }

    private String describeLine(EarningsLine line, EarningsContext context) {
        if (line.getPayrollItem() != null
                && line.getPayrollItem().getPayrollRun() != null
                && line.getPayrollItem().getPayrollRun().getRunType() == RunType.ADJUSTMENT) {
            return "Manual Adjustment";
        }
        if (isBasicPay(line)) {
            return BASIC_PAY;
        }
        if (Boolean.TRUE.equals(line.getOvertime())) {
            return APPROVED_OVERTIME;
        }

        LocalDate earningDate = line.getEarningsDate();
        if (earningDate == null || context.isEmpty()) {
            return PREMIUM_PAY;
        }

        boolean isRegularHoliday = context.holidayLookup().regularHolidayDates().contains(earningDate);
        boolean isHoliday = context.holidayLookup().holidayDates().contains(earningDate);
        boolean isRestDay = context.schedulesByDate().getOrDefault(earningDate, List.of()).isEmpty();

        BigDecimal premiumRate = payrollCalculator.resolveHolidayOrRestDayPremiumRate(isRestDay, isHoliday, isRegularHoliday);
        BigDecimal workdayMultiplier = payrollCalculator.resolveHolidayOrRestDayMultiplier(isRestDay, isHoliday, isRegularHoliday);
        BigDecimal premiumExpectedRate = context.hourlyRate().multiply(premiumRate);
        BigDecimal nightDiffExpectedRate = context.hourlyRate()
                .multiply(workdayMultiplier)
                .multiply(payrollConfig.getNightDifferentialMultiplier());
        BigDecimal overtimeNightDiffExpectedRate = context.hourlyRate()
                .multiply(workdayMultiplier)
                .multiply(payrollConfig.getOvertimeMultiplier())
                .multiply(payrollConfig.getNightDifferentialMultiplier());

        if (matchesRate(line.getRate(), overtimeNightDiffExpectedRate)) {
            return OT_NIGHT_DIFFERENTIAL;
        }
        if (matchesRate(line.getRate(), nightDiffExpectedRate)) {
            return NIGHT_DIFFERENTIAL;
        }
        if (matchesRate(line.getRate(), premiumExpectedRate)) {
            if (isRegularHoliday) {
                return REGULAR_HOLIDAY_PREMIUM;
            }
            if (isHoliday) {
                return SPECIAL_HOLIDAY_PREMIUM;
            }
            if (isRestDay) {
                return REST_DAY_PREMIUM;
            }
        }

        return PREMIUM_PAY;
    }

    private boolean isBasicPay(EarningsLine line) {
        return isZero(line.getHours()) && isZero(line.getRate());
    }

    private boolean isZero(BigDecimal value) {
        return normalize(value).compareTo(BigDecimal.ZERO.setScale(2)) == 0;
    }

    private boolean matchesRate(BigDecimal actual, BigDecimal expected) {
        return normalize(actual)
                .subtract(normalize(expected))
                .abs()
                .compareTo(RATE_TOLERANCE) <= 0;
    }

    private BigDecimal normalize(BigDecimal value) {
        return CommonCalculator.normalize(value);
    }

    private record EarningsContext(
            BigDecimal hourlyRate,
            HolidayLookup holidayLookup,
            Map<LocalDate, List<DailyShiftSchedule>> schedulesByDate
    ) {
        private static EarningsContext empty() {
            return new EarningsContext(
                    BigDecimal.ZERO.setScale(2),
                    HolidayLookup.empty(),
                    Map.of()
            );
        }

        private boolean isEmpty() {
            return hourlyRate.compareTo(BigDecimal.ZERO) <= 0;
        }
    }
}
