package org.example.maridone.payroll.item.component;

import org.example.maridone.payroll.projection.PayTypeSummaryProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeductionsRepository extends JpaRepository<DeductionsLine, Long> {

    List<DeductionsLine> findByPayrollItem_ItemId(Long itemId);

    List<DeductionsLine> findByPayrollItem_ItemIdIn(List<Long> itemIds);

    @Query(value = """
    SELECT
        d.deduction_type AS type,
        SUM(d.amount) AS totalAmount
    FROM deductions_line d
    JOIN payroll_item pi ON d.item_id = pi.item_id
    WHERE pi.pay_id = :payId
    GROUP BY d.deduction_type
""", nativeQuery = true)
    List<PayTypeSummaryProjection> getSummarizedDeductions(@Param("payId") Long payId);
    @Query("""
        SELECT dl.deductionType AS type, SUM(dl.amount) AS totalAmount
        FROM DeductionsLine dl
        JOIN dl.payrollItem pi
        WHERE pi.payrollRun.payrollStatus = 'POSTED'
          AND pi.payrollRun.paymentDate >= :startDate
          AND pi.payrollRun.paymentDate <= :endDate
        GROUP BY dl.deductionType
    """)
    List<PayTypeSummaryProjection> getSummarizedDeductionsByDateRange(
            @Param("startDate") java.time.LocalDate startDate,
            @Param("endDate") java.time.LocalDate endDate
    );
}
