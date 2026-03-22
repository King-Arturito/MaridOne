package org.example.maridone.payroll;

import org.example.maridone.config.DefaultConfig;
import org.example.maridone.config.PayrollConfig;
import org.example.maridone.payroll.item.component.DeductionsRepository;
import org.example.maridone.payroll.item.component.EarningsRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;

class PayrollCalculatorHolidayMultiplierTest {

    @Test
    void resolveHolidayAndRestDayRates_ShouldKeepEffectiveMultipliersUnchanged() {
        PayrollConfig payrollConfig = new PayrollConfig();
        PayrollCalculator payrollCalculator = new PayrollCalculator(
                Mockito.mock(EarningsRepository.class),
                Mockito.mock(DeductionsRepository.class),
                new BracketService(payrollConfig),
                Mockito.mock(DefaultConfig.class),
                payrollConfig
        );

        Assertions.assertEquals(0, new BigDecimal("0.00").compareTo(
                payrollCalculator.resolveHolidayOrRestDayPremiumRate(false, false, false)
        ));
        Assertions.assertEquals(0, new BigDecimal("1.00").compareTo(
                payrollCalculator.resolveHolidayOrRestDayMultiplier(false, false, false)
        ));

        Assertions.assertEquals(0, new BigDecimal("0.30").compareTo(
                payrollCalculator.resolveHolidayOrRestDayPremiumRate(true, false, false)
        ));
        Assertions.assertEquals(0, new BigDecimal("1.30").compareTo(
                payrollCalculator.resolveHolidayOrRestDayMultiplier(true, false, false)
        ));

        Assertions.assertEquals(0, new BigDecimal("0.30").compareTo(
                payrollCalculator.resolveHolidayOrRestDayPremiumRate(false, true, false)
        ));
        Assertions.assertEquals(0, new BigDecimal("1.30").compareTo(
                payrollCalculator.resolveHolidayOrRestDayMultiplier(false, true, false)
        ));

        Assertions.assertEquals(0, new BigDecimal("1.00").compareTo(
                payrollCalculator.resolveHolidayOrRestDayPremiumRate(false, true, true)
        ));
        Assertions.assertEquals(0, new BigDecimal("2.00").compareTo(
                payrollCalculator.resolveHolidayOrRestDayMultiplier(false, true, true)
        ));
    }
}
