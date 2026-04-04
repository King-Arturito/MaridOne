package org.example.maridone.core.user;

import org.example.maridone.annotation.ExecutionTime;
import org.example.maridone.auth.token.RefreshTokenRepository;
import org.example.maridone.core.dto.CreateUserAccountDto;
import org.example.maridone.core.employee.Employee;
import org.example.maridone.core.employee.EmployeeRepository;
import org.example.maridone.document.DocumentPathRepository;
import org.example.maridone.document.dto.DocumentResponseDto;
import org.example.maridone.document.mapper.DocumentMapper;
import org.example.maridone.document.spec.DocumentSpecs;
import org.example.maridone.exception.notfound.AccountNotFoundException;
import org.example.maridone.exception.unauthorized.DuplicateAccountException;
import org.example.maridone.exception.notfound.EmployeeNotFoundException;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


@Service
public class UserAccountService {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmployeeRepository employeeRepository;
    private final DocumentPathRepository documentPathRepository;
    private final DocumentMapper documentMapper;
    private final RefreshTokenRepository refreshTokenRepository;

    public UserAccountService
            (UserAccountRepository userAccountRepository,
             PasswordEncoder passwordEncoder,
             EmployeeRepository employeeRepository,
             DocumentPathRepository documentPathRepository,
             DocumentMapper documentMapper,
             RefreshTokenRepository refreshTokenRepository) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.employeeRepository = employeeRepository;
        this.documentPathRepository = documentPathRepository;
        this.documentMapper = documentMapper;
        this.refreshTokenRepository = refreshTokenRepository;
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
        if (payload.getPassword() != null && !payload.getPassword().trim().isEmpty()) {
            updatedPasswordHash = passwordEncoder.encode(payload.getPassword().trim());
        }

        if (!oldUsername.equals(username)) {
            userAccountRepository.insertReplacementAccount(
                    username,
                    updatedPasswordHash,
                    existing.getAccountStatus().name(),
                    payload.getEmployeeId(),
                    Boolean.TRUE.equals(existing.getTwoFactorEnabled()),
                    existing.getTwoFactorSecret()
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

        userAccountRepository.save(existing);
        return existing;
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
