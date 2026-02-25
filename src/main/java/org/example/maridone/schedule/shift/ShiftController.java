package org.example.maridone.schedule.shift;

import org.example.maridone.marker.OnCreate;
import org.example.maridone.marker.OnUpdate;
import org.example.maridone.schedule.dto.ShiftRequestDto;
import org.example.maridone.schedule.dto.ShiftResponseDto;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
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
    @PreAuthorize("hasRole('HR')")
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
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<TemplateShiftSchedule> updateShiftSchedule(
            @RequestBody @Validated(OnUpdate.class) ShiftRequestDto payload) {
        TemplateShiftSchedule schedule = shiftService.updateShiftSchedule(payload);
        return ResponseEntity.ok(schedule);
    }
    //shift change request

    //get employee shifts
    @PreAuthorize("@userCheck.isSelf(#empId, Authentication.getName())")
    @GetMapping("/{empId}")
    public List<ShiftResponseDto> getShiftSchedule(
            @PathVariable Long empId) {
        return shiftService.getShiftSchedule(empId);
    }
}
