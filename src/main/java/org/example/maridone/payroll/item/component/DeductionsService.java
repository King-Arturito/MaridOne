package org.example.maridone.payroll.item.component;

import org.example.maridone.enums.DeductionType;
import org.example.maridone.payroll.dto.DeductionsDto;
import org.example.maridone.payroll.mapper.PayrollMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DeductionsService {

    private final DeductionsRepository deductionsRepository;
    private final PayrollMapper payrollMapper;

    public DeductionsService(
            DeductionsRepository deductionsRepository,
            PayrollMapper payrollMapper
    ) {
        this.deductionsRepository = deductionsRepository;
        this.payrollMapper = payrollMapper;
    }

    @Transactional(readOnly = true)
    public List<DeductionsDto> getDeductions(Long itemId) {
        return toDtos(deductionsRepository.findByPayrollItem_ItemId(itemId));
    }

    @Transactional(readOnly = true)
    public List<DeductionsDto> getOriginalDeductions(Long itemId) {
        return toDtos(deductionsRepository.findByPayrollItem_ItemId(itemId));
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
}
