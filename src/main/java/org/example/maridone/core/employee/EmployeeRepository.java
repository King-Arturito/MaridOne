package org.example.maridone.core.employee;


import org.example.maridone.enums.EmploymentStatus;
import org.example.maridone.enums.Position;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long>, JpaSpecificationExecutor<Employee> {
    List<Employee> findByEmployeeIdIn(List<Long> employeeIds);
}
