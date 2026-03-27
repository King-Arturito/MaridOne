package org.example.maridone.payroll.run;

import org.example.maridone.payroll.dto.ItemViewDto;
import org.example.maridone.payroll.item.ItemFilter;
import org.example.maridone.payroll.item.PayrollItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PayrollItemRepository extends JpaRepository<PayrollItem, Long>, JpaSpecificationExecutor<PayrollItem> {

    List<PayrollItem> findByEmployee_EmployeeId(Long empId);

    @Query("""
        SELECT new org.example.maridone.payroll.dto.ItemViewDto(
            e.employeeId, 
            e.firstName, 
            e.lastName, 
            pi.itemId, 
            pi.grossPay, 
            pi.netPay
        )
        FROM PayrollItem pi
        JOIN pi.employee e
        WHERE pi.payrollRun.payId = :payId
          AND (:#{#filter.empId} IS NULL OR e.employeeId = :#{#filter.empId})
          AND (:#{#filter.itemId} IS NULL OR pi.employee.employeeId = :#{#filter.itemId})
          AND (:#{#filter.firstName} IS NULL OR LOWER(e.firstName) LIKE LOWER(CONCAT('%', :#{#filter.firstName}, '%')))
          AND (:#{#filter.lastName} IS NULL OR LOWER(e.lastName) LIKE LOWER(CONCAT('%', :#{#filter.lastName}, '%')))
    """)
    Page<ItemViewDto> findSpecificDetails(
            @Param("payId") Long payId,
            @Param("filter") ItemFilter filter,
            Pageable pageable
    );
}
