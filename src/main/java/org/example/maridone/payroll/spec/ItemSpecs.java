package org.example.maridone.payroll.spec;

import jakarta.persistence.criteria.JoinType;
import org.example.maridone.payroll.item.PayrollItem;
import org.springframework.data.jpa.domain.Specification;

public class ItemSpecs {

    public static Specification<PayrollItem> hasEmployeeId(Long empId) {
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
}
