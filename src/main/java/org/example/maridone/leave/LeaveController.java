package org.example.maridone.leave;

import jakarta.validation.Valid;
import org.example.maridone.annotation.AuditLog;
import org.example.maridone.leave.dto.BalanceRequestDto;
import org.example.maridone.leave.dto.BalanceResponseDto;
import org.example.maridone.leave.balance.LeaveBalance;
import org.example.maridone.leave.dto.LeaveRequestDto;
import org.example.maridone.leave.dto.UpdateBalanceDto;
import org.example.maridone.leave.request.LeaveRequest;
import org.example.maridone.marker.OnUpdate;
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

    /*
        create balance
        payload:
            Long empId;
            BigDecimal balanceHours;
            private LeaveType leaveType;
     */
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
        leaveService.updateYearlyBalance(empId, payload);

        return ResponseEntity.noContent().build();
    }


    //create leave request
    @PostMapping("/create")
    public ResponseEntity<LeaveRequest> createLeaveRequest(@RequestBody LeaveRequestDto payload, Long empId) {
        LeaveRequest request = leaveService.createLeaveRequest(payload, empId);
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{requestId}")
                .buildAndExpand(request.getRequestId())
                .toUri();
        return ResponseEntity.created(location).body(request);
    }

    @PatchMapping("update/{requestId}")
    @PreAuthorize("hasRole('HR')")
    @AuditLog
    public ResponseEntity<LeaveRequest> updateLeaveRequest(@Validated(OnUpdate.class) @RequestBody LeaveRequest payload, Long requestId) {
        LeaveRequest request = leaveService.updateLeaveRequest(payload, requestId);
        return ResponseEntity.ok(request);
    }
}
