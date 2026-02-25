package org.example.maridone.schedule.shift;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface DailyShiftRepository extends JpaRepository<DailyShiftSchedule, Long>, JpaSpecificationExecutor<DailyShiftSchedule> {
}
