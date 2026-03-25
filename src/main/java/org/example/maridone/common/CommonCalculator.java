package org.example.maridone.common;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class CommonCalculator {

    public static BigDecimal normalize(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal calculateHours(LocalDateTime start, LocalDateTime end) {
        long minutes = end.isBefore(start)
                ? Duration.between(start, end).toMinutes() + (24 * 60) // overnight
                : Duration.between(start, end).toMinutes();
        return BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }
}
