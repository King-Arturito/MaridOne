package org.example.maridone.schedule.shift;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.DayOfWeek;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface TemplateShiftRepository extends JpaRepository<TemplateShiftSchedule, Long> {
    List<TemplateShiftSchedule> findAllByEmployeeId(Long empId);

    Optional<TemplateShiftSchedule> findByEmployeeIdAndDayOfWeek(Long empId, DayOfWeek dayOfWeek);

    boolean existsByShiftIdAndEmployee_Manager_EmployeeId(Long shiftId, Long managerId);

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
