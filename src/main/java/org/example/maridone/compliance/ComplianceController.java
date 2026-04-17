package org.example.maridone.compliance;

import org.example.maridone.auth.sensitive.SensitiveAccessService;
import org.example.maridone.compliance.dto.ComplianceRecordDto;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/compliance")
public class ComplianceController {

    private final ComplianceOrchestrator complianceOrchestrator;
    private final SensitiveAccessService sensitiveAccessService;

    public ComplianceController(ComplianceOrchestrator complianceOrchestrator,
                                SensitiveAccessService sensitiveAccessService) {
        this.complianceOrchestrator = complianceOrchestrator;
        this.sensitiveAccessService = sensitiveAccessService;
    }

    @GetMapping("/{employeeId}")
    @PreAuthorize("hasRole('HR')")
    public ComplianceRecordDto getEmployeeComplianceRecord(@PathVariable Long employeeId,
                                                           Authentication authentication) {
        sensitiveAccessService.assertHrSensitiveAccessForEmployee(authentication, employeeId);
        return complianceOrchestrator.getEmployeeComplianceRecord(employeeId);
    }
}
