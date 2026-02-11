package org.example.maridone.log;

import org.example.maridone.common.CommonSpecs;
import org.example.maridone.core.employee.EmployeeRepository;
import org.example.maridone.exception.EmployeeNotFoundException;
import org.example.maridone.log.activity.ActivityLog;
import org.example.maridone.log.attendance.AttendanceLog;
import org.example.maridone.log.dto.ActivityRequestDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class LogService {

    private final ActivityLogRepository activityLogRepository;
    private final AttendanceLogRepository attendanceLogRepository;
    private final EmployeeRepository employeeRepository;

    public LogService(ActivityLogRepository activityLogRepository,
                      AttendanceLogRepository attendanceLogRepository,
                      EmployeeRepository employeeRepository)
    {
        this.activityLogRepository = activityLogRepository;
        this.attendanceLogRepository = attendanceLogRepository;
        this.employeeRepository = employeeRepository;
    }


    @Transactional
    public void attendance(Long empId, String direction) {
        employeeRepository.findById(empId).orElseThrow(
                () -> new EmployeeNotFoundException(empId));
        AttendanceLog attendanceLog = new AttendanceLog();
        attendanceLog.setEmployeeId(empId);
        attendanceLog.setDirection(direction);
        attendanceLog.setTimestamp(Instant.now());
        attendanceLogRepository.save(attendanceLog);
    }

    @Transactional
    public void activity(ActivityRequestDto payload) {
        ActivityLog activityLog = new ActivityLog();
        activityLog.setEmployeeId(payload.getEmployeeId());
        activityLog.setMessage(payload.getMessage());
        activityLog.setActivityType(payload.getActivityType());
        activityLog.setTimestamp(Instant.now());
        activityLogRepository.save(activityLog);
    }

    public Page<AttendanceLog> getAttendance(Long empId, Pageable pageable) {
        Specification<AttendanceLog> spec = Specification.allOf(
                CommonSpecs.fieldEquals("employeeId", empId)
        );
        return attendanceLogRepository.findAll(spec, pageable);
    }

    public Page<ActivityLog> getActivity(Long empId, Pageable pageable) {
        Specification<ActivityLog> spec = Specification.allOf(
                CommonSpecs.fieldEquals("employeeId", empId)
        );

        return activityLogRepository.findAll(spec, pageable);
    }
}
