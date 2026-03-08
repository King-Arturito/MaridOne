package org.example.maridone.notification;

import org.example.maridone.annotation.ExecutionTime;
import org.example.maridone.common.CommonSpecs;
import org.example.maridone.notification.spec.NotificationSpecs;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
    public void readNotification(Long notificationId) {
        Notification notif = notificationRepository.findById(notificationId).orElse(null);
        if (notif != null) {
            notif.setReadStatus(true);
            notificationRepository.save(notif);
        }
    }
}