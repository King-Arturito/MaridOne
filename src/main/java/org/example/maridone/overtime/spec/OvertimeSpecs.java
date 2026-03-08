package org.example.maridone.overtime.spec;

import jakarta.persistence.criteria.JoinType;
import org.example.maridone.enums.ExemptionStatus;
import org.example.maridone.enums.Status;
import org.example.maridone.exception.unauthorized.InvalidRangeException;
import org.example.maridone.overtime.OvertimeRequest;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;

public class OvertimeSpecs {
    public static Specification<OvertimeRequest> hasEmployeeId(Long empId) {
        return (root, query, cb) -> {
            if (empId == null) {
                return cb.conjunction();
            }
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("employee", JoinType.LEFT);
            }
            return cb.equal(root.join("employee").get("employeeId"), empId);
        };
    }

    public static Specification<OvertimeRequest> hasEmployeeIds(List<Long> empIds) {
        return (root, query, cb) -> {
            if (empIds == null || empIds.isEmpty()) {
                return cb.disjunction();
            }
            return root.join("employee", JoinType.INNER).get("employeeId").in(empIds);
        };
    }

    public static Specification<OvertimeRequest> hasExemptionStatus(ExemptionStatus exemptionStatus) {
        return (root, query, cb) -> {
            if (exemptionStatus == null) {
                return cb.conjunction();
            }
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("employee", JoinType.LEFT).fetch("exemptionStatus", JoinType.LEFT);
            }
            return cb.equal(root.join("employee").get("exemptionStatus"), exemptionStatus);
        };
    }

    public static Specification<OvertimeRequest> hasStatus(Status status) {
        return (root, query, cb) -> cb.equal(root.get("requestStatus"), status);
    }

    public static Specification<OvertimeRequest> hasOvertimeId(Long overtimeId) {
        return (root, query, cb) -> cb.equal(root.get("overtimeId"), overtimeId);
    }

    public static Specification<OvertimeRequest> checkOverlaps(LocalDateTime start, LocalDateTime end) {
        return (root, query, cb) -> {

            if (start.isAfter(end)) {
                throw new InvalidRangeException("Start time must be less than End time.");
            }
            return cb.and(
                    cb.lessThan(root.get("startTime"), end),
                    cb.greaterThan(root.get("endTime"), start)
            );
        };
    }
}
