package org.example.maridone.auth.sensitive;

import org.example.maridone.annotation.AuditLog;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/security/sensitive-access")
public class SensitiveAccessController {

    private final SensitiveAccessService sensitiveAccessService;

    public SensitiveAccessController(SensitiveAccessService sensitiveAccessService) {
        this.sensitiveAccessService = sensitiveAccessService;
    }

    @GetMapping("/state")
    @PreAuthorize("hasAnyRole('HR', 'MANAGEMENT')")
    public SensitiveAccessStateDto getState(Authentication authentication) {
        return sensitiveAccessService.getState(authentication.getName());
    }

    @PostMapping("/verify")
    @PreAuthorize("hasAnyRole('HR', 'MANAGEMENT')")
    @AuditLog
    public ResponseEntity<SensitiveAccessVerifyResponse> verify(Authentication authentication,
                                                                @RequestBody SensitiveAccessVerifyRequest request) {
        return ResponseEntity.ok(sensitiveAccessService.verify(authentication.getName(), request));
    }

    @PostMapping("/send-email-code")
    @PreAuthorize("hasAnyRole('HR', 'MANAGEMENT')")
    @AuditLog
    public ResponseEntity<Void> sendEmailCode(Authentication authentication) {
        sensitiveAccessService.sendEmailCode(authentication.getName());
        return ResponseEntity.ok().build();
    }
}
