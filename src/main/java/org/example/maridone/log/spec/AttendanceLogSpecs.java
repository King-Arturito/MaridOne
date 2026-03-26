package org.example.maridone.log.spec;


import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.example.maridone.core.employee.Employee;
import org.example.maridone.log.attendance.AttendanceLog;
import org.example.maridone.log.filter.AttendanceLogFilter;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class AttendanceLogSpecs {
    public static Specification<AttendanceLog> filtered(AttendanceLogFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter.attendanceId() != null) {
                predicates.add(cb.equal(root.get("attendanceId"), filter.attendanceId()));
            }
            if (filter.direction() != null) {
                predicates.add(cb.equal(root.get("direction"), filter.direction()));
            }
            if (filter.employeeId() != null) {
                predicates.add(cb.equal(root.get("employeeId"), filter.employeeId()));
            }
            if (filter.timestampAfter() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("timestamp"), filter.timestampAfter()));
            }
            if (filter.timestampBefore() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("timestamp"), filter.timestampBefore()));
            }

            if (filter.firstName() != null || filter.lastName() != null || filter.middleName() != null) {

                assert query != null;
                Subquery<Long> subquery = query.subquery(Long.class);
                Root<Employee> empRoot = subquery.from(Employee.class);
                subquery.select(empRoot.get("employeeId"));

                List<Predicate> empPredicates = new ArrayList<>();

                if (filter.firstName() != null && !filter.firstName().trim().isEmpty()) {
                    empPredicates.add(cb.like(
                            cb.lower(empRoot.get("firstName")),
                            "%" + filter.firstName().toLowerCase() + "%"
                    ));
                }
                if (filter.lastName() != null && !filter.lastName().trim().isEmpty()) {
                    empPredicates.add(cb.like(
                            cb.lower(empRoot.get("lastName")),
                            "%" + filter.lastName().toLowerCase() + "%"
                    ));
                }
                if (filter.middleName() != null && !filter.middleName().trim().isEmpty()) {
                    empPredicates.add(cb.like(
                            cb.lower(empRoot.get("middleName")),
                            "%" + filter.middleName().toLowerCase() + "%"
                    ));
                }

                subquery.where(cb.and(empPredicates.toArray(new Predicate[0])));
                predicates.add(root.get("employeeId").in(subquery));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<AttendanceLog> handledEmployees(List<Long> handledEmployeeIds) {
        return (root, query, cb) -> {
            if (handledEmployeeIds == null || handledEmployeeIds.isEmpty()) {
                return cb.conjunction();
            }
            return root.get("employeeId").in(handledEmployeeIds);
        };
    }
}
