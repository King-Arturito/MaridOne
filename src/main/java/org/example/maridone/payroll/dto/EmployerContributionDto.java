package org.example.maridone.payroll.dto;

import org.example.maridone.enums.EmployerContributionType;

import java.math.BigDecimal;

public class EmployerContributionDto {
    private Long employerContributionId;
    private EmployerContributionType contributionType;
    private String description;
    private BigDecimal amount;

    public Long getEmployerContributionId() {
        return employerContributionId;
    }

    public void setEmployerContributionId(Long employerContributionId) {
        this.employerContributionId = employerContributionId;
    }

    public EmployerContributionType getContributionType() {
        return contributionType;
    }

    public void setContributionType(EmployerContributionType contributionType) {
        this.contributionType = contributionType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
}
