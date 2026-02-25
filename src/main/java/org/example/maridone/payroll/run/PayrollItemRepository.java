package org.example.maridone.payroll.run;

import org.example.maridone.payroll.item.PayrollItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PayrollItemRepository extends JpaRepository<PayrollItem, Long>, JpaSpecificationExecutor<PayrollItem> {

    List<PayrollItem> findByEmployee_EmployeeId(Long empId);

    Page<PayrollItem> findByPayrollRun_PayId(Long payrollRunPayId, Pageable pageable);
}
