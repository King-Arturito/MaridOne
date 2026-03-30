package org.example.maridone.payroll.payslip;

import org.example.maridone.config.DefaultConfig;
import org.example.maridone.core.employee.Employee;
import org.example.maridone.core.employee.EmployeeRepository;
import org.example.maridone.document.NOCLOUD.LocalDocumentService;
import org.example.maridone.log.AttendanceLogRepository;
import org.example.maridone.log.attendance.AttendanceLog;
import org.example.maridone.payroll.item.PayrollItem;
import org.example.maridone.payroll.item.component.DeductionsLine;
import org.example.maridone.payroll.item.component.DeductionsRepository;
import org.example.maridone.payroll.item.component.EarningsLine;
import org.example.maridone.payroll.item.component.EarningsRepository;
import org.example.maridone.payroll.run.PayrollItemRepository;
import org.example.maridone.payroll.run.PayrollRunRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
//todo: modify localDocumentService after defense, use Cloud
@Profile("dev")
public class PayslipService {

    private final EmployeeRepository employeeRepository;
    private final PayrollRunRepository payrollRunRepository;
    private final AttendanceLogRepository attendanceLogRepository;
    private final EarningsRepository earningsRepository;
    private final DeductionsRepository deductionsRepository;
    private final LocalDocumentService localDocumentService;
    private final PayrollItemRepository payrollItemRepository;
    private final DefaultConfig defaultConfig;

    public PayslipService(EmployeeRepository employeeRepository,
                          PayrollRunRepository payrollRunRepository,
                          AttendanceLogRepository attendanceLogRepository,
                          EarningsRepository earningsRepository,
                          DeductionsRepository deductionsRepository,
                          LocalDocumentService localDocumentService,
                          PayrollItemRepository payrollItemRepository,
                          DefaultConfig defaultConfig)
    {
        this.employeeRepository = employeeRepository;
        this.payrollRunRepository = payrollRunRepository;
        this.attendanceLogRepository = attendanceLogRepository;
        this.earningsRepository = earningsRepository;
        this.deductionsRepository = deductionsRepository;
        this.localDocumentService = localDocumentService;
        this.payrollItemRepository = payrollItemRepository;
        this.defaultConfig = defaultConfig;
    }

    //todo: code
    public void generatePayslips(List<DetailBlocks> detailBlocks) {
        //use localdocumentservice
    }

    @Transactional
    public void generatePayslip(Long payId) {
        List<DetailBlocks> detailBlocks = new ArrayList<>();
        List<Long> empIds = payrollItemRepository.findEmployeeIdsByPayrollRun(payId);
        PayslipProjection payslipProjection = payrollRunRepository.findPayPeriod(payId);

        // get all employees and map by manager
        List<Employee> employees = employeeRepository.findAllById(empIds);
        Map<Employee, Employee> managerMap = employees.stream()
                .filter(e -> e.getManager() !=  null)
                .collect(Collectors.toMap(employee -> employee, Employee::getManager));

        //get all payrollitems and map by emp id
        List<PayrollItem> allPayrollItems = payrollItemRepository.findByPayrollRun_PayId(payId);
        Map<Long, PayrollItem> payrollItemMap = allPayrollItems.stream()
                .collect(Collectors.toMap(item -> item.getEmployee().getEmployeeId(), item -> item));

        List<Long> itemIds = allPayrollItems.stream().map(PayrollItem::getItemId).toList();

        //earnings and deductions
        List<EarningsLine> allEarnings = earningsRepository.findByPayrollItem_ItemIdInOrderByEarningsDateAsc(itemIds);
        List<DeductionsLine> allDeductions = deductionsRepository.findByPayrollItem_ItemIdIn(itemIds);

        //map by itemId
        Map<Long, List<EarningsLine>> earningsMap = allEarnings.stream()
                .collect(Collectors.groupingBy(line -> line.getPayrollItem().getItemId()));

        Map<Long, List<DeductionsLine>> deductionsMap = allDeductions.stream()
                .collect(Collectors.groupingBy(line -> line.getPayrollItem().getItemId()));

        //get all attendancelogs
        Instant startInstant = payslipProjection.payPeriodStart().atStartOfDay(defaultConfig.getTimeZone()).toInstant();
        Instant endInstant = payslipProjection.payPeriodEnd().atTime(LocalTime.MAX).atZone(defaultConfig.getTimeZone()).toInstant();
        List<AttendanceLog> allLogs = attendanceLogRepository.findByEmployeeIdInAndDirectionAndTimestampBetween(
                empIds, "IN", startInstant, endInstant);

        //map by empId
        Map<Long, List<AttendanceLog>> logsMap = allLogs.stream()
                .collect(Collectors.groupingBy(AttendanceLog::getEmployeeId));

        //iterate to build details
        for (Employee employee : employees) {
            String department = managerMap.containsKey(employee)
                    ? (managerMap.get(employee).getEmpTitle().getDisplayName() == null ? "_" : managerMap.get(employee).getEmpTitle().getDisplayName()) :
                    (employee.getEmpTitle().getDisplayName() == null ? "_" :  employee.getEmpTitle().getDisplayName());

            PayrollItem item = payrollItemMap.get(employee.getEmployeeId());
            if (item == null) {
                throw new RuntimeException("Unknown Error: No PayrollItem for employee " + employee.getEmployeeId());
            }

            // Retrieve pre-fetched lists from the Maps (using getOrDefault to avoid nulls if lists are empty)
            List<EarningsLine> employeeEarningsLines = earningsMap.getOrDefault(item.getItemId(), Collections.emptyList());
            List<DeductionsLine> employeeDeductionsLines = deductionsMap.getOrDefault(item.getItemId(), Collections.emptyList());
            List<AttendanceLog> employeeLogs = logsMap.getOrDefault(employee.getEmployeeId(), Collections.emptyList());

            // Process data using updated helper methods
            List<PayBreakdown> earnings = buildEarningsList(employeeEarningsLines);
            List<PayBreakdown> deductions = buildDeductionsList(employeeDeductionsLines);
            Integer workedDays = resolveWorkedDays(employeeLogs);

            BigDecimal overallEarnings =  earnings.stream().map(PayBreakdown::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal overallDeductions = deductions.stream().map(PayBreakdown::amount).reduce(BigDecimal.ZERO, BigDecimal::add);

            DetailBlocks details = new DetailBlocks(
                    employee.getFirstName() + " " + employee.getLastName(),
                    department,
                    employee.getPosition() != null ? employee.getPosition().toString() : "_",
                    payslipProjection.payPeriodStart(),
                    payslipProjection.payPeriodEnd(),
                    workedDays,
                    earnings,
                    deductions,
                    overallEarnings,
                    overallDeductions
            );
            detailBlocks.add(details);
        }
        //generate now
        generatePayslips(detailBlocks);
    }

    public record DetailBlocks(
            String name,
            String department,
            String designation,
            LocalDate periodStart,
            LocalDate periodEnd,
            Integer workedDays,
            List<PayBreakdown> earningsList,
            List<PayBreakdown> deductionsBreakdownList,
            BigDecimal overallEarnings,
            BigDecimal overallDeductions
    ){}

    public record PayBreakdown(
            String name,
            BigDecimal amount
    ) {}


    public Integer resolveWorkedDays(List<AttendanceLog> logs) {
        Set<LocalDate> workedDays = logs.stream()
                .map(log -> log.getTimestamp().atZone(defaultConfig.getTimeZone()).toLocalDate())
                .collect(Collectors.toSet());
        return workedDays.size();
    }

    public List<PayBreakdown> buildEarningsList(List<EarningsLine> earningsLines) {
        List<PayBreakdown> earnings = new ArrayList<>();
        for (EarningsLine earningsLine : earningsLines) {
            String name = earningsLine.getOvertime() != null && earningsLine.getOvertime()
                    ? "Overtime: " + earningsLine.getEarningsDate().toString()
                    : earningsLine.getEarningsDate().toString();

            PayBreakdown breakdown = new PayBreakdown(name, earningsLine.getAmount());
            earnings.add(breakdown);
        }
        return earnings;
    }

    public List<PayBreakdown> buildDeductionsList(List<DeductionsLine> deductionsLines) {
        List<PayBreakdown> deductions = new ArrayList<>();
        for (DeductionsLine deductionsLine : deductionsLines)  {
            String name = deductionsLine.getDeductionType().toString();
            PayBreakdown breakdown = new PayBreakdown(name, deductionsLine.getAmount());
            deductions.add(breakdown);
        }
        return deductions;
    }
}

