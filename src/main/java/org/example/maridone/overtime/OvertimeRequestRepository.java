package org.example.maridone.overtime;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OvertimeRequestRepository extends JpaRepository<OvertimeRequest, Long>, JpaSpecificationExecutor<OvertimeRequest> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OvertimeRequest o where o.overtimeId = :overtimeId")
    java.util.Optional<OvertimeRequest> findByIdForUpdate(@Param("overtimeId") Long overtimeId);

    @Query("""
        Select count(o) > 0 from OvertimeRequest o, UserAccount u
        where o.overtimeId = :overtimeId
        and u.username = :username
        and o.employee.employeeId = u.employee.employeeId
""")
    boolean isOvertimeOwnedByUser(@Param("overtimeId") Long overtimeId, @Param("username") String username);

    @Query("""
        SELECT COUNT(o) > 0
        FROM OvertimeRequest o
        WHERE o.employee.employeeId = :employeeId
        AND o.requestStatus NOT IN ('CANCELLED', 'REJECTED')
        AND o.startTime < :endDateTime
        AND o.endTime > :startDateTime
    """)
    boolean existsActiveOverlapForEmployee(
            @Param("employeeId") Long employeeId,
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime
    );

    @Query("""
        SELECT COUNT(o) > 0
        FROM OvertimeRequest o
        WHERE o.employee.employeeId = :employeeId
        AND o.requestStatus NOT IN ('CANCELLED', 'REJECTED')
        AND o.workDate >= :startDate
        AND o.workDate <= :endDate
    """)
    boolean existsActiveOnWorkDateRange(
            @Param("employeeId") Long employeeId,
            @Param("startDate") java.time.LocalDate startDate,
            @Param("endDate") java.time.LocalDate endDate
    );

    @Query("""
        SELECT o from OvertimeRequest o
        where o.requestStatus = 'APPROVED'
        and o.employee.employeeId IN :employeeIds
        and o.workDate >= :startDate
        and o.workDate <= :endDate""")
    java.util.List<OvertimeRequest> findApprovedOvertimeForPeriod(
            @Param("employeeIds") java.util.List<Long> employeeIds,
            @Param("startDate") java.time.LocalDate startDate,
            @Param("endDate") java.time.LocalDate endDate
    );
}
