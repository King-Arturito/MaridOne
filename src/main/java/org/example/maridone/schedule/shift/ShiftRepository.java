package org.example.maridone.schedule.shift;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ShiftRepository extends JpaRepository<ShiftSchedule, Long> {
    List<ShiftSchedule> findAllByEmployeeId(Long empId);

    List<ShiftSchedule> findByEmployee_EmployeeIdIn(List<Long> employeeIds);
}
