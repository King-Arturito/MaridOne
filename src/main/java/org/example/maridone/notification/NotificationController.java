package org.example.maridone.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public Page<Notification> getNotifications
            (Authentication authentication,
             @RequestParam(required = false) Boolean status,
             @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return notificationService.getUserNotifications(authentication.getName(), status, pageable);
    }

    @PreAuthorize("isAuthenticated()")
    @PatchMapping
    public void readNotification(Long notificationId) {
        notificationService.readNotification(notificationId);
    }
}
