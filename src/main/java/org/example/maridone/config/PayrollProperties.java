package org.example.maridone.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

@Component
@ConfigurationProperties("payroll")
public class PayrollProperties {

    //todo: verify grace periods
    private Duration gracePeriod = Duration.of(15, ChronoUnit.MINUTES);
    private BigDecimal overtimeMultiplier = BigDecimal.valueOf(1.25);
    private BigDecimal vacationLeaveHours = BigDecimal.valueOf(120);
    private BigDecimal sickLeaveHours =  BigDecimal.valueOf(120);

    /*
        minimum weekly hours
        default: 48 hours, 8 hours per day, 6 days a week
    */
    private BigDecimal defaultMinimumWeekly = BigDecimal.valueOf(48);

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
}
