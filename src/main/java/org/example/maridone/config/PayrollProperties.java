package org.example.maridone.config;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.example.maridone.enums.DeductionType;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("payroll")
public class PayrollProperties {

    //todo: verify grace periods
    private Duration gracePeriod = Duration.of(15, ChronoUnit.MINUTES);
    private BigDecimal overtimeMultiplier = BigDecimal.valueOf(1.25);
    private BigDecimal nightDifferentialMultiplier = BigDecimal.valueOf(0.10);
    private BigDecimal restDayWorkMultiplier = BigDecimal.valueOf(1.30);
    private BigDecimal specialHolidayWorkMultiplier = BigDecimal.valueOf(1.30);
    private BigDecimal regularHolidayWorkMultiplier = BigDecimal.valueOf(2.00);
    private BigDecimal vacationLeaveHours = BigDecimal.valueOf(120);
    private BigDecimal sickLeaveHours =  BigDecimal.valueOf(120);
    private BigDecimal sssEmployeeRate = BigDecimal.valueOf(0.05);
    private BigDecimal sssMinimumMsc = BigDecimal.valueOf(5_000);
    private BigDecimal sssMaximumMsc = BigDecimal.valueOf(35_000);
    private BigDecimal sssMscStep = BigDecimal.valueOf(500);
    private BigDecimal philHealthEmployeeRate = BigDecimal.valueOf(0.025);
    private BigDecimal philHealthFloor = BigDecimal.valueOf(10_000);
    private BigDecimal philHealthCeiling = BigDecimal.valueOf(100_000);
    private BigDecimal pagibigLowerSalaryThreshold = BigDecimal.valueOf(1_500);
    private BigDecimal pagibigLowerRate = BigDecimal.valueOf(0.01);
    private BigDecimal pagibigHigherRate = BigDecimal.valueOf(0.02);
    private BigDecimal pagibigMfsCap = BigDecimal.valueOf(10_000);

    /*
        minimum weekly hours
        default: 48 hours, 9 hours per day including lunch break, 6 days a week
    */
    private BigDecimal defaultMinimumWeekly = BigDecimal.valueOf(48);
    private List<WithholdingTaxBracket> withholdingTaxBrackets = defaultWithholdingTaxBrackets();

    public Duration getGracePeriod() {
        return gracePeriod;
    }

    public void setGracePeriod(Duration gracePeriod) {
        this.gracePeriod = gracePeriod;
    }

    public BigDecimal getOvertimeMultiplier() {
        return overtimeMultiplier;
    }

    public void setOvertimeMultiplier(BigDecimal overtimeMultiplier) {
        this.overtimeMultiplier = overtimeMultiplier;
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

    public BigDecimal getSssEmployeeRate() {
        return sssEmployeeRate;
    }

    public void setSssEmployeeRate(BigDecimal sssEmployeeRate) {
        this.sssEmployeeRate = sssEmployeeRate;
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

    public BigDecimal getPagibigMfsCap() {
        return pagibigMfsCap;
    }

    public void setPagibigMfsCap(BigDecimal pagibigMfsCap) {
        this.pagibigMfsCap = pagibigMfsCap;
    }

    public List<WithholdingTaxBracket> getWithholdingTaxBrackets() {
        return withholdingTaxBrackets;
    }

    public void setWithholdingTaxBrackets(List<WithholdingTaxBracket> withholdingTaxBrackets) {
        this.withholdingTaxBrackets = withholdingTaxBrackets;
    }

    public BigDecimal computeWithholdingTaxPerCutoff(BigDecimal taxableCompensationPerCutoff) {
        BigDecimal nti = normalizeMoney(taxableCompensationPerCutoff);
        WithholdingTaxBracket bracket = resolveWithholdingTaxBracket(nti);
        if (bracket == null || bracket.getRate().compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal excess = nti.subtract(bracket.getLowerLimit());
        BigDecimal withholding = bracket.getBaseTax().add(excess.multiply(bracket.getRate()));
        return normalizeMoney(withholding);
    }

    public DeductionType resolveWithholdingDeductionType(BigDecimal taxableCompensationPerCutoff) {
        WithholdingTaxBracket bracket = resolveWithholdingTaxBracket(taxableCompensationPerCutoff);
        return bracket == null ? DeductionType.BRACKET_LEVEL_ONE : bracket.getDeductionType();
    }

    public BigDecimal computeMonthlyBasicPay(BigDecimal yearlyCompensation) {
        BigDecimal yearly = normalizeMoney(yearlyCompensation);
        if (yearly.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        return yearly.divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
    }

    public BigDecimal computeSemiMonthlyBasicPay(BigDecimal yearlyCompensation) {
        BigDecimal yearly = normalizeMoney(yearlyCompensation);
        if (yearly.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        return yearly.divide(BigDecimal.valueOf(24), 2, RoundingMode.HALF_UP);
    }

    public BigDecimal computeDailyRate(BigDecimal yearlyCompensation) {
        BigDecimal monthly = computeMonthlyBasicPay(yearlyCompensation);
        if (monthly.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        return monthly.divide(BigDecimal.valueOf(26), 2, RoundingMode.HALF_UP);
    }

    public BigDecimal computeHourlyRate(BigDecimal yearlyCompensation) {
        BigDecimal daily = computeDailyRate(yearlyCompensation);
        if (daily.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);
        }

        return daily.divide(BigDecimal.valueOf(8), 6, RoundingMode.HALF_UP);
    }

    public BigDecimal computeSssEmployeeSharePerCutoff(BigDecimal monthlyCompensation) {
        BigDecimal msc = resolveSssMsc(monthlyCompensation);
        if (msc.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal monthlyEmployeeShare = msc.multiply(sssEmployeeRate);
        return monthlyEmployeeShare.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
    }

    public BigDecimal computePhilHealthEmployeeSharePerCutoff(BigDecimal monthlyCompensation) {
        BigDecimal monthly = normalizeMoney(monthlyCompensation);
        if (monthly.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal adjustedMonthly = monthly.max(philHealthFloor).min(philHealthCeiling);
        BigDecimal employeeMonthlyShare = adjustedMonthly.multiply(philHealthEmployeeRate);
        return employeeMonthlyShare.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
    }

    public BigDecimal computePagibigEmployeeSharePerCutoff(BigDecimal monthlyCompensation) {
        BigDecimal monthly = normalizeMoney(monthlyCompensation);
        if (monthly.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal mfs = monthly.min(pagibigMfsCap);
        BigDecimal rate = monthly.compareTo(pagibigLowerSalaryThreshold) <= 0
                ? pagibigLowerRate
                : pagibigHigherRate;

        BigDecimal monthlyShare = mfs.multiply(rate);
        return monthlyShare.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
    }

    public BigDecimal computeExemptDailyAbsenceRate(BigDecimal yearlyCompensation) {
        return computeDailyRate(yearlyCompensation);
    }

    public BigDecimal resolveHolidayOrRestDayMultiplier(boolean isRestDay, boolean isHoliday, boolean isRegularHoliday) {
        if (isRegularHoliday) {
            return regularHolidayWorkMultiplier;
        }
        if (isHoliday) {
            return specialHolidayWorkMultiplier;
        }
        if (isRestDay) {
            return restDayWorkMultiplier;
        }
        return BigDecimal.ONE;
    }

    private WithholdingTaxBracket resolveWithholdingTaxBracket(BigDecimal taxableCompensationPerCutoff) {
        BigDecimal nti = normalizeMoney(taxableCompensationPerCutoff);
        for (WithholdingTaxBracket bracket : withholdingTaxBrackets) {
            boolean aboveLower = nti.compareTo(bracket.getLowerLimit()) >= 0;
            boolean withinUpper = bracket.getUpperLimit() == null || nti.compareTo(bracket.getUpperLimit()) <= 0;
            if (aboveLower && withinUpper) {
                return bracket;
            }
        }
        return withholdingTaxBrackets.isEmpty() ? null : withholdingTaxBrackets.get(withholdingTaxBrackets.size() - 1);
    }

    private BigDecimal resolveSssMsc(BigDecimal monthlyCompensation) {
        BigDecimal monthly = normalizeMoney(monthlyCompensation);
        if (monthly.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal clamped = monthly.max(sssMinimumMsc).min(sssMaximumMsc);
        BigDecimal roundedMsc = clamped
                .divide(sssMscStep, 0, RoundingMode.HALF_UP)
                .multiply(sssMscStep);

        return roundedMsc.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal normalizeMoney(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return value.setScale(2, RoundingMode.HALF_UP);
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
