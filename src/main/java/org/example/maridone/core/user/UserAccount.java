package org.example.maridone.core.user;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import org.example.maridone.auth.PasswordPolicy;
import org.example.maridone.document.path.DocumentPath;
import org.example.maridone.core.employee.Employee;
import org.example.maridone.enums.AccountStatus;

import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "user_account")
public class UserAccount {

    @Id
    @Column(name = "username", unique = true, nullable = false)
    @Size(min = 4, max = 30)
    private String username;

    @JsonIgnore
    @Column(name = "password_hash",  nullable = false)
    private String passwordHash;

    @Column(name = "account_status", nullable = false)
    @Enumerated(EnumType.STRING)
    private AccountStatus accountStatus;

    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JoinColumn(name = "emp_id",nullable = false)
    @JsonIgnore
    private Employee employee;

    @Column(name = "two_factor_enabled", nullable = false)
    private Boolean twoFactorEnabled = false;

    @JsonIgnore
    @Column(name = "two_factor_secret", nullable = true)
    private String twoFactorSecret;

    @Column(name = "password_change_required", nullable = false)
    private Boolean passwordChangeRequired = false;

    @JsonIgnore
    @Column(name = "password_reset_secret", nullable = true)
    private String passwordResetSecret;

    @JsonIgnore
    @Column(name = "password_reset_expires_at", nullable = true)
    private Instant passwordResetExpiresAt;

    @Column(name = "last_password_changed_at", nullable = true)
    private Instant lastPasswordChangedAt;

    @Column(name = "emp_id", insertable = false, updatable = false)
    private Long employeeId;

    @JsonIgnore
    @OneToMany(mappedBy = "username", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<DocumentPath> documentPaths;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public AccountStatus getAccountStatus() {
        return accountStatus;
    }

    public void setAccountStatus(AccountStatus accountStatus) {
        this.accountStatus = accountStatus;
    }

    public Employee getEmployee() {
        return employee;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public Boolean getTwoFactorEnabled() {
        return twoFactorEnabled;
    }

    public void setTwoFactorEnabled(Boolean twoFactorEnabled) {
        this.twoFactorEnabled = twoFactorEnabled;
    }

    public String getTwoFactorSecret() {
        return twoFactorSecret;
    }

    public void setTwoFactorSecret(String twoFactorSecret) {
        this.twoFactorSecret = twoFactorSecret;
    }

    public Boolean getPasswordChangeRequired() {
        return passwordChangeRequired;
    }

    public void setPasswordChangeRequired(Boolean passwordChangeRequired) {
        this.passwordChangeRequired = passwordChangeRequired;
    }

    public String getPasswordResetSecret() {
        return passwordResetSecret;
    }

    public void setPasswordResetSecret(String passwordResetSecret) {
        this.passwordResetSecret = passwordResetSecret;
    }

    public Instant getPasswordResetExpiresAt() {
        return passwordResetExpiresAt;
    }

    public void setPasswordResetExpiresAt(Instant passwordResetExpiresAt) {
        this.passwordResetExpiresAt = passwordResetExpiresAt;
    }

    public Instant getLastPasswordChangedAt() {
        return lastPasswordChangedAt;
    }

    public void setLastPasswordChangedAt(Instant lastPasswordChangedAt) {
        this.lastPasswordChangedAt = lastPasswordChangedAt;
    }

    @Transient
    public Instant getPasswordChangeAvailableAt() {
        return PasswordPolicy.getSelfServicePasswordChangeAvailableAt(lastPasswordChangedAt);
    }

    @Transient
    public Boolean getCanChangePasswordNow() {
        return PasswordPolicy.canSelfServicePasswordBeChanged(
                Boolean.TRUE.equals(passwordChangeRequired),
                lastPasswordChangedAt,
                Instant.now()
        );
    }

    public List<DocumentPath> getDocumentPaths() {
        return documentPaths;
    }

    public void setDocumentPaths(List<DocumentPath> documentPaths) {
        this.documentPaths = documentPaths;
    }
}
