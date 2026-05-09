package org.example.maridone.core.dto;


import jakarta.validation.constraints.NotNull;
import org.example.maridone.enums.AccountStatus;

public class UpdateUserAccountDto {
    @NotNull
    private String username;
    @NotNull
    private String password;
    @NotNull
    private AccountStatus accountStatus = AccountStatus.ACTIVE;
    @NotNull
    private Long employeeId;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public AccountStatus getAccountStatus() {
        return accountStatus;
    }

    public void setAccountStatus(AccountStatus accountStatus) {
        this.accountStatus = accountStatus;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(Long employeeId) {
        this.employeeId = employeeId;
    }
}
