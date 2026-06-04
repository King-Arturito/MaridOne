package org.example.maridone.core.bank;

import org.example.maridone.annotation.AuditLog;
import org.example.maridone.auth.sensitive.SensitiveAccessService;
import org.example.maridone.core.dto.BankAccountDto;
import org.example.maridone.core.mapper.CoreMapper;
import org.example.maridone.marker.OnCreate;
import org.example.maridone.marker.OnUpdate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/bank")
public class BankController {

    private final BankService bankService;
    private final CoreMapper coreMapper;
    private final SensitiveAccessService sensitiveAccessService;

    public BankController(BankService bankService, CoreMapper coreMapper, SensitiveAccessService sensitiveAccessService) {
        this.bankService = bankService;
        this.coreMapper = coreMapper;
        this.sensitiveAccessService = sensitiveAccessService;
    }

    //add new bank
    @PostMapping("/add/{empId}")
    @PreAuthorize("hasRole('HR') or @userCheck.isSelf(#empId, authentication.getName())")
    @AuditLog
    public ResponseEntity<BankAccount> addBankAccount(
            @PathVariable Long empId,
            Authentication authentication,
            @RequestBody @Validated(OnCreate.class) BankAccountDto payload) {
        sensitiveAccessService.assertSensitiveAccessForEmployee(authentication, empId);
        BankAccount bankAccount = bankService.addBankAccount(empId, payload);
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{empId}")
                .buildAndExpand(empId)
                .toUri();
        return ResponseEntity.created(location).body(bankAccount);
    }
    //update?
    @PatchMapping("/update/{empId}")
    @PreAuthorize("hasRole('HR') or @userCheck.isSelf(#empId, authentication.getName())")
    @AuditLog
    public ResponseEntity<BankAccount> updateBankAccount(
            @PathVariable Long empId,
            Authentication authentication,
            @RequestBody @Validated(OnUpdate.class) BankAccountDto payload
    ) {
        sensitiveAccessService.assertSensitiveAccessForEmployee(authentication, empId);
        BankAccount bankAccount = bankService.updateBankAccount(empId, payload);
        return ResponseEntity.ok(bankAccount);
    }
    @GetMapping("{empId}")
    @PreAuthorize("hasRole('HR') or @userCheck.isSelf(#empId, authentication.getName())")
    @AuditLog
    public List<BankAccountDto> getBankAccounts(@PathVariable Long empId, Authentication authentication) {
        sensitiveAccessService.assertSensitiveAccessForEmployee(authentication, empId);
        return coreMapper.toBankAccountDtos(bankService.getBankAccounts(empId));
    }
}
