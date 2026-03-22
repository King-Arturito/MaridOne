package org.example.maridone.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class CommonCalculator {

    public static BigDecimal normalize(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
