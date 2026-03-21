package org.example.maridone.core.employee;


import org.example.maridone.enums.EmploymentStatus;
import org.example.maridone.enums.Position;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long>, JpaSpecificationExecutor<Employee> {


    @Query("""
        select e.employeeId from Employee e
            where e.position = :position
            and e.employmentStatus != :employmentStatus
""")
    List<Long> findEmployeeIdsByPosition(@Param("position") Position position,
                                         @Param("employmentStatus") EmploymentStatus employmentStatus);
}
