package org.example.maridone.auth;

import org.example.maridone.auth.email.EmailService;
import org.example.maridone.auth.token.RefreshTokenRepository;
import org.example.maridone.auth.twofactor.TotpService;
import org.example.maridone.config.JwtConfig;
import org.example.maridone.core.employee.Employee;
import org.example.maridone.core.user.UserAccount;
import org.example.maridone.core.user.UserAccountRepository;
import org.example.maridone.core.user.UserLoginService;
import org.example.maridone.enums.EmploymentStatus;
import org.example.maridone.exception.badrequest.UnauthorizedAccessException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private JwtService jwtService;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private UserLoginService userLoginService;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private UserAccountRepository userAccountRepository;
    @Mock private JwtConfig jwtConfig;
    @Mock private TotpService totpService;
    @Mock private EmailService emailService;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    @Test
    void requestPasswordReset_ShouldSendResetCode_WhenAccountExists() {
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setUsername("richard14");

        Employee employee = new Employee();
        employee.setEmail("richard@maridone.test");
        employee.setEmploymentStatus(EmploymentStatus.REGULAR);

        UserAccount account = new UserAccount();
        account.setUsername("richard14");
        account.setEmployee(employee);

        when(userAccountRepository.findByUsername("richard14")).thenReturn(Optional.of(account));
        when(totpService.generateSecret()).thenReturn("reset-secret");
        when(totpService.generateCurrentCode("reset-secret")).thenReturn("123456");

        authService.requestPasswordReset(request);

        Assertions.assertEquals("reset-secret", account.getPasswordResetSecret());
        Assertions.assertNotNull(account.getPasswordResetExpiresAt());
        verify(userAccountRepository).save(account);
        verify(emailService).sendPasswordResetCode("richard@maridone.test", "123456");
    }

    @Test
    void requestPasswordReset_ShouldNotRevealMissingAccounts() {
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setUsername("missing-user");

        when(userAccountRepository.findByUsername("missing-user")).thenReturn(Optional.empty());

        authService.requestPasswordReset(request);

        verify(userAccountRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(emailService, never()).sendPasswordResetCode(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void resetForgottenPassword_ShouldUpdatePassword_WhenCodeIsValid() {
        ForgotPasswordResetRequest request = new ForgotPasswordResetRequest();
        request.setUsername("richard14");
        request.setCode("123456");
        request.setNewPassword("newStrongPassword");

        UserAccount account = new UserAccount();
        account.setUsername("richard14");
        account.setPasswordHash("old-hash");
        account.setPasswordChangeRequired(true);
        account.setPasswordResetSecret("reset-secret");
        account.setPasswordResetExpiresAt(Instant.now().plus(5, ChronoUnit.MINUTES));

        when(userAccountRepository.findByUsername("richard14")).thenReturn(Optional.of(account));
        when(totpService.verifyPasswordResetCode("reset-secret", "123456")).thenReturn(true);
        when(passwordEncoder.encode("newStrongPassword")).thenReturn("new-hash");

        authService.resetForgottenPassword(request);

        Assertions.assertEquals("new-hash", account.getPasswordHash());
        Assertions.assertFalse(Boolean.TRUE.equals(account.getPasswordChangeRequired()));
        Assertions.assertNull(account.getPasswordResetSecret());
        Assertions.assertNull(account.getPasswordResetExpiresAt());
        verify(userAccountRepository).save(account);
        verify(refreshTokenRepository).deleteByUsername("richard14");
    }

    @Test
    void resetForgottenPassword_ShouldRejectInvalidCodes() {
        ForgotPasswordResetRequest request = new ForgotPasswordResetRequest();
        request.setUsername("richard14");
        request.setCode("111111");
        request.setNewPassword("newStrongPassword");

        UserAccount account = new UserAccount();
        account.setUsername("richard14");
        account.setPasswordResetSecret("reset-secret");
        account.setPasswordResetExpiresAt(Instant.now().plus(5, ChronoUnit.MINUTES));

        when(userAccountRepository.findByUsername("richard14")).thenReturn(Optional.of(account));
        when(totpService.verifyPasswordResetCode("reset-secret", "111111")).thenReturn(false);

        Assertions.assertThrows(UnauthorizedAccessException.class, () -> authService.resetForgottenPassword(request));
        verify(userAccountRepository, never()).save(account);
    }

    @Test
    void resetForgottenPassword_ShouldRejectShortUserPasswords() {
        ForgotPasswordResetRequest request = new ForgotPasswordResetRequest();
        request.setUsername("richard14");
        request.setCode("123456");
        request.setNewPassword("short");

        IllegalArgumentException error = Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> authService.resetForgottenPassword(request)
        );

        Assertions.assertTrue(error.getMessage().contains("at least 8 characters"));
        verify(userAccountRepository, never()).findByUsername("richard14");
    }
}
