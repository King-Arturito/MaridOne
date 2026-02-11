package org.example.maridone.log;

import org.example.maridone.log.attendance.AttendanceLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AttendanceLogRepository extends JpaRepository<AttendanceLog, Long>, JpaSpecificationExecutor<AttendanceLog> {
    List<AttendanceLog> findByEmployeeIdIn(List<Long> employeeIds);
}
