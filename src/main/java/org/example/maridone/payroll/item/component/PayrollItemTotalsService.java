package org.example.maridone.payroll.item.component;

import org.example.maridone.common.CommonCalculator;
import org.example.maridone.config.PayrollConfig;
import org.example.maridone.core.employee.Employee;
import org.example.maridone.enums.DeductionType;
import org.example.maridone.enums.EarningsType;
import org.example.maridone.enums.RunType;
import org.example.maridone.payroll.BracketService;
import org.example.maridone.payroll.item.PayrollItem;
import org.example.maridone.payroll.run.PayrollItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class PayrollItemTotalsService {
    private static final Set<DeductionType> WITHHOLDING_TYPES = EnumSet.of(
            DeductionType.BRACKET_LEVEL_ONE,
            DeductionType.BRACKET_LEVEL_TWO,
            DeductionType.BRACKET_LEVEL_THREE,
            DeductionType.BRACKET_LEVEL_FOUR,
            DeductionType.BRACKET_LEVEL_FIVE,
            DeductionType.BRACKET_LEVEL_SIX
    );

    private final EarningsRepository earningsRepository;
    private final DeductionsRepository deductionsRepository;
    private final PayrollItemRepository payrollItemRepository;
    private final BracketService bracketService;
    private final PayrollConfig payrollConfig;

    public PayrollItemTotalsService(
            EarningsRepository earningsRepository,
            DeductionsRepository deductionsRepository,
            PayrollItemRepository payrollItemRepository,
            BracketService bracketService,
            PayrollConfig payrollConfig
    ) {
        this.earningsRepository = earningsRepository;
        this.deductionsRepository = deductionsRepository;
        this.payrollItemRepository = payrollItemRepository;
        this.bracketService = bracketService;
        this.payrollConfig = payrollConfig;
    }

    @Transactional
    public void refreshItem(PayrollItem item) {
        if (item == null || item.getItemId() <= 0L) {
            return;
        }

        List<EarningsLine> earnings = earningsRepository.findByPayrollItem_ItemId(item.getItemId());
        List<DeductionsLine> deductions = deductionsRepository.findByPayrollItem_ItemId(item.getItemId());

        if (isAdjustmentRun(item)) {
            deductions = syncAdjustmentBonusWithholding(item, earnings, deductions);
        }

        item.setEarnings(earnings);
        item.setDeductions(deductions);

        BigDecimal totalEarnings = earnings.stream()
                .map(EarningsLine::getAmount)
                .map(CommonCalculator::normalize)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);

        BigDecimal totalDeductions = deductions.stream()
                .map(DeductionsLine::getAmount)
                .map(CommonCalculator::normalize)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);

        item.setGrossPay(CommonCalculator.normalize(totalEarnings));
        item.setNetPay(CommonCalculator.normalizeNonNegative(totalEarnings.subtract(totalDeductions)));
        payrollItemRepository.save(item);
    }

    @Transactional
    public void syncBonusTaxationForEmployeeYear(Employee employee, LocalDate referenceDate) {
        if (employee == null || employee.getEmployeeId() == null) {
            return;
        }

        LocalDate effectiveDate = referenceDate != null ? referenceDate : LocalDate.now();
        LocalDate yearStart = effectiveDate.withDayOfYear(1);
        LocalDate yearEnd = effectiveDate.withMonth(12).withDayOfMonth(31);

        List<EarningsLine> bonusLines =
                earningsRepository.findByPayrollItem_Employee_EmployeeIdAndEarningsTypeAndEarningsDateBetweenOrderByEarningsDateAscEarningsIdAsc(
                        employee.getEmployeeId(),
                        EarningsType.BONUS,
                        yearStart,
                        yearEnd
                );

        if (bonusLines.isEmpty()) {
            return;
        }

        BigDecimal remainingTaxFreeCap = CommonCalculator.normalize(payrollConfig.getTaxFreeBonusAnnualCap())
                .max(BigDecimal.ZERO.setScale(2));
        Set<Long> affectedItemIds = new LinkedHashSet<>();

        for (EarningsLine line : bonusLines) {
            BigDecimal lineAmount = CommonCalculator.normalize(line.getAmount()).max(BigDecimal.ZERO.setScale(2));
            BigDecimal nonTaxableAmount = lineAmount.min(remainingTaxFreeCap);
            BigDecimal taxableAmount = CommonCalculator.normalize(lineAmount.subtract(nonTaxableAmount));

            line.setNonTaxableAmount(CommonCalculator.normalize(nonTaxableAmount));
            line.setTaxableAmount(taxableAmount);

            remainingTaxFreeCap = CommonCalculator.normalize(remainingTaxFreeCap.subtract(nonTaxableAmount))
                    .max(BigDecimal.ZERO.setScale(2));

            if (line.getPayrollItem() != null && line.getPayrollItem().getItemId() > 0L) {
                affectedItemIds.add(line.getPayrollItem().getItemId());
            }
        }

        earningsRepository.saveAll(bonusLines);

        for (Long itemId : affectedItemIds) {
            payrollItemRepository.findById(itemId).ifPresent(this::refreshItem);
        }
    }

    public void applyNonBonusTaxSplit(EarningsLine line, BigDecimal amount) {
        if (line == null) {
            return;
        }

        BigDecimal normalizedAmount = CommonCalculator.normalize(amount).max(BigDecimal.ZERO.setScale(2));
        line.setTaxableAmount(normalizedAmount);
        line.setNonTaxableAmount(BigDecimal.ZERO.setScale(2));
    }

    private List<DeductionsLine> syncAdjustmentBonusWithholding(
            PayrollItem item,
            List<EarningsLine> earnings,
            List<DeductionsLine> deductions
    ) {
        List<DeductionsLine> manualDeductions = new ArrayList<>();
        List<DeductionsLine> withholdingLines = new ArrayList<>();

        for (DeductionsLine line : deductions) {
            if (line != null && isWithholdingLine(line.getDeductionType())) {
                withholdingLines.add(line);
            } else if (line != null) {
                manualDeductions.add(line);
            }
        }

        if (!withholdingLines.isEmpty()) {
            deductionsRepository.deleteAll(withholdingLines);
        }

        BigDecimal taxableBonusTotal = earnings.stream()
                .filter(line -> line.getEarningsType() == EarningsType.BONUS)
                .map(line -> line.getTaxableAmount() != null ? line.getTaxableAmount() : line.getAmount())
                .map(CommonCalculator::normalize)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);

        if (taxableBonusTotal.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal withholdingAmount = bracketService.computeWithholdingTaxPerCutoff(taxableBonusTotal);
            if (withholdingAmount.compareTo(BigDecimal.ZERO) > 0) {
                DeductionsLine withholdingLine = new DeductionsLine();
                withholdingLine.setPayrollItem(item);
                withholdingLine.setDeductionType(bracketService.resolveWithholdingDeductionType(taxableBonusTotal));
                withholdingLine.setAmount(CommonCalculator.normalize(withholdingAmount));
                manualDeductions.add(deductionsRepository.save(withholdingLine));
            }
        }

        return manualDeductions;
    }

    private boolean isAdjustmentRun(PayrollItem item) {
        return item.getPayrollRun() != null && item.getPayrollRun().getRunType() == RunType.ADJUSTMENT;
    }

    private boolean isWithholdingLine(DeductionType deductionType) {
        return deductionType != null && WITHHOLDING_TYPES.contains(deductionType);
    }
}
