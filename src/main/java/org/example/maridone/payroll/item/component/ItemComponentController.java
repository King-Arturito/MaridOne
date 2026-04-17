package org.example.maridone.payroll.item.component;

import java.util.List;

import org.example.maridone.annotation.AuditLog;
import org.example.maridone.auth.sensitive.SensitiveAccessService;
import org.example.maridone.payroll.payslip.PayslipService;
import org.example.maridone.payroll.dto.DeductionsDto;
import org.example.maridone.payroll.dto.EarningsDto;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payroll/item")
public class ItemComponentController {
    private final EarningsService earningsService;
    private final DeductionsService deductionsService;
    private final PayslipService payslipService;
    private final SensitiveAccessService sensitiveAccessService;

    public ItemComponentController(
            EarningsService earningsService,
            DeductionsService deductionsService,
            PayslipService payslipService,
            SensitiveAccessService sensitiveAccessService
    ) {
        this.earningsService = earningsService;
        this.deductionsService = deductionsService;
        this.payslipService = payslipService;
        this.sensitiveAccessService = sensitiveAccessService;
    }

    @GetMapping("/earnings/{itemId}")
    @PreAuthorize("hasAnyRole('HR', 'ACCOUNTING') or @itemOwnerCheck.isSelf(#itemId, authentication.getName())")
    public List<EarningsDto> getEarnings(@PathVariable Long itemId, Authentication authentication) {
        sensitiveAccessService.assertHrSensitiveAccess(authentication);
        return earningsService.getEarnings(itemId);
    }

    @GetMapping("/deductions/{itemId}")
    @PreAuthorize("hasAnyRole('HR', 'ACCOUNTING') or @itemOwnerCheck.isSelf(#itemId, authentication.getName())")
    public List<DeductionsDto> getDeductions(@PathVariable Long itemId, Authentication authentication) {
        sensitiveAccessService.assertHrSensitiveAccess(authentication);
        return deductionsService.getDeductions(itemId);
    }

    @GetMapping("/{itemId}/payslip/pdf")
    @PreAuthorize("hasAnyRole('HR', 'ACCOUNTING') or @itemOwnerCheck.isSelf(#itemId, authentication.getName())")
    public ResponseEntity<byte[]> getPayslipPdf(@PathVariable Long itemId, Authentication authentication) {
        sensitiveAccessService.assertHrSensitiveAccess(authentication);
        PayslipService.GeneratedPayslipPdf pdf = payslipService.generatePayslipPdf(itemId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + pdf.fileName() + "\"")
                .body(pdf.content());
    }

    @PostMapping("/earnings/create/{itemId}")
    @PreAuthorize("hasRole('HR')")
    @AuditLog
    public EarningsDto createEarning(@RequestBody EarningsDto payload, @PathVariable Long itemId, Authentication authentication) {
        sensitiveAccessService.assertHrSensitiveAccess(authentication);
        return earningsService.createEarning(payload, itemId);
    }

    @PostMapping("/deductions/create/{itemId}")
    @PreAuthorize("hasRole('HR')")
    @AuditLog
    public DeductionsDto createDeduction(@RequestBody DeductionsDto payload, @PathVariable Long itemId, Authentication authentication) {
        sensitiveAccessService.assertHrSensitiveAccess(authentication);
        return deductionsService.createDeduction(payload, itemId);
    }

    @PatchMapping("/earnings/update/{earningsId}")
    @PreAuthorize("hasRole('HR')")
    @AuditLog
    public EarningsDto updateEarning(@RequestBody EarningsDto payload, @PathVariable Long earningsId, Authentication authentication) {
        sensitiveAccessService.assertHrSensitiveAccess(authentication);
        return earningsService.updateEarning(payload, earningsId);
    }

    @PatchMapping("/deductions/update/{deductionsId}")
    @PreAuthorize("hasRole('HR')")
    @AuditLog
    public DeductionsDto updateDeduction(@RequestBody DeductionsDto payload, @PathVariable Long deductionsId, Authentication authentication) {
        sensitiveAccessService.assertHrSensitiveAccess(authentication);
        return deductionsService.updateDeduction(payload, deductionsId);
    }
}
