package org.example.maridone.payroll.item.component;

import org.example.maridone.payroll.dto.DeductionsDto;
import org.example.maridone.payroll.mapper.PayrollMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DeductionsService {

    private final DeductionsRepository deductionsRepository;
    private final PayrollMapper payrollMapper;

    public DeductionsService(DeductionsRepository deductionsRepository, PayrollMapper payrollMapper) {
        this.deductionsRepository = deductionsRepository;
        this.payrollMapper = payrollMapper;
    }

    @Transactional(readOnly = true)
    public List<DeductionsDto> getDeductions(Long itemId) {
        List<DeductionsLine> deductions = deductionsRepository.findByPayrollItem_ItemId(itemId);
        return payrollMapper.toDeductionsDtos(deductions);
    }
}
