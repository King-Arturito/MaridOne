package org.example.maridone.core.employee;

import org.example.maridone.annotation.AuditLog;
import org.example.maridone.core.dto.EmployeeDetailsDto;
import org.example.maridone.core.dto.EmployeeRequestDto;
import org.example.maridone.core.dto.EmployeeResponseDto;
import org.example.maridone.core.filter.EmployeeFilter;
import org.example.maridone.core.user.UserAccountService;
import org.example.maridone.marker.HrUpdate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import java.net.URI;

@RestController
@RequestMapping("/api/employees")

public class EmployeeController {

    private final EmployeeService employeeService;
    private final UserAccountService userAccountService;

    public EmployeeController(EmployeeService employeeService,
                              UserAccountService userAccountService) {
        this.employeeService = employeeService;
        this.userAccountService = userAccountService;
    }

    // ENDPOINT: /api/employees/create
    @PostMapping("/create")
    @PreAuthorize("hasRole('MANAGEMENT')")
    @AuditLog
    public ResponseEntity<EmployeeResponseDto> createEmployee(@RequestBody EmployeeRequestDto employeeRequestDto) {
        EmployeeResponseDto response = employeeService.createEmployee(employeeRequestDto);
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.getId())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    // ENDPOINT: /api/employees
    @PreAuthorize("hasRole('MANAGEMENT')")
    @GetMapping()
    @AuditLog
    public Page<EmployeeResponseDto> getAllEmployees(
            EmployeeFilter employeeFilter,
            @PageableDefault(sort = "employeeId", direction = Sort.Direction.ASC) Pageable pageable) {
        return employeeService.getAllEmployees(employeeFilter,pageable);
    }

    // ENDPOINT: /api/employees/id
    // for personal details check only
    @GetMapping("/{id}")
    @PreAuthorize("@userCheck.isSelf(#id, authentication.getName())")
    public EmployeeDetailsDto getSelfEmployee(@PathVariable Long id) {
        return employeeService.getSelfEmployee(id);
    }

    // ENDPOINT: /api/employees/id
    // id is dynamic
    //returns a status code instead of automatically showing it in the HRIS
    @PatchMapping("/{id}")
    @AuditLog
    public ResponseEntity<EmployeeResponseDto> updateEmployee(@PathVariable Long id, @RequestBody EmployeeRequestDto employeeRequestDto) {
        EmployeeResponseDto response =  employeeService.updateEmployee(id, employeeRequestDto);
        return ResponseEntity.ok(response);
    }

    // ENDPOINT: /api/employees/status/id
    // id is dynamic
    @PatchMapping("/status/{id}")
    @PreAuthorize("hasRole('HR')")
    @AuditLog
    public EmployeeResponseDto updateStatus(@PathVariable Long id,
                                            @Validated(HrUpdate.class) @RequestBody EmployeeRequestDto employeeRequestDto) {
        return employeeService.updateStatus(id, employeeRequestDto);
    }
}
