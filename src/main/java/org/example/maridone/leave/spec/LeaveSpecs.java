package org.example.maridone.leave.spec;

import jakarta.persistence.criteria.JoinType;
import org.example.maridone.enums.LeaveType;
import org.example.maridone.leave.balance.LeaveBalance;
import org.springframework.data.jpa.domain.Specification;

public class LeaveSpecs {
    public static Specification<LeaveBalance> hasEmployeeId(Long id) {
        return (root, query, cb) -> {
            if (id == null) {
                return cb.conjunction();
            }
            if (query.getResultType() != Long.class && query.getResultType() != Integer.class) {
                root.fetch("employee", JoinType.LEFT);
            }
            return cb.equal(
                    root
                            .join("employee")
                            .get("employeeId"), id
            );
        };
    }
}
