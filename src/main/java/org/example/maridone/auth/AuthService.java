package org.example.maridone.auth;

import org.example.maridone.annotation.ExecutionTime;
import org.example.maridone.auth.dto.*;
import org.example.maridone.auth.email.EmailService;
import org.example.maridone.auth.token.RefreshToken;
import org.example.maridone.auth.token.RefreshTokenRepository;
import org.example.maridone.auth.twofactor.TotpService;
import org.example.maridone.config.JwtConfig;
import org.example.maridone.core.user.UserAccount;
import org.example.maridone.core.user.UserAccountRepository;
import org.example.maridone.core.user.UserLoginService;
import org.example.maridone.enums.EmploymentStatus;
import org.example.maridone.exception.notfound.AccountNotFoundException;
import org.example.maridone.exception.badrequest.InvalidRefreshTokenException;
import org.example.maridone.exception.badrequest.UnauthorizedAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class AuthService {
    private static final long PASSWORD_RESET_WINDOW_MINUTES = 10;

    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final UserLoginService userLoginService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserAccountRepository userAccountRepository;
    private final JwtConfig jwtConfig;
    private final TotpService totpService;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    public AuthService(JwtService jwtService,
                       AuthenticationManager authenticationManager,
                       UserLoginService userLoginService,
                       RefreshTokenRepository refreshTokenRepository,
                       UserAccountRepository userAccountRepository,
                       JwtConfig jwtConfig,
                       TotpService totpService,
                       EmailService emailService,
                       PasswordEncoder passwordEncoder) {
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.userLoginService = userLoginService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.userAccountRepository = userAccountRepository;
        this.jwtConfig = jwtConfig;
        this.totpService = totpService;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
    }

    @ExecutionTime
    @Transactional
    public AuthResponse authenticateUser(LoginRequest loginRequest) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword()));

            UserDetails userDetails = (UserDetails) authentication.getPrincipal();

            UserAccount userAccount = userAccountRepository.findByUsername(authentication.getName())
                    .orElseThrow(() -> new AccountNotFoundException(loginRequest.getUsername()));

            ensureLoginAllowed(userAccount);

            if (userAccount.getTwoFactorEnabled()) {
                String preAuthToken = jwtService.generatePreAuthToken(userDetails);

                return new AuthResponse(preAuthToken, null, true, false);
            }


            return issueStandardTokens(userDetails, userAccount, loginRequest.isRememberMe());

        } catch (AuthenticationException e) {
            throw new UnauthorizedAccessException("Invalid username or password");
        }

    }

    public Void sendEmailVerification(String username) {
        UserAccount account = userAccountRepository.findByUsername(username)
                .orElseThrow(() -> new AccountNotFoundException("UserAccount with: " + username + " not found."));

        String tokenToSend = jwtService.generateEmailVerificationToken(account.getUsername());
        String email = resolvePersonalEmail(account);
        if (!StringUtils.hasText(email)) {
            throw new IllegalArgumentException("Personal email is required before sending a verification link.");
        }
        emailService.sendVerificationEmail(email, tokenToSend);
        return null;
    }

    public ResponseEntity<Object> sendTwoFactorLoginCode(String authHeader) {

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String preAuthToken = authHeader.substring(7);

        if (!jwtService.isValidPreAuthToken(preAuthToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String username = jwtService.extractUsername(preAuthToken);

        UserAccount account = userAccountRepository.findByUsername(username)
                .orElseThrow(() -> new AccountNotFoundException("UserAccount with: " + username + " not found."));

        String tokenToSend = totpService.generateCurrentCode(account.getTwoFactorSecret());
        sendVerificationCodeToEmployeeEmails(
                account,
                tokenToSend,
                "At least one email address is required before sending a login code."
        );
        return null;
    }

    @Transactional
    public void requestPasswordReset(ForgotPasswordRequest request) {
        String username = normalizeRequiredValue(request.getUsername(), "Username is required.");
        UserAccount account = userAccountRepository.findByUsername(username).orElse(null);

        if (account == null || account.getEmployee() == null || account.getEmployee().getEmploymentStatus() == EmploymentStatus.TERMINATED) {
            return;
        }

        List<String> emails = resolveEmployeeEmails(account);
        if (emails.isEmpty()) {
            return;
        }

        String resetSecret = totpService.generateSecret();
        account.setPasswordResetSecret(resetSecret);
        account.setPasswordResetExpiresAt(Instant.now().plus(PASSWORD_RESET_WINDOW_MINUTES, ChronoUnit.MINUTES));
        userAccountRepository.save(account);

        String code = totpService.generateCurrentCode(resetSecret);
        emails.forEach(email -> emailService.sendPasswordResetCode(email, code));
    }
    @Transactional
    public AuthResponse refreshToken(RefreshRequest request) {

        RefreshToken refreshTokenEntity = refreshTokenRepository.findByRefreshToken(request.getRefreshToken())
                .orElseThrow(InvalidRefreshTokenException::new);

        if (refreshTokenEntity.getExpiryDate().compareTo(Instant.now()) < 0) {
            refreshTokenRepository.delete(refreshTokenEntity);
            throw new InvalidRefreshTokenException();
        }

        UserAccount userAccount = refreshTokenEntity.getUserAccount();
        ensureLoginAllowed(userAccount);
        UserDetails userDetails = userLoginService.loadUserByUsername(userAccount.getUsername());

        String newAccessToken = jwtService.generateToken(userDetails);
        return new AuthResponse(
                newAccessToken,
                request.getRefreshToken(),
                false,
                Boolean.TRUE.equals(userAccount.getPasswordChangeRequired())
        );
    }

    @Transactional
    public void logoutUser(LogoutRequest request) {
        refreshTokenRepository.deleteByRefreshToken(request.getRefreshToken());
    }

    @Transactional
    public void resetForgottenPassword(ForgotPasswordResetRequest request) {
        String username = normalizeRequiredValue(request.getUsername(), "Username is required.");
        String code = normalizeRequiredValue(request.getCode(), "Reset code is required.");
        String newPassword = PasswordPolicy.requireUserSelectedPassword(
                request.getNewPassword(),
                "New password is required."
        );

        UserAccount account = userAccountRepository.findByUsername(username)
                .orElseThrow(() -> new UnauthorizedAccessException("Invalid or expired password reset code."));

        if (!isPasswordResetAllowed(account, code)) {
            throw new UnauthorizedAccessException("Invalid or expired password reset code.");
        }

        account.setPasswordHash(passwordEncoder.encode(newPassword));
        account.setPasswordChangeRequired(false);
        account.setLastPasswordChangedAt(Instant.now());
        clearPasswordResetState(account);
        userAccountRepository.save(account);
        refreshTokenRepository.deleteByUsername(account.getUsername());
    }

    private AuthResponse issueStandardTokens(UserDetails userDetails, UserAccount userAccount, boolean rememberMe) {
        String accessToken  = jwtService.generateToken(userDetails);
        String refreshTokenString = jwtService.generateRefreshToken(userDetails, rememberMe);

        RefreshToken refreshTokenEntity = new RefreshToken();
        refreshTokenEntity.setRefreshToken(refreshTokenString);
        refreshTokenEntity.setUserAccount(userAccount);

        long milliseconds = rememberMe ? jwtConfig.getRememberMeExpiration() : jwtConfig.getRefreshTokenExpiration();
        refreshTokenEntity.setExpiryDate(Instant.now().plus(milliseconds, ChronoUnit.MILLIS));

        refreshTokenRepository.save(refreshTokenEntity);

        return new AuthResponse(
                accessToken,
                refreshTokenString,
                false,
                Boolean.TRUE.equals(userAccount.getPasswordChangeRequired())
        );
    }

    @Transactional
    public AuthResponse verify2fa(String preAuthToken, String code) {
        if (!jwtService.isValidPreAuthToken(preAuthToken)) {
            throw new UnauthorizedAccessException("Invalid or expired pre-auth token");
        }

        String username = jwtService.extractUsername(preAuthToken);
        UserAccount userAccount = userAccountRepository.findByUsername(username)
                .orElseThrow(() -> new AccountNotFoundException("UserAccount with: " + username + " not found."));
        ensureLoginAllowed(userAccount);

        // Verify the 6-digit code
        boolean isCodeValid = totpService.verifyCode(userAccount.getTwoFactorSecret(), code);

        if (!isCodeValid) {
            throw new UnauthorizedAccessException("Invalid 2FA code");
        }

        // issue tokens
        UserDetails userDetails = userLoginService.loadUserByUsername(username);
        return issueStandardTokens(userDetails, userAccount, false);
    }

    @Transactional
    public void verifyEmail(String verificationToken) {

        // scope == email_verification
        if (!jwtService.isValidVerificationToken(verificationToken)) {
            throw new UnauthorizedAccessException("Invalid or expired verification link");
        }

        String username = jwtService.extractUsername(verificationToken);

        // update flag
        UserAccount userAccount = userAccountRepository.findByUsername(username)
                .orElseThrow(() -> new AccountNotFoundException("UserAccount with: " + username + " not found."));

        userAccount.getEmployee().setIsVerified(true);
        userAccountRepository.save(userAccount);
    }

    private void ensureLoginAllowed(UserAccount userAccount) {
        EmploymentStatus status = userAccount.getEmployee().getEmploymentStatus();
        if (status == EmploymentStatus.TERMINATED) {
            throw new UnauthorizedAccessException("You may not login. Your account has been terminated.");
        }
        if (status == EmploymentStatus.SUSPENDED) {
            throw new UnauthorizedAccessException("You are currently suspended.");
        }
    }

    private boolean isPasswordResetAllowed(UserAccount account, String code) {
        String secret = account.getPasswordResetSecret();
        Instant expiresAt = account.getPasswordResetExpiresAt();
        if (secret == null || expiresAt == null || expiresAt.isBefore(Instant.now())) {
            return false;
        }

        return totpService.verifyPasswordResetCode(secret, code);
    }

    private void clearPasswordResetState(UserAccount account) {
        account.setPasswordResetSecret(null);
        account.setPasswordResetExpiresAt(null);
    }

    private void sendVerificationCodeToEmployeeEmails(UserAccount account, String tokenToSend, String emptyMessage) {
        List<String> emails = resolveEmployeeEmails(account);
        if (emails.isEmpty()) {
            throw new IllegalArgumentException(emptyMessage);
        }
        emails.forEach(email -> emailService.sendVerificationCode(email, tokenToSend));
    }

    private List<String> resolveEmployeeEmails(UserAccount account) {
        if (account == null || account.getEmployee() == null) {
            return List.of();
        }

        Set<String> emails = new LinkedHashSet<>();
        addEmail(emails, account.getEmployee().getCompanyEmail());
        addEmail(emails, account.getEmployee().getPersonalEmail());
        return List.copyOf(emails);
    }

    private String resolvePersonalEmail(UserAccount account) {
        return account == null || account.getEmployee() == null
                ? null
                : normalizeOptionalValue(account.getEmployee().getPersonalEmail());
    }

    private void addEmail(Set<String> emails, String value) {
        String normalized = normalizeOptionalValue(value);
        if (normalized != null) {
            emails.add(normalized);
        }
    }

    private String normalizeOptionalValue(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String normalizeRequiredValue(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

}
