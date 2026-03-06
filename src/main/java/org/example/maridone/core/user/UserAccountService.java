package org.example.maridone.core.user;

import org.example.maridone.annotation.ExecutionTime;
import org.example.maridone.core.dto.CreateUserAccountDto;
import org.example.maridone.core.employee.Employee;
import org.example.maridone.core.employee.EmployeeRepository;
import org.example.maridone.core.mapper.CoreMapper;
import org.example.maridone.enums.Position;
import org.example.maridone.exception.AccountNotFoundException;
import org.example.maridone.exception.BadCredentialsException;
import org.example.maridone.exception.DuplicateAccountException;
import org.example.maridone.exception.EmployeeNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;


@Service
public class UserAccountService {

    private final UserAccountRepository userAccountRepository;
    private final CoreMapper coreMapper;
    private final PasswordEncoder passwordEncoder;
    private final EmployeeRepository employeeRepository;

    public UserAccountService
            (UserAccountRepository userAccountRepository,
             CoreMapper coreMapper,
             PasswordEncoder passwordEncoder,
             EmployeeRepository employeeRepository) {
        this.userAccountRepository = userAccountRepository;
        this.coreMapper = coreMapper;
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
        if (userAccountRepository.existsByUsername(payload.getUsername())) {
            throw new DuplicateAccountException("Username already exists");
        }
        if (userAccountRepository.existsByEmployee_EmployeeId(payload.getEmployeeId())) {
            throw new DuplicateAccountException("Duplicate Account for Employee Id: " + payload.getEmployeeId() + " found. Update account instead.");
        }
        Employee emp = employeeRepository.findById(payload.getEmployeeId()).orElseThrow(() -> new EmployeeNotFoundException(payload.getEmployeeId()));

        UserAccount user = new UserAccount();
        user.setUsername(payload.getUsername());
        user.setPasswordHash(passwordEncoder.encode(payload.getPassword()));
        user.setEmployee(emp);
        user.setAccountStatus(payload.getAccountStatus());
        userAccountRepository.save(user);
        return user;
    }
}
