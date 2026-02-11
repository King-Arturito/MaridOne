package org.example.maridone.core.bank;

import org.example.maridone.core.dto.BankAccountDto;
import org.example.maridone.core.mapper.CoreMapper;
import org.example.maridone.marker.OnCreate;
import org.example.maridone.marker.OnUpdate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
    public BankController(BankService bankService, CoreMapper coreMapper) {
        this.bankService = bankService;
        this.coreMapper = coreMapper;
    }

    //add new bank
    @PostMapping("/add/{empId}")
    @PreAuthorize("@userCheck.isSelf(#empId, authentication.getName())")
    public ResponseEntity<BankAccount> addBankAccount(
            @PathVariable Long empId,
            @RequestBody @Validated(OnCreate.class) BankAccountDto payload) {
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
    @PreAuthorize("@userCheck.isSelf(#empId, authentication.getName())")
    public ResponseEntity<BankAccount> updateBankAccount(
            @PathVariable Long empId,
            @RequestBody @Validated(OnUpdate.class) BankAccountDto payload
    ) {
        BankAccount bankAccount = bankService.updateBankAccount(payload);
        return ResponseEntity.ok(bankAccount);
    }
    @GetMapping("{empId}")
    @PreAuthorize("@userCheck.isSelf(#empId, authentication.getName())")
    public List<BankAccountDto> getBankAccounts(@PathVariable Long empId) {
        return coreMapper.toBankAccountDtos(bankService.getBankAccounts(empId));
    }
}
