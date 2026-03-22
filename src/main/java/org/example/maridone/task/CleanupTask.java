package org.example.maridone.task;

import org.example.maridone.config.DataRetentionConfig;
import org.example.maridone.notification.NotificationService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CleanupTask {

    private final NotificationService notificationService;
    private final DataRetentionConfig dataRetentionConfig;

    public CleanupTask(NotificationService notificationService, DataRetentionConfig dataRetentionConfig) {
        this.notificationService = notificationService;
        this.dataRetentionConfig = dataRetentionConfig;
    }


    //Scheduled at 00:00 daily
    //deletes old low priority notifications (60 days)
    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Manila")
    public void cleanNotification(){
        notificationService.bulkClean(dataRetentionConfig.getNotificationMaxAge());
    }
}
