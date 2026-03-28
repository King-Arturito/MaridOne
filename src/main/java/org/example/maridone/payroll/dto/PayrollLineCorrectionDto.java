package org.example.maridone.payroll.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.example.maridone.enums.AdjustmentKind;

import java.math.BigDecimal;

public class PayrollLineCorrectionDto {
    @NotNull
    private Long sourceItemId;

    @NotNull
    private AdjustmentKind sourceLineType;

    @NotNull
    private Long sourceLineId;

    @NotNull
    @DecimalMin(value = "0.00")
    private BigDecimal correctedAmount;

    private Long disputeId;

    public Long getSourceItemId() {
        return sourceItemId;
    }

    public void setSourceItemId(Long sourceItemId) {
        this.sourceItemId = sourceItemId;
    }

    public AdjustmentKind getSourceLineType() {
        return sourceLineType;
    }

    public void setSourceLineType(AdjustmentKind sourceLineType) {
        this.sourceLineType = sourceLineType;
    }

    public Long getSourceLineId() {
        return sourceLineId;
    }

    public void setSourceLineId(Long sourceLineId) {
        this.sourceLineId = sourceLineId;
    }

    public BigDecimal getCorrectedAmount() {
        return correctedAmount;
    }

    public void setCorrectedAmount(BigDecimal correctedAmount) {
        this.correctedAmount = correctedAmount;
    }

    public Long getDisputeId() {
        return disputeId;
    }

    public void setDisputeId(Long disputeId) {
        this.disputeId = disputeId;
    }
}
