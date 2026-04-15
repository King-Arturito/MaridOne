package org.example.maridone.core.user;

import org.example.maridone.annotation.ExecutionTime;
import org.example.maridone.auth.PasswordPolicy;
import org.example.maridone.auth.token.RefreshTokenRepository;
import org.example.maridone.config.DefaultConfig;
import org.example.maridone.core.dto.ChangePasswordDto;
import org.example.maridone.core.dto.CreateUserAccountDto;
import org.example.maridone.core.employee.Employee;
import org.example.maridone.core.employee.EmployeeRepository;
import org.example.maridone.document.DocumentPathRepository;
import org.example.maridone.document.dto.DocumentResponseDto;
import org.example.maridone.document.mapper.DocumentMapper;
import org.example.maridone.document.spec.DocumentSpecs;
import org.example.maridone.exception.badrequest.UnauthorizedAccessException;
import org.example.maridone.exception.notfound.AccountNotFoundException;
import org.example.maridone.exception.notfound.EmployeeNotFoundException;
import org.example.maridone.exception.unauthorized.DuplicateAccountException;
import org.springframework.data.domain.Sort;
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
                .orElseThrow(()-> new AccountNotFoundException(username));
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
    public UserAccount updateUserAccount(CreateUserAccountDto payload) {
        UserAccount existing = userAccountRepository.findByEmployee_EmployeeId(payload.getEmployeeId()).orElse(null);
        if (existing == null) {
            throw new AccountNotFoundException("employeeId=" + payload.getEmployeeId());
        }

        String username = requireUsername(payload.getUsername());
        if (!existing.getUsername().equals(username) && userAccountRepository.existsByUsername(username)) {
            throw new DuplicateAccountException("Username already exists");
        }

        String oldUsername = existing.getUsername();
        String updatedPasswordHash = existing.getPasswordHash();
        boolean passwordChangeRequired = Boolean.TRUE.equals(existing.getPasswordChangeRequired());
        boolean passwordUpdated = payload.getPassword() != null && !payload.getPassword().trim().isEmpty();
        if (passwordUpdated) {
            updatedPasswordHash = passwordEncoder.encode(payload.getPassword().trim());
            passwordChangeRequired = true;
        }
        Instant lastPasswordChangedAt = existing.getLastPasswordChangedAt();
        if (passwordUpdated) {
            lastPasswordChangedAt = null;
        }

        if (!oldUsername.equals(username)) {
            userAccountRepository.insertReplacementAccount(
                    username,
                    updatedPasswordHash,
                    existing.getAccountStatus().name(),
                    payload.getEmployeeId(),
                    Boolean.TRUE.equals(existing.getTwoFactorEnabled()),
                    existing.getTwoFactorSecret(),
                    passwordChangeRequired,
                    lastPasswordChangedAt
            );
            documentPathRepository.reassignUsername(oldUsername, username);
            refreshTokenRepository.deleteByUsername(oldUsername);
            userAccountRepository.deleteByUsernameNative(oldUsername);

            return userAccountRepository.findByUsername(username)
                    .orElseThrow(() -> new AccountNotFoundException(username));
        }

        existing.setUsername(username);
        if (existing.getEmployee() != null) {
            existing.getEmployee().setUserAccount(existing);
        }

        existing.setPasswordHash(updatedPasswordHash);
        existing.setPasswordChangeRequired(passwordChangeRequired);
        existing.setLastPasswordChangedAt(lastPasswordChangedAt);
        if (passwordUpdated) {
            existing.setPasswordResetSecret(null);
            existing.setPasswordResetExpiresAt(null);
            refreshTokenRepository.deleteByUsername(existing.getUsername());
        }

        userAccountRepository.save(existing);
        return existing;
    }

    @Transactional
    @ExecutionTime
    public void changeOwnPassword(String username, ChangePasswordDto payload) {
        UserAccount existing = userAccountRepository.findByUsername(username)
                .orElseThrow(() -> new AccountNotFoundException(username));

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
    public List<DocumentResponseDto> getDocuments(String username, String subDir) {
        getUserAccount(username);
        return documentPathRepository.findAll(
                        DocumentSpecs.hasUsername(username),
                        Sort.by(Sort.Direction.DESC, "pathId")
                ).stream()
                .filter(document -> matchesSubDirectory(document.getFilePath(), subDir))
                .map(documentMapper::toDocumentResponseDto)
                .toList();
    }

    private boolean matchesSubDirectory(String filePath, String subDir) {
        if (subDir == null || subDir.trim().isEmpty()) {
            return true;
        }
        if (filePath == null || filePath.isBlank()) {
            return false;
        }

        String normalizedPath = filePath.replace('\\', '/').toLowerCase();
        String normalizedSubDir = "/" + subDir.trim().toLowerCase() + "/";
        return normalizedPath.contains(normalizedSubDir);
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
