package org.example.maridone.payroll.dispute;

import jakarta.validation.Valid;
import org.example.maridone.annotation.AuditLog;
import org.example.maridone.enums.Status;
import org.example.maridone.marker.OnUpdate;
import org.example.maridone.payroll.dto.DisputeActionDto;
import org.example.maridone.payroll.dto.DisputeDetails;
import org.example.maridone.payroll.dto.DisputeRequestDto;
import org.example.maridone.payroll.dto.DisputeResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/payroll/item/dispute")
public class DisputeController {

    private final DisputeService disputeService;
    public DisputeController(DisputeService disputeService) {
        this.disputeService = disputeService;
    }

    //View all Dispute Requests
    @GetMapping("/all")
    @PreAuthorize("hasRole('HR')")
    public Page<DisputeResponseDto> getActiveDisputeRequests(@PageableDefault(sort = "createdAt", direction = Sort.Direction.ASC) Pageable pageable)
    {
            return disputeService.getActiveDisputeRequests(pageable);
    }

    @GetMapping("/details/{disputeId}")
    @PreAuthorize("hasRole('HR')")
    public DisputeDetails getDisputeRequestDetails(@PathVariable Long disputeId) {
        return disputeService.getDisputeRequestDetails(disputeId);
    }

    @GetMapping("/self")
    @PreAuthorize("@userCheck.isSelf(#empId, authentication.getName())")
    public Page<DisputeResponseDto> getMyDisputeRequests(
            Long empId,
            Status status,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.ASC) Pageable pageable)
    {
            return disputeService.getMyDisputeRequests(empId, status, pageable);
    }

    @PostMapping("/create/{itemId}")
    @PreAuthorize("@itemOwnerCheck.isSelf(#itemId, authentication.getName())")
    public DisputeRequest createDisputeRequest
            (@PathVariable Long itemId,
             @RequestBody @Valid DisputeRequestDto payload,
            @RequestParam(required = false) MultipartFile file)
    {
        return disputeService.createDisputeRequest(itemId, payload,file);
    }

    @PatchMapping("/update/{disputeId}")
    @PreAuthorize("hasRole('HR')")
    @AuditLog
    public ResponseEntity<?> updateDisputeRequest(
            @PathVariable Long disputeId,
            @RequestBody @Validated(OnUpdate.class) DisputeActionDto payload,
            Authentication authentication) {
        DisputeResponseDto response = disputeService.updateDisputeStatus(disputeId, payload, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/cancel/{disputeId}")
    @AuditLog
    @PreAuthorize("@disputeOwnerCheck.isSelf(#disputeId, authentication.getName())")
    public ResponseEntity<Void> cancelRequest(@PathVariable Long disputeId) {
        disputeService.cancelRequest(disputeId);
        return ResponseEntity.noContent().build();
    }
}
