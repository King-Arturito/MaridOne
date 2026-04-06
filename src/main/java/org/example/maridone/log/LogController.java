package org.example.maridone.log;

import org.example.maridone.log.activity.ActivityLog;
import org.example.maridone.log.attendance.AttendanceLog;
import org.example.maridone.log.dto.ActivityRequestDto;
import org.example.maridone.log.dto.AttendanceImportSummaryDto;
import org.example.maridone.log.filter.AttendanceLogFilter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/logs")
public class LogController {
    private final ActivityLogService activityLogService;
    private final AttendanceLogService attendanceLogService;
    private final AttendanceImportService attendanceImportService;

    public LogController(ActivityLogService activityLogService,
                         AttendanceLogService attendanceLogService,
                         AttendanceImportService attendanceImportService) {
        this.activityLogService = activityLogService;
        this.attendanceLogService = attendanceLogService;
        this.attendanceImportService = attendanceImportService;
    }


    //create ActivityLog
    @PostMapping("/activity")
    @PreAuthorize("@userCheck.isSelf(#payload.employeeId, authentication.getName())")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void activity(@RequestBody ActivityRequestDto payload) {
        activityLogService.activity(payload);
    }

    //create AttendanceLog
    @PostMapping("/attendance/{empId}")
    @PreAuthorize("@userCheck.isSelf(#empId, authentication.getName())")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void attendance(@PathVariable Long empId) {
        attendanceLogService.attendance(empId);
    }

    @PostMapping(value = "/attendance/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<AttendanceImportSummaryDto> importAttendance(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(attendanceImportService.importAttendanceCsv(file));
    }

    @GetMapping("/attendance")
    @PreAuthorize("hasAnyRole('HR', 'MANAGEMENT')")
    public Page<AttendanceLog> getAttendance(
            @RequestParam(required = false) Long empId,
            AttendanceLogFilter filter,
            @PageableDefault(size = 25, sort = "timestamp", direction = Sort.Direction.DESC) Pageable pageable) {
        return attendanceLogService.getAttendance(empId, filter, pageable);
    }

    @GetMapping("/attendance/team")
    @PreAuthorize("hasRole('MANAGER')")
    public Page<AttendanceLog> getAttendanceTeam(
            Authentication authentication,
            AttendanceLogFilter filter,
            @PageableDefault(size = 25, sort = "timestamp", direction = Sort.Direction.DESC) Pageable pageable) {

        return attendanceLogService.getAttendanceTeam(filter, authentication.getName(), pageable);
    }

    @GetMapping("/attendance/self/{empId}")
    @PreAuthorize("@userCheck.isSelf(#empId, authentication.getName())")
    public Page<AttendanceLog> getMyAttendance(
            @PathVariable Long empId,
            AttendanceLogFilter filter,
            @PageableDefault(size = 25, sort = "timestamp", direction = Sort.Direction.DESC) Pageable pageable) {
        return attendanceLogService.getAttendance(empId, filter, pageable);
    }


    @GetMapping("/activity")
    @PreAuthorize("hasRole('HR')")
    public Page<ActivityLog> getActivity(
            @RequestParam(required = false) Long empId,
            @PageableDefault(size = 25, sort = "timestamp", direction = Sort.Direction.DESC) Pageable pageable) {
        return activityLogService.getActivity(empId, pageable);
    }
}
