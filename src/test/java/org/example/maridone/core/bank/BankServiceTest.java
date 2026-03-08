package org.example.maridone.core.bank;

import java.util.Optional;

import org.example.maridone.core.dto.BankAccountDto;
import org.example.maridone.core.employee.Employee;
import org.example.maridone.core.employee.EmployeeRepository;
import org.example.maridone.exception.notfound.BankNotFoundException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class BankServiceTest {

    @Mock
    private BankAccountRepository bankAccountRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @InjectMocks
    private BankService bankService;

    @Test
    void addBankAccount_ShouldSaveAndReturnAccount() {
        Long empId = 1L;
        BankAccountDto payload = new BankAccountDto();
        payload.setBankName("BPI");
        payload.setAccountNumber("1234567890");

        Employee employee = new Employee();
        ReflectionTestUtils.setField(employee, "employeeId", empId);

        when(employeeRepository.findById(empId)).thenReturn(Optional.of(employee));
        when(bankAccountRepository.save(any(BankAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BankAccount result = bankService.addBankAccount(empId, payload);

        Assertions.assertNotNull(result);
        Assertions.assertEquals("BPI", result.getBankName());
        Assertions.assertEquals(Boolean.TRUE, result.getActive());
        Assertions.assertEquals(employee, result.getEmployee());
    }

    @Test
    void updateBankAccount_ShouldUpdateDetails_WhenExists() {
        Long bankId = 100L;
        BankAccountDto payload = new BankAccountDto();
        payload.setBankId(bankId);
        payload.setBankName("BDO");
        payload.setAccountNumber("0987654321");

        BankAccount existing = new BankAccount();
        ReflectionTestUtils.setField(existing, "bankId", bankId);
        existing.setActive(true);
        existing.setBankName("OldBank");

        when(bankAccountRepository.findById(bankId)).thenReturn(Optional.of(existing));
        when(bankAccountRepository.save(any(BankAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BankAccount result = bankService.updateBankAccount(payload);

        Assertions.assertEquals("BDO", result.getBankName());
        Assertions.assertEquals("0987654321", result.getAccountNumber());
    }

    @Test
    void updateBankAccount_ShouldThrowException_WhenNotFound() {
        BankAccountDto payload = new BankAccountDto();
        payload.setBankId(999L);
        
        when(bankAccountRepository.findById(999L)).thenReturn(Optional.empty());

        Assertions.assertThrows(BankNotFoundException.class, () -> {
            bankService.updateBankAccount(payload);
        });
    }
}