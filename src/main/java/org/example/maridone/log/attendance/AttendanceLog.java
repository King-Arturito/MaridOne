package org.example.maridone.log.attendance;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import org.example.maridone.core.employee.Employee;

import java.time.Instant;
import java.time.LocalDateTime;

@Entity
public class AttendanceLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "attendance_id", nullable = false)
    private Long attendanceId;

    @Column(name = "emp_id", nullable = false)
    private Long employeeId;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    @Column(name = "direction", nullable = false)
    private String direction;

    public Long getAttendanceId() {
        return attendanceId;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(Long employeeId) {
        this.employeeId = employeeId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }
}
