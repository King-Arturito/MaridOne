package org.example.maridone.log;

import org.example.maridone.annotation.ExecutionTime;
import org.example.maridone.common.CommonSpecs;
import org.example.maridone.core.employee.Employee;
import org.example.maridone.core.employee.EmployeeRepository;
import org.example.maridone.core.user.UserAccountRepository;
import org.example.maridone.enums.EmploymentStatus;
import org.example.maridone.exception.badrequest.UnauthorizedAccessException;
import org.example.maridone.exception.notfound.EmployeeNotFoundException;
import org.example.maridone.log.attendance.AttendanceLog;
import org.example.maridone.log.filter.AttendanceLogFilter;
import org.example.maridone.log.spec.AttendanceLogSpecs;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class AttendanceLogService {

    private final AttendanceLogRepository attendanceLogRepository;
    private final EmployeeRepository employeeRepository;
    private final UserAccountRepository userAccountRepository;

    public AttendanceLogService(AttendanceLogRepository attendanceLogRepository,
                                EmployeeRepository employeeRepository,
                                UserAccountRepository userAccountRepository) {
        this.attendanceLogRepository = attendanceLogRepository;
        this.employeeRepository = employeeRepository;
        this.userAccountRepository = userAccountRepository;
    }

    @Transactional
    public void attendance(Long empId) {
        Employee employee = employeeRepository.findById(empId)
                .orElseThrow(() -> new EmployeeNotFoundException(empId));
        if (employee.getEmploymentStatus() == EmploymentStatus.SUSPENDED) {
            throw new UnauthorizedAccessException("You are currently suspended and cannot log attendance.");
        }
        if (employee.getEmploymentStatus() == EmploymentStatus.TERMINATED) {
            throw new UnauthorizedAccessException("You may not log attendance. Your account has been terminated.");
        }
        AttendanceLog attendanceLog = new AttendanceLog();
        AttendanceLog recent = attendanceLogRepository.findTopByEmployeeIdOrderByTimestampDescAttendanceIdDesc(empId);
        if (recent == null || "OUT".equals(recent.getDirection())) {
            attendanceLog.setDirection("IN");
        } else {
            attendanceLog.setDirection("OUT");
        }
        attendanceLog.setEmployeeId(empId);
        attendanceLog.setTimestamp(Instant.now());
        attendanceLogRepository.save(attendanceLog);
    }

    @ExecutionTime
    public Page<AttendanceLog> getAttendance(Long empId, AttendanceLogFilter filter, Pageable pageable) {
        Specification<AttendanceLog> spec = Specification.allOf(
                CommonSpecs.fieldEquals("employeeId", empId),
                filter == null ? null : AttendanceLogSpecs.filtered(filter)
        );
        return attendanceLogRepository.findAll(spec, pageable);
    }

    @ExecutionTime
    public Page<AttendanceLog> getAttendanceTeam(AttendanceLogFilter filter, String username, Pageable pageable) {
        List<Long> handledIds = employeeRepository.findHandledEmployeeIds(userAccountRepository.findEmployeeIdByUsername(username));
        Specification<AttendanceLog> spec = Specification.allOf(
                AttendanceLogSpecs.filtered(filter)
        );
        if (!handledIds.isEmpty()) {
            spec = spec.and(AttendanceLogSpecs.handledEmployees(handledIds));
        }

        return attendanceLogRepository.findAll(spec, pageable);
    }
}
