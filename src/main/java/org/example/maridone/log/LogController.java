package org.example.maridone.log;

import org.example.maridone.log.activity.ActivityLog;
import org.example.maridone.log.filter.ActivityLogFilter;
import org.example.maridone.log.attendance.AttendanceLog;
import org.example.maridone.log.dto.ActivityRequestDto;
import org.example.maridone.log.dto.AttendanceImportSummaryDto;
import org.example.maridone.log.filter.AttendanceLogFilter;
import org.example.maridone.log.timecard.TimecardService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/logs")
public class LogController {
    private final ActivityLogService activityLogService;
    private final AttendanceLogService attendanceLogService;
    private final AttendanceImportService attendanceImportService;
    private final AttendanceReportService attendanceReportService;
    private final TimecardService timecardService;

    public LogController(ActivityLogService activityLogService,
                         AttendanceLogService attendanceLogService,
                         AttendanceImportService attendanceImportService,
                         AttendanceReportService attendanceReportService,
                         TimecardService timecardService) {
        this.activityLogService = activityLogService;
        this.attendanceLogService = attendanceLogService;
        this.attendanceImportService = attendanceImportService;
        this.attendanceReportService = attendanceReportService;
        this.timecardService = timecardService;
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

    @GetMapping(value = "/attendance/report/export", produces = "text/csv")
    @PreAuthorize("hasAnyRole('HR', 'MANAGEMENT')")
    public ResponseEntity<byte[]> exportAttendanceReport(
            @RequestParam("from") java.time.LocalDate from,
            @RequestParam("to") java.time.LocalDate to,
            @RequestParam(required = false) Long empId) {
        byte[] csv = attendanceReportService.exportAttendanceReport(from, to, empId);
        String filename = "attendance-report-" + from + "-to-" + to + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
    }

    @GetMapping(value = "/attendance/team/report/export", produces = "text/csv")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<byte[]> exportTeamAttendanceReport(
            Authentication authentication,
            @RequestParam("from") java.time.LocalDate from,
            @RequestParam("to") java.time.LocalDate to,
            @RequestParam(required = false) Long empId) {
        byte[] csv = attendanceReportService.exportTeamAttendanceReport(from, to, empId, authentication.getName());
        String filename = "attendance-team-report-" + from + "-to-" + to + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
    }

    @GetMapping("/attendance/self/{empId}")
    @PreAuthorize("@userCheck.isSelf(#empId, authentication.getName())")
    public Page<AttendanceLog> getMyAttendance(
            @PathVariable Long empId,
            AttendanceLogFilter filter,
            @PageableDefault(size = 25, sort = "timestamp", direction = Sort.Direction.DESC) Pageable pageable) {
        return attendanceLogService.getAttendance(empId, filter, pageable);
    }

    @GetMapping("/attendance/self/{empId}/timecard/pdf")
    @PreAuthorize("@userCheck.isSelf(#empId, authentication.getName())")
    public ResponseEntity<byte[]> getMyTimecardPdf(
            @PathVariable Long empId,
            @RequestParam("from") LocalDate from,
            @RequestParam("to") LocalDate to) {
        TimecardService.GeneratedTimecardPdf pdf = timecardService.generateTimecardPdf(empId, from, to);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + pdf.fileName() + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf.content());
    }


    @GetMapping("/activity")
    @PreAuthorize("hasRole('HR')")
    public Page<ActivityLog> getActivity(
            @RequestParam(required = false) Long empId,
            @RequestParam(required = false) java.time.Instant startDate,
            @RequestParam(required = false) java.time.Instant endDate,
            @PageableDefault(size = 25, sort = "timestamp", direction = Sort.Direction.DESC) Pageable pageable) {
        return activityLogService.getActivity(
                empId,
                new ActivityLogFilter(null, empId, null, null, null, startDate, endDate),
                pageable
        );
    }
}
