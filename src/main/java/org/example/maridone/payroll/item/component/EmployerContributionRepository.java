package org.example.maridone.payroll.item.component;

import org.example.maridone.payroll.projection.PayTypeSummaryProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmployerContributionRepository extends JpaRepository<EmployerContributionLine, Long> {

    List<EmployerContributionLine> findByPayrollItem_ItemId(Long itemId);

    @Query(value = """
    SELECT
        ecl.contribution_type AS type,
        SUM(ecl.amount) AS totalAmount
    FROM employer_contribution_line ecl
    JOIN payroll_item pi ON ecl.item_id = pi.item_id
    WHERE pi.pay_id = :payId
    GROUP BY ecl.contribution_type
""", nativeQuery = true)
    List<PayTypeSummaryProjection> getSummarizedEmployerContributions(@Param("payId") Long payId);

    @Query("""
        SELECT ecl.contributionType AS type, SUM(ecl.amount) AS totalAmount
        FROM EmployerContributionLine ecl
        JOIN ecl.payrollItem pi
        WHERE pi.payrollRun.payrollStatus = 'POSTED'
          AND pi.payrollRun.paymentDate >= :startDate
          AND pi.payrollRun.paymentDate <= :endDate
        GROUP BY ecl.contributionType
    """)
    List<PayTypeSummaryProjection> getSummarizedEmployerContributionsByDateRange(
            @Param("startDate") java.time.LocalDate startDate,
            @Param("endDate") java.time.LocalDate endDate
    );
}
