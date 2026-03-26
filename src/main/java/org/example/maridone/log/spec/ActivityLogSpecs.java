package org.example.maridone.log.spec;

import org.example.maridone.log.activity.ActivityLog;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

public class ActivityLogSpecs {
    public static Specification<ActivityLog> handledEmployees(List<Long> handledEmployeeIds) {
        return (root, query, cb) -> {
            if (handledEmployeeIds.isEmpty()) {
                return cb.conjunction();
            }
            return root.get("employeeId").in(handledEmployeeIds);
        };
    }
}
