package org.example.maridone.schedule.shift;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DailyShiftRepository extends JpaRepository<DailyShiftSchedule, Long>, JpaSpecificationExecutor<DailyShiftSchedule> {

    List<DailyShiftSchedule> findByEmployee_EmployeeIdIn(List<Long> employeeIds);
}
