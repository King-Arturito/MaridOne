package org.example.maridone.overtime;

import jakarta.validation.Valid;
import org.example.maridone.enums.Status;
import org.example.maridone.overtime.dto.OvertimeRequestDto;
import org.example.maridone.overtime.dto.OvertimeResponseDto;
import org.example.maridone.overtime.dto.OvertimeUpdateDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/overtime")
public class OvertimeController {

    private final OvertimeService overtimeService;

    public OvertimeController(OvertimeService overtimeService) {
        this.overtimeService = overtimeService;
    }

    //approve overtime for hr?


    //request overtime for all
    @PostMapping("/create")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<OvertimeRequest> createOvertimeRequest(
            @RequestBody @Valid OvertimeRequestDto requestDto,
            Authentication authentication) {
        OvertimeRequest request = overtimeService.createOvertimeRequest(requestDto, authentication.getName());
        URI location = ServletUriComponentsBuilder
                .fromCurrentContextPath()
                .path("/api/overtime/create/{id}")
                .buildAndExpand(request.getOvertimeId())
                .toUri();
        return ResponseEntity.created(location).body(request);
    }

    //update Overtime Request status for HR
    @PatchMapping("/update")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<OvertimeResponseDto> updateOvertimeRequest(
            @RequestBody @Valid OvertimeUpdateDto updateDto,
            Authentication authentication) {
        OvertimeResponseDto request = overtimeService.updateOvertimeRequest(updateDto, authentication.getName());
        return ResponseEntity.ok(request);
    }
    //get all overtimes for HR
    @GetMapping("/all")
    @PreAuthorize("hasRole('HR')")
    public Page<OvertimeResponseDto> getAllOvertimeRequest(
            @PageableDefault(sort = "requestAt", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        return overtimeService.getAllOvertimeRequest(pageable);
    }
    //get all pending overtimes for Employee
    @GetMapping("/all/{empId}")
    @PreAuthorize("@userCheck.isSelf(#empId, Authentication.getName())")
    public Page<OvertimeResponseDto> getMyOvertimeRequests(
            @PathVariable Long empId,
            @PageableDefault(sort = "requestAt", direction = Sort.Direction.ASC) Pageable pageable) {
        return overtimeService.getMyOvertimeRequests(empId, pageable);
    }
    //overrule previous overtime update for errors
}