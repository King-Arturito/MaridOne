package org.example.maridone.payroll.item.component;

import org.example.maridone.annotation.AuditLog;
import org.example.maridone.payroll.dto.DeductionsDto;
import org.example.maridone.payroll.dto.EarningsDto;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/payroll/item")
public class ItemComponentController {
    private final EarningsService earningsService;
    private final DeductionsService deductionsService;

    public ItemComponentController(EarningsService earningsService, DeductionsService deductionsService) {
        this.earningsService = earningsService;
        this.deductionsService = deductionsService;
    }

    @GetMapping("/earnings/{itemId}")
    @PreAuthorize("hasRole('HR') or @itemOwnerCheck.isSelf(#itemId, authentication.getName())")
    public List<EarningsDto> getEarnings(@PathVariable Long itemId) {
        return earningsService.getEarnings(itemId);
    }

    @GetMapping("/deductions/{itemId}")
    @PreAuthorize("hasRole('HR') or @itemOwnerCheck.isSelf(#itemId, authentication.getName())")
    public List<DeductionsDto> getDeductions(@PathVariable Long itemId) {
        return deductionsService.getDeductions(itemId);
    }

    @PatchMapping("/earnings/update/{earningsId}")
    @PreAuthorize("hasRole('HR')")
    @AuditLog
    public EarningsDto updateEarning(@RequestBody EarningsDto payload, @PathVariable Long earningsId) {
        return earningsService.updateEarning(payload, earningsId);
    }

    @PatchMapping("/deductions/update/{deductionsId}")
    @PreAuthorize("hasRole('HR')")
    @AuditLog
    public DeductionsDto updateDeduction(@RequestBody DeductionsDto payload, @PathVariable Long deductionsId) {
        return deductionsService.updateDeduction(payload, deductionsId);
    }
}
