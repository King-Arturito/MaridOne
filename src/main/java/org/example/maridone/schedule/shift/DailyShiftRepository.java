package org.example.maridone.schedule.shift;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface DailyShiftRepository extends JpaRepository<DailyShiftSchedule, Long>, JpaSpecificationExecutor<DailyShiftSchedule> {

    List<DailyShiftSchedule> findByTemplateShiftSchedule_EmployeeIdIn(List<Long> employeeIds);

    @Query("""
        SELECT d
        FROM DailyShiftSchedule d
        WHERE d.templateShiftSchedule.employeeId IN :employeeIds
          AND d.startDateTime >= :periodStart
          AND d.startDateTime < :periodEndExclusive
        ORDER BY d.startDateTime
        """)
    List<DailyShiftSchedule> findAllByEmployeeIdsAndPeriod(
            @Param("employeeIds") List<Long> employeeIds,
            @Param("periodStart") LocalDateTime periodStart,
            @Param("periodEndExclusive") LocalDateTime periodEndExclusive
    );
}
