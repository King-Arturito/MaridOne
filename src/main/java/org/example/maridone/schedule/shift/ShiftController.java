package org.example.maridone.schedule.shift;

import org.example.maridone.annotation.AuditLog;
import org.example.maridone.marker.OnCreate;
import org.example.maridone.marker.OnUpdate;
import org.example.maridone.schedule.dto.DailyShiftOverrideRequestDto;
import org.example.maridone.schedule.dto.ShiftRequestDto;
import org.example.maridone.schedule.dto.ShiftResponseDto;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;


@RestController
@RequestMapping("/api/employee/schedule")
public class ShiftController {

    private final ShiftService shiftService;

    public ShiftController(ShiftService shiftService) {
        this.shiftService = shiftService;
    }

    //add shift
    // if missing some shifts for some reason.
    @PostMapping("/add")
    @PreAuthorize("hasRole('HR') or @managedEmployeeCheck.canManageEmployee(#payload.empId, authentication.name)")
    public ResponseEntity<TemplateShiftSchedule> addShiftSchedule(
            @RequestBody @Validated(OnCreate.class) ShiftRequestDto payload) {
        TemplateShiftSchedule schedule = shiftService.addShiftSchedule(payload);
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .build()
                .toUri();
        return ResponseEntity.created(location).body(schedule);
    }

    //update shift
    @PatchMapping("/update")
    @PreAuthorize("hasRole('HR') or @managedEmployeeCheck.canManageShift(#payload.shiftId, authentication.name)")
    @AuditLog
    public ResponseEntity<TemplateShiftSchedule> updateShiftSchedule(
            @RequestBody @Validated(OnUpdate.class) ShiftRequestDto payload) {
        TemplateShiftSchedule schedule = shiftService.updateShiftSchedule(payload);
        return ResponseEntity.ok(schedule);
    }

    @DeleteMapping("/{shiftId}")
    @PreAuthorize("hasRole('HR') or @managedEmployeeCheck.canManageShift(#shiftId, authentication.name)")
    @AuditLog
    public ResponseEntity<Void> deleteShiftSchedule(@PathVariable Long shiftId) {
        shiftService.deleteShiftSchedule(shiftId);
        return ResponseEntity.noContent().build();
    }
    //shift change request

    //get employee shifts
    @PreAuthorize("hasRole('HR') or @userCheck.isSelf(#empId, authentication.name) or @managedEmployeeCheck.canManageEmployee(#empId, authentication.name)")
    @GetMapping("/{empId}")
    public List<ShiftResponseDto> getShiftSchedule(
            @PathVariable Long empId) {
        return shiftService.getShiftSchedule(empId);
    }

    @GetMapping("/daily")
    @PreAuthorize("hasAnyRole('HR', 'MANAGER')")
    public List<DailyShiftResponse> getDailyShifts(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return shiftService.getDailyShifts(startDate, endDate).stream()
                .map(shift -> new DailyShiftResponse(
                        shift.getDailyShiftId(),
                        shift.getEmployeeId(),
                        shift.getWorkDate(),
                        shift.getStartDateTime(),
                        shift.getEndDateTime(),
                        shift.isDayOff()
                ))
                .toList();
    }

    @GetMapping("/daily/{empId}")
    @PreAuthorize("hasRole('HR') or @userCheck.isSelf(#empId, authentication.name) or @managedEmployeeCheck.canManageEmployee(#empId, authentication.name)")
    public List<DailyShiftResponse> getDailyShiftsForEmployee(
            @PathVariable Long empId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return shiftService.getDailyShiftsForEmployee(empId, startDate, endDate).stream()
                .map(shift -> new DailyShiftResponse(
                        shift.getDailyShiftId(),
                        shift.getEmployeeId(),
                        shift.getWorkDate(),
                        shift.getStartDateTime(),
                        shift.getEndDateTime(),
                        shift.isDayOff()
                ))
                .toList();
    }

    @PutMapping("/daily/override")
    @PreAuthorize("hasRole('HR') or @managedEmployeeCheck.canManageEmployee(#payload.empId, authentication.name)")
    @AuditLog
    public ResponseEntity<DailyShiftResponse> upsertDailyShiftOverride(
            @RequestBody @Validated DailyShiftOverrideRequestDto payload) {
        DailyShiftSchedule shift = shiftService.upsertDailyShiftOverride(payload);
        return ResponseEntity.ok(toDailyShiftResponse(shift));
    }

    @PutMapping("/daily/override/batch")
    @PreAuthorize("hasRole('HR') or @managedEmployeeCheck.canManageEmployee(#payload.empId, authentication.name)")
    @AuditLog
    public ResponseEntity<List<DailyShiftResponse>> upsertDailyShiftOverrides(
            @RequestBody @Validated DailyShiftOverrideRequestDto payload) {
        List<DailyShiftSchedule> shifts = shiftService.upsertDailyShiftOverrides(payload);
        return ResponseEntity.ok(shifts.stream()
                .map(this::toDailyShiftResponse)
                .toList());
    }

    private DailyShiftResponse toDailyShiftResponse(DailyShiftSchedule shift) {
        return new DailyShiftResponse(
                shift.getDailyShiftId(),
                shift.getEmployeeId(),
                shift.getWorkDate(),
                shift.getStartDateTime(),
                shift.getEndDateTime(),
                shift.isDayOff()
        );
    }

    public record DailyShiftResponse(
            Long dailyShiftId,
            Long employeeId,
            LocalDate workDate,
            LocalDateTime startDateTime,
            LocalDateTime endDateTime,
            boolean dayOff
    ) {
    }
}
