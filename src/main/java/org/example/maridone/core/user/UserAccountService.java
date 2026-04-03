package org.example.maridone.core.user;

import org.example.maridone.annotation.ExecutionTime;
import org.example.maridone.core.dto.CreateUserAccountDto;
import org.example.maridone.core.employee.Employee;
import org.example.maridone.core.employee.EmployeeRepository;
import org.example.maridone.document.path.DocumentPath;
import org.example.maridone.exception.notfound.AccountNotFoundException;
import org.example.maridone.exception.unauthorized.DuplicateAccountException;
import org.example.maridone.exception.notfound.EmployeeNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


@Service
public class UserAccountService {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmployeeRepository employeeRepository;

    public UserAccountService
            (UserAccountRepository userAccountRepository,
             PasswordEncoder passwordEncoder,
             EmployeeRepository employeeRepository) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.employeeRepository = employeeRepository;
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

        existing.setUsername(username);
        if (existing.getEmployee() != null) {
            existing.getEmployee().setUserAccount(existing);
        }

        if (payload.getPassword() != null && !payload.getPassword().trim().isEmpty()) {
            existing.setPasswordHash(passwordEncoder.encode(payload.getPassword().trim()));
        }

        userAccountRepository.save(existing);
        return existing;
    }

    @ExecutionTime
    public List<DocumentPath> getDocuments(String username) {
        UserAccount userAccount = getUserAccount(username);
        return userAccount.getDocumentPaths();
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
