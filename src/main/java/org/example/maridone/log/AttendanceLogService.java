package org.example.maridone.log;

import org.example.maridone.annotation.ExecutionTime;
import org.example.maridone.common.CommonSpecs;
import org.example.maridone.config.DefaultConfig;
import org.example.maridone.core.employee.Employee;
import org.example.maridone.core.employee.EmployeeRepository;
import org.example.maridone.core.user.UserAccountRepository;
import org.example.maridone.enums.EmploymentStatus;
import org.example.maridone.exception.unauthorized.UnauthorizedAccessException;
import org.example.maridone.exception.notfound.EmployeeNotFoundException;
import org.example.maridone.log.attendance.AttendanceLog;
import org.example.maridone.log.filter.AttendanceLogFilter;
import org.example.maridone.log.spec.AttendanceLogSpecs;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

@Service
public class AttendanceLogService {
    private static final LocalTime ATTENDANCE_DAY_RESET_TIME = LocalTime.of(6, 0);

    private final AttendanceLogRepository attendanceLogRepository;
    private final EmployeeRepository employeeRepository;
    private final UserAccountRepository userAccountRepository;
    private final DefaultConfig defaultConfig;

    public AttendanceLogService(AttendanceLogRepository attendanceLogRepository,
                                EmployeeRepository employeeRepository,
                                UserAccountRepository userAccountRepository,
                                DefaultConfig defaultConfig) {
        this.attendanceLogRepository = attendanceLogRepository;
        this.employeeRepository = employeeRepository;
        this.userAccountRepository = userAccountRepository;
        this.defaultConfig = defaultConfig;
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
        if (!Boolean.TRUE.equals(employee.getIsVerified())) {
            throw new UnauthorizedAccessException("Verify your account before starting attendance tracking.");
        }
        Instant timestamp = Instant.now();
        ZoneId timeZone = defaultConfig.getTimeZone();
        LocalDate attendanceDate = resolveAttendanceDate(timestamp, timeZone);
        Instant dayStart = attendanceDate.atTime(ATTENDANCE_DAY_RESET_TIME).atZone(timeZone).toInstant();
        Instant dayEnd = attendanceDate.plusDays(1).atTime(ATTENDANCE_DAY_RESET_TIME).atZone(timeZone).toInstant().minusNanos(1);
        List<AttendanceLog> todayLogs = attendanceLogRepository.findByEmployeeIdAndTimestampBetween(
                empId,
                dayStart,
                dayEnd,
                Sort.by(Sort.Order.desc("timestamp"), Sort.Order.desc("attendanceId"))
        );
        AttendanceLog attendanceLog = new AttendanceLog();
        AttendanceLog recent = todayLogs.isEmpty() ? null : todayLogs.get(0);
        String recentDirection = recent == null ? "" : String.valueOf(recent.getDirection()).trim();
        if (recent == null || recentDirection.isBlank() || "OUT".equalsIgnoreCase(recentDirection)) {
            attendanceLog.setDirection("IN");
        } else {
            attendanceLog.setDirection("OUT");
        }
        attendanceLog.setEmployeeId(empId);
        attendanceLog.setTimestamp(timestamp);
        attendanceLogRepository.save(attendanceLog);
    }

    LocalDate resolveAttendanceDate(Instant timestamp, ZoneId timeZone) {
        LocalDate attendanceDate = LocalDate.ofInstant(timestamp, timeZone);
        if (timestamp.atZone(timeZone).toLocalTime().isBefore(ATTENDANCE_DAY_RESET_TIME)) {
            return attendanceDate.minusDays(1);
        }
        return attendanceDate;
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
