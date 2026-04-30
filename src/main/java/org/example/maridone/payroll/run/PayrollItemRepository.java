package org.example.maridone.payroll.run;

import org.example.maridone.enums.PayrollStatus;
import org.example.maridone.enums.RunType;
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
import java.util.Optional;

@Repository
public interface PayrollItemRepository extends JpaRepository<PayrollItem, Long>, JpaSpecificationExecutor<PayrollItem> {

    List<PayrollItem> findByEmployee_EmployeeId(Long empId);

    List<PayrollItem> findByEmployee_EmployeeIdAndPayrollRun_PayrollStatusOrderByPayrollRun_PeriodEndDescItemIdDesc(
            Long empId,
            PayrollStatus payrollStatus
    );

    List<PayrollItem> findByEmployee_EmployeeIdAndPayrollRun_PayrollStatusAndPayrollRun_RunTypeNotOrderByPayrollRun_PeriodEndDescItemIdDesc(
            Long empId,
            PayrollStatus payrollStatus,
            RunType runType
    );

    List<PayrollItem> findByEmployee_EmployeeIdAndPayrollRun_RunTypeOrderByPayrollRun_PaymentDateDescPayrollRun_PeriodEndDescItemIdDesc(
            Long empId,
            RunType runType
    );

    Optional<PayrollItem> findByPayrollRun_PayIdAndEmployee_EmployeeId(Long payId, Long empId);

    Optional<PayrollItem> findByItemIdAndPayrollRun_PayrollStatus(Long itemId, PayrollStatus payrollStatus);

    Optional<PayrollItem> findByItemIdAndPayrollRun_PayrollStatusAndPayrollRun_RunTypeNot(
            Long itemId,
            PayrollStatus payrollStatus,
            RunType runType
    );

    long countByPayrollRun_PayId(Long payId);

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
          AND (:#{#filter.itemId} IS NULL OR pi.itemId = :#{#filter.itemId})
          AND (:#{#filter.firstName} IS NULL OR LOWER(e.firstName) LIKE LOWER(CONCAT('%', :#{#filter.firstName}, '%')))
          AND (:#{#filter.lastName} IS NULL OR LOWER(e.lastName) LIKE LOWER(CONCAT('%', :#{#filter.lastName}, '%')))
    """)
    Page<ItemViewDto> findSpecificDetails(
            @Param("payId") Long payId,
            @Param("filter") ItemFilter filter,
            Pageable pageable
    );

    @Query("""
    select pi.employee.employeeId from PayrollItem pi
    where pi.payrollRun.payId = :payId
""")
    List<Long> findEmployeeIdsByPayrollRun(@Param("payId") Long payId);

    List<PayrollItem> findByPayrollRun_PayId(Long payId);

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
        WHERE pi.payrollRun.payrollStatus = 'POSTED'
          AND pi.payrollRun.paymentDate >= :startDate
          AND pi.payrollRun.paymentDate <= :endDate
          AND (:#{#filter.empId} IS NULL OR e.employeeId = :#{#filter.empId})
          AND (:#{#filter.itemId} IS NULL OR pi.itemId = :#{#filter.itemId})
          AND (:#{#filter.firstName} IS NULL OR LOWER(e.firstName) LIKE LOWER(CONCAT('%', :#{#filter.firstName}, '%')))
          AND (:#{#filter.lastName} IS NULL OR LOWER(e.lastName) LIKE LOWER(CONCAT('%', :#{#filter.lastName}, '%')))
    """)
    Page<ItemViewDto> findSpecificDetailsByDateRange(
            @Param("startDate") java.time.LocalDate startDate,
            @Param("endDate") java.time.LocalDate endDate,
            @Param("filter") ItemFilter filter,
            Pageable pageable
    );

    @Query("""
        SELECT pi.employee.employeeId from PayrollItem pi
        WHERE pi.payrollRun.payrollStatus = 'POSTED'
          AND pi.payrollRun.paymentDate >= :startDate
          AND pi.payrollRun.paymentDate <= :endDate
    """)
    List<Long> findEmployeeIdsByDateRange(
            @Param("startDate") java.time.LocalDate startDate,
            @Param("endDate") java.time.LocalDate endDate
    );
}
