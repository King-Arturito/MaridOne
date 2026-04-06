package org.example.maridone.payroll.item.component;

import org.example.maridone.common.CommonCalculator;
import org.example.maridone.config.PayrollConfig;
import org.example.maridone.core.employee.Employee;
import org.example.maridone.enums.EarningsType;
import org.example.maridone.enums.PayrollStatus;
import org.example.maridone.holiday.HolidayLookup;
import org.example.maridone.holiday.HolidayService;
import org.example.maridone.payroll.PayrollCalculator;
import org.example.maridone.payroll.dto.EarningsDto;
import org.example.maridone.payroll.item.PayrollItem;
import org.example.maridone.payroll.run.PayrollItemRepository;
import org.example.maridone.payroll.mapper.PayrollMapper;
import org.example.maridone.payroll.run.PayrollRun;
import org.example.maridone.exception.unauthorized.InvalidActionException;
import org.example.maridone.enums.RunType;
import org.example.maridone.schedule.shift.DailyShiftRepository;
import org.example.maridone.schedule.shift.DailyShiftSchedule;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class EarningsService {
    private static final BigDecimal RATE_TOLERANCE = new BigDecimal("0.02");
    private static final String BASIC_PAY = "Basic Pay";
    private static final String APPROVED_OVERTIME = "Approved Overtime";
    private static final String APPROVED_DOUBLE_OVERTIME = "Approved Double Overtime";
    private static final String APPROVED_TRIPLE_OVERTIME = "Approved Triple Overtime";
    private static final String REGULAR_HOLIDAY_PREMIUM = "Regular Holiday Premium";
    private static final String SPECIAL_HOLIDAY_PREMIUM = "Special Holiday Premium";
    private static final String REST_DAY_PREMIUM = "Rest Day Premium";
    private static final String NIGHT_DIFFERENTIAL = "Night Differential";
    private static final String OT_NIGHT_DIFFERENTIAL = "OT Night Differential";
    private static final String PREMIUM_PAY = "Premium Pay";

    private final EarningsRepository earningsRepository;
    private final DeductionsRepository deductionsRepository;
    private final PayrollItemRepository payrollItemRepository;
    private final PayrollMapper payrollMapper;
    private final HolidayService holidayService;
    private final DailyShiftRepository dailyShiftRepository;
    private final PayrollCalculator payrollCalculator;
    private final PayrollConfig payrollConfig;

    public EarningsService(
            EarningsRepository earningsRepository,
            DeductionsRepository deductionsRepository,
            PayrollItemRepository payrollItemRepository,
            PayrollMapper payrollMapper,
            HolidayService holidayService,
            DailyShiftRepository dailyShiftRepository,
            PayrollCalculator payrollCalculator,
            PayrollConfig payrollConfig
    ) {
        this.earningsRepository = earningsRepository;
        this.deductionsRepository = deductionsRepository;
        this.payrollItemRepository = payrollItemRepository;
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

    @Transactional
    public EarningsDto updateEarning(EarningsDto payload, Long earningsId) {
        if (payload == null) {
            throw new InvalidActionException("Earnings update payload is required.");
        }

        EarningsLine line = earningsRepository.findById(earningsId)
                .orElseThrow(() -> new InvalidActionException("Earnings line ID:" + earningsId + " not found."));
        PayrollItem item = getEditablePayrollItem(line.getPayrollItem());
        BigDecimal previousAmount = normalize(line.getAmount());
        BigDecimal updatedAmount = normalizeNonNegative(payload.getAmount(), "Earnings amount is required.");

        if (payload.getHours() != null) {
            line.setHours(normalizeNonNegative(payload.getHours(), "Hours cannot be negative."));
        }
        if (payload.getRate() != null) {
            line.setRate(normalizeNonNegative(payload.getRate(), "Rate cannot be negative."));
        }
        if (isRegularRun(item) && updatedAmount.compareTo(previousAmount) != 0) {
            stampRegularEdit(line, previousAmount);
        }

        line.setAmount(updatedAmount);
        EarningsLine updatedLine = earningsRepository.save(line);
        refreshItemTotals(item);
        return toDtos(List.of(updatedLine), buildContext(List.of(updatedLine))).get(0);
    }

    @Transactional
    public EarningsDto createEarning(EarningsDto payload, Long itemId) {
        if (payload == null) {
            throw new InvalidActionException("Earnings create payload is required.");
        }

        PayrollItem item = payrollItemRepository.findById(itemId)
                .orElseThrow(() -> new InvalidActionException("Payroll item ID:" + itemId + " not found."));
        PayrollRun run = requireEditableAdjustmentRun(item);
        BigDecimal amount = normalizePositive(payload.getAmount(), "Earnings amount must be greater than zero.");

        EarningsLine line = new EarningsLine();
        line.setPayrollItem(item);
        line.setHours(BigDecimal.ONE.setScale(2));
        line.setRate(amount);
        line.setAmount(amount);
        line.setEarningsDate(run.getPeriodEnd());
        line.setOvertime(false);
        line.setOvertimeRequest(null);

        EarningsLine createdLine = earningsRepository.save(line);
        refreshItemTotals(item);
        return toDtos(List.of(createdLine), buildContext(List.of(createdLine))).get(0);
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
        ).stream()
                .filter(schedule -> !schedule.isDayOff())
                .toList();
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
            return describeOvertimeLine(line);
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
        if (matchesOvertimeNightDiffRate(line.getRate(), context.hourlyRate(), workdayMultiplier)) {
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

    private String describeOvertimeLine(EarningsLine line) {
        if (line.getOvertimeRequest() == null || line.getOvertimeRequest().getOvertimeType() == null) {
            return APPROVED_OVERTIME;
        }
        return switch (line.getOvertimeRequest().getOvertimeType()) {
            case DOUBLE_OVERTIME -> APPROVED_DOUBLE_OVERTIME;
            case TRIPLE_OVERTIME -> APPROVED_TRIPLE_OVERTIME;
            default -> APPROVED_OVERTIME;
        };
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

    private boolean matchesOvertimeNightDiffRate(
            BigDecimal actualRate,
            BigDecimal hourlyRate,
            BigDecimal workdayMultiplier
    ) {
        for (EarningsType overtimeType : List.of(
                EarningsType.OVERTIME,
                EarningsType.DOUBLE_OVERTIME,
                EarningsType.TRIPLE_OVERTIME
        )) {
            BigDecimal expectedRate = hourlyRate
                    .multiply(workdayMultiplier)
                    .multiply(payrollCalculator.resolveOvertimeMultiplier(overtimeType))
                    .multiply(payrollConfig.getNightDifferentialMultiplier());
            if (matchesRate(actualRate, expectedRate)) {
                return true;
            }
        }
        return false;
    }

    private BigDecimal normalize(BigDecimal value) {
        return CommonCalculator.normalize(value);
    }

    private PayrollItem getEditablePayrollItem(PayrollItem item) {
        if (item == null) {
            throw new InvalidActionException("This earnings line is missing its payroll item.");
        }
        PayrollRun run = item.getPayrollRun();
        if (run == null) {
            throw new InvalidActionException("This earnings line is missing its payroll run.");
        }
        if (run.getPayrollStatus() == PayrollStatus.POSTED) {
            throw new InvalidActionException("This payroll run has already been posted.");
        }
        return item;
    }

    private PayrollRun requireEditableAdjustmentRun(PayrollItem item) {
        PayrollItem editableItem = getEditablePayrollItem(item);
        PayrollRun run = editableItem.getPayrollRun();
        if (run == null || run.getRunType() != RunType.ADJUSTMENT) {
            throw new InvalidActionException("Manual lines can only be added to adjustment payroll items.");
        }
        return run;
    }

    private boolean isRegularRun(PayrollItem item) {
        return item.getPayrollRun() != null && item.getPayrollRun().getRunType() == RunType.REGULAR;
    }

    private void stampRegularEdit(EarningsLine line, BigDecimal previousAmount) {
        if (line.getOriginalAmount() == null) {
            line.setOriginalAmount(previousAmount);
        }
        line.setAdjustedBy(getCurrentUsername());
        line.setAdjustedAt(Instant.now());
    }

    private BigDecimal normalizeNonNegative(BigDecimal value, String errorMessage) {
        if (value == null) {
            throw new InvalidActionException(errorMessage);
        }
        BigDecimal normalized = normalize(value);
        if (normalized.compareTo(BigDecimal.ZERO.setScale(2)) < 0) {
            throw new InvalidActionException(errorMessage);
        }
        return normalized;
    }

    private BigDecimal normalizePositive(BigDecimal value, String errorMessage) {
        if (value == null) {
            throw new InvalidActionException(errorMessage);
        }
        BigDecimal normalized = normalize(value);
        if (normalized.compareTo(BigDecimal.ZERO.setScale(2)) <= 0) {
            throw new InvalidActionException(errorMessage);
        }
        return normalized;
    }

    private void refreshItemTotals(PayrollItem item) {
        List<EarningsLine> earnings = earningsRepository.findByPayrollItem_ItemId(item.getItemId());
        List<DeductionsLine> deductions = deductionsRepository.findByPayrollItem_ItemId(item.getItemId());

        item.setEarnings(earnings);
        item.setDeductions(deductions);

        BigDecimal totalEarnings = earnings.stream()
                .map(EarningsLine::getAmount)
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalDeductions = deductions.stream()
                .map(DeductionsLine::getAmount)
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        item.setGrossPay(CommonCalculator.normalize(totalEarnings));
        item.setNetPay(CommonCalculator.normalize(totalEarnings.subtract(totalDeductions)));
        payrollItemRepository.save(item);
    }

    private String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return "unknownUser";
        }
        return authentication.getName();
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
