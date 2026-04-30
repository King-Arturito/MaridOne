package org.example.maridone.payroll.item.component;

import org.example.maridone.payroll.dto.EmployerContributionDto;
import org.example.maridone.payroll.mapper.PayrollMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
public class EmployerContributionService {
    private final EmployerContributionRepository employerContributionRepository;
    private final PayrollMapper payrollMapper;

    public EmployerContributionService(
            EmployerContributionRepository employerContributionRepository,
            PayrollMapper payrollMapper
    ) {
        this.employerContributionRepository = employerContributionRepository;
        this.payrollMapper = payrollMapper;
    }

    @Transactional(readOnly = true)
    public List<EmployerContributionDto> getEmployerContributions(Long itemId) {
        return employerContributionRepository.findByPayrollItem_ItemId(itemId).stream()
                .sorted(Comparator.comparing(EmployerContributionLine::getContributionType))
                .map(line -> {
                    EmployerContributionDto dto = payrollMapper.toEmployerContributionDto(line);
                    dto.setDescription(describeLine(line));
                    return dto;
                })
                .toList();
    }

    private String describeLine(EmployerContributionLine line) {
        if (line.getContributionType() == null) {
            return "Employer Contribution";
        }

        return switch (line.getContributionType()) {
            case SSS -> "Employer SSS Contribution";
            case PHILHEALTH -> "Employer PhilHealth Contribution";
            case PAGIBIG -> "Employer Pag-IBIG Contribution";
        };
    }
}
