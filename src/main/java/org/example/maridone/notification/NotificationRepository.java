package org.example.maridone.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long>, JpaSpecificationExecutor<Notification> {
    Optional<Notification> findByNotificationIdAndEmployee_UserAccount_Username(Long notificationId, String username);

    @Modifying
    @Query(
            "delete from Notification n where " +
                    "n.createdAt <= :cutoff " +
                    "and n.importance = 'LOW' " +
                    "and  n.readStatus = true"
    )
    void bulkDelete(@Param("cutoff") Instant cutoff);
}
