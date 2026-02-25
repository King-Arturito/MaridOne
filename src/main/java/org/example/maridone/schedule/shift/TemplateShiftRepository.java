package org.example.maridone.schedule.shift;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.DayOfWeek;
import java.util.Collection;
import java.util.List;

@Repository
public interface TemplateShiftRepository extends JpaRepository<TemplateShiftSchedule, Long> {
    List<TemplateShiftSchedule> findAllByEmployeeId(Long empId);

    List<TemplateShiftSchedule> findByEmployee_EmployeeIdIn(List<Long> employeeIds);

    List<TemplateShiftSchedule> findAllByEmployeeIdIn(Collection<Long> employeeIds);

    @Query("""
    select t from TemplateShiftSchedule t
    join fetch t.employee
    where t.employee.employeeId in :employeeIds
    and t.dayOfWeek = :dayOfWeek""")
    List<TemplateShiftSchedule> findAllByEmployeeIdInAndDayOfWeek(
            @Param("employeeIds") Collection<Long> employeeIds,
            @Param("dayOfWeek") DayOfWeek targetDay);
}
