package org.example.maridone.leave.request;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.example.maridone.leave.dto.LeaveDetails;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface LeaveRequestRepository extends JpaRepository<LeaveRequest,Long>, JpaSpecificationExecutor<LeaveRequest> {

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

    @Query("""
        SELECT COUNT(lr) > 0
        FROM LeaveRequest lr
        WHERE lr.employee.employeeId = :employeeId
        AND lr.requestStatus NOT IN ('CANCELLED', 'REJECTED')
        AND lr.startDateTime < :endDateTime
        AND lr.endDateTime > :startDateTime
    """)
    boolean existsActiveOverlapForEmployee(
            @Param("employeeId") Long employeeId,
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime
    );

    @Query("""
        SELECT COUNT(lr) > 0
        FROM LeaveRequest lr
        WHERE lr.employee.employeeId = :employeeId
        AND lr.requestStatus NOT IN ('CANCELLED', 'REJECTED')
        AND lr.startDateTime < :endDateExclusive
        AND lr.endDateTime > :startDateTime
    """)
    boolean existsActiveOnDateRange(
            @Param("employeeId") Long employeeId,
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateExclusive") LocalDateTime endDateExclusive
    );


    @Query("SELECT COUNT(l) > 0 FROM LeaveRequest l, UserAccount u " +
            "WHERE l.requestId = :leaveRequestId " +
            "AND u.username = :username " +
            "AND l.employee.employeeId = u.employee.employeeId")
    boolean isLeaveOwnedByUser(@Param("leaveRequestId") Long leaveRequestId, @Param("username") String username);

    Page<LeaveRequest> findByEmployee_EmployeeId(Long empId, Pageable pageable);

    @Query("select lr.employee.employeeId from LeaveRequest lr where lr.requestId = :requestId")
    Optional<Long> findEmployeeIdByRequestId(@Param("requestId") Long requestId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select lr from LeaveRequest lr where lr.requestId = :requestId")
    Optional<LeaveRequest> findByIdForUpdate(@Param("requestId") Long requestId);

    @Query("""
    SELECT new org.example.maridone.leave.dto.LeaveDetails(
        lr.requestId,
        e.firstName,
        e.lastName,
        e.userAccount.username,
        lr.startDateTime,
        lr.endDateTime,
        lr.requestStatus,
        lr.reason,
        lr.approverReason,
        lr.isPaid
    )
    FROM LeaveRequest lr
    JOIN lr.employee e
    WHERE lr.requestId = :requestId
""")
    LeaveDetails getLeaveRequestDetails(@Param("requestId") Long requestId);
}
