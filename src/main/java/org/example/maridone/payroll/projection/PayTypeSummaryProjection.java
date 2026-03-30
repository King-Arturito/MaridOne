package org.example.maridone.payroll.projection;

import java.math.BigDecimal;

public interface PayTypeSummaryProjection {
    String getType();
    BigDecimal getTotalAmount();
}