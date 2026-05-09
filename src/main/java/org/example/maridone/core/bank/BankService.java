package org.example.maridone.core.bank;

import jakarta.validation.Valid;
import org.example.maridone.annotation.ExecutionTime;
import org.example.maridone.core.dto.BankAccountDto;
import org.example.maridone.core.employee.Employee;
import org.example.maridone.core.employee.EmployeeRepository;
import org.example.maridone.exception.notfound.BankNotFoundException;
import org.example.maridone.exception.notfound.EmployeeNotFoundException;
import org.example.maridone.exception.unauthorized.BankInactiveException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class BankService {

    private final BankAccountRepository bankAccountRepository;
    private final EmployeeRepository employeeRepository;

    public BankService(BankAccountRepository bankAccountRepository,  EmployeeRepository employeeRepository) {
        this.bankAccountRepository = bankAccountRepository;
        this.employeeRepository = employeeRepository;
    }

    @Transactional
    @ExecutionTime
    public BankAccount addBankAccount(Long empId, @Valid BankAccountDto payload) {
        Employee emp = employeeRepository.findById(empId).orElseThrow(() -> new EmployeeNotFoundException(empId));
        BankAccount bankAccount = new BankAccount();
        bankAccount.setBankName(payload.getBankName());
        bankAccount.setAccountNumber(payload.getAccountNumber());
        bankAccount.setEmployee(emp);
        bankAccount.setActive(Boolean.TRUE);
        bankAccountRepository.save(bankAccount);
        return bankAccount;
    }

    @Transactional
    @ExecutionTime
    public BankAccount updateBankAccount(Long empId, @Valid BankAccountDto payload) {
        employeeRepository.findById(empId).orElseThrow(() -> new EmployeeNotFoundException(empId));
        BankAccount bankAccount = bankAccountRepository.findById(payload.getBankId()).orElse(null);
        if (bankAccount == null) {
            throw new BankNotFoundException("No bank accounts found");
        }
        if (!empId.equals(bankAccount.getEmployeeId())) {
            throw new BankNotFoundException("No bank accounts found");
        }
        if (bankAccount.getActive() == Boolean.FALSE) {
            throw new BankInactiveException("This bank account is inactive");
        }
        bankAccount.setBankName(payload.getBankName());
        bankAccount.setAccountNumber(payload.getAccountNumber());
        bankAccountRepository.save(bankAccount);
        return bankAccount;
    }

    @ExecutionTime
    public List<BankAccount> getBankAccounts(Long empId) {
        employeeRepository.findById(empId).orElseThrow(() -> new EmployeeNotFoundException(empId));
        return bankAccountRepository.findAllByEmployeeIdAndIsActiveTrueOrderByBankIdDesc(empId);
    }

    @ExecutionTime
    public Optional<BankAccount> getPrimaryBankAccount(Long empId) {
        return getBankAccounts(empId).stream().findFirst();
    }

    @Transactional
    @ExecutionTime
    public BankAccount upsertPrimaryBankAccount(Employee employee, String bankName, String accountNumber) {
        if (employee == null || employee.getEmployeeId() == null) {
            throw new IllegalArgumentException("Employee is required when saving bank details.");
        }

        BankAccount bankAccount = getPrimaryBankAccount(employee.getEmployeeId()).orElseGet(() -> {
            BankAccount created = new BankAccount();
            created.setEmployee(employee);
            created.setActive(Boolean.TRUE);
            return created;
        });

        bankAccount.setEmployee(employee);
        bankAccount.setBankName(bankName);
        bankAccount.setAccountNumber(accountNumber);
        return bankAccountRepository.save(bankAccount);
    }
}
