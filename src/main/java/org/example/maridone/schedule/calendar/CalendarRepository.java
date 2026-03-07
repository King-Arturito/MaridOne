package org.example.maridone.schedule.calendar;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface CalendarRepository extends JpaRepository<CompanyCalendar,Long>, JpaSpecificationExecutor<CompanyCalendar> {

    @Query("""
        SELECT c FROM CompanyCalendar c
        WHERE c.isActive = true
        AND c.startDate <= :periodEnd
        AND c.endDate >= :periodStart
    """)
    List<CompanyCalendar> findActiveEventsOverlappingPeriod(
            @Param("periodStart") Instant periodStart,
            @Param("periodEnd") Instant periodEnd
    );
}
