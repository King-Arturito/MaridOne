package org.example.maridone.core.user;

import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.example.maridone.auth.token.RefreshTokenRepository;
import org.example.maridone.config.DefaultConfig;
import org.example.maridone.core.dto.ChangePasswordDto;
import org.example.maridone.core.dto.CreateUserAccountDto;
import org.example.maridone.core.dto.UpdateUserAccountDto;
import org.example.maridone.core.employee.Employee;
import org.example.maridone.core.employee.EmployeeRepository;
import org.example.maridone.document.path.DocumentPathRepository;
import org.example.maridone.document.mapper.DocumentMapper;
import org.example.maridone.document.path.DocumentPath;
import org.example.maridone.enums.AccountStatus;
import org.example.maridone.enums.EmploymentStatus;
import org.example.maridone.exception.notfound.AccountNotFoundException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UserAccountServiceTest {

    @Mock private UserAccountRepository userAccountRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private DocumentPathRepository documentPathRepository;
    @Mock private DocumentMapper documentMapper;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private DefaultConfig defaultConfig;

    @InjectMocks
    private UserAccountService userAccountService;

    @BeforeEach
    void setUp() {
        DefaultConfig config = new DefaultConfig();
        config.setTimeZone(ZoneId.of("Asia/Manila"));

        userAccountService = new UserAccountService(
                userAccountRepository,
                passwordEncoder,
                employeeRepository,
                documentPathRepository,
                documentMapper,
                refreshTokenRepository,
                config
        );
    }

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
        Assertions.assertEquals(1L, result.getAccountId());
        Assertions.assertEquals("niko123", result.getUsername());
        Assertions.assertEquals("hashed_secret", result.getPasswordHash());
        Assertions.assertEquals(AccountStatus.ACTIVE, result.getAccountStatus());
        Assertions.assertTrue(Boolean.TRUE.equals(result.getPasswordChangeRequired()));
        verify(userAccountRepository).save(any(UserAccount.class));
    }

    @Test
    void createUserAccount_ShouldForceInactive_WhenEmployeeIsTerminated() {
        CreateUserAccountDto dto = new CreateUserAccountDto();
        dto.setEmployeeId(1L);
        dto.setUsername("niko123");
        dto.setPassword("secretPassword");
        dto.setAccountStatus(AccountStatus.ACTIVE);

        Employee emp = new Employee();
        emp.setEmploymentStatus(EmploymentStatus.TERMINATED);

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(emp));
        when(passwordEncoder.encode("secretPassword")).thenReturn("hashed_secret");
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(i -> i.getArgument(0));

        UserAccount result = userAccountService.createUserAccount(dto);

        Assertions.assertEquals(AccountStatus.INACTIVE, result.getAccountStatus());
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

    @Test
    void updateUserAccount_ShouldUpdateUsername_WhenUsernameChanges() {
        UpdateUserAccountDto dto = new UpdateUserAccountDto();
        dto.setEmployeeId(21L);
        dto.setUsername("chloe.renamed");

        Employee employee = new Employee();
        UserAccount existing = new UserAccount();
        existing.setUsername("chloe.old");
        existing.setAccountStatus(AccountStatus.ACTIVE);
        existing.setEmployee(employee);

        when(userAccountRepository.findByEmployee_EmployeeId(21L)).thenReturn(Optional.of(existing));
        when(userAccountRepository.existsByUsername("chloe.renamed")).thenReturn(false);
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserAccount result = userAccountService.updateUserAccount(dto);

        Assertions.assertEquals("chloe.renamed", result.getUsername());

        verify(userAccountRepository).save(existing);

        verifyNoInteractions(documentPathRepository);
    }

    @Test
    void updateUserAccount_ShouldForceInactive_WhenEmployeeIsTerminated() {
        UpdateUserAccountDto dto = new UpdateUserAccountDto();
        dto.setEmployeeId(21L);
        dto.setUsername("chloe.old");
        dto.setAccountStatus(AccountStatus.ACTIVE);

        Employee employee = new Employee();
        employee.setEmploymentStatus(EmploymentStatus.TERMINATED);
        UserAccount existing = new UserAccount();
        ReflectionTestUtils.setField(existing, "accountId", 99L);
        existing.setUsername("chloe.old");
        existing.setAccountStatus(AccountStatus.ACTIVE);
        existing.setEmployee(employee);

        when(userAccountRepository.findByEmployee_EmployeeId(21L)).thenReturn(Optional.of(existing));
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserAccount result = userAccountService.updateUserAccount(dto);

        Assertions.assertEquals(AccountStatus.INACTIVE, result.getAccountStatus());
        verify(refreshTokenRepository).deleteAllByUserAccount(99L);
        verify(userAccountRepository).save(existing);
    }

    @Test
    void updateUserAccount_ShouldRequirePasswordChange_WhenPasswordIsReset() {
        UpdateUserAccountDto dto = new UpdateUserAccountDto();
        dto.setEmployeeId(21L);
        dto.setUsername("chloe.old");
        dto.setPassword("temp1234");

        Employee employee = new Employee();
        UserAccount existing = new UserAccount();
        ReflectionTestUtils.setField(existing, "accountId", 99L);
        existing.setUsername("chloe.old");
        existing.setPasswordHash("existing_hash");
        existing.setAccountStatus(AccountStatus.ACTIVE);
        existing.setEmployee(employee);
        existing.setPasswordChangeRequired(false);

        when(userAccountRepository.findByEmployee_EmployeeId(21L)).thenReturn(Optional.of(existing));
        when(passwordEncoder.encode("temp1234")).thenReturn("reset_hash");
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(i -> i.getArgument(0));
        UserAccount result = userAccountService.updateUserAccount(dto);

        Assertions.assertEquals("reset_hash", result.getPasswordHash());
        Assertions.assertTrue(Boolean.TRUE.equals(result.getPasswordChangeRequired()));
        verify(refreshTokenRepository).deleteAllByUserAccount(99L);
    }

    @Test
    void changeOwnPassword_ShouldClearPasswordChangeRequired_AfterSuccessfulUpdate() {
        ChangePasswordDto dto = new ChangePasswordDto();
        dto.setCurrentPassword("temp1234");
        dto.setNewPassword("myNewPassword");

        UserAccount existing = new UserAccount();
        existing.setUsername("niko123");
        existing.setPasswordHash("hashed_temp");
        existing.setPasswordChangeRequired(true);

        when(userAccountRepository.findByUsername("niko123")).thenReturn(Optional.of(existing));
        when(passwordEncoder.matches("temp1234", "hashed_temp")).thenReturn(true);
        when(passwordEncoder.encode("myNewPassword")).thenReturn("hashed_new");
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(i -> i.getArgument(0));

        userAccountService.changeOwnPassword("niko123", dto);

        Assertions.assertEquals("hashed_new", existing.getPasswordHash());
        Assertions.assertFalse(Boolean.TRUE.equals(existing.getPasswordChangeRequired()));
        ArgumentCaptor<UserAccount> captor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userAccountRepository).save(captor.capture());
        Assertions.assertEquals("hashed_new", captor.getValue().getPasswordHash());
        Assertions.assertNotNull(captor.getValue().getLastPasswordChangedAt());
    }

    @Test
    void changeOwnPassword_ShouldRejectShortUserPasswords() {
        ChangePasswordDto dto = new ChangePasswordDto();
        dto.setCurrentPassword("temp1234");
        dto.setNewPassword("short");

        UserAccount existing = new UserAccount();
        existing.setUsername("niko123");
        existing.setPasswordHash("hashed_temp");

        when(userAccountRepository.findByUsername("niko123")).thenReturn(Optional.of(existing));

        IllegalArgumentException error = Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> userAccountService.changeOwnPassword("niko123", dto)
        );

        Assertions.assertTrue(error.getMessage().contains("at least 8 characters"));
        verify(userAccountRepository, never()).save(any(UserAccount.class));
    }

    @Test
    void changeOwnPassword_ShouldRejectChangesInsideCooldownWindow() {
        ChangePasswordDto dto = new ChangePasswordDto();
        dto.setCurrentPassword("temp1234");
        dto.setNewPassword("myNewPassword");

        UserAccount existing = new UserAccount();
        existing.setUsername("niko123");
        existing.setPasswordHash("hashed_temp");
        existing.setPasswordChangeRequired(false);
        existing.setLastPasswordChangedAt(Instant.now().minus(10, ChronoUnit.DAYS));

        when(userAccountRepository.findByUsername("niko123")).thenReturn(Optional.of(existing));
        when(passwordEncoder.matches("temp1234", "hashed_temp")).thenReturn(true);

        IllegalArgumentException error = Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> userAccountService.changeOwnPassword("niko123", dto)
        );

        Assertions.assertTrue(error.getMessage().contains("You can change your password again on"));
        verify(userAccountRepository, never()).save(any(UserAccount.class));
    }
}
