package org.example.maridone.schedule.shift;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ShiftRepository extends JpaRepository<TemplateShiftSchedule, Long> {
    List<TemplateShiftSchedule> findAllByEmployeeId(Long empId);

    List<TemplateShiftSchedule> findByEmployee_EmployeeIdIn(List<Long> employeeIds);
}
