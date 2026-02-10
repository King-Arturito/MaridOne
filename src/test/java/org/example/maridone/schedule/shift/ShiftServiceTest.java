package org.example.maridone.schedule.shift;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.example.maridone.core.employee.Employee;
import org.example.maridone.core.employee.EmployeeRepository;
import org.example.maridone.enums.EarningsType;
import org.example.maridone.schedule.dto.ShiftRequestDto;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ShiftServiceTest {

    @Mock private ShiftRepository shiftRepository;
    @Mock private EmployeeRepository employeeRepository;

    @InjectMocks
    private ShiftService shiftService;

    @Test
    void createShifts_ShouldSave7Days() {
        Long empId = 1L;
        Employee emp = new Employee();
        ReflectionTestUtils.setField(emp, "employeeId", empId);

        when(employeeRepository.findById(empId)).thenReturn(Optional.of(emp));
        when(shiftRepository.saveAll(anyList())).thenReturn(Collections.emptyList());

        shiftService.createShifts(empId);

        verify(shiftRepository).saveAll(anyList());
    }

    @Test
    void getShiftSchedule_ShouldReturnList_WhenFound() {
        Long empId = 1L;
        Employee emp = new Employee();
        ReflectionTestUtils.setField(emp, "employeeId", empId);
        
        when(employeeRepository.findById(empId)).thenReturn(Optional.of(emp));
        when(shiftRepository.findAllByEmployeeId(empId)).thenReturn(List.of(new ShiftSchedule()));

        List<ShiftSchedule> result = shiftService.getShiftSchedule(empId);
        Assertions.assertFalse(result.isEmpty());
    }

    @Test
    void addShiftSchedule_ShouldSave() {
        Long empId = 1L;
        ShiftRequestDto dto = new ShiftRequestDto();
        
        ReflectionTestUtils.setField(dto, "empId", empId);
        
        dto.setStartTime(LocalTime.of(9, 0));
        dto.setEndTime(LocalTime.of(18, 0));
        dto.setDayOfWeek(DayOfWeek.MONDAY);
        dto.setEarningsType(EarningsType.BASIC);

        Employee emp = new Employee();
        ReflectionTestUtils.setField(emp, "employeeId", empId);

        when(employeeRepository.findById(empId)).thenReturn(Optional.of(emp));
        when(shiftRepository.save(any(ShiftSchedule.class))).thenAnswer(i -> i.getArgument(0));

        ShiftSchedule result = shiftService.addShiftSchedule(dto);
       
        Assertions.assertNotNull(result);
        Assertions.assertEquals(DayOfWeek.MONDAY, result.getDayOfWeek());
    }
}