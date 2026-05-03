package org.example.maridone.document.listener;

import org.example.maridone.document.event.S3CleanupEvent;
import org.example.maridone.document.event.S3RollbackEvent;
import org.example.maridone.document.storage.CloudStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class S3CleanupListener {
    private final CloudStorageService cloudStorageService;
    private static final Logger logger = LoggerFactory.getLogger(S3CleanupListener.class);

    public S3CleanupListener(CloudStorageService cloudStorageService) {
        this.cloudStorageService = cloudStorageService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
    public void handleS3RollbackEvent(S3RollbackEvent s3RollbackEvent) {
        for (String key : s3RollbackEvent.s3Keys()) {
            try {
                cloudStorageService.delete(key);
            } catch (Exception e) {
                logger.error("failed to rollback key: " + key);
            }
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleS3CleanupEvent(S3CleanupEvent s3CleanupEvent) {
        for (String key :  s3CleanupEvent.s3Keys()) {
            try {
              cloudStorageService.delete(key);
            } catch (Exception e) {
                logger.error("failed to clean up key: " + key);
            }
        }
    }
}
