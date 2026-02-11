package org.example.maridone.payroll.run;

import jakarta.validation.Valid;
import org.example.maridone.common.CommonSpecs;
import org.example.maridone.core.employee.Employee;
import org.example.maridone.core.employee.EmployeeRepository;
import org.example.maridone.core.spec.EmployeeSpecs;
import org.example.maridone.enums.EmploymentStatus;
import org.example.maridone.exception.EmployeeNotFoundException;
import org.example.maridone.exception.ItemNotFoundException;
import org.example.maridone.exception.RunNotFoundException;
import org.example.maridone.exception.ShiftsNotFoundException;
import org.example.maridone.log.AttendanceLogRepository;
import org.example.maridone.log.attendance.AttendanceLog;
import org.example.maridone.payroll.PayrollItem;
import org.example.maridone.payroll.dto.ItemSummaryDto;
import org.example.maridone.payroll.dto.PayrollItemDto;
import org.example.maridone.payroll.dto.RunCreateDto;
import org.example.maridone.payroll.itemcomponent.DeductionsService;
import org.example.maridone.payroll.dto.ItemDetailsDto;
import org.example.maridone.payroll.itemcomponent.EarningsService;
import org.example.maridone.payroll.mapper.PayrollMapper;
import org.example.maridone.payroll.spec.ItemSpecs;
import org.example.maridone.schedule.shift.ShiftRepository;
import org.example.maridone.schedule.shift.ShiftSchedule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PayrollService {

    private final PayrollRunRepository payrollRunRepository;
    private final PayrollItemRepository payrollItemRepository;
    private final EmployeeRepository employeeRepository;
    private final AttendanceLogRepository attendanceLogRepository;
    private final ShiftRepository shiftRepository;
    private final EarningsService earningsService;
    private final DeductionsService deductionsService;
    private final PayrollMapper payrollMapper;

    PayrollService(
            PayrollRunRepository payrollRunRepository,
            PayrollItemRepository payrollItemRepository,
            EmployeeRepository employeeRepository,
            AttendanceLogRepository attendanceLogRepository,
            ShiftRepository shiftRepository,
            EarningsService earningsService,
            DeductionsService deductionsService,
            PayrollMapper payrollMapper
    )
    {
        this.payrollRunRepository = payrollRunRepository;
        this.payrollItemRepository = payrollItemRepository;
        this.employeeRepository = employeeRepository;
        this.attendanceLogRepository = attendanceLogRepository;
        this.shiftRepository = shiftRepository;
        this.earningsService = earningsService;
        this.deductionsService = deductionsService;
        this.payrollMapper = payrollMapper;
    }

    @Transactional
    public List<ItemDetailsDto> getItems(Long empId) {

        Specification<PayrollItem> spec = Specification.allOf(
                ItemSpecs.hasEmployeeId(empId)
        );
        List<PayrollItem> items = payrollItemRepository.findAll(spec);

        return payrollMapper.toItemDetailsDtos(items);
    }

    @Transactional
    public PayrollRun createRun(RunCreateDto payload) {
        PayrollRun run = new PayrollRun();
        run.setPeriodDescription(payload.getPeriodDescription());
        run.setRunType(payload.getRunType());
        run.setPayrollStatus(payload.getPayrollStatus());
        run.setPeriodEnd(payload.getPeriodEnd());
        run.setPeriodStart(payload.getPeriodStart());
        payrollRunRepository.save(run);
        return run;
    }

    @Transactional
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

    @Transactional
    public void processPayroll(RunCreateDto payload) {
        PayrollRun run = createRun(payload);
        List<PayrollItem> items = new ArrayList<>();

        Specification<Employee> spec = Specification.allOf(
                CommonSpecs.fieldEquals("employmentStatus", EmploymentStatus.TERMINATED)
        );
        List<Employee> employees = employeeRepository.findAll(spec);


        //get all emp ids
        List<Long> employeeIds = employees.stream()
                .map(Employee::getEmployeeId)
                .toList();

        //get all attendance logs in all those employeeIds
        List<AttendanceLog> allLogs = attendanceLogRepository.findByEmployeeIdIn(employeeIds);

        //get all shift schedules of employees
        List<ShiftSchedule> allSchedules = shiftRepository.findByEmployee_EmployeeIdIn(employeeIds);

        //create a key-value pair by emp id -> List<AttendanceLogs>
        Map<Long, List<AttendanceLog>> attendanceMap = allLogs.stream()
                .collect(Collectors.groupingBy(AttendanceLog::getEmployeeId));

        //create a key-value pair by emp id -> List<ShiftSchedule>
        Map<Long, List<ShiftSchedule>> scheduleMap = allSchedules.stream()
                .collect(Collectors.groupingBy(schedule -> schedule.getEmployee().getEmployeeId()));

        //loop through all employees
        for (Employee emp :  employees) {
            PayrollItem item = new PayrollItem();
            item.setPayrollRun(run);
            List<AttendanceLog> empLogs = attendanceMap.getOrDefault(emp.getEmployeeId(), Collections.emptyList());
            List<ShiftSchedule> schedules = scheduleMap.getOrDefault(emp.getEmployeeId(), Collections.emptyList());
            if (schedules.isEmpty()) {
                throw new ShiftsNotFoundException("Shifts for Employee ID:"  + emp.getEmployeeId() + " not found.");
            }
            if (empLogs.isEmpty()) {
                //logging
                //whats da logic chat
            }

            //calculate hours
            //minus some hours if passed schedule
            //dont minus if has overtime request for that hour and day
            //check grace periods sa handbook?
            //calculate pay using their hourly rate?
            //put gross pay
            //put net pay

            items.add(item);
        }

        payrollItemRepository.saveAll(items);
    }
}
