package org.example.maridone.schedule.shift;

import org.example.maridone.core.employee.Employee;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class TemplateShiftScheduleTest {

    @Test
    void getEmployeeId_ShouldFallbackToLinkedEmployeeId_WhenShadowFieldIsNull() {
        Employee employee = new Employee();
        ReflectionTestUtils.setField(employee, "employeeId", 42L);

        TemplateShiftSchedule schedule = new TemplateShiftSchedule();
        schedule.setEmployee(employee);

        Assertions.assertEquals(42L, schedule.getEmployeeId());
    }
}
