package org.example.maridone.payroll.run;

import org.example.maridone.enums.RunType;
import org.example.maridone.payroll.dto.PayrollRunViewDto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PayrollRunRepository extends JpaRepository<PayrollRun, Long> {

    List<PayrollRun> findPayrollRunsByPayId(Long id);

    boolean existsByPeriodStartAndPeriodEndAndRunType(LocalDate periodStart, LocalDate periodEnd, RunType runType);

    @Query("""
        SELECT new org.example.maridone.payroll.dto.PayrollRunViewDto(
            pr.payId,
            pr.periodStart,
            pr.periodEnd,
            pr.periodDescription,
            pr.payrollStatus,
            pr.runType,
            COUNT(pi.itemId)
        )
        FROM PayrollRun pr
        LEFT JOIN pr.items pi
        GROUP BY pr.payId, pr.periodStart, pr.periodEnd, pr.periodDescription, pr.payrollStatus, pr.runType
        ORDER BY pr.payId DESC
    """)
    List<PayrollRunViewDto> findAllRunViews();

    @Query("""
        SELECT new org.example.maridone.payroll.dto.PayrollRunViewDto(
            pr.payId,
            pr.periodStart,
            pr.periodEnd,
            pr.periodDescription,
            pr.payrollStatus,
            pr.runType,
            COUNT(pi.itemId)
        )
        FROM PayrollRun pr
        LEFT JOIN pr.items pi
        WHERE pr.payId = :payId
        GROUP BY pr.payId, pr.periodStart, pr.periodEnd, pr.periodDescription, pr.payrollStatus, pr.runType
    """)
    Optional<PayrollRunViewDto> findRunViewByPayId(@Param("payId") Long payId);
}
