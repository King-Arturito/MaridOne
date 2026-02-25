package org.example.maridone.payroll.item.component;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface EarningsRepository extends JpaRepository<EarningsLine, Long> {
    List<EarningsLine> findByPayrollItem_ItemId(Long itemId);
}
