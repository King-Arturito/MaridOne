package org.example.maridone.payroll.dispute;

import jakarta.validation.Valid;
import org.example.maridone.annotation.AuditLog;
import org.example.maridone.enums.Status;
import org.example.maridone.marker.OnUpdate;
import org.example.maridone.payroll.dto.DisputeActionDto;
import org.example.maridone.payroll.dto.DisputeRequestDto;
import org.example.maridone.payroll.dto.DisputeResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

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
             @RequestBody @Valid DisputeRequestDto payload)
    {
        return disputeService.createDisputeRequest(itemId, payload);
    }

    @PatchMapping("/update/{disputeId}")
    @PreAuthorize("hasRole('HR')")
    @AuditLog
    public ResponseEntity<?> updateDisputeRequest(@PathVariable Long disputeId, @RequestBody @Validated(OnUpdate.class) DisputeActionDto payload) {
        DisputeResponseDto response = disputeService.updateDisputeStatus(disputeId, payload);
        return ResponseEntity.ok(response);
    }
}
