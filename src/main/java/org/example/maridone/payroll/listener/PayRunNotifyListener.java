package org.example.maridone.payroll.listener;

import org.example.maridone.notification.NotificationService;
import org.example.maridone.payroll.event.PayRunNotifyEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.logging.Logger;

@Component
public class PayRunNotifyListener {
    private final NotificationService notificationService;
    private static final Logger logger = Logger.getLogger(PayRunNotifyListener.class.getName());

    public PayRunNotifyListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleNotifications(PayRunNotifyEvent event) {
        notificationService.notifyRun(event.employeeIds(), event.message(), event.payId());
        logger.info("Payroll Run notifications sent.");
    }
}
