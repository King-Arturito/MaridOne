package org.example.maridone.log;

import org.example.maridone.enums.Activity;
import org.example.maridone.log.activity.ActivityLog;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long>, JpaSpecificationExecutor<ActivityLog> {
    List<ActivityLog> findByActivityTypeAndMessageStartingWithOrderByActivityIdAsc(Activity activityType, String prefix);

    @Query("""
        select a from ActivityLog a
        where a.activityType = :activityType
          and a.detailPayload like concat('%Employee #', :employeeId, '%')
        order by a.timestamp desc, a.activityId desc
    """)
    List<ActivityLog> findByActivityTypeAndEmployeeReference(@Param("activityType") Activity activityType,
                                                             @Param("employeeId") Long employeeId);
}
