package org.example.maridone.auth.sensitive;

import org.example.maridone.auth.JwtService;
import org.example.maridone.auth.email.EmailService;
import org.example.maridone.auth.twofactor.TotpService;
import org.example.maridone.core.user.UserAccount;
import org.example.maridone.core.user.UserAccountRepository;
import org.example.maridone.exception.badrequest.UnauthorizedAccessException;
import org.example.maridone.exception.notfound.AccountNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;

@Service
public class SensitiveAccessService {
    public static final String HEADER_NAME = "X-Sensitive-Access-Token";
    private static final long TOKEN_EXPIRATION_MS = 10 * 60 * 1000L;

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final TotpService totpService;
    private final EmailService emailService;
    private final JwtService jwtService;

    public SensitiveAccessService(UserAccountRepository userAccountRepository,
                                  PasswordEncoder passwordEncoder,
                                  TotpService totpService,
                                  EmailService emailService,
                                  JwtService jwtService) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.totpService = totpService;
        this.emailService = emailService;
        this.jwtService = jwtService;
    }

    public SensitiveAccessStateDto getState(String username) {
        UserAccount account = findAccount(username);
        String token = resolveCurrentSensitiveAccessToken();
        boolean active = token != null && jwtService.isValidSensitiveAccessToken(token, username);
        String expiresAt = active ? jwtService.extractExpirationInstant(token).toString() : null;

        return new SensitiveAccessStateDto(
                Boolean.TRUE.equals(account.getTwoFactorEnabled()),
                active,
                expiresAt
        );
    }

    public SensitiveAccessVerifyResponse verify(String username, SensitiveAccessVerifyRequest request) {
        UserAccount account = findAccount(username);
        String mode = normalizeMode(request != null ? request.getMode() : null);

        if ("PASSWORD".equals(mode)) {
            verifyPassword(account, request != null ? request.getPassword() : null);
        } else {
            verifyTwoFactorCode(account, request != null ? request.getCode() : null);
        }

        Instant expiresAt = Instant.now().plusMillis(TOKEN_EXPIRATION_MS);
        String sensitiveAccessToken = jwtService.generateSensitiveAccessToken(username, TOKEN_EXPIRATION_MS, mode);
        return new SensitiveAccessVerifyResponse(sensitiveAccessToken, expiresAt.toString(), mode);
    }

    public void sendEmailCode(String username) {
        UserAccount account = findAccount(username);
        ensureTwoFactorEnabled(account);

        String tokenToSend = totpService.generateCurrentCode(account.getTwoFactorSecret());
        String email = account.getEmployee() != null ? account.getEmployee().getEmail() : null;
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("A verified email is required before sending a sensitive-access code.");
        }

        emailService.sendVerificationCode(email, tokenToSend);
    }

    public void assertHrSensitiveAccess(Authentication authentication) {
        if (!hasAuthority(authentication, "ROLE_HR")) {
            return;
        }
        assertSensitiveAccessToken(authentication.getName());
    }

    public void assertHrSensitiveAccessForEmployee(Authentication authentication, Long employeeId) {
        if (!hasAuthority(authentication, "ROLE_HR")) {
            return;
        }
        Long currentEmployeeId = userAccountRepository.findEmployeeIdByUsername(authentication.getName());
        if (currentEmployeeId != null && currentEmployeeId.equals(employeeId)) {
            return;
        }
        assertSensitiveAccessToken(authentication.getName());
    }

    private void assertSensitiveAccessToken(String username) {
        String token = resolveCurrentSensitiveAccessToken();
        if (token != null && jwtService.isValidSensitiveAccessToken(token, username)) {
            return;
        }
        throw new SensitiveAccessRequiredException();
    }

    private void verifyPassword(UserAccount account, String password) {
        String normalizedPassword = password == null ? "" : password.trim();
        if (normalizedPassword.isEmpty()) {
            throw new IllegalArgumentException("Current password is required.");
        }
        if (!passwordEncoder.matches(normalizedPassword, account.getPasswordHash())) {
            throw new UnauthorizedAccessException("Current password is incorrect.");
        }
    }

    private void verifyTwoFactorCode(UserAccount account, String code) {
        ensureTwoFactorEnabled(account);

        String normalizedCode = code == null ? "" : code.trim();
        if (!normalizedCode.matches("\\d{6}")) {
            throw new IllegalArgumentException("Enter the current 6-digit verification code.");
        }
        if (!totpService.verifyCode(account.getTwoFactorSecret(), normalizedCode)) {
            throw new UnauthorizedAccessException("The sensitive-access verification code is invalid.");
        }
    }

    private void ensureTwoFactorEnabled(UserAccount account) {
        if (!Boolean.TRUE.equals(account.getTwoFactorEnabled()) || account.getTwoFactorSecret() == null || account.getTwoFactorSecret().isBlank()) {
            throw new IllegalArgumentException("Two-factor verification is not enabled for this account.");
        }
    }

    private UserAccount findAccount(String username) {
        return userAccountRepository.findByUsername(username)
                .orElseThrow(() -> new AccountNotFoundException("username of: " + username));
    }

    private boolean hasAuthority(Authentication authentication, String authority) {
        return authentication != null
                && authentication.getAuthorities().stream()
                .anyMatch(grantedAuthority -> authority.equals(grantedAuthority.getAuthority()));
    }

    private String resolveCurrentSensitiveAccessToken() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        return attributes.getRequest().getHeader(HEADER_NAME);
    }

    private String normalizeMode(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase();
        if ("AUTHENTICATOR".equals(normalized) || "EMAIL_OTP".equals(normalized)) {
            return normalized;
        }
        return "PASSWORD";
    }
}
