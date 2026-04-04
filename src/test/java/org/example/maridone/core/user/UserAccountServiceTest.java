package org.example.maridone.core.user;

import java.util.Optional;

import org.example.maridone.auth.token.RefreshTokenRepository;
import org.example.maridone.core.dto.CreateUserAccountDto;
import org.example.maridone.core.employee.Employee;
import org.example.maridone.core.employee.EmployeeRepository;
import org.example.maridone.document.DocumentPathRepository;
import org.example.maridone.document.dto.DocumentResponseDto;
import org.example.maridone.document.mapper.DocumentMapper;
import org.example.maridone.document.path.DocumentPath;
import org.example.maridone.enums.AccountStatus;
import org.example.maridone.exception.notfound.AccountNotFoundException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserAccountServiceTest {

    @Mock private UserAccountRepository userAccountRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private DocumentPathRepository documentPathRepository;
    @Mock private DocumentMapper documentMapper;
    @Mock private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private UserAccountService userAccountService;

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
        Assertions.assertEquals("niko123", result.getUsername());
        Assertions.assertEquals("hashed_secret", result.getPasswordHash());
        Assertions.assertEquals(AccountStatus.ACTIVE, result.getAccountStatus());
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
    void getDocuments_ShouldReturnFilteredDocumentDtos() {
        String username = "niko123";
        UserAccount account = new UserAccount();
        account.setUsername(username);

        DocumentPath payslip = new DocumentPath();
        payslip.setFilePath("C:/tmp/local-documents/niko123/payslip/sample.pdf");
        DocumentPath dispute = new DocumentPath();
        dispute.setFilePath("C:/tmp/local-documents/niko123/dispute-request/sample.pdf");

        DocumentResponseDto payslipDto = new DocumentResponseDto();
        payslipDto.setFilePath(payslip.getFilePath());

        when(userAccountRepository.findByUsername(username)).thenReturn(Optional.of(account));
        when(documentPathRepository.findAll(org.mockito.ArgumentMatchers.<Specification<DocumentPath>>any(), any(Sort.class)))
                .thenReturn(java.util.List.of(payslip, dispute));
        when(documentMapper.toDocumentResponseDto(payslip)).thenReturn(payslipDto);

        java.util.List<DocumentResponseDto> result = userAccountService.getDocuments(username, "payslip");

        Assertions.assertEquals(1, result.size());
        Assertions.assertEquals(payslip.getFilePath(), result.get(0).getFilePath());
    }

    @Test
    void updateUserAccount_ShouldMigrateLinkedRecords_WhenUsernameChanges() {
        CreateUserAccountDto dto = new CreateUserAccountDto();
        dto.setEmployeeId(21L);
        dto.setUsername("chloe.renamed");

        Employee employee = new Employee();
        UserAccount existing = new UserAccount();
        existing.setUsername("chloe.old");
        existing.setPasswordHash("existing_hash");
        existing.setAccountStatus(AccountStatus.ACTIVE);
        existing.setEmployee(employee);
        existing.setTwoFactorEnabled(true);
        existing.setTwoFactorSecret("secret-2fa");

        UserAccount replacement = new UserAccount();
        replacement.setUsername("chloe.renamed");

        when(userAccountRepository.findByEmployee_EmployeeId(21L)).thenReturn(Optional.of(existing));
        when(userAccountRepository.existsByUsername("chloe.renamed")).thenReturn(false);
        when(userAccountRepository.findByUsername("chloe.renamed")).thenReturn(Optional.of(replacement));

        UserAccount result = userAccountService.updateUserAccount(dto);

        Assertions.assertEquals("chloe.renamed", result.getUsername());
        verify(userAccountRepository).insertReplacementAccount(
                "chloe.renamed",
                "existing_hash",
                AccountStatus.ACTIVE.name(),
                21L,
                true,
                "secret-2fa"
        );
        verify(documentPathRepository).reassignUsername("chloe.old", "chloe.renamed");
        verify(refreshTokenRepository).deleteByUsername("chloe.old");
        verify(userAccountRepository).deleteByUsernameNative("chloe.old");
        verify(userAccountRepository, never()).save(existing);
    }
}
