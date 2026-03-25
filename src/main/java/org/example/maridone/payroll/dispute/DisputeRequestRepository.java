package org.example.maridone.payroll.dispute;

import org.example.maridone.enums.Status;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DisputeRequestRepository extends JpaRepository<DisputeRequest, Long>, JpaSpecificationExecutor<DisputeRequest> {
    Page<DisputeRequest> findAllByStatus(Status status, Pageable pageable);

    @Query("""
        select d from DisputeRequest d
        Join fetch d.payrollItem p
        Join fetch p.employee e
        where d.disputeId = :disputeId
""")
    Optional<DisputeRequest> findByIdWithEmployee(@Param("disputeId") Long disputeId);

    Page<DisputeRequest> findAllByPayrollItem_Employee_EmployeeIdAndStatus(Long employeeId, Status status, Pageable pageable);

    Page<DisputeRequest> findAllByPayrollItem_Employee_EmployeeId(Long employeeId, Pageable pageable);

    Page<DisputeRequest> findByPayrollItem_ItemId(Long itemId, Pageable pageable);

    DisputeRequest findTopByPayrollItem_ItemIdOrderByDisputeIdDesc(Long itemId);

    @Query("""
        select count(d) > 0 from DisputeRequest d, UserAccount u
        where d.disputeId = :disputeId
        and u.username = :username
        and d.payrollItem.employee.employeeId = u.employee.employeeId
""")
    boolean isDisputeOwnedByUser(@Param("disputeId") Long disputeId, @Param("username")  String username);
}
