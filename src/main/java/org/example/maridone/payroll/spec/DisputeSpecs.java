package org.example.maridone.payroll.spec;

import jakarta.persistence.criteria.JoinType;
import org.example.maridone.enums.Status;
import org.example.maridone.payroll.dispute.DisputeRequest;
import org.springframework.data.jpa.domain.Specification;

public class DisputeSpecs {

    public static Specification<DisputeRequest> hasEmployeeId(Long empId) {
        return (root, query, cb) -> {
            if (empId == null) {
                return cb.conjunction();
            }
            if (query.getResultType() != Long.class &&  query.getResultType() != long.class) {
                root.fetch("payrollItem", JoinType.LEFT).fetch("employee", JoinType.LEFT);
            }
            return cb.equal(
                    root
                            .join("payrollItem")
                            .join("employee")
                            .get("employeeId"), empId);
        };
    }
}
