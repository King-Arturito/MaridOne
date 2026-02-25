package org.example.maridone.log;

import org.example.maridone.log.attendance.AttendanceLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import org.springframework.data.domain.Sort;
import java.util.Collection;
import java.util.List;

@Repository
public interface AttendanceLogRepository extends JpaRepository<AttendanceLog, Long>, JpaSpecificationExecutor<AttendanceLog> {
    List<AttendanceLog> findByEmployeeIdIn(List<Long> employeeIds);

    AttendanceLog findTopByEmployeeIdOrderByAttendanceIdDesc(Long empId);

    List<AttendanceLog> findByEmployeeIdInAndTimestampBetween(Collection<Long> employeeId, Instant timestamp, Instant timestamp2, Sort sort);
}
