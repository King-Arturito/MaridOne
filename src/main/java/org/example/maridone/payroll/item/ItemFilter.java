package org.example.maridone.payroll.item;

import java.math.BigDecimal;

public record ItemFilter(
        Long empId,
        String firstName,
        String lastName,
        Long itemId
) {
}
