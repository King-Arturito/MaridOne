package org.example.maridone.payroll.payslip;

import java.time.LocalDate;

public interface PayslipProjection {
    LocalDate getPayPeriodStart();
    LocalDate getPayPeriodEnd();
}
