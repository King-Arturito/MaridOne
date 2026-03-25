package org.example.maridone.leave.spec;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.example.maridone.enums.LeaveType;
import org.example.maridone.leave.LeaveFilter;
import org.example.maridone.leave.balance.LeaveBalance;
import org.example.maridone.leave.request.LeaveRequest;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class LeaveSpecs {
    public static Specification<LeaveRequest> hasFilters(LeaveFilter filter)  {
        return(root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
          if (filter.isPaid() != null) {
              predicates.add(cb.equal(root.get("isPaid"), filter.isPaid()));
          }
          if (filter.endDateTime() != null) {
              predicates.add(cb.lessThanOrEqualTo(root.get("endDateTime"), filter.endDateTime()));
          }
          if (filter.startDateTime() != null) {
              predicates.add(cb.greaterThanOrEqualTo(root.get("startDateTime"), filter.startDateTime()));
          }
          if (filter.requestStatus() != null) {
              predicates.add(cb.equal(root.get("requestStatus"), filter.requestStatus()));
          }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
    /*
        LocalDateTime startDateTime,
        LocalDateTime endDateTime,
        Status requestStatus,
        String reason,
        Boolean isPaid) {
     */
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
