package org.example.maridone.core.employee;


import org.example.maridone.enums.EmploymentStatus;
import org.example.maridone.enums.Position;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long>, JpaSpecificationExecutor<Employee> {


    @Query("""
        select e.employeeId from Employee e
            where e.position = :position
            and e.employmentStatus != :employmentStatus
""")
    List<Long> findEmployeeIdsByPosition(@Param("position") Position position,
                                         @Param("employmentStatus") EmploymentStatus employmentStatus);


    @Query("SELECT e.employeeId FROM Employee e WHERE e.manager.employeeId = :managerId")
    List<Long> findHandledEmployeeIds(@Param("managerId") Long managerId);

    List<Employee> findByEmploymentStatusNotOrderByLastNameAscFirstNameAsc(EmploymentStatus employmentStatus);

    List<Employee> findByPositionAndEmploymentStatusNotOrderByLastNameAscFirstNameAsc(
            Position position,
            EmploymentStatus employmentStatus
    );

    @Query(
            "select e.employeeId from Employee e " +
                    "where e.position != 'ACCOUNTING' " +
                    "and e.employmentStatus != 'TERMINATED' " +
                    "and e.employmentStatus != 'SUSPENDED' " +
                    "and e.employeeId != :authorId " +
                    "and e.userAccount is not null"
    )
    List<Long> findIdsbyStatusAndPosition(@Param("authorId") Long authorId);

    List<Employee> findByManager_EmployeeIdAndEmploymentStatusNotOrderByLastNameAscFirstNameAsc(
            Long managerId,
            EmploymentStatus employmentStatus
    );

    Optional<Employee> findFirstByEmpTitleAndPositionAndEmploymentStatusNot(
            org.example.maridone.enums.ManagerTitle empTitle,
            Position position,
            EmploymentStatus employmentStatus
    );

    boolean existsByEmployeeIdAndManager_EmployeeId(Long employeeId, Long managerId);

    @Query("""
        SELECT e
        FROM Employee e
        WHERE e.employmentDateStart <= :periodEnd
          AND (e.employmentDateEnd IS NULL OR e.employmentDateEnd >= :periodStart)
          AND (
                e.employmentStatus <> :excludedStatus
                OR e.employmentDateEnd IS NOT NULL
          )
    """)
    List<Employee> findPayrollEligibleEmployees(
            @Param("periodStart") LocalDate periodStart,
            @Param("periodEnd") LocalDate periodEnd,
            @Param("excludedStatus") EmploymentStatus excludedStatus
    );
}
