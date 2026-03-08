package org.example.maridone.payroll.run;

import jakarta.validation.Valid;
import org.example.maridone.annotation.AuditLog;
import org.example.maridone.marker.OnCreate;
import org.example.maridone.marker.OnUpdate;
import org.example.maridone.payroll.PayrollService;
import org.example.maridone.payroll.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/payroll")
public class PayrollController {
    private final PayrollService payrollService;

    public PayrollController(PayrollService payrollService) {
        this.payrollService = payrollService;
    }

    @GetMapping("/item/{empId}")
    @PreAuthorize("@userCheck.isSelf(#empId, authentication.getName())")
    public List<ItemDetailsDto> getItems(@PathVariable Long empId) {
        return payrollService.getItems(empId);
    }

    @GetMapping("/run/{payId}/items")
    @PreAuthorize("hasRole('HR')")
    @AuditLog
    public Page<ItemSummaryDto> getRunItems(@PathVariable Long payId, Pageable pageable) {
        return payrollService.getRunItems(payId, pageable);
    }

     /*
        Will probably not be used since creation of run is automatic when launching payroll
     */
    @PostMapping("/run/create")
    @PreAuthorize("hasRole('HR')")
    @AuditLog
    public ResponseEntity<PayrollRun> createRun(@RequestBody @Valid RunCreateDto payload) {
        PayrollRun run = payrollService.createRun(payload);
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(run.getPayId())
                .toUri();
        return ResponseEntity.created(location).body(run);
    }

    /*
       Will probably not be used also since creation of items are automatic when launching payroll
    */
    @PostMapping("/item/create")
    @PreAuthorize("hasRole('HR')")
    @AuditLog
    public ResponseEntity<ItemDetailsDto> createItem(@RequestBody @Validated(OnCreate.class) PayrollItemDto payload) {
        ItemDetailsDto item = payrollService.createItem(payload);
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(item.getId())
                .toUri();
        return ResponseEntity.created(location).body(item);
    }
    
    @PatchMapping("/item/update/{itemId}")
    @PreAuthorize("hasRole('HR')")
    @AuditLog
    public ResponseEntity<PayrollItemDto> updateItem(@RequestBody @Validated(OnUpdate.class) PayrollItemDto payload, @PathVariable Long itemId) {
        PayrollItemDto item = payrollService.updateItem(payload, itemId);
        return ResponseEntity.ok(item);
    }


}
