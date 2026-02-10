package org.example.maridone.notification;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
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
}