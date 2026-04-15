package org.example.maridone.core.employee;

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
import org.example.maridone.enums.SeparationReason;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "employee_separation")
public class EmployeeSeparation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "separation_id", nullable = false, unique = true)
    private Long separationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "emp_id", nullable = false)
    private Employee employee;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false)
    private SeparationReason reason;

    @Column(name = "eligible_for_rehire", nullable = false)
    private Boolean eligibleForRehire;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(name = "processed_by", length = 255)
    private String processedBy;

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;

    public Long getSeparationId() {
        return separationId;
    }

    public void setSeparationId(Long separationId) {
        this.separationId = separationId;
    }

    public Employee getEmployee() {
        return employee;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    public SeparationReason getReason() {
        return reason;
    }

    public void setReason(SeparationReason reason) {
        this.reason = reason;
    }

    public Boolean getEligibleForRehire() {
        return eligibleForRehire;
    }

    public void setEligibleForRehire(Boolean eligibleForRehire) {
        this.eligibleForRehire = eligibleForRehire;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }

    public void setEffectiveDate(LocalDate effectiveDate) {
        this.effectiveDate = effectiveDate;
    }

    public String getProcessedBy() {
        return processedBy;
    }

    public void setProcessedBy(String processedBy) {
        this.processedBy = processedBy;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(LocalDateTime processedAt) {
        this.processedAt = processedAt;
    }
}
