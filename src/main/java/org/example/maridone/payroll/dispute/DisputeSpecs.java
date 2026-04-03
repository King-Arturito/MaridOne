package org.example.maridone.payroll.dispute;

import org.springframework.data.jpa.domain.Specification;

public class DisputeSpecs {
    public static Specification<DisputeRequest> hasUsername(String username) {
        return ((root, query, cb) -> {
            if (username == null) {
                return cb.conjunction();
            }
            return cb.equal(root.get("payrollItem").get("employee").get("username"), username);
        });
    }
}
