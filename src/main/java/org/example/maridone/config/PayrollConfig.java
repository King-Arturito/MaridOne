package org.example.maridone.config;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.example.maridone.enums.DeductionType;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("payroll")
@Validated
public class PayrollConfig {
    private BigDecimal overtimeMultiplier = BigDecimal.valueOf(1.25);
    private BigDecimal doubleOvertimeMultiplier = BigDecimal.valueOf(2.00);
    private BigDecimal tripleOvertimeMultiplier = BigDecimal.valueOf(3.00);
    private BigDecimal nightDifferentialMultiplier = BigDecimal.valueOf(0.10);
    private BigDecimal restDayWorkMultiplier = BigDecimal.valueOf(0.30);
    private BigDecimal specialHolidayWorkMultiplier = BigDecimal.valueOf(0.30);
    private BigDecimal regularHolidayWorkMultiplier = BigDecimal.valueOf(1.00);
    private BigDecimal vacationLeaveHours = BigDecimal.valueOf(40);
    private BigDecimal sickLeaveHours =  BigDecimal.valueOf(40);
    private BigDecimal taxFreeBonusAnnualCap = BigDecimal.valueOf(90_000);
    private BigDecimal sssEmployeeRate = BigDecimal.valueOf(0.05);
    private BigDecimal sssEmployerRegularRate = BigDecimal.valueOf(0.10);
    private BigDecimal sssEmployerWispRate = BigDecimal.valueOf(0.10);
    private BigDecimal sssWispThreshold = BigDecimal.valueOf(20_000);
    private BigDecimal sssEcThreshold = BigDecimal.valueOf(15_000);
    private BigDecimal sssEcLowerAmount = BigDecimal.valueOf(10);
    private BigDecimal sssEcUpperAmount = BigDecimal.valueOf(30);
    private BigDecimal sssMinimumMsc = BigDecimal.valueOf(5_000);
    private BigDecimal sssMaximumMsc = BigDecimal.valueOf(35_000);
    private BigDecimal sssMscStep = BigDecimal.valueOf(500);
    private BigDecimal philHealthEmployeeRate = BigDecimal.valueOf(0.025);
    private BigDecimal philHealthEmployerRate = BigDecimal.valueOf(0.025);
    private BigDecimal philHealthFloor = BigDecimal.valueOf(10_000);
    private BigDecimal philHealthCeiling = BigDecimal.valueOf(100_000);
    private BigDecimal pagibigLowerSalaryThreshold = BigDecimal.valueOf(1_500);
    private BigDecimal pagibigLowerRate = BigDecimal.valueOf(0.01);
    private BigDecimal pagibigHigherRate = BigDecimal.valueOf(0.02);
    private BigDecimal pagibigEmployerRate = BigDecimal.valueOf(0.02);
    private BigDecimal pagibigMfsCap = BigDecimal.valueOf(10_000);

    /*
       minimum weekly hours
       default: 48 hours, 9 hours per day including lunch break, 6 days a week
   */
    private BigDecimal defaultMinimumWeekly = BigDecimal.valueOf(48);

    private List<WithholdingTaxBracket> withholdingTaxBrackets = defaultWithholdingTaxBrackets();

    public BigDecimal getOvertimeMultiplier() {
        return overtimeMultiplier;
    }

    public void setOvertimeMultiplier(BigDecimal overtimeMultiplier) {
        this.overtimeMultiplier = overtimeMultiplier;
    }

    public BigDecimal getDoubleOvertimeMultiplier() {
        return doubleOvertimeMultiplier;
    }

    public void setDoubleOvertimeMultiplier(BigDecimal doubleOvertimeMultiplier) {
        this.doubleOvertimeMultiplier = doubleOvertimeMultiplier;
    }

    public BigDecimal getTripleOvertimeMultiplier() {
        return tripleOvertimeMultiplier;
    }

    public void setTripleOvertimeMultiplier(BigDecimal tripleOvertimeMultiplier) {
        this.tripleOvertimeMultiplier = tripleOvertimeMultiplier;
    }

    public BigDecimal getNightDifferentialMultiplier() {
        return nightDifferentialMultiplier;
    }

    public void setNightDifferentialMultiplier(BigDecimal nightDifferentialMultiplier) {
        this.nightDifferentialMultiplier = nightDifferentialMultiplier;
    }

    public BigDecimal getRestDayWorkMultiplier() {
        return restDayWorkMultiplier;
    }

    public void setRestDayWorkMultiplier(BigDecimal restDayWorkMultiplier) {
        this.restDayWorkMultiplier = restDayWorkMultiplier;
    }

    public BigDecimal getSpecialHolidayWorkMultiplier() {
        return specialHolidayWorkMultiplier;
    }

    public void setSpecialHolidayWorkMultiplier(BigDecimal specialHolidayWorkMultiplier) {
        this.specialHolidayWorkMultiplier = specialHolidayWorkMultiplier;
    }

    public BigDecimal getRegularHolidayWorkMultiplier() {
        return regularHolidayWorkMultiplier;
    }

    public void setRegularHolidayWorkMultiplier(BigDecimal regularHolidayWorkMultiplier) {
        this.regularHolidayWorkMultiplier = regularHolidayWorkMultiplier;
    }

    public BigDecimal getDefaultMinimumWeekly() {
        return defaultMinimumWeekly;
    }

    public void setDefaultMinimumWeekly(BigDecimal defaultMinimumWeekly) {
        this.defaultMinimumWeekly = defaultMinimumWeekly;
    }

    public BigDecimal getVacationLeaveHours() {
        return vacationLeaveHours;
    }

    public void setVacationLeaveHours(BigDecimal vacationLeaveHours) {
        this.vacationLeaveHours = vacationLeaveHours;
    }

    public BigDecimal getSickLeaveHours() {
        return sickLeaveHours;
    }

    public void setSickLeaveHours(BigDecimal sickLeaveHours) {
        this.sickLeaveHours = sickLeaveHours;
    }

    public BigDecimal getTaxFreeBonusAnnualCap() {
        return taxFreeBonusAnnualCap;
    }

    public void setTaxFreeBonusAnnualCap(BigDecimal taxFreeBonusAnnualCap) {
        this.taxFreeBonusAnnualCap = taxFreeBonusAnnualCap;
    }

    public BigDecimal getSssEmployeeRate() {
        return sssEmployeeRate;
    }

    public void setSssEmployeeRate(BigDecimal sssEmployeeRate) {
        this.sssEmployeeRate = sssEmployeeRate;
    }

    public BigDecimal getSssEmployerRegularRate() {
        return sssEmployerRegularRate;
    }

    public void setSssEmployerRegularRate(BigDecimal sssEmployerRegularRate) {
        this.sssEmployerRegularRate = sssEmployerRegularRate;
    }

    public BigDecimal getSssEmployerWispRate() {
        return sssEmployerWispRate;
    }

    public void setSssEmployerWispRate(BigDecimal sssEmployerWispRate) {
        this.sssEmployerWispRate = sssEmployerWispRate;
    }

    public BigDecimal getSssWispThreshold() {
        return sssWispThreshold;
    }

    public void setSssWispThreshold(BigDecimal sssWispThreshold) {
        this.sssWispThreshold = sssWispThreshold;
    }

    public BigDecimal getSssEcThreshold() {
        return sssEcThreshold;
    }

    public void setSssEcThreshold(BigDecimal sssEcThreshold) {
        this.sssEcThreshold = sssEcThreshold;
    }

    public BigDecimal getSssEcLowerAmount() {
        return sssEcLowerAmount;
    }

    public void setSssEcLowerAmount(BigDecimal sssEcLowerAmount) {
        this.sssEcLowerAmount = sssEcLowerAmount;
    }

    public BigDecimal getSssEcUpperAmount() {
        return sssEcUpperAmount;
    }

    public void setSssEcUpperAmount(BigDecimal sssEcUpperAmount) {
        this.sssEcUpperAmount = sssEcUpperAmount;
    }

    public BigDecimal getSssMinimumMsc() {
        return sssMinimumMsc;
    }

    public void setSssMinimumMsc(BigDecimal sssMinimumMsc) {
        this.sssMinimumMsc = sssMinimumMsc;
    }

    public BigDecimal getSssMaximumMsc() {
        return sssMaximumMsc;
    }

    public void setSssMaximumMsc(BigDecimal sssMaximumMsc) {
        this.sssMaximumMsc = sssMaximumMsc;
    }

    public BigDecimal getSssMscStep() {
        return sssMscStep;
    }

    public void setSssMscStep(BigDecimal sssMscStep) {
        this.sssMscStep = sssMscStep;
    }

    public BigDecimal getPhilHealthEmployeeRate() {
        return philHealthEmployeeRate;
    }

    public void setPhilHealthEmployeeRate(BigDecimal philHealthEmployeeRate) {
        this.philHealthEmployeeRate = philHealthEmployeeRate;
    }

    public BigDecimal getPhilHealthEmployerRate() {
        return philHealthEmployerRate;
    }

    public void setPhilHealthEmployerRate(BigDecimal philHealthEmployerRate) {
        this.philHealthEmployerRate = philHealthEmployerRate;
    }

    public BigDecimal getPhilHealthFloor() {
        return philHealthFloor;
    }

    public void setPhilHealthFloor(BigDecimal philHealthFloor) {
        this.philHealthFloor = philHealthFloor;
    }

    public BigDecimal getPhilHealthCeiling() {
        return philHealthCeiling;
    }

    public void setPhilHealthCeiling(BigDecimal philHealthCeiling) {
        this.philHealthCeiling = philHealthCeiling;
    }

    public BigDecimal getPagibigLowerSalaryThreshold() {
        return pagibigLowerSalaryThreshold;
    }

    public void setPagibigLowerSalaryThreshold(BigDecimal pagibigLowerSalaryThreshold) {
        this.pagibigLowerSalaryThreshold = pagibigLowerSalaryThreshold;
    }

    public BigDecimal getPagibigLowerRate() {
        return pagibigLowerRate;
    }

    public void setPagibigLowerRate(BigDecimal pagibigLowerRate) {
        this.pagibigLowerRate = pagibigLowerRate;
    }

    public BigDecimal getPagibigHigherRate() {
        return pagibigHigherRate;
    }

    public void setPagibigHigherRate(BigDecimal pagibigHigherRate) {
        this.pagibigHigherRate = pagibigHigherRate;
    }

    public BigDecimal getPagibigEmployerRate() {
        return pagibigEmployerRate;
    }

    public void setPagibigEmployerRate(BigDecimal pagibigEmployerRate) {
        this.pagibigEmployerRate = pagibigEmployerRate;
    }

    public BigDecimal getPagibigMfsCap() {
        return pagibigMfsCap;
    }

    public void setPagibigMfsCap(BigDecimal pagibigMfsCap) {
        this.pagibigMfsCap = pagibigMfsCap;
    }

    public void setWithholdingTaxBrackets(List<WithholdingTaxBracket> withholdingTaxBrackets) {
        this.withholdingTaxBrackets = withholdingTaxBrackets;
    }

    public List<WithholdingTaxBracket> getWithholdingTaxBrackets() {
        return withholdingTaxBrackets;
    }

    private List<WithholdingTaxBracket> defaultWithholdingTaxBrackets() {
        List<WithholdingTaxBracket> brackets = new ArrayList<>();
        brackets.add(new WithholdingTaxBracket(BigDecimal.ZERO, new BigDecimal("10417.00"), BigDecimal.ZERO, BigDecimal.ZERO, DeductionType.BRACKET_LEVEL_ONE));
        brackets.add(new WithholdingTaxBracket(new BigDecimal("10417.01"), new BigDecimal("16666.99"), BigDecimal.ZERO, new BigDecimal("0.15"), DeductionType.BRACKET_LEVEL_TWO));
        brackets.add(new WithholdingTaxBracket(new BigDecimal("16667.00"), new BigDecimal("33332.99"), new BigDecimal("937.50"), new BigDecimal("0.20"), DeductionType.BRACKET_LEVEL_THREE));
        brackets.add(new WithholdingTaxBracket(new BigDecimal("33333.00"), new BigDecimal("83332.99"), new BigDecimal("4270.83"), new BigDecimal("0.25"), DeductionType.BRACKET_LEVEL_FOUR));
        brackets.add(new WithholdingTaxBracket(new BigDecimal("83333.00"), new BigDecimal("333332.99"), new BigDecimal("16770.83"), new BigDecimal("0.30"), DeductionType.BRACKET_LEVEL_FIVE));
        brackets.add(new WithholdingTaxBracket(new BigDecimal("333333.00"), null, new BigDecimal("91770.83"), new BigDecimal("0.35"), DeductionType.BRACKET_LEVEL_SIX));
        return brackets;
    }

    public static class WithholdingTaxBracket {
        private BigDecimal lowerLimit;
        private BigDecimal upperLimit;
        private BigDecimal baseTax;
        private BigDecimal rate;
        private DeductionType deductionType;

        public WithholdingTaxBracket() {
        }

        public WithholdingTaxBracket(
                BigDecimal lowerLimit,
                BigDecimal upperLimit,
                BigDecimal baseTax,
                BigDecimal rate,
                DeductionType deductionType
        ) {
            this.lowerLimit = lowerLimit;
            this.upperLimit = upperLimit;
            this.baseTax = baseTax;
            this.rate = rate;
            this.deductionType = deductionType;
        }

        public BigDecimal getLowerLimit() {
            return lowerLimit;
        }

        public void setLowerLimit(BigDecimal lowerLimit) {
            this.lowerLimit = lowerLimit;
        }

        public BigDecimal getUpperLimit() {
            return upperLimit;
        }

        public void setUpperLimit(BigDecimal upperLimit) {
            this.upperLimit = upperLimit;
        }

        public BigDecimal getBaseTax() {
            return baseTax;
        }

        public void setBaseTax(BigDecimal baseTax) {
            this.baseTax = baseTax;
        }

        public BigDecimal getRate() {
            return rate;
        }

        public void setRate(BigDecimal rate) {
            this.rate = rate;
        }

        public DeductionType getDeductionType() {
            return deductionType;
        }

        public void setDeductionType(DeductionType deductionType) {
            this.deductionType = deductionType;
        }
    }
}

