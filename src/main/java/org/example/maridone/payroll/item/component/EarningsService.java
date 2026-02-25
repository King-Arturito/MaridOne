package org.example.maridone.payroll.item.component;

import org.example.maridone.payroll.dto.EarningsDto;
import org.example.maridone.payroll.mapper.PayrollMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class EarningsService {
    private final EarningsRepository earningsRepository;
    private final PayrollMapper payrollMapper;

    public EarningsService(EarningsRepository earningsRepository, PayrollMapper payrollMapper) {
        this.earningsRepository = earningsRepository;
        this.payrollMapper = payrollMapper;
    }


    @Transactional(readOnly = true)
    public List<EarningsDto> getEarnings(Long itemId) {
        List<EarningsLine> earnings = earningsRepository.findByPayrollItem_ItemId(itemId);
        return payrollMapper.toEarningsDtos(earnings);
    }
}
