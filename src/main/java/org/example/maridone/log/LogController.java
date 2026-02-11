package org.example.maridone.log;

import org.example.maridone.log.activity.ActivityLog;
import org.example.maridone.log.attendance.AttendanceLog;
import org.example.maridone.log.dto.ActivityRequestDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController("/api/logs")
public class LogController {
    private final LogService logService;
    public LogController(LogService logService) {
        this.logService = logService;
    }


    //create ActivityLog
    @PostMapping("/activity")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void activity(@RequestBody ActivityRequestDto payload) {
        logService.activity(payload);
    }

    //create AttendanceLog
    @PostMapping("/attendance/{empId}")
    @PreAuthorize("@userCheck.isSelf(empId, authentication.getName())")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void attendance(@PathVariable Long empId, @RequestBody String direction) {
        logService.attendance(empId, direction);
    }

    @GetMapping("/attendance/{empId}")
    @PreAuthorize("hasRole('HR')")
    public Page<AttendanceLog> getAttendance(
            @PathVariable Long empId,
            @PageableDefault(size = 25, sort = "timestamp", direction = Sort.Direction.DESC) Pageable pageable) {
        return logService.getAttendance(empId, pageable);
    }


    @GetMapping("/activity/{empId}")
    @PreAuthorize("hasRole('HR')")
    public Page<ActivityLog> getActivity(
            @PathVariable Long empId,
            @PageableDefault(size = 25, sort = "timestamp", direction = Sort.Direction.DESC) Pageable pageable) {
        return logService.getActivity(empId, pageable);
    }
}
