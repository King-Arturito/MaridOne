package org.example.maridone.payroll.run;

import jakarta.validation.Valid;
import org.example.maridone.component.ItemOwnerCheck;
import org.example.maridone.marker.OnCreate;
import org.example.maridone.marker.OnUpdate;
import org.example.maridone.payroll.PayrollItem;
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
    public List<ItemDetailsDto> getItems(@PathVariable Long empId) {
        return payrollService.getItems(empId);
    }

    @GetMapping("/run/{payId}/items")
    @PreAuthorize("hasRole('HR')")
    public Page<ItemSummaryDto> getRunItems(@PathVariable Long payId, Pageable pageable) {
        return payrollService.getRunItems(payId, pageable);
    }

    @PostMapping("/run/create")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<PayrollRun> createRun(@RequestBody @Valid RunCreateDto payload) {
        PayrollRun run = payrollService.createRun(payload);
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(run.getPayId())
                .toUri();
        return ResponseEntity.created(location).body(run);
    }

    @PostMapping("/item/create")
    @PreAuthorize("hasRole('HR')")
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
    public ResponseEntity<PayrollItemDto> updateItem(@RequestBody @Validated(OnUpdate.class) PayrollItemDto payload, @PathVariable Long itemId) {
        PayrollItemDto item = payrollService.updateItem(payload, itemId);
        return ResponseEntity.ok(item);
    }


}
