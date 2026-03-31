package org.example.maridone.payroll.payslip;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.example.maridone.config.DefaultConfig;
import org.example.maridone.core.employee.Employee;
import org.example.maridone.core.employee.EmployeeRepository;
import org.example.maridone.core.user.UserAccount;
import org.example.maridone.document.NOCLOUD.LocalDocumentService;
import org.example.maridone.enums.ManagerTitle;
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
import org.springframework.web.multipart.MultipartFile;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.*;
import java.math.BigDecimal;
import java.nio.file.Files;
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
    private final TemplateEngine templateEngine;

    public PayslipService(EmployeeRepository employeeRepository,
                          PayrollRunRepository payrollRunRepository,
                          AttendanceLogRepository attendanceLogRepository,
                          EarningsRepository earningsRepository,
                          DeductionsRepository deductionsRepository,
                          LocalDocumentService localDocumentService,
                          PayrollItemRepository payrollItemRepository,
                          DefaultConfig defaultConfig,
                          TemplateEngine templateEngine)
    {
        this.employeeRepository = employeeRepository;
        this.payrollRunRepository = payrollRunRepository;
        this.attendanceLogRepository = attendanceLogRepository;
        this.earningsRepository = earningsRepository;
        this.deductionsRepository = deductionsRepository;
        this.localDocumentService = localDocumentService;
        this.payrollItemRepository = payrollItemRepository;
        this.defaultConfig = defaultConfig;
        this.templateEngine = templateEngine;
    }

    //final stage of payslip generation
    public void generatePayslips(List<DetailBlocks> detailBlocks) {
        List<FileUploadBatchRequest> batchRequests = new ArrayList<>();
        for (DetailBlocks block : detailBlocks) {
            Context context = new Context();
            context.setVariable("details", block);

            String htmlContent = templateEngine.process("payslip-template", context);

            byte[] pdfBytes;
            try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
                PdfRendererBuilder builder = new PdfRendererBuilder();
                builder.useFastMode();
                builder.withHtmlContent(htmlContent, Objects.requireNonNull(getClass().getResource("/templates/")).toExternalForm());
                builder.toStream(os);
                builder.run();

                pdfBytes = os.toByteArray();
            } catch (Exception e) {
                throw new RuntimeException("Failed to generate PDF for " + block.name(), e);
            }

            // sample file name: UUID_Payslip_Arthur_Evangelista_2026-03-31.pdf
            String safeName = block.name().replace(" ", "_");
            String fileName = "Payslip_" + safeName + "_" + block.periodEnd() + ".pdf";

            // custom wrapper
            org.springframework.web.multipart.MultipartFile multipartFile = new ByteArrayMultipartFile(
                    fileName,
                    fileName,
                    "application/pdf",
                    pdfBytes
            );
            batchRequests.add(new FileUploadBatchRequest(multipartFile, block.userAccountName()));
        }
        localDocumentService.batchUpload(batchRequests);
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

        //get connected useraccounts
        Map<Employee, UserAccount> userAccountMap = employees.stream()
                .filter(e -> e.getUserAccount()  != null)
                .collect(Collectors.toMap(employee -> employee, Employee::getUserAccount));

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
        Instant startInstant = payslipProjection.getPayPeriodStart().atStartOfDay(defaultConfig.getTimeZone()).toInstant();
        Instant endInstant = payslipProjection.getPayPeriodEnd().atTime(LocalTime.MAX).atZone(defaultConfig.getTimeZone()).toInstant();
        List<AttendanceLog> allLogs = attendanceLogRepository.findByEmployeeIdInAndDirectionAndTimestampBetween(
                empIds, "IN", startInstant, endInstant);

        //map by empId
        Map<Long, List<AttendanceLog>> logsMap = allLogs.stream()
                .collect(Collectors.groupingBy(AttendanceLog::getEmployeeId));

        //iterate to build details
        for (Employee employee : employees) {
            Employee targetForTitle = managerMap.getOrDefault(employee, employee);

            String department = java.util.Optional.ofNullable(targetForTitle.getEmpTitle())
                    .map(ManagerTitle::getDisplayName)
                    .orElse("_");

            PayrollItem item = payrollItemMap.get(employee.getEmployeeId());
            if (item == null) {
                throw new RuntimeException("Unknown Error: No PayrollItem for employee " + employee.getEmployeeId());
            }

            // Retrieve prefetched lists
            List<EarningsLine> employeeEarningsLines = earningsMap.getOrDefault(item.getItemId(), Collections.emptyList());
            List<DeductionsLine> employeeDeductionsLines = deductionsMap.getOrDefault(item.getItemId(), Collections.emptyList());
            List<AttendanceLog> employeeLogs = logsMap.getOrDefault(employee.getEmployeeId(), Collections.emptyList());

            // Process data
            List<PayBreakdown> earnings = buildEarningsList(employeeEarningsLines);
            List<PayBreakdown> deductions = buildDeductionsList(employeeDeductionsLines);
            Integer workedDays = resolveWorkedDays(employeeLogs);

            BigDecimal overallEarnings =  earnings.stream().map(PayBreakdown::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal overallDeductions = deductions.stream().map(PayBreakdown::amount).reduce(BigDecimal.ZERO, BigDecimal::add);

            DetailBlocks details = new DetailBlocks(
                    employee.getFirstName() + " " + employee.getLastName(),
                    department,
                    employee.getPosition() != null ? employee.getPosition().toString() : "_",
                    payslipProjection.getPayPeriodStart(),
                    payslipProjection.getPayPeriodEnd(),
                    workedDays,
                    earnings,
                    deductions,
                    overallEarnings,
                    overallDeductions,
                    userAccountMap.get(employee).getUsername()
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
            BigDecimal overallDeductions,
            String userAccountName
    ){}

    public record PayBreakdown(
            String name,
            BigDecimal amount
    ) {}

    public record ByteArrayMultipartFile(String name, String originalFilename, String contentType, byte[] content) implements MultipartFile {
        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getOriginalFilename() {
            return originalFilename;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return content == null || content.length == 0;
        }

        @Override
        public long getSize() {
            return content.length;
        }

        @Override
        public byte[] getBytes() {
            return content;
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(content);
        }

        @Override
        public void transferTo(File dest) throws IOException, IllegalStateException {
            Files.write(dest.toPath(), content);
        }
    }


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

