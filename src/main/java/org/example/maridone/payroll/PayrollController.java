package org.example.maridone.payroll;

import jakarta.validation.Valid;
import org.example.maridone.annotation.AuditLog;
import org.example.maridone.marker.OnCreate;
import org.example.maridone.marker.OnUpdate;
import org.example.maridone.payroll.dto.*;
import org.example.maridone.payroll.item.ItemFilter;
import org.example.maridone.payroll.item.PayrollItemService;
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
    private final PayrollItemService payrollItemService;

    public PayrollController(PayrollService payrollService, PayrollItemService payrollItemService) {
        this.payrollService = payrollService;
        this.payrollItemService = payrollItemService;
    }

    @GetMapping("/item/{empId}")
    @PreAuthorize("@userCheck.isSelf(#empId, authentication.getName())")
    public List<ItemDetailsDto> getItems(@PathVariable Long empId) {
        return payrollItemService.getItems(empId);
    }

    @GetMapping("/run/{payId}/items")
    @PreAuthorize("hasRole('HR')")
    @AuditLog
    public Page<ItemViewDto> getRunItems(ItemFilter filter,
                                         @PathVariable Long payId,
                                         Pageable pageable) {
        return payrollItemService.getRunItems(payId, filter, pageable);
    }

    @GetMapping("/run/summary/{payId}")
    @PreAuthorize("hasAnyRole('HR', 'MANAGEMENT')")
    public List<PayTypeSummaryDto> getSummarizedRunItems(@PathVariable Long payId) {
        return payrollItemService.getSummarizedRunItems(payId);
    }

    @GetMapping("/run")
    @PreAuthorize("hasRole('HR')")
    public List<PayrollRunViewDto> getRuns() {
        return payrollService.getRuns();
    }

    @GetMapping("/run/{payId}")
    @PreAuthorize("hasRole('HR')")
    public PayrollRunViewDto getRun(@PathVariable Long payId) {
        return payrollService.getRun(payId);
    }

    @GetMapping("/item/manage/{itemId}")
    @PreAuthorize("hasRole('HR')")
    public CorrectionSourceItemDto getItemForCorrection(@PathVariable Long itemId) {
        return payrollItemService.getItemForCorrection(itemId);
    }

    @PostMapping("/run/create")
    @PreAuthorize("hasRole('HR')")
    @AuditLog
    public ResponseEntity<PayrollRunViewDto> createRun(@RequestBody @Valid RunCreateDto payload) {
        var run = payrollService.createRun(payload);
        URI location = ServletUriComponentsBuilder
                .fromCurrentContextPath()
                .path("/api/payroll/run/{id}")
                .buildAndExpand(run.getPayId())
                .toUri();
        return ResponseEntity.created(location).body(payrollService.getRun(run.getPayId()));
    }

    @PostMapping("/run/process")
    @PreAuthorize("hasRole('HR')")
    @AuditLog
    public ResponseEntity<PayrollRunViewDto> processRun(@RequestBody @Valid RunCreateDto payload) {
        PayrollRunViewDto run = payrollService.processPayroll(payload);
        URI location = ServletUriComponentsBuilder
                .fromCurrentContextPath()
                .path("/api/payroll/run/{id}")
                .buildAndExpand(run.getPayId())
                .toUri();
        return ResponseEntity.created(location).body(run);
    }

    @PatchMapping("/run/{payId}/post")
    @PreAuthorize("hasRole('HR')")
    @AuditLog
    public ResponseEntity<PayrollRunViewDto> postRun(@PathVariable Long payId) {
        return ResponseEntity.ok(payrollService.postRun(payId));
    }

    @PostMapping("/item/create")
    @PreAuthorize("hasRole('HR')")
    @AuditLog
    public ResponseEntity<ItemDetailsDto> createItem(@RequestBody @Validated(OnCreate.class) PayrollItemDto payload) {
        ItemDetailsDto item = payrollItemService.createItem(payload);
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(item.getId())
                .toUri();
        return ResponseEntity.created(location).body(item);
    }

    @PostMapping("/run/{payId}/adjustment/correction")
    @PreAuthorize("hasRole('HR')")
    @AuditLog
    public ResponseEntity<ItemDetailsDto> addLineCorrection(
            @PathVariable Long payId,
            @RequestBody @Valid PayrollLineCorrectionDto payload
    ) {
        ItemDetailsDto item = payrollItemService.addLineCorrection(payId, payload);
        URI location = ServletUriComponentsBuilder
                .fromCurrentContextPath()
                .path("/api/payroll/item/{id}")
                .buildAndExpand(item.getId())
                .toUri();
        return ResponseEntity.created(location).body(item);
    }
    
    @PatchMapping("/item/update/{itemId}")
    @PreAuthorize("hasRole('HR')")
    @AuditLog
    public ResponseEntity<PayrollItemDto> updateItem(@RequestBody @Validated(OnUpdate.class) PayrollItemDto payload, @PathVariable Long itemId) {
        PayrollItemDto item = payrollItemService.updateItem(payload, itemId);
        return ResponseEntity.ok(item);
    }
}
