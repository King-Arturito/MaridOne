package org.example.maridone.core.user;

import org.example.maridone.annotation.ExecutionTime;
import org.example.maridone.auth.PasswordPolicy;
import org.example.maridone.auth.token.RefreshTokenRepository;
import org.example.maridone.config.DefaultConfig;
import org.example.maridone.core.dto.ChangePasswordDto;
import org.example.maridone.core.dto.CreateUserAccountDto;
import org.example.maridone.core.dto.UpdateUserAccountDto;
import org.example.maridone.core.employee.Employee;
import org.example.maridone.core.employee.EmployeeRepository;
import org.example.maridone.document.dto.DocumentResponseDto;
import org.example.maridone.document.path.DocumentPathRepository;
import org.example.maridone.document.mapper.DocumentMapper;
import org.example.maridone.exception.unauthorized.UnauthorizedAccessException;
import org.example.maridone.exception.notfound.AccountNotFoundException;
import org.example.maridone.exception.notfound.EmployeeNotFoundException;
import org.example.maridone.exception.conflict.DuplicateAccountException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;


@Service
public class UserAccountService {
    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmployeeRepository employeeRepository;
    private final DocumentPathRepository documentPathRepository;
    private final DocumentMapper documentMapper;
    private final RefreshTokenRepository refreshTokenRepository;
    private final DefaultConfig defaultConfig;

    private final ZoneId APP_TIME_ZONE;
    private final DateTimeFormatter PASSWORD_CHANGE_AVAILABLE_FORMAT;


    public UserAccountService
            (UserAccountRepository userAccountRepository,
             PasswordEncoder passwordEncoder,
             EmployeeRepository employeeRepository,
             DocumentPathRepository documentPathRepository,
             DocumentMapper documentMapper,
             RefreshTokenRepository refreshTokenRepository,
             DefaultConfig defaultConfig) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.employeeRepository = employeeRepository;
        this.documentPathRepository = documentPathRepository;
        this.documentMapper = documentMapper;
        this.refreshTokenRepository = refreshTokenRepository;
        this.defaultConfig =  defaultConfig;
        this.APP_TIME_ZONE = defaultConfig.getTimeZone();
        this.PASSWORD_CHANGE_AVAILABLE_FORMAT = DateTimeFormatter.ofPattern("MMMM d, yyyy 'at' h:mm a")
                .withZone(APP_TIME_ZONE);
    }

    @ExecutionTime
    public UserAccount getUserAccount(String username) {
        return userAccountRepository.findByUsername(username)
                .orElseThrow(()-> new AccountNotFoundException("UserAccount with: " + username + " not found."));
    }


    @Transactional
    @ExecutionTime
    public UserAccount createUserAccount(CreateUserAccountDto payload) {
        String username = requireUsername(payload.getUsername());
        String password = requirePassword(payload.getPassword());

        if (userAccountRepository.existsByUsername(username)) {
            throw new DuplicateAccountException("Username already exists");
        }
        if (userAccountRepository.existsByEmployee_EmployeeId(payload.getEmployeeId())) {
            throw new DuplicateAccountException("Duplicate Account for Employee Id: " + payload.getEmployeeId() + " found. Update account instead.");
        }
        Employee emp = employeeRepository.findById(payload.getEmployeeId()).orElseThrow(() -> new EmployeeNotFoundException(payload.getEmployeeId()));

        UserAccount user = new UserAccount();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setPasswordChangeRequired(true);
        user.setPasswordResetSecret(null);
        user.setPasswordResetExpiresAt(null);
        user.setLastPasswordChangedAt(null);
        user.setEmployee(emp);
        emp.setUserAccount(user);
        user.setAccountStatus(payload.getAccountStatus());
        userAccountRepository.save(user);
        return user;
    }

    @Transactional
    @ExecutionTime
    public UserAccount updateUserAccount(UpdateUserAccountDto payload) {
        UserAccount existing = userAccountRepository.findByEmployee_EmployeeId(payload.getEmployeeId())
                .orElseThrow(() -> new AccountNotFoundException("UserAccount with: " + payload.getUsername() + " not found."));
        Long existingId = existing.getAccountId();

        String newUsername = requireUsername(payload.getUsername());
        if (!existing.getUsername().equals(newUsername)) {
            if (userAccountRepository.existsByUsername(newUsername)) {
                throw new DuplicateAccountException("Username already exists");
            }
            existing.setUsername(newUsername);
        }

        boolean passwordUpdated = payload.getPassword() != null && !payload.getPassword().trim().isEmpty();
        if (passwordUpdated) {
            existing.setPasswordHash(passwordEncoder.encode(payload.getPassword().trim()));
            existing.setPasswordChangeRequired(true);
            existing.setLastPasswordChangedAt(null);
            existing.setPasswordResetSecret(null);
            existing.setPasswordResetExpiresAt(null);

            refreshTokenRepository.deleteAllByUserAccount(existingId);
        }

        existing.setAccountStatus(payload.getAccountStatus());

        return userAccountRepository.save(existing);
    }

    @Transactional
    @ExecutionTime
    public void changeOwnPassword(String username, ChangePasswordDto payload) {
        UserAccount existing = userAccountRepository.findByUsername(username)
                .orElseThrow(() -> new AccountNotFoundException("UserAccount with: " + username + " not found."));

        String currentPassword = requirePassword(payload.getCurrentPassword());
        String newPassword = PasswordPolicy.requireUserSelectedPassword(
                payload.getNewPassword(),
                "New password is required."
        );

        if (!passwordEncoder.matches(currentPassword, existing.getPasswordHash())) {
            throw new UnauthorizedAccessException("Current password is incorrect.");
        }

        Instant now = Instant.now();
        if (!PasswordPolicy.canSelfServicePasswordBeChanged(
                Boolean.TRUE.equals(existing.getPasswordChangeRequired()),
                existing.getLastPasswordChangedAt(),
                now
        )) {
            Instant availableAt = PasswordPolicy.getSelfServicePasswordChangeAvailableAt(existing.getLastPasswordChangedAt());
            String formattedAvailableAt = availableAt == null
                    ? "later"
                    : PASSWORD_CHANGE_AVAILABLE_FORMAT.format(availableAt);
            throw new IllegalArgumentException("You can change your password again on " + formattedAvailableAt + ".");
        }

        existing.setPasswordHash(passwordEncoder.encode(newPassword));
        existing.setPasswordChangeRequired(false);
        existing.setPasswordResetSecret(null);
        existing.setPasswordResetExpiresAt(null);
        existing.setLastPasswordChangedAt(now);
        userAccountRepository.save(existing);
    }

    @ExecutionTime
    public List<DocumentResponseDto> getDocuments(String username) {
        return documentPathRepository.findAllByUsername(username);
    }

    private String requireUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username is required.");
        }
        return username.trim();
    }

    private String requirePassword(String password) {
        if (password == null || password.trim().isEmpty()) {
            throw new IllegalArgumentException("Password is required.");
        }
        return password.trim();
    }
}
