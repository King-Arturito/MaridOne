package org.example.maridone.log.spec;

import org.example.maridone.log.attendance.AttendanceLog;
import org.springframework.data.jpa.domain.Specification;

public class AttendanceLogSpecs {

    public static Specification<AttendanceLog> hasEmployeeId(Long employeeId) {
        return (root, query, cb) -> {
            if (employeeId == null) {
                return cb.conjunction();
            }

            return cb.equal(root.get("employeeId"), employeeId);
        };
    }
}
