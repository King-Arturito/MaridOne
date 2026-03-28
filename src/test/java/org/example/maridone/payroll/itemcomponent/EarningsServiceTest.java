package org.example.maridone.payroll.itemcomponent;

import org.example.maridone.config.PayrollConfig;
import org.example.maridone.core.employee.Employee;
import org.example.maridone.holiday.HolidayLookup;
import org.example.maridone.holiday.HolidayService;
import org.example.maridone.payroll.PayrollCalculator;
import org.example.maridone.payroll.dto.EarningsDto;
import org.example.maridone.payroll.item.PayrollItem;
import org.example.maridone.payroll.item.component.EarningsLine;
import org.example.maridone.payroll.item.component.EarningsRepository;
import org.example.maridone.payroll.item.component.EarningsService;
import org.example.maridone.payroll.mapper.PayrollMapper;
import org.example.maridone.payroll.run.PayrollRun;
import org.example.maridone.schedule.shift.DailyShiftRepository;
import org.example.maridone.schedule.shift.DailyShiftSchedule;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EarningsServiceTest {

    @Mock private EarningsRepository earningsRepository;
    @Mock private PayrollMapper payrollMapper;
    @Mock private HolidayService holidayService;
    @Mock private DailyShiftRepository dailyShiftRepository;
    @Mock private PayrollCalculator payrollCalculator;
    @Mock private PayrollConfig payrollConfig;

    @InjectMocks
    private EarningsService earningsService;

    @Test
    void getEarnings_ShouldReturnBasicPayDescription() {
        Long itemId = 50L;
        EarningsLine line = new EarningsLine();
        ReflectionTestUtils.setField(line, "earningsId", 1L);
        line.setHours(BigDecimal.ZERO);
        line.setRate(BigDecimal.ZERO);
        line.setAmount(new BigDecimal("1200.00"));
        line.setOvertime(false);
        EarningsDto dto = new EarningsDto();

        when(earningsRepository.findByPayrollItem_ItemId(itemId)).thenReturn(List.of(line));
        when(payrollMapper.toEarningsDto(any(EarningsLine.class))).thenReturn(dto);

        List<EarningsDto> result = earningsService.getEarnings(itemId);

        Assertions.assertFalse(result.isEmpty());
        Assertions.assertEquals("Basic Pay", result.get(0).getDescription());
    }

    @Test
    void getEarnings_ShouldReturnApprovedOvertimeDescription() {
        Long itemId = 52L;
        EarningsLine line = new EarningsLine();
        ReflectionTestUtils.setField(line, "earningsId", 2L);
        line.setHours(new BigDecimal("2.00"));
        line.setRate(new BigDecimal("150.00"));
        line.setAmount(new BigDecimal("300.00"));
        line.setOvertime(true);
        EarningsDto dto = new EarningsDto();

        when(earningsRepository.findByPayrollItem_ItemId(itemId)).thenReturn(List.of(line));
        when(payrollMapper.toEarningsDto(line)).thenReturn(dto);

        List<EarningsDto> result = earningsService.getEarnings(itemId);

        Assertions.assertEquals("Approved Overtime", result.get(0).getDescription());
    }

    @Test
    void getEarnings_ShouldLabelRegularHolidayPremium() {
        Long itemId = 51L;
        LocalDate earningDate = LocalDate.of(2026, 4, 9);

        Employee employee = new Employee();
        ReflectionTestUtils.setField(employee, "employeeId", 6L);
        employee.setYearlySalary(new BigDecimal("1200000"));

        PayrollRun run = new PayrollRun();
        run.setPeriodStart(LocalDate.of(2026, 3, 26));
        run.setPeriodEnd(LocalDate.of(2026, 4, 10));

        PayrollItem item = new PayrollItem();
        item.setEmployee(employee);
        item.setPayrollRun(run);

        EarningsLine line = new EarningsLine();
        ReflectionTestUtils.setField(line, "earningsId", 3L);
        line.setPayrollItem(item);
        line.setEarningsDate(earningDate);
        line.setHours(new BigDecimal("8.00"));
        line.setRate(new BigDecimal("480.77"));
        line.setAmount(new BigDecimal("3846.16"));
        line.setOvertime(false);

        EarningsDto dto = new EarningsDto();
        DailyShiftSchedule shift = new DailyShiftSchedule();
        shift.setStartDateTime(LocalDateTime.of(earningDate, java.time.LocalTime.of(8, 0)));
        shift.setEndDateTime(LocalDateTime.of(earningDate, java.time.LocalTime.of(17, 0)));

        when(earningsRepository.findByPayrollItem_ItemId(itemId)).thenReturn(List.of(line));
        when(payrollMapper.toEarningsDto(line)).thenReturn(dto);
        when(holidayService.getHolidayLookup(run.getPeriodStart(), run.getPeriodEnd()))
                .thenReturn(new HolidayLookup(Set.of(earningDate), Set.of(earningDate)));
        when(dailyShiftRepository.findAllByEmployeeIdsAndPeriod(
                List.of(employee.getEmployeeId()),
                run.getPeriodStart().atStartOfDay(),
                run.getPeriodEnd().plusDays(1).atStartOfDay()
        )).thenReturn(List.of(shift));
        when(payrollCalculator.computeHourlyRate(employee.getYearlySalary())).thenReturn(new BigDecimal("480.768750"));
        when(payrollCalculator.resolveHolidayOrRestDayPremiumRate(false, true, true)).thenReturn(BigDecimal.ONE);
        when(payrollCalculator.resolveHolidayOrRestDayMultiplier(false, true, true)).thenReturn(new BigDecimal("2.00"));
        when(payrollConfig.getNightDifferentialMultiplier()).thenReturn(new BigDecimal("0.10"));
        when(payrollConfig.getOvertimeMultiplier()).thenReturn(new BigDecimal("1.25"));

        List<EarningsDto> result = earningsService.getEarnings(itemId);

        Assertions.assertEquals("Regular Holiday Premium", result.get(0).getDescription());
    }

    @Test
    void getEarnings_ShouldKeepBaseDescriptionAfterDirectCorrection() {
        Long itemId = 60L;

        EarningsLine baseLine = new EarningsLine();
        ReflectionTestUtils.setField(baseLine, "earningsId", 18L);
        baseLine.setHours(BigDecimal.ZERO);
        baseLine.setRate(BigDecimal.ZERO);
        baseLine.setAmount(new BigDecimal("650.00"));
        baseLine.setOvertime(false);

        EarningsDto baseDto = new EarningsDto();
        baseDto.setAmount(new BigDecimal("650.00"));

        when(earningsRepository.findByPayrollItem_ItemId(itemId)).thenReturn(List.of(baseLine));
        when(payrollMapper.toEarningsDto(baseLine)).thenReturn(baseDto);

        List<EarningsDto> result = earningsService.getEarnings(itemId);

        Assertions.assertEquals(1, result.size());
        Assertions.assertEquals("Basic Pay", result.get(0).getDescription());
        Assertions.assertEquals(new BigDecimal("650.00"), result.get(0).getAmount());
    }
}
