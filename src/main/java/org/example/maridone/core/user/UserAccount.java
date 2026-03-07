package org.example.maridone.core.user;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import org.example.maridone.document.path.DocumentPath;
import org.example.maridone.core.employee.Employee;
import org.example.maridone.enums.AccountStatus;

import java.util.List;

@Entity
@Table(name = "user_account")
public class UserAccount {

    @Id
    @Column(name = "username", unique = true, nullable = false)
    @Size(min = 4, max = 30)
    private String username;

    @Column(name = "password_hash",  nullable = false)
    private String passwordHash;

    @Column(name = "account_status", nullable = false)
    @Enumerated(EnumType.STRING)
    private AccountStatus accountStatus;

    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JoinColumn(name = "emp_id",nullable = false)
    @JsonIgnore
    private Employee employee;

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

    public List<DocumentPath> getDocumentPaths() {
        return documentPaths;
    }

    public void setDocumentPaths(List<DocumentPath> documentPaths) {
        this.documentPaths = documentPaths;
    }
}
