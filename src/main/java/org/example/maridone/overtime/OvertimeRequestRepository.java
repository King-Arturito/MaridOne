package org.example.maridone.overtime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OvertimeRequestRepository extends JpaRepository<OvertimeRequest, Long>, JpaSpecificationExecutor<OvertimeRequest> {

    @Query("""
        Select count(o) > 0 from OvertimeRequest o, UserAccount u
        where o.overtimeId = :overtimeId
        and u.username = :username
        and o.employee.employeeId = u.employee.employeeId
""")
    boolean isOvertimeOwnedByUser(@Param("overtimeId") Long overtimeId, @Param("username") String username);
}
