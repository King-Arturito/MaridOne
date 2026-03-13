package org.example.maridone.leave.request;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface LeaveRequestRepository extends JpaRepository<LeaveRequest,Long> {

    //find all approved ids for the given day, given that employee is not terminated and request status is approved
    @Query("""
        SELECT lr from LeaveRequest lr
        join fetch lr.employee emp
        where emp.employmentStatus != 'TERMINATED'
        and lr.requestStatus = 'APPROVED'
        and lr.startDateTime < :endOfDay
        and lr.endDateTime > :startOfDay""")
    List<LeaveRequest> findApprovedLeavesForDay(@Param("startOfDay") LocalDateTime startOfDay, @Param("endOfDay")  LocalDateTime endOfDay);

    @Query("""
        SELECT lr from LeaveRequest lr
        join fetch lr.employee emp
        where emp.employmentStatus != 'TERMINATED'
        and lr.requestStatus = 'APPROVED'
        and emp.employeeId IN :employeeIds
        and lr.startDateTime < :endOfDay
        and lr.endDateTime > :startOfDay""")
    List<LeaveRequest> findApprovedLeavesForPeriod(
            @Param("employeeIds") List<Long> employeeIds, 
            @Param("startOfDay") LocalDateTime startOfDay,
            @Param("endOfDay") LocalDateTime endOfDay
    );
}
