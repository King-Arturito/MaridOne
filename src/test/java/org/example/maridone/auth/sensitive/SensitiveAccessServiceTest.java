package org.example.maridone.auth.sensitive;

import org.example.maridone.auth.JwtService;
import org.example.maridone.auth.email.EmailService;
import org.example.maridone.auth.twofactor.TotpService;
import org.example.maridone.core.user.UserAccount;
import org.example.maridone.core.user.UserAccountRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SensitiveAccessServiceTest {

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TotpService totpService;

    @Mock
    private EmailService emailService;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private SensitiveAccessService sensitiveAccessService;

    @Test
    void verify_ShouldMatchExactSubmittedPassword() {
        UserAccount account = new UserAccount();
        account.setPasswordHash("hashed-password");

        SensitiveAccessVerifyRequest request = new SensitiveAccessVerifyRequest();
        request.setMode("PASSWORD");
        request.setPassword(" password-with-spaces ");

        when(userAccountRepository.findByUsername("employee.user")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches(" password-with-spaces ", "hashed-password")).thenReturn(true);
        when(jwtService.generateSensitiveAccessToken(eq("employee.user"), anyLong(), eq("PASSWORD")))
                .thenReturn("sensitive-token");

        SensitiveAccessVerifyResponse response = sensitiveAccessService.verify("employee.user", request);

        Assertions.assertEquals("sensitive-token", response.getSensitiveAccessToken());
        Assertions.assertEquals("PASSWORD", response.getVerificationMethod());
        verify(passwordEncoder).matches(" password-with-spaces ", "hashed-password");
    }
}
