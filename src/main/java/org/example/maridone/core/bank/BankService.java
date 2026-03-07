package org.example.maridone.core.bank;

import jakarta.validation.Valid;
import org.example.maridone.annotation.ExecutionTime;
import org.example.maridone.common.CommonSpecs;
import org.example.maridone.core.dto.BankAccountDto;
import org.example.maridone.core.employee.Employee;
import org.example.maridone.core.employee.EmployeeRepository;
import org.example.maridone.exception.BankInactiveException;
import org.example.maridone.exception.BankNotFoundException;
import org.example.maridone.exception.EmployeeNotFoundException;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
    public BankAccount updateBankAccount(@Valid BankAccountDto payload) {
        BankAccount bankAccount = bankAccountRepository.findById(payload.getBankId()).orElse(null);
        if (bankAccount == null) {
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

        Specification<BankAccount> specs = Specification.allOf(
                CommonSpecs.fieldEquals("isActive", Boolean.TRUE),
                CommonSpecs.fieldEquals("employeeId", empId)
        );
        return bankAccountRepository.findAll(specs);
    }
}
