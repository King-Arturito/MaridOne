package org.example.maridone.payroll.item;

import jakarta.validation.Valid;
import org.example.maridone.annotation.ExecutionTime;
import org.example.maridone.core.employee.Employee;
import org.example.maridone.core.employee.EmployeeRepository;
import org.example.maridone.exception.notfound.EmployeeNotFoundException;
import org.example.maridone.exception.notfound.ItemNotFoundException;
import org.example.maridone.exception.notfound.RunNotFoundException;
import org.example.maridone.payroll.dto.ItemDetailsDto;
import org.example.maridone.payroll.dto.ItemSummaryDto;
import org.example.maridone.payroll.dto.PayrollItemDto;
import org.example.maridone.payroll.mapper.PayrollMapper;
import org.example.maridone.payroll.run.PayrollItemRepository;
import org.example.maridone.payroll.run.PayrollRun;
import org.example.maridone.payroll.run.PayrollRunRepository;
import org.example.maridone.payroll.spec.ItemSpecs;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class PayrollItemService {

    private final PayrollItemRepository payrollItemRepository;
    private final PayrollMapper payrollMapper;
    private final PayrollRunRepository payrollRunRepository;
    private final EmployeeRepository employeeRepository;

    public PayrollItemService(
            PayrollItemRepository payrollItemRepository,
            PayrollMapper payrollMapper,
            PayrollRunRepository payrollRunRepository,
            EmployeeRepository employeeRepository
    ) {
        this.payrollItemRepository = payrollItemRepository;
        this.payrollMapper = payrollMapper;
        this.payrollRunRepository = payrollRunRepository;
        this.employeeRepository = employeeRepository;
    }

    @Transactional
    @ExecutionTime
    public List<ItemDetailsDto> getItems(Long empId) {

        Specification<PayrollItem> spec = Specification.allOf(
                ItemSpecs.hasEmployeeId(empId)
        );
        List<PayrollItem> items = payrollItemRepository.findAll(spec);

        return payrollMapper.toItemDetailsDtos(items);
    }

    @Transactional
    @ExecutionTime
    public ItemDetailsDto createItem(@Valid PayrollItemDto payload) {
        PayrollItem item = new PayrollItem();
        PayrollRun run = payrollRunRepository.findById(payload.getPayId()).orElseThrow(
                () -> new RunNotFoundException("Payroll Run ID:" + payload.getPayId() + " not found.")
        );

        Employee emp = employeeRepository.findById(payload.getEmpId()).orElseThrow(
                () -> new EmployeeNotFoundException(payload.getEmpId())
        );

        item.setPayrollRun(run);
        item.setEmployee(emp);
        item.setGrossPay(BigDecimal.valueOf(-1));
        item.setNetPay(BigDecimal.valueOf(-1));
        payrollItemRepository.save(item);
        return payrollMapper.toItemDetailsDto(item);
    }

    @Transactional
    @ExecutionTime
    public PayrollItemDto updateItem(PayrollItemDto payload, Long itemId) {
        PayrollItem item = payrollItemRepository.findById(itemId).orElseThrow(
                () -> new ItemNotFoundException(itemId)
        );

        item.setGrossPay(payload.getGrossPay());
        item.setNetPay(payload.getNetPay());
        payrollItemRepository.save(item);
        return payrollMapper.toPayrollItemDto(item);
    }

    public Page<ItemSummaryDto> getRunItems(Long payId, Pageable pageable) {
        if (!payrollRunRepository.existsById(payId)) {
            throw new RunNotFoundException("Payroll Run ID:" + payId + " not found.");
        }

        Page<PayrollItem> items = payrollItemRepository.findByPayrollRun_PayId(payId, pageable);
        return items.map(payrollMapper::toItemSummaryDto);
    }
}
