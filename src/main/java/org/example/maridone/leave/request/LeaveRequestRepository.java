package org.example.maridone.leave.request;

import org.example.maridone.core.employee.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

@Repository
public interface LeaveRequestRepository extends JpaRepository<LeaveRequest,Long> {

    //find all approved ids for the given day, given that employee is not terminated and request status is approved
    @Query("""
        SELECT lr from LeaveRequest lr
        join fetch lr.employee emp
        where emp.employmentStatus != 'TERMINATED'
        and lr.requestStatus = 'APPROVED'
        and lr.leaveDate = :leaveDate""")
    List<LeaveRequest> findApprovedLeavesForDay(@Param("leaveDate") LocalDate leaveDate);
}
