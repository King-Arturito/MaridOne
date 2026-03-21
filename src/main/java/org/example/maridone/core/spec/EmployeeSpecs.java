package org.example.maridone.core.spec;

import jakarta.persistence.criteria.Predicate;
import org.example.maridone.core.employee.Employee;
import org.example.maridone.core.filter.EmployeeFilter;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class EmployeeSpecs {
    public static Specification<Employee> hasFilters(EmployeeFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter.firstName() != null) {
                predicates.add(cb.like(
                        cb.lower(
                                root.get("firstName")), "%" + filter.firstName().toLowerCase() + "%"));
            }
            if (filter.lastName() != null) {
                predicates.add(cb.like(
                        cb.lower(
                                root.get("lastName")), "%" + filter.lastName().toLowerCase() + "%"));
            }
            if (filter.middleName() != null) {
                predicates.add(cb.like(
                        cb.lower(
                                root.get("middleName")), "%" + filter.middleName().toLowerCase() + "%"));
            }
            if (filter.email() != null) {
                predicates.add(cb.like(
                        cb.lower(
                                root.get("email")), "%" + filter.email().toLowerCase() + "%"));
            }
            if (filter.employeeId() != null) {
                predicates.add(cb.equal(root.get("employeeId"), filter.employeeId()));
            }
            if (filter.hiredDateStart() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("hiredDateStart"), filter.hiredDateStart()));
            }
            if (filter.hiredDateEnd() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("hiredDateStart"), filter.hiredDateEnd()));
            }
            if (filter.positionList() != null && !filter.positionList().isEmpty()) {
                predicates.add(root.get("position").in(filter.positionList()));
            }
            if (filter.employmentStatusList() != null && !filter.employmentStatusList().isEmpty()) {
                predicates.add(root.get("employmentStatus").in(filter.employmentStatusList()));
            }
            if (filter.exemptionStatus() != null) {
                predicates.add(cb.equal(root.get("exemptionStatus"), filter.exemptionStatus()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<Employee> hasUserAccount(String username) {
        return (root, query, cb) -> cb.equal(root.get("userAccount").get("username"), username);
    }
}
