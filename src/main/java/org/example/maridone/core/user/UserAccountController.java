package org.example.maridone.core.user;

import java.net.URI;

import org.example.maridone.annotation.AuditLog;
import org.example.maridone.core.dto.ChangePasswordDto;
import org.example.maridone.core.dto.CreateUserAccountDto;
import org.example.maridone.document.dto.DocumentResponseDto;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import java.util.List;

@RestController
@RequestMapping("/api/user")
public class UserAccountController {

    private final UserAccountService userAccountService;

    public UserAccountController(UserAccountService userAccountService) {
        this.userAccountService = userAccountService;
    }

    //create new UserAccount
    @PostMapping("/create")
    @PreAuthorize("hasRole('HR')")
    @AuditLog
    public ResponseEntity<UserAccount> createUserAccount(@RequestBody CreateUserAccountDto payload) {
        UserAccount user = userAccountService.createUserAccount(payload);
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest().build().toUri();
        return ResponseEntity.created(location).body(user);
    }

    @PatchMapping("/{employeeId}")
    @PreAuthorize("hasRole('HR')")
    @AuditLog
    public ResponseEntity<UserAccount> updateUserAccount(
            @PathVariable Long employeeId,
            @RequestBody CreateUserAccountDto payload) {
        payload.setEmployeeId(employeeId);
        return ResponseEntity.ok(userAccountService.updateUserAccount(payload));
    }

    @PatchMapping("/password/change")
    public ResponseEntity<String> changeOwnPassword(
            Authentication authentication,
            @RequestBody ChangePasswordDto payload) {
        userAccountService.changeOwnPassword(authentication.getName(), payload);
        return ResponseEntity.ok("Password updated successfully.");
    }

    //view your own UserAccount
    @GetMapping("/details/{username}")
    @PreAuthorize("#username == authentication.getName()")
    public UserAccount getUserAccount(@PathVariable String username) {
        return userAccountService.getUserAccount(username);
    }

    @GetMapping("/documents/{username}")
    @PreAuthorize("#username == authentication.getName()")
    public List<DocumentResponseDto> getDocuments(@PathVariable String username,
                                                  @org.springframework.web.bind.annotation.RequestParam(required = false) String subDir) {
        return userAccountService.getDocuments(username, subDir);
    }
}
