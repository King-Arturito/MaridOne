package org.example.maridone.payroll.item.component;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.example.maridone.enums.EmployerContributionType;
import org.example.maridone.payroll.item.PayrollItem;

import java.math.BigDecimal;

@Entity
@Table(name = "employer_contribution_line")
public class EmployerContributionLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "employer_contribution_id", nullable = false)
    private Long employerContributionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private PayrollItem payrollItem;

    @Column(name = "contribution_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private EmployerContributionType contributionType;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    public Long getEmployerContributionId() {
        return employerContributionId;
    }

    public PayrollItem getPayrollItem() {
        return payrollItem;
    }

    public void setPayrollItem(PayrollItem payrollItem) {
        this.payrollItem = payrollItem;
    }

    public EmployerContributionType getContributionType() {
        return contributionType;
    }

    public void setContributionType(EmployerContributionType contributionType) {
        this.contributionType = contributionType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
}
