package org.example.maridone.notification;

import org.example.maridone.annotation.AutoScheduled;
import org.example.maridone.annotation.ExecutionTime;
import org.example.maridone.common.CommonSpecs;
import org.example.maridone.core.employee.Employee;
import org.example.maridone.enums.EmploymentStatus;
import org.example.maridone.exception.notfound.RequestNotFoundException;
import org.example.maridone.exception.unauthorized.InvalidActionException;
import org.example.maridone.notification.spec.NotificationSpecs;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional(readOnly = true)
    @ExecutionTime
    public Page<Notification> getUserNotifications(String username, Boolean status, Pageable pageable) {
        Specification<Notification> spec = Specification.allOf(
                CommonSpecs.fieldEquals("readStatus", status),
                NotificationSpecs.hasUsername(username)
        );
        return notificationRepository.findAll(spec, pageable);
    }


    @Transactional
    @ExecutionTime
    public void readNotification(String username, Long notificationId) {
        if (notificationId == null) {
            throw new InvalidActionException("Notification ID is required.");
        }

        Notification notif = notificationRepository
                .findByNotificationIdAndEmployee_UserAccount_Username(notificationId, username)
                .orElseThrow(() -> new RequestNotFoundException("Notification", notificationId));

        notif.setReadStatus(true);
        notificationRepository.save(notif);
    }

    @Transactional
    @ExecutionTime
    public void createNotification(Employee employee, String message, String importance) {
        if (employee == null
                || employee.getUserAccount() == null
                || employee.getEmploymentStatus() == EmploymentStatus.TERMINATED
                || employee.getEmploymentStatus() == EmploymentStatus.SUSPENDED
                || !StringUtils.hasText(message)
                || !StringUtils.hasText(importance)) {
            return;
        }

        Notification notif = new Notification();
        notif.setEmployee(employee);
        notif.setMessage(message.trim());
        notif.setImportance(importance.trim());
        notif.setReadStatus(false);
        notif.setCreatedAt(Instant.now());
        notificationRepository.save(notif);
    }

    @Transactional
    @AutoScheduled
    public void bulkClean(Duration days){
        Specification<Notification> spec = Specification.allOf(
                CommonSpecs.fieldEquals("readStatus", true),
                CommonSpecs.fieldLessThanOrEqual("createdAt", Instant.now().minus(days)),
                CommonSpecs.fieldEquals("importance", "LOW")
        );
        notificationRepository.delete(spec);
    }
}
