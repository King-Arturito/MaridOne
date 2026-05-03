package org.example.maridone.common;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;

public class CommonCalculator {

    public static BigDecimal normalize(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal normalizeNonNegative(BigDecimal value) {
        return normalize(value).max(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
    }

    public static BigDecimal calculateHours(LocalDateTime start, LocalDateTime end) {
        long minutes = end.isBefore(start)
                ? Duration.between(start, end).toMinutes() + (24 * 60) // overnight
                : Duration.between(start, end).toMinutes();
        return BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }

    public static BigDecimal deductUnpaidLunchHour(BigDecimal rawHours) {
        if (rawHours == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        if (rawHours.compareTo(BigDecimal.valueOf(5)) <= 0) {
            return rawHours.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal adjusted = rawHours.subtract(BigDecimal.ONE);
        if (adjusted.compareTo(BigDecimal.ZERO) < 0) {
            adjusted = BigDecimal.ZERO;
        }
        return adjusted.setScale(2, RoundingMode.HALF_UP);
    }
}
