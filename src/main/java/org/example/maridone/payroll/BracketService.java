package org.example.maridone.payroll;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.example.maridone.common.CommonCalculator;
import org.example.maridone.config.PayrollConfig;
import org.example.maridone.enums.DeductionType;
import org.springframework.stereotype.Service;

@Service
public class BracketService {

    private final PayrollConfig payrollConfig;
    public BracketService(PayrollConfig payrollConfig) {
        this.payrollConfig = payrollConfig;
    }

    public BigDecimal computeWithholdingTaxPerCutoff(BigDecimal taxableCompensationPerCutoff) {
        BigDecimal nti = CommonCalculator.normalize(taxableCompensationPerCutoff);
        PayrollConfig.WithholdingTaxBracket bracket = resolveWithholdingTaxBracket(nti);
        if (bracket == null || bracket.getRate().compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal excess = nti.subtract(bracket.getLowerLimit());
        BigDecimal withholding = bracket.getBaseTax().add(excess.multiply(bracket.getRate()));
        return CommonCalculator.normalize(withholding);
    }

    public DeductionType resolveWithholdingDeductionType(BigDecimal taxableCompensationPerCutoff) {
        PayrollConfig.WithholdingTaxBracket bracket = resolveWithholdingTaxBracket(taxableCompensationPerCutoff);
        return bracket == null ? DeductionType.BRACKET_LEVEL_ONE : bracket.getDeductionType();
    }

    private PayrollConfig.WithholdingTaxBracket resolveWithholdingTaxBracket(BigDecimal taxableCompensationPerCutoff) {
        BigDecimal nti = CommonCalculator.normalize(taxableCompensationPerCutoff);
        for (PayrollConfig.WithholdingTaxBracket bracket : payrollConfig.getWithholdingTaxBrackets()) {
            boolean aboveLower = nti.compareTo(bracket.getLowerLimit()) >= 0;
            boolean withinUpper = bracket.getUpperLimit() == null || nti.compareTo(bracket.getUpperLimit()) <= 0;
            if (aboveLower && withinUpper) {
                return bracket;
            }
        }
        return payrollConfig.getWithholdingTaxBrackets().isEmpty() ? null :
                payrollConfig.getWithholdingTaxBrackets().get(payrollConfig.getWithholdingTaxBrackets().size() - 1);
    }


}
