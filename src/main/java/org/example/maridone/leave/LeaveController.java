package org.example.maridone.leave;

import jakarta.validation.Valid;
import org.example.maridone.annotation.AuditLog;
import org.example.maridone.leave.dto.*;
import org.example.maridone.leave.balance.LeaveBalance;
import org.example.maridone.leave.request.LeaveRequest;
import org.example.maridone.marker.OnUpdate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/leave")
public class LeaveController {

    private final LeaveService leaveService;

    public LeaveController(LeaveService leaveService) {
        this.leaveService = leaveService;
    }

    @GetMapping("/balance/{empId}")
    @PreAuthorize("@userCheck.isSelf(#empId, authentication.getName())")
    public List<BalanceResponseDto> getBalance(@PathVariable Long empId){
        return leaveService.getBalance(empId);
    }

    @GetMapping("/request")
    @PreAuthorize("hasRole('HR')")
    public Page<LeaveResponseDto> getLeaveRequests(LeaveFilter filter,
                                                   @PageableDefault(sort = "requestId", direction = Sort.Direction.ASC, size = 20) Pageable pageable) {
        return leaveService.getLeaveRequests(filter, pageable);
    }

    @GetMapping("/request/{empId}")
    @PreAuthorize("@userCheck.isSelf(#empId, authentication.getName())")
    public Page<LeaveResponseDto> getMyLeaveRequests(Long empId,
                                                     @PageableDefault(sort = "requestId", direction = Sort.Direction.ASC, size = 10) Pageable pageable) {
        return leaveService.getMyLeaveRequests(empId, pageable);
    }

    @PatchMapping("/request/cancel/{requestId}")
    @PreAuthorize("@leaveOwnerCheck.isSelf(#requestId, authentication.getName())")
    public ResponseEntity<Void> cancelRequest(@PathVariable Long requestId) {
        leaveService.cancelRequest(requestId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/balance/{empId}")
    @PreAuthorize("hasRole('HR')")
    @AuditLog
    public ResponseEntity<LeaveBalance> createLeave(
            @PathVariable Long empId,
            @RequestBody @Valid BalanceRequestDto payload){
        LeaveBalance response = leaveService.createLeaveBalance(empId,payload);
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .build()
                .toUri();
        return ResponseEntity.created(location).body(response);
    }
    /*
    update a user's leave balance
    payload:
        balanceHours;
        LeaveType leaveType;
        String type;
    */
    @PatchMapping("/balance/update/{empId}")
    @PreAuthorize("hasRole('HR')")
    @AuditLog
    public ResponseEntity<Void> updateBalance(
            @PathVariable Long empId,
            @RequestBody @Valid UpdateBalanceDto payload) {
        leaveService.updateLeaveBalance(empId, payload);

        return ResponseEntity.noContent().build();
    }


    //create leave request
    @PostMapping("/create/{empId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<LeaveRequest>> createLeaveRequest(
            @Valid @RequestBody LeaveRequestDto payload,
            @PathVariable Long empId) {
        List<LeaveRequest> requests = leaveService.createLeaveRequest(payload, empId);
        return ResponseEntity.ok(requests);
    }

    @PatchMapping("/update/{requestId}")
    @PreAuthorize("hasRole('HR')")
    @AuditLog
    public ResponseEntity<LeaveRequest> updateLeaveRequest(@Validated(OnUpdate.class) @RequestBody LeaveRequestDto payload, @PathVariable Long requestId) {
        LeaveRequest request = leaveService.updateLeaveRequest(payload, requestId);
        return ResponseEntity.ok(request);
    }
}
