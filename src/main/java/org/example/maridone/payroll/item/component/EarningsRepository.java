package org.example.maridone.payroll.item.component;

import org.example.maridone.enums.EarningsType;
import org.example.maridone.payroll.projection.PayTypeSummaryProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface EarningsRepository extends JpaRepository<EarningsLine, Long> {
    List<EarningsLine> findByPayrollItem_ItemId(Long itemId);

    List<EarningsLine> findByPayrollItem_ItemIdInOrderByEarningsDateAsc(List<Long> itemIds);

    List<EarningsLine>  findByPayrollItem_ItemIdOrderByEarningsDateAsc(Long itemId);

    List<EarningsLine> findByPayrollItem_Employee_EmployeeIdAndEarningsTypeAndEarningsDateBetweenOrderByEarningsDateAscEarningsIdAsc(
            Long employeeId,
            EarningsType earningsType,
            java.time.LocalDate startDate,
            java.time.LocalDate endDate
    );

    @Query(value = """
    SELECT
        CASE
            WHEN e.earnings_type = 'BONUS' THEN 'BONUS'
            WHEN pr.run_type = 'ADJUSTMENT' THEN 'MANUAL_ADJUSTMENT'
            WHEN e.is_overtime = false THEN 'BASIC'
            ELSE cast(otr.overtime_type as text)
        END AS type,
        SUM(e.amount) AS totalAmount
    FROM earnings_line e
    JOIN payroll_item pi ON e.item_id = pi.item_id
    JOIN payroll_run pr ON pi.pay_id = pr.pay_id
    LEFT JOIN overtime_request otr ON e.overtime_id = otr.overtime_id
    WHERE pi.pay_id = :payId
    GROUP BY 
        CASE 
            WHEN e.earnings_type = 'BONUS' THEN 'BONUS'
            WHEN pr.run_type = 'ADJUSTMENT' THEN 'MANUAL_ADJUSTMENT'
            WHEN e.is_overtime = false THEN 'BASIC'
            ELSE cast(otr.overtime_type as text)
        END
""", nativeQuery = true)
    List<PayTypeSummaryProjection> getSummarizedEarnings(@Param("payId") Long payId);
    @Query("""
        SELECT el.earningsType AS type, SUM(el.amount) AS totalAmount
        FROM EarningsLine el
        JOIN el.payrollItem pi
        WHERE pi.payrollRun.payrollStatus = 'POSTED'
          AND pi.payrollRun.paymentDate >= :startDate
          AND pi.payrollRun.paymentDate <= :endDate
        GROUP BY el.earningsType
    """)
    List<PayTypeSummaryProjection> getSummarizedEarningsByDateRange(
            @Param("startDate") java.time.LocalDate startDate,
            @Param("endDate") java.time.LocalDate endDate
    );
}
