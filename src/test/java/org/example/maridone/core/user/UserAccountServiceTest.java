package org.example.maridone.core.user;

import java.util.Optional;

import org.example.maridone.core.dto.CreateUserAccountDto;
import org.example.maridone.core.employee.Employee;
import org.example.maridone.core.employee.EmployeeRepository;
import org.example.maridone.core.mapper.CoreMapper;
import org.example.maridone.enums.AccountStatus;
import org.example.maridone.exception.notfound.AccountNotFoundException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserAccountServiceTest {

    @Mock private UserAccountRepository userAccountRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private CoreMapper coreMapper;

    @InjectMocks
    private UserAccountService userAccountService;

    @Test
    void createUserAccount_ShouldHashPasswordAndSave() {
        // Arrange
        CreateUserAccountDto dto = new CreateUserAccountDto();
        dto.setEmployeeId(1L);
        dto.setUsername("niko123");
        dto.setPassword("secretPassword");

        Employee emp = new Employee();
        
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(emp));
        when(passwordEncoder.encode("secretPassword")).thenReturn("hashed_secret");
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(i -> i.getArgument(0));

        UserAccount result = userAccountService.createUserAccount(dto);
    
        Assertions.assertNotNull(result);
        Assertions.assertEquals("niko123", result.getUsername());
        Assertions.assertEquals("hashed_secret", result.getPasswordHash());
        Assertions.assertEquals(AccountStatus.ACTIVE, result.getAccountStatus());
        verify(userAccountRepository).save(any(UserAccount.class));
    }

    @Test
    void getUserAccount_ShouldReturnAccount_WhenFound() {
        String username = "niko123";
        UserAccount account = new UserAccount();
        account.setUsername(username);

        when(userAccountRepository.findByUsername(username)).thenReturn(Optional.of(account));

        UserAccount result = userAccountService.getUserAccount(username);

        Assertions.assertEquals(username, result.getUsername());
    }

    @Test
    void getUserAccount_ShouldThrow_WhenNotFound() {
        String username = "unknown";
        when(userAccountRepository.findByUsername(username)).thenReturn(Optional.empty());

        Assertions.assertThrows(AccountNotFoundException.class, () -> {
            userAccountService.getUserAccount(username);
        });
    }
}
