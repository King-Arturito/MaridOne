package org.example.maridone.notification;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.example.maridone.exception.notfound.RequestNotFoundException;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    void getUserNotifications_ShouldReturnPage() {
        String username = "niko";
        Notification note = new Notification();
        Page<Notification> page = new PageImpl<>(List.of(note));

        when(notificationRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        Page<Notification> result = notificationService.getUserNotifications(username, false, Pageable.unpaged());

        Assertions.assertFalse(result.isEmpty());
    }

    @Test
    void readNotification_ShouldMarkOwnedNotificationAsRead() {
        String username = "niko";
        Long notificationId = 12L;
        Notification note = new Notification();
        note.setReadStatus(false);

        when(notificationRepository.findByNotificationIdAndEmployee_UserAccount_Username(notificationId, username))
                .thenReturn(Optional.of(note));

        notificationService.readNotification(username, notificationId);

        Assertions.assertTrue(note.isReadStatus());
        verify(notificationRepository).save(note);
    }

    @Test
    void readNotification_ShouldThrowWhenNotificationDoesNotBelongToUser() {
        String username = "niko";
        Long notificationId = 12L;

        when(notificationRepository.findByNotificationIdAndEmployee_UserAccount_Username(notificationId, username))
                .thenReturn(Optional.empty());

        Assertions.assertThrows(
                RequestNotFoundException.class,
                () -> notificationService.readNotification(username, notificationId)
        );
        verify(notificationRepository, never()).save(any(Notification.class));
    }
}
