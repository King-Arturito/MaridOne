package org.example.maridone.payroll.dispute;

import org.example.maridone.enums.Status;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DisputeRequestRepository extends JpaRepository<DisputeRequest, Long>, JpaSpecificationExecutor<DisputeRequest> {
    Page<DisputeRequest> findAllByStatus(Status status, Pageable pageable);

    Page<DisputeRequest> findAllByPayrollItem_Employee_EmployeeIdAndStatus(Long employeeId, Status status, Pageable pageable);

    Page<DisputeRequest> findAllByPayrollItem_Employee_EmployeeId(Long employeeId, Pageable pageable);

    Page<DisputeRequest> findByPayrollItem_ItemId(Long itemId, Pageable pageable);

    DisputeRequest findTopByPayrollItem_ItemIdOrderByDisputeIdDesc(Long itemId);
}
