package org.example.maridone.payroll.item.component;
import jakarta.persistence.*;
import org.example.maridone.enums.DeductionType;
import org.example.maridone.payroll.item.PayrollItem;

import java.math.BigDecimal;


@Entity
@Table(name = "deductions_line")
public class DeductionsLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "deductions_id", nullable = false)
    private Long deductionsId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private PayrollItem payrollItem;

    @Column(name = "deduction_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private DeductionType deductionType;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;


    public Long getDeductionsId() {
        return deductionsId;
    }

    public PayrollItem getPayrollItem() {
        return payrollItem;
    }

    public void setPayrollItem(PayrollItem payrollItem) {
        this.payrollItem = payrollItem;
    }

    public DeductionType getDeductionType() {
        return deductionType;
    }

    public void setDeductionType(DeductionType deductionType) {
        this.deductionType = deductionType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
}
