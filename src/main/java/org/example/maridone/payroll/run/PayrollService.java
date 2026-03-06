package org.example.maridone.payroll.run;

import jakarta.validation.Valid;
import org.example.maridone.annotation.ExecutionTime;
import org.example.maridone.common.CommonSpecs;
import org.example.maridone.config.DefaultProperties;
import org.example.maridone.core.employee.Employee;
import org.example.maridone.core.employee.EmployeeRepository;
import org.example.maridone.enums.EmploymentStatus;
import org.example.maridone.enums.ExemptionStatus;
import org.example.maridone.enums.Status;
import org.example.maridone.exception.EmployeeNotFoundException;
import org.example.maridone.exception.ItemNotFoundException;
import org.example.maridone.exception.RunNotFoundException;
import org.example.maridone.log.AttendanceLogRepository;
import org.example.maridone.log.attendance.AttendanceLog;
import org.example.maridone.overtime.OvertimeRequest;
import org.example.maridone.overtime.OvertimeRequestRepository;
import org.example.maridone.overtime.spec.OvertimeSpecs;
import org.example.maridone.payroll.PayrollCalculator;
import org.example.maridone.payroll.item.PayrollItem;
import org.example.maridone.payroll.dto.ItemSummaryDto;
import org.example.maridone.payroll.dto.PayrollItemDto;
import org.example.maridone.payroll.dto.RunCreateDto;
import org.example.maridone.payroll.dto.ItemDetailsDto;
import org.example.maridone.payroll.mapper.PayrollMapper;
import org.example.maridone.payroll.spec.ItemSpecs;
import org.example.maridone.schedule.shift.TemplateShiftRepository;
import org.example.maridone.schedule.shift.TemplateShiftSchedule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PayrollService {

    private final PayrollRunRepository payrollRunRepository;
    private final PayrollItemRepository payrollItemRepository;
    private final EmployeeRepository employeeRepository;
    private final AttendanceLogRepository attendanceLogRepository;
    private final TemplateShiftRepository templateShiftRepository;
    private final OvertimeRequestRepository overtimeRequestRepository;
    private final PayrollCalculator payrollCalculator;
    private final PayrollMapper payrollMapper;
    private final DefaultProperties defaultProperties;

    PayrollService(
            PayrollRunRepository payrollRunRepository,
            PayrollItemRepository payrollItemRepository,
            EmployeeRepository employeeRepository,
            AttendanceLogRepository attendanceLogRepository,
            TemplateShiftRepository templateShiftRepository,
            OvertimeRequestRepository overtimeRequestRepository,
            PayrollCalculator payrollCalculator,
            PayrollMapper payrollMapper,
            DefaultProperties defaultProperties
    )
    {
        this.payrollRunRepository = payrollRunRepository;
        this.payrollItemRepository = payrollItemRepository;
        this.employeeRepository = employeeRepository;
        this.attendanceLogRepository = attendanceLogRepository;
        this.templateShiftRepository = templateShiftRepository;
        this.overtimeRequestRepository = overtimeRequestRepository;
        this.payrollCalculator = payrollCalculator;
        this.payrollMapper = payrollMapper;
        this.defaultProperties = defaultProperties;
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





    /*
        PAYROLL PROCESS
    */
    @Transactional
    @ExecutionTime
    public void processPayroll(RunCreateDto payload) {
        //create run for this period
        PayrollRun run = createRun(payload);

        Specification<Employee> spec = CommonSpecs.fieldNotEquals("employmentStatus", EmploymentStatus.TERMINATED);
        List<Employee> employees = employeeRepository.findAll(spec);
        Map<Boolean, List<Employee>> partitioned = employees.stream()
                .collect(Collectors.partitioningBy(
                        emp -> emp.getExemptionStatus().equals(ExemptionStatus.EXEMPT)
                ));
        List<Employee> exemptEmployees = partitioned.getOrDefault(true, List.of());
        List<Employee> nonExemptEmployees = partitioned.getOrDefault(false, List.of());

        //do process payroll exempt for managerial roles
        List<PayrollItem> items = new ArrayList<>(processPayrollExempt(run, exemptEmployees));

        //do process payroll nonexempt for regular employees (non exempt)
        processPayrollNonExempt(run, items, nonExemptEmployees);
        //save all
        payrollItemRepository.saveAll(items);
    }

    @Transactional
    public List<PayrollItem> processPayrollExempt(PayrollRun run, List<Employee> employees) {

        //map each exempt employee with a PayrollItem Object
        //collect the result to a list
        //return the list
        return employees.stream()
                .map(emp -> {
                    PayrollItem item = new PayrollItem();
                    BigDecimal biweeklySalary = emp.getYearlySalary().divide(BigDecimal.valueOf(26), 2, RoundingMode.HALF_UP);
                    item.setEmployee(emp);
                    item.setPayrollRun(run);
                    //todo
                    item.setGrossPay(biweeklySalary);
                    item.setNetPay(biweeklySalary);
                    item.setDisputes(new ArrayList<>());
                    //todo
                    item.setEarnings(payrollCalculator.setEarnings(emp, item));
                    //todo
                    item.setDeductions(payrollCalculator.setDeductions(emp));
                    return item;
                })
                .toList();
    }

    @Transactional
    public void processPayrollNonExempt(PayrollRun run, List<PayrollItem> items, List<Employee> employees) {

        List<Long> employeeIds = employees.stream()
                .map(Employee::getEmployeeId)
                .toList();

        //get all attendance logs in all those employeeIds within the specified period + 6 hours
        Sort sorting = Sort.by(Sort.Direction.ASC, "timestamp");
        List<AttendanceLog> allLogs = attendanceLogRepository
                .findByEmployeeIdInAndTimestampBetween(
                        employeeIds,
                        run.getPeriodStart().atStartOfDay(defaultProperties.getTimeZone()).toInstant(),
                        run.getPeriodEnd().atTime(LocalTime.MAX).plusHours(6).atZone(defaultProperties.getTimeZone()).toInstant(),
                        sorting);

        Specification<OvertimeRequest> overtimeSpecs = Specification.allOf(
                OvertimeSpecs.hasStatus(Status.APPROVED),
                OvertimeSpecs.hasEmployeeIds(employeeIds),
                CommonSpecs.fieldIsBetween(
                        "workDate",
                        run.getPeriodStart(),
                        run.getPeriodEnd())
        );

        //get all overtime requests for the period
        List<OvertimeRequest> overtimeRequests = overtimeRequestRepository.findAll(overtimeSpecs);

        //get all shift schedules of employees
        List<TemplateShiftSchedule> allSchedules = templateShiftRepository.findByEmployee_EmployeeIdIn(employeeIds);

        //create a key-value pair by emp id -> List<AttendanceLogs>
        Map<Long, List<AttendanceLog>> attendanceMap = allLogs.stream()
                .collect(Collectors.groupingBy(AttendanceLog::getEmployeeId));

        //create a key-value pair by emp id -> List<ShiftSchedule>
        Map<Long, List<TemplateShiftSchedule>> scheduleMap = allSchedules.stream()
                .collect(Collectors.groupingBy(schedule -> schedule.getEmployee().getEmployeeId()));

        Map<Long, List<OvertimeRequest>> overtimeMap = overtimeRequests.stream()
                .collect(Collectors.groupingBy(OvertimeRequest::getEmployeeId));

        for (Employee emp :  employees) {
            List<OvertimeRequest> requests = overtimeMap.getOrDefault(emp.getEmployeeId(), List.of());
            List<TemplateShiftSchedule> schedules = scheduleMap.getOrDefault(emp.getEmployeeId(), List.of());
            List<AttendanceLog> attendanceLogs = attendanceMap.getOrDefault(emp.getEmployeeId(), List.of());

            //get total hours from shift schedule
            BigDecimal shiftScheduleHours = payrollCalculator.calculateHours(schedules);
            //get logged hours from attendance
            BigDecimal loggedHours = BigDecimal.ZERO;
            //check how much of it was overtime

            //todo nonexempt logic
            for (int i = 0; i < attendanceLogs.size(); i++) {
                AttendanceLog attendanceLog = attendanceLogs.get(i);
            }
        }


        //calculate hours
        //minus some hours if passed schedule
        //dont minus if has overtime request for that hour and day
        //check grace periods sa handbook?
        //calculate pay using their hourly rate?
        //put gross pay
        //put net pay
    }


}
