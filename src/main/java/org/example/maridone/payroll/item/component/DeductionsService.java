package org.example.maridone.payroll.item.component;

import org.example.maridone.common.CommonCalculator;
import org.example.maridone.enums.DeductionType;
import org.example.maridone.enums.PayrollStatus;
import org.example.maridone.enums.RunType;
import org.example.maridone.exception.unauthorized.InvalidActionException;
import org.example.maridone.payroll.dto.DeductionsDto;
import org.example.maridone.payroll.item.PayrollItem;
import org.example.maridone.payroll.mapper.PayrollMapper;
import org.example.maridone.payroll.run.PayrollItemRepository;
import org.example.maridone.payroll.run.PayrollRun;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
public class DeductionsService {

    private final DeductionsRepository deductionsRepository;
    private final PayrollItemRepository payrollItemRepository;
    private final PayrollMapper payrollMapper;
    private final PayrollItemTotalsService payrollItemTotalsService;

    public DeductionsService(
            DeductionsRepository deductionsRepository,
            PayrollItemRepository payrollItemRepository,
            PayrollMapper payrollMapper,
            PayrollItemTotalsService payrollItemTotalsService
    ) {
        this.deductionsRepository = deductionsRepository;
        this.payrollItemRepository = payrollItemRepository;
        this.payrollMapper = payrollMapper;
        this.payrollItemTotalsService = payrollItemTotalsService;
    }

    @Transactional(readOnly = true)
    public List<DeductionsDto> getDeductions(Long itemId) {
        return toDtos(deductionsRepository.findByPayrollItem_ItemId(itemId));
    }

    @Transactional(readOnly = true)
    public List<DeductionsDto> getOriginalDeductions(Long itemId) {
        return toDtos(deductionsRepository.findByPayrollItem_ItemId(itemId));
    }

    @Transactional
    public DeductionsDto updateDeduction(DeductionsDto payload, Long deductionsId) {
        if (payload == null) {
            throw new InvalidActionException("Deduction update payload is required.");
        }

        DeductionsLine line = deductionsRepository.findById(deductionsId)
                .orElseThrow(() -> new InvalidActionException("Deduction line ID:" + deductionsId + " not found."));
        PayrollItem item = getEditablePayrollItem(line.getPayrollItem());
        BigDecimal previousAmount = CommonCalculator.normalize(line.getAmount());
        BigDecimal updatedAmount = normalizeNonNegative(payload.getAmount(), "Deduction amount is required.");

        if (payload.getDeductionType() != null) {
            line.setDeductionType(payload.getDeductionType());
        }
        if (isRegularRun(item) && updatedAmount.compareTo(previousAmount) != 0) {
            stampRegularEdit(line, previousAmount);
        }

        line.setAmount(updatedAmount);
        DeductionsLine updatedLine = deductionsRepository.save(line);
        payrollItemTotalsService.refreshItem(item);
        return toDtos(List.of(updatedLine)).get(0);
    }

    @Transactional
    public DeductionsDto createDeduction(DeductionsDto payload, Long itemId) {
        if (payload == null) {
            throw new InvalidActionException("Deduction create payload is required.");
        }

        PayrollItem item = payrollItemRepository.findById(itemId)
                .orElseThrow(() -> new InvalidActionException("Payroll item ID:" + itemId + " not found."));
        requireEditableAdjustmentRun(item);
        BigDecimal amount = normalizePositive(payload.getAmount(), "Deduction amount must be greater than zero.");

        DeductionsLine line = new DeductionsLine();
        line.setPayrollItem(item);
        line.setDeductionType(DeductionType.MANUAL_ADJUSTMENT);
        line.setAmount(amount);

        DeductionsLine createdLine = deductionsRepository.save(line);
        payrollItemTotalsService.refreshItem(item);
        return toDtos(List.of(createdLine)).get(0);
    }

    private List<DeductionsDto> toDtos(List<DeductionsLine> deductions) {
        return deductions.stream()
                .map(line -> {
                    DeductionsDto dto = payrollMapper.toDeductionsDto(line);
                    dto.setDescription(describeLine(line));
                    return dto;
                })
                .toList();
    }

    private String describeLine(DeductionsLine line) {
        if (line.getDeductionType() == null) {
            return "Deduction";
        }

        return switch (line.getDeductionType()) {
            case PAGIBIG -> "Pag-IBIG Contribution";
            case SSS -> "SSS Contribution";
            case PHILHEALTH -> "PhilHealth Contribution";
            case BRACKET_LEVEL_ONE,
                 BRACKET_LEVEL_TWO,
                 BRACKET_LEVEL_THREE,
                 BRACKET_LEVEL_FOUR,
                 BRACKET_LEVEL_FIVE,
                 BRACKET_LEVEL_SIX -> "Withholding Tax";
            case SSS_LOAN -> "SSS Loan";
            case PAGIBIG_LOAN -> "Pag-IBIG Loan";
            case CASH_ADVANCE -> "Cash Advance";
            case MANUAL_ADJUSTMENT -> "Manual Adjustment";
            case LATE_PENALTY -> "Late Penalty";
            case ABSENT_DEDUCTION -> "Absent Deduction";
            case EQUIPMENT_LOSS_PENALTY -> "Equipment Loss Penalty";
            case HMO_UPGRADE -> "HMO Upgrade";
        };
    }

    private PayrollItem getEditablePayrollItem(PayrollItem item) {
        if (item == null) {
            throw new InvalidActionException("This deduction line is missing its payroll item.");
        }
        PayrollRun run = item.getPayrollRun();
        if (run == null) {
            throw new InvalidActionException("This deduction line is missing its payroll run.");
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

    private void stampRegularEdit(DeductionsLine line, BigDecimal previousAmount) {
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
        BigDecimal normalized = CommonCalculator.normalize(value);
        if (normalized.compareTo(BigDecimal.ZERO.setScale(2)) < 0) {
            throw new InvalidActionException(errorMessage);
        }
        return normalized;
    }

    private BigDecimal normalizePositive(BigDecimal value, String errorMessage) {
        if (value == null) {
            throw new InvalidActionException(errorMessage);
        }
        BigDecimal normalized = CommonCalculator.normalize(value);
        if (normalized.compareTo(BigDecimal.ZERO.setScale(2)) <= 0) {
            throw new InvalidActionException(errorMessage);
        }
        return normalized;
    }

    private String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return "unknownUser";
        }
        return authentication.getName();
    }
}
