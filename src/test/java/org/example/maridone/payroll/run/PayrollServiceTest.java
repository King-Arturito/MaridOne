package org.example.maridone.payroll.run;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.example.maridone.config.DefaultProperties;
import org.example.maridone.core.employee.Employee;
import org.example.maridone.enums.EarningsType;
import org.example.maridone.enums.DeductionType;
import org.example.maridone.core.employee.EmployeeRepository;
import org.example.maridone.enums.ExemptionStatus;
import org.example.maridone.holiday.HolidayLookup;
import org.example.maridone.holiday.HolidayService;
import org.example.maridone.leave.request.LeaveRequest;
import org.example.maridone.leave.request.LeaveRequestRepository;
import org.example.maridone.log.AttendanceLogRepository;
import org.example.maridone.log.attendance.AttendanceLog;
import org.example.maridone.overtime.OvertimeRequest;
import org.example.maridone.overtime.OvertimeRequestRepository;
import org.example.maridone.payroll.PayrollCalculator;
import org.example.maridone.payroll.PayrollService;
import org.example.maridone.payroll.item.PayrollItem;
import org.example.maridone.payroll.item.component.DeductionsLine;
import org.example.maridone.payroll.item.component.DeductionsRepository;
import org.example.maridone.payroll.item.component.EarningsLine;
import org.example.maridone.payroll.item.component.EarningsRepository;
import org.example.maridone.payroll.dto.ItemDetailsDto;
import org.example.maridone.payroll.mapper.PayrollMapper;
import org.example.maridone.schedule.shift.TemplateShiftRepository;
import org.example.maridone.schedule.shift.TemplateShiftSchedule;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PayrollServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Manila");

    @Mock private PayrollRunRepository payrollRunRepository;
    @Mock private PayrollItemRepository payrollItemRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private AttendanceLogRepository attendanceLogRepository;
    @Mock private TemplateShiftRepository templateShiftRepository;
    @Mock private OvertimeRequestRepository overtimeRequestRepository;
    @Mock private LeaveRequestRepository leaveRequestRepository;
    @Mock private HolidayService holidayService;
    @Mock private DefaultProperties defaultProperties;
    @Mock private EarningsRepository earningsRepository;
    @Mock private DeductionsRepository deductionsRepository;
    @Mock private PayrollMapper payrollMapper;

    private PayrollService payrollService;

    @BeforeEach
    void setUp() {
        PayrollCalculator payrollCalculator = new PayrollCalculator(earningsRepository, deductionsRepository);
        payrollService = new PayrollService(
                payrollRunRepository,
                payrollItemRepository,
                employeeRepository,
                attendanceLogRepository,
                templateShiftRepository,
                overtimeRequestRepository,
                leaveRequestRepository,
                holidayService,
                payrollCalculator,
                payrollMapper,
                defaultProperties
        );
    }

    @Test
    void getItems_ShouldReturnDtos() {
        Long empId = 1L;
        PayrollItem item = new PayrollItem();
        ItemDetailsDto dto = new ItemDetailsDto();

        when(payrollItemRepository.findAll(any(Specification.class))).thenReturn(List.of(item));
        when(payrollMapper.toItemDetailsDtos(anyList())).thenReturn(List.of(dto));

        List<ItemDetailsDto> result = payrollService.getItems(empId);

        Assertions.assertFalse(result.isEmpty());
    }

    @Test
    void processPayrollNonExempt_RegularHoliday_ShouldApplyTwoHundredPercentPremium() {
        LocalDate workDate = LocalDate.of(2026, 3, 2);
        Employee emp = buildNonExemptEmployee(1L, new BigDecimal("624000.00"));
        PayrollRun run = buildSingleDayRun(workDate);
        TemplateShiftSchedule schedule = buildSchedule(emp, workDate, LocalTime.of(8, 0), LocalTime.of(17, 0));
        List<AttendanceLog> logs = List.of(
                buildAttendance(1L, workDate, LocalTime.of(8, 0), "IN"),
                buildAttendance(1L, workDate, LocalTime.of(17, 0), "OUT")
        );
        HolidayLookup holiday = new HolidayLookup(Set.of(workDate), Set.of(workDate));
        stubCommonDependencies(logs, schedule, holiday);

        List<PayrollItem> items = new ArrayList<>();

        payrollService.processPayrollNonExempt(run, items, List.of(emp));

        Assertions.assertEquals(1, items.size());
        PayrollItem item = items.get(0);
        Assertions.assertEquals(1, item.getEarnings().size());
        Assertions.assertEquals(4, item.getDeductions().size());
        Assertions.assertTrue(item.getDeductions().stream().anyMatch(d -> d.getDeductionType() == DeductionType.SSS));
        Assertions.assertTrue(item.getDeductions().stream().anyMatch(d -> d.getDeductionType() == DeductionType.PHILHEALTH));
        Assertions.assertTrue(item.getDeductions().stream().anyMatch(d -> d.getDeductionType() == DeductionType.PAGIBIG));
        Assertions.assertTrue(item.getDeductions().stream().anyMatch(d -> d.getDeductionType() == DeductionType.BRACKET_LEVEL_THREE));

        EarningsLine holidayLine = item.getEarnings().get(0);
        assertBigDecimalEquals("8.00", holidayLine.getHours());
        assertBigDecimalEquals("500.00", holidayLine.getRate());
        assertBigDecimalEquals("4000.00", holidayLine.getAmount());
        Assertions.assertSame(item, holidayLine.getPayrollItem());

        assertBigDecimalEquals("30000.00", item.getGrossPay());
        assertBigDecimalEquals("25145.83", item.getNetPay());
    }

    @Test
    void processPayrollNonExempt_SpecialHoliday_ShouldApplyOneHundredThirtyPercentPremium() {
        LocalDate workDate = LocalDate.of(2026, 3, 2);
        Employee emp = buildNonExemptEmployee(1L, new BigDecimal("624000.00"));
        PayrollRun run = buildSingleDayRun(workDate);
        TemplateShiftSchedule schedule = buildSchedule(emp, workDate, LocalTime.of(8, 0), LocalTime.of(17, 0));
        List<AttendanceLog> logs = List.of(
                buildAttendance(1L, workDate, LocalTime.of(8, 0), "IN"),
                buildAttendance(1L, workDate, LocalTime.of(17, 0), "OUT")
        );
        HolidayLookup holiday = new HolidayLookup(Set.of(workDate), Set.of());
        stubCommonDependencies(logs, schedule, holiday);

        List<PayrollItem> items = new ArrayList<>();

        payrollService.processPayrollNonExempt(run, items, List.of(emp));

        Assertions.assertEquals(1, items.size());
        PayrollItem item = items.get(0);
        Assertions.assertEquals(1, item.getEarnings().size());
        Assertions.assertEquals(4, item.getDeductions().size());

        EarningsLine holidayLine = item.getEarnings().get(0);
        assertBigDecimalEquals("8.00", holidayLine.getHours());
        assertBigDecimalEquals("325.00", holidayLine.getRate());
        assertBigDecimalEquals("2600.00", holidayLine.getAmount());

        assertBigDecimalEquals("28600.00", item.getGrossPay());
        assertBigDecimalEquals("23745.83", item.getNetPay());
    }

    @Test
    void processPayrollNonExempt_NonHolidayEventTitle_ShouldNotApplyHolidayPremium() {
        LocalDate workDate = LocalDate.of(2026, 3, 2);
        Employee emp = buildNonExemptEmployee(1L, new BigDecimal("624000.00"));
        PayrollRun run = buildSingleDayRun(workDate);
        TemplateShiftSchedule schedule = buildSchedule(emp, workDate, LocalTime.of(8, 0), LocalTime.of(17, 0));
        List<AttendanceLog> logs = List.of(
                buildAttendance(1L, workDate, LocalTime.of(8, 0), "IN"),
                buildAttendance(1L, workDate, LocalTime.of(17, 0), "OUT")
        );
        stubCommonDependencies(logs, schedule, HolidayLookup.empty());

        List<PayrollItem> items = new ArrayList<>();

        payrollService.processPayrollNonExempt(run, items, List.of(emp));

        Assertions.assertEquals(1, items.size());
        PayrollItem item = items.get(0);
        Assertions.assertTrue(item.getEarnings().isEmpty());
        Assertions.assertEquals(4, item.getDeductions().size());
        assertBigDecimalEquals("26000.00", item.getGrossPay());
        assertBigDecimalEquals("21145.83", item.getNetPay());
    }

    @Test
    void processPayrollNonExempt_NoLogs_ShouldApplyAbsentDeduction() {
        LocalDate workDate = LocalDate.of(2026, 3, 2);
        Employee emp = buildNonExemptEmployee(1L, new BigDecimal("624000.00"));
        PayrollRun run = buildSingleDayRun(workDate);
        TemplateShiftSchedule schedule = buildSchedule(emp, workDate, LocalTime.of(8, 0), LocalTime.of(17, 0));

        stubCommonDependencies(List.of(), schedule, HolidayLookup.empty(), List.of(), List.of());

        List<PayrollItem> items = new ArrayList<>();
        payrollService.processPayrollNonExempt(run, items, List.of(emp));

        Assertions.assertEquals(1, items.size());
        PayrollItem item = items.get(0);

        DeductionsLine absentLine = item.getDeductions().stream()
                .filter(d -> d.getDeductionType() == DeductionType.ABSENT_DEDUCTION)
                .findFirst()
                .orElseThrow();
        assertBigDecimalEquals("2000.00", absentLine.getAmount());
        assertBigDecimalEquals("26000.00", item.getGrossPay());
        assertBigDecimalEquals("19145.83", item.getNetPay());
    }

    @Test
    void processPayrollNonExempt_LateOrEarlyOut_ShouldApplyLatePenalty() {
        LocalDate workDate = LocalDate.of(2026, 3, 2);
        Employee emp = buildNonExemptEmployee(1L, new BigDecimal("624000.00"));
        PayrollRun run = buildSingleDayRun(workDate);
        TemplateShiftSchedule schedule = buildSchedule(emp, workDate, LocalTime.of(8, 0), LocalTime.of(17, 0));
        List<AttendanceLog> logs = List.of(
                buildAttendance(1L, workDate, LocalTime.of(9, 0), "IN"),
                buildAttendance(1L, workDate, LocalTime.of(17, 0), "OUT")
        );

        stubCommonDependencies(logs, schedule, HolidayLookup.empty(), List.of(), List.of());

        List<PayrollItem> items = new ArrayList<>();
        payrollService.processPayrollNonExempt(run, items, List.of(emp));

        Assertions.assertEquals(1, items.size());
        PayrollItem item = items.get(0);

        DeductionsLine lateLine = item.getDeductions().stream()
                .filter(d -> d.getDeductionType() == DeductionType.LATE_PENALTY)
                .findFirst()
                .orElseThrow();
        assertBigDecimalEquals("250.00", lateLine.getAmount());
        assertBigDecimalEquals("26000.00", item.getGrossPay());
        assertBigDecimalEquals("20895.83", item.getNetPay());
    }

    @Test
    void processPayrollExempt_ShouldSetNetPayAsGrossMinusDeductions() {
        Employee exempt = new Employee();
        ReflectionTestUtils.setField(exempt, "employeeId", 9L);
        exempt.setExemptionStatus(ExemptionStatus.EXEMPT);
        exempt.setYearlySalary(new BigDecimal("624000.00"));
        PayrollRun run = buildSingleDayRun(LocalDate.of(2026, 3, 2));

        List<PayrollItem> items = payrollService.processPayrollExempt(run, List.of(exempt));

        Assertions.assertEquals(1, items.size());
        PayrollItem item = items.get(0);
        Assertions.assertEquals(1, item.getEarnings().size());
        Assertions.assertEquals(4, item.getDeductions().size());
        assertBigDecimalEquals("26000.00", item.getEarnings().get(0).getAmount());
        Assertions.assertFalse(item.getEarnings().get(0).getOvertime());
        Assertions.assertTrue(item.getDeductions().stream().anyMatch(d -> d.getDeductionType() == DeductionType.SSS));
        Assertions.assertTrue(item.getDeductions().stream().anyMatch(d -> d.getDeductionType() == DeductionType.PHILHEALTH));
        Assertions.assertTrue(item.getDeductions().stream().anyMatch(d -> d.getDeductionType() == DeductionType.PAGIBIG));
        Assertions.assertTrue(item.getDeductions().stream().anyMatch(d -> d.getDeductionType() == DeductionType.BRACKET_LEVEL_THREE));
        assertBigDecimalEquals("26000.00", item.getGrossPay());
        assertBigDecimalEquals("21145.83", item.getNetPay());
        assertBigDecimalEquals("21145.83", item.getGrossPay().subtract(
                item.getDeductions().stream()
                        .map(DeductionsLine::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
        ));
    }

    @Test
    void processPayrollNonExempt_ApprovedOvertime_ShouldCreateOvertimeEarningsLine() {
        LocalDate workDate = LocalDate.of(2026, 3, 2);
        Employee emp = buildNonExemptEmployee(1L, new BigDecimal("624000.00"));
        PayrollRun run = buildSingleDayRun(workDate);
        TemplateShiftSchedule schedule = buildSchedule(emp, workDate, LocalTime.of(8, 0), LocalTime.of(17, 0));
        List<AttendanceLog> logs = List.of(
                buildAttendance(1L, workDate, LocalTime.of(8, 0), "IN"),
                buildAttendance(1L, workDate, LocalTime.of(17, 0), "OUT")
        );
        OvertimeRequest ot = buildOvertime(1L, workDate, LocalTime.of(18, 0), LocalTime.of(20, 0));

        stubCommonDependencies(logs, schedule, HolidayLookup.empty(), List.of(ot), List.of());

        List<PayrollItem> items = new ArrayList<>();
        payrollService.processPayrollNonExempt(run, items, List.of(emp));

        Assertions.assertEquals(1, items.size());
        PayrollItem item = items.get(0);
        EarningsLine overtimeLine = item.getEarnings().stream()
                .filter(EarningsLine::getOvertime)
                .findFirst()
                .orElseThrow();

        assertBigDecimalEquals("2.00", overtimeLine.getHours());
        assertBigDecimalEquals("312.50", overtimeLine.getRate());
        assertBigDecimalEquals("625.00", overtimeLine.getAmount());
        assertBigDecimalEquals("26625.00", item.getGrossPay());
        assertBigDecimalEquals("21770.83", item.getNetPay());
    }

    private void assertBigDecimalEquals(String expected, BigDecimal actual) {
        Assertions.assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }

    private void stubCommonDependencies(
            List<AttendanceLog> logs,
            TemplateShiftSchedule schedule,
            HolidayLookup holidayLookup
    ) {
        stubCommonDependencies(logs, schedule, holidayLookup, List.of(), List.of());
    }

    private void stubCommonDependencies(
            List<AttendanceLog> logs,
            TemplateShiftSchedule schedule,
            HolidayLookup holidayLookup,
            List<OvertimeRequest> overtimeRequests,
            List<LeaveRequest> leaves
    ) {
        when(defaultProperties.getTimeZone()).thenReturn(ZONE);
        when(attendanceLogRepository.findByEmployeeIdInAndTimestampBetween(anyList(), any(Instant.class), any(Instant.class), any()))
                .thenReturn(logs);
        when(overtimeRequestRepository.findAll(any(Specification.class)))
                .thenReturn(overtimeRequests);
        when(templateShiftRepository.findByEmployee_EmployeeIdIn(anyList()))
                .thenReturn(List.of(schedule));
        when(leaveRequestRepository.findApprovedLeavesForPeriod(anyList(), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(leaves);
        when(holidayService.getHolidayLookup(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(holidayLookup);
    }

    private PayrollRun buildSingleDayRun(LocalDate date) {
        PayrollRun run = new PayrollRun();
        run.setPeriodStart(date);
        run.setPeriodEnd(date);
        return run;
    }

    private Employee buildNonExemptEmployee(Long employeeId, BigDecimal yearlySalary) {
        Employee emp = new Employee();
        ReflectionTestUtils.setField(emp, "employeeId", employeeId);
        emp.setExemptionStatus(ExemptionStatus.NON_EXEMPT);
        emp.setYearlySalary(yearlySalary);
        return emp;
    }

    private TemplateShiftSchedule buildSchedule(Employee emp, LocalDate date, LocalTime start, LocalTime end) {
        TemplateShiftSchedule schedule = new TemplateShiftSchedule();
        schedule.setEmployee(emp);
        schedule.setDayOfWeek(date.getDayOfWeek());
        schedule.setStartTime(start);
        schedule.setEndTime(end);
        return schedule;
    }

    private AttendanceLog buildAttendance(Long empId, LocalDate date, LocalTime time, String direction) {
        AttendanceLog log = new AttendanceLog();
        log.setEmployeeId(empId);
        log.setDirection(direction);
        log.setTimestamp(date.atTime(time).atZone(ZONE).toInstant());
        return log;
    }

    private OvertimeRequest buildOvertime(
            Long empId,
            LocalDate workDate,
            LocalTime start,
            LocalTime end
    ) {
        OvertimeRequest request = new OvertimeRequest();
        ReflectionTestUtils.setField(request, "employeeId", empId);
        request.setWorkDate(workDate);
        request.setStartTime(workDate.atTime(start));
        request.setEndTime(workDate.atTime(end));
        request.setOvertimeType(EarningsType.OVERTIME);
        return request;
    }

}
