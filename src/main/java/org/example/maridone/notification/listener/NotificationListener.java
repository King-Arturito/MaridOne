package org.example.maridone.notification.listener;

import org.example.maridone.notification.NotificationService;
import org.example.maridone.notification.event.BulkNotificationCreatedEvent;
import org.example.maridone.notification.event.NotificationCreatedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class NotificationListener {
    private final NotificationService notificationService;
    public NotificationListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void notify(NotificationCreatedEvent event) {
        notificationService.notifyUsers(event.employeeIds(), event.message(), event.importance());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void notifyRole(BulkNotificationCreatedEvent event) {
        notificationService.notifyRole(event.message(), event.importance(), event.targetRole());
    }
}
